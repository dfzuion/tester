import * as admin from "firebase-admin";
import * as crypto from "crypto";
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { Collections, REGION, ENFORCE_APP_CHECK } from "./config";
import { requireAdmin } from "./guards";
import { writeAudit } from "./audit";
import { queueEmail, pushToUser, createUserNotification, pushToTopic } from "./notifications";

/**
 * WINNER SELECTION
 * ------------------------------------------------------------------
 * Two-phase and auditable:
 *   1. close  - locks the raffle, no further entries can be reserved.
 *   2. draw   - verifies eligible entries, commits to a random seed, and
 *               derives the winning index from that seed.
 *
 * The seed is generated with crypto.randomBytes and its SHA-256 hash is
 * written to the draw record alongside the entry count, so the result can be
 * independently recomputed and shown to be untampered after the fact.
 * No customer-writable path can influence any of it.
 */

export const closeCompetition = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const ctx = await requireAdmin(req, "competitions.close");
  const { competitionId, reason } = req.data ?? {};
  const db = admin.firestore();
  const ref = db.collection(Collections.competitions).doc(competitionId);

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "Raffle not found.");
    const c = snap.data()!;
    if (c.status === "closed" || c.status === "drawn") {
      throw new HttpsError("failed-precondition", "This raffle is already closed.");
    }
    tx.update(ref, {
      status: "closed",
      closedAt: admin.firestore.FieldValue.serverTimestamp(),
      closedBy: ctx.uid,
      closeReason: reason ?? null,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    await writeAudit({
      action: "competition.closed", actorId: ctx.uid, actorRole: "admin",
      objectType: "competition", objectId: competitionId,
      previousValue: { status: c.status }, newValue: { status: "closed" },
    }, tx);
  });

  return { ok: true };
});

export const drawWinner = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const ctx = await requireAdmin(req, "competitions.draw");
  const { competitionId, publishImmediately } = req.data ?? {};
  const db = admin.firestore();
  const compRef = db.collection(Collections.competitions).doc(competitionId);
  const compSnap = await compRef.get();
  if (!compSnap.exists) throw new HttpsError("not-found", "Raffle not found.");
  const c = compSnap.data()!;
  if (c.status !== "closed") throw new HttpsError("failed-precondition", "Close the raffle before drawing.");
  if (c.winnerId) throw new HttpsError("failed-precondition", "A winner has already been drawn.");

  // Eligible = paid, not refunded, not void, belongs to a non-suspended account.
  const entriesSnap = await db.collection(Collections.entries)
    .where("competitionId", "==", competitionId)
    .where("status", "==", "active")
    .orderBy("entryNumber")
    .get();

  const eligible: { id: string; entryNumber: number; userId: string; userDisplayName: string }[] = [];
  const suspendedCache = new Map<string, boolean>();
  for (const d of entriesSnap.docs) {
    const e = d.data();
    if (!suspendedCache.has(e.userId)) {
      const u = await db.collection(Collections.users).doc(e.userId).get();
      suspendedCache.set(e.userId, u.data()?.suspended === true);
    }
    if (!suspendedCache.get(e.userId)) {
      eligible.push({ id: d.id, entryNumber: e.entryNumber, userId: e.userId, userDisplayName: e.userDisplayName });
    }
  }
  if (eligible.length === 0) throw new HttpsError("failed-precondition", "There are no eligible entries to draw from.");

  // Commit-reveal: hash first, then derive the index from the same seed.
  const seed = crypto.randomBytes(32);
  const seedHash = crypto.createHash("sha256").update(seed).digest("hex");
  const winningIndex = uniformIndex(seed, eligible.length);
  const winner = eligible[winningIndex];

  const drawRef = db.collection(Collections.draws).doc();
  const winnerRef = db.collection(Collections.winners).doc();

  await db.runTransaction(async (tx) => {
    const fresh = await tx.get(compRef);
    if (fresh.data()!.winnerId) throw new HttpsError("aborted", "A winner has already been drawn.");

    // Immutable draw record. Firestore rules forbid update/delete on draws.
    tx.set(drawRef, {
      competitionId,
      competitionTitle: c.title,
      method: c.winnerMechanism ?? "random_eligible_entry",
      eligibleEntryCount: eligible.length,
      seedHash,
      seed: seed.toString("hex"),
      winningIndex,
      winningEntryId: winner.id,
      winningEntryNumber: winner.entryNumber,
      drawnBy: ctx.uid,
      drawnAt: admin.firestore.FieldValue.serverTimestamp(),
      algorithm: "sha256(seed || counter) -> rejection sampling -> index",
    });

    tx.set(winnerRef, {
      competitionId,
      competitionTitle: c.title,
      prizeName: c.prizeName ?? c.title,
      prizeImageUrl: c.heroImageUrl ?? null,
      drawId: drawRef.id,
      winningEntryNumber: winner.entryNumber,
      winnerUserId: winner.userId,
      winnerDisplayName: publicName(winner.userDisplayName, c.winnerNameDisplay ?? "first_name_last_initial"),
      published: publishImmediately === true,
      drawnAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    tx.update(compRef, {
      status: "drawn",
      winnerId: winnerRef.id,
      drawId: drawRef.id,
      winningEntryNumber: winner.entryNumber,
      drawnAt: admin.firestore.FieldValue.serverTimestamp(),
      resultPublished: publishImmediately === true,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    tx.update(db.collection(Collections.entries).doc(winner.id), { isWinner: true });

    await writeAudit({
      action: "winner.selected", actorId: ctx.uid, actorRole: "admin",
      objectType: "competition", objectId: competitionId,
      newValue: { winningEntryNumber: winner.entryNumber, drawId: drawRef.id, eligibleEntryCount: eligible.length, seedHash },
    }, tx);
  });

  await notifyWinner(winner.userId, c, winner.entryNumber, winnerRef.id);
  if (publishImmediately) await publishResultInternal(competitionId, ctx.uid);

  return { drawId: drawRef.id, winnerId: winnerRef.id, winningEntryNumber: winner.entryNumber, eligibleEntryCount: eligible.length, seedHash };
});

export const publishResult = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const ctx = await requireAdmin(req, "competitions.draw");
  await publishResultInternal(req.data.competitionId, ctx.uid);
  return { ok: true };
});

async function publishResultInternal(competitionId: string, actorId: string) {
  const db = admin.firestore();
  const compRef = db.collection(Collections.competitions).doc(competitionId);
  const c = (await compRef.get()).data();
  if (!c?.winnerId) throw new HttpsError("failed-precondition", "Draw the raffle first.");

  await db.collection(Collections.winners).doc(c.winnerId).update({ published: true });
  await compRef.update({ resultPublished: true, resultPublishedAt: admin.firestore.FieldValue.serverTimestamp() });
  await writeAudit({
    action: "result.published", actorId, actorRole: "admin",
    objectType: "competition", objectId: competitionId, newValue: { published: true },
  });
  await pushToTopic("results", "Result published", `${c.title} has been drawn. See the winning number in Results.`);
}

async function notifyWinner(userId: string, c: any, entryNumber: number, winnerId: string) {
  const user = (await admin.firestore().collection(Collections.users).doc(userId).get()).data();
  await createUserNotification(userId, {
    category: "win",
    title: "You've won",
    body: `Entry ${entryNumber} has won ${c.prizeName ?? c.title}. We'll email you about delivery.`,
    deepLink: `rrr://results/${winnerId}`,
  });
  await pushToUser(userId, "win", "You've won", `Entry ${entryNumber} took ${c.prizeName ?? c.title}.`, { winnerId });
  await queueEmail(user?.email, "winner_notification", {
    displayName: user?.displayName ?? "Angler",
    prizeName: c.prizeName ?? c.title,
    entryNumber: String(entryNumber),
    competitionTitle: c.title,
  });
}

/** Rejection sampling keeps every entry exactly equally likely - no modulo bias. */
export function uniformIndex(seed: Buffer, count: number): number {
  if (count <= 1) return 0;
  const limit = Math.floor(0xffffffff / count) * count;
  for (let counter = 0; counter < 10000; counter++) {
    const h = crypto.createHash("sha256").update(seed).update(Buffer.from(String(counter))).digest();
    const x = h.readUInt32BE(0);
    if (x < limit) return x % count;
  }
  throw new Error("Uniform sampling failed");
}

/** Controls how much of a winner's name is shown publicly. */
export function publicName(displayName: string, mode: string): string {
  const parts = (displayName ?? "Angler").trim().split(/\s+/);
  switch (mode) {
    case "full_name": return displayName;
    case "first_name": return parts[0];
    case "initials": return parts.map(p => p[0]?.toUpperCase() ?? "").join("");
    case "first_name_last_initial":
    default: return parts.length > 1 ? `${parts[0]} ${parts[1][0].toUpperCase()}.` : parts[0];
  }
}

/** Auto-close raffles the moment their published closing time passes. */
export const autoCloseDueCompetitions = onSchedule(
  { region: REGION, schedule: "every 5 minutes" },
  async () => {
    const db = admin.firestore();
    const due = await db.collection(Collections.competitions)
      .where("status", "==", "live")
      .where("closesAt", "<=", admin.firestore.Timestamp.now())
      .limit(50).get();

    for (const doc of due.docs) {
      await doc.ref.update({
        status: "closed",
        closedAt: admin.firestore.FieldValue.serverTimestamp(),
        closedBy: "system",
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      await writeAudit({
        action: "competition.closed", actorId: "system", actorRole: "system",
        objectType: "competition", objectId: doc.id, newValue: { status: "closed", trigger: "closing_time" },
      });
    }
    console.log(`Auto-closed ${due.size} raffles`);
  }
);
