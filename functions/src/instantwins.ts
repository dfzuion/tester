import * as admin from "firebase-admin";
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { Collections, REGION, ENFORCE_APP_CHECK } from "./config";
import { requireAdmin } from "./guards";
import { writeAudit } from "./audit";
import { pushToUser, createUserNotification } from "./notifications";

/**
 * INSTANT WINS
 * ------------------------------------------------------------------
 * Each prize is sealed to one specific entry number the moment it is created,
 * chosen at random from the numbers nobody has bought yet. Nothing is decided
 * at purchase time, so:
 *
 *   - the outcome cannot depend on who is buying, or when;
 *   - two people can never be awarded the same prize, because a prize is one
 *     document and claiming it is a transactional read-modify-write;
 *   - the full mapping exists before the raffle opens, so a dispute can be
 *     settled from the audit trail rather than from a log of dice rolls.
 *
 * Firestore rules keep unclaimed prizes unreadable by every client. Only the
 * won ones become visible, and only to the winner and the public feed.
 */

interface PrizeSpec { prizeName: string; valuePence: number; imageUrl?: string; quantity: number }

function parseSpecs(raw: unknown): PrizeSpec[] {
  if (!Array.isArray(raw) || raw.length === 0) {
    throw new HttpsError("invalid-argument", "Add at least one prize.");
  }
  if (raw.length > 50) throw new HttpsError("invalid-argument", "Fifty prize lines is the maximum.");
  return raw.map((p: any, i: number) => {
    const prizeName = typeof p?.prizeName === "string" ? p.prizeName.trim() : "";
    if (!prizeName || prizeName.length > 120) {
      throw new HttpsError("invalid-argument", `Prize ${i + 1}: give it a name.`);
    }
    const valuePence = Number(p?.valuePence);
    if (!Number.isInteger(valuePence) || valuePence < 0 || valuePence > 1_000_000_00) {
      throw new HttpsError("invalid-argument", `Prize ${i + 1}: value looks wrong.`);
    }
    const quantity = Number(p?.quantity ?? 1);
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > 5000) {
      throw new HttpsError("invalid-argument", `Prize ${i + 1}: quantity must be 1 to 5000.`);
    }
    const imageUrl = typeof p?.imageUrl === "string" ? p.imageUrl.trim() : "";
    return { prizeName, valuePence, imageUrl, quantity };
  });
}

/**
 * Reservoir-free random selection: we hold the set of numbers already taken
 * (sold, or sealed to another prize) and reject until we land on a free one.
 * maxEntries is always comfortably larger than the number of prizes, so this
 * terminates quickly; the guard exists so a misconfigured raffle fails loudly
 * rather than spinning.
 */
function pickFreeNumbers(count: number, maxEntries: number, taken: Set<number>): number[] {
  const free = maxEntries - taken.size;
  if (count > free) {
    throw new HttpsError("failed-precondition", `Only ${free} unsold numbers are left.`);
  }
  const chosen: number[] = [];
  let guard = 0;
  while (chosen.length < count) {
    if (guard++ > count * 1000 + 10_000) {
      throw new HttpsError("internal", "Could not place the prizes. Try fewer at a time.");
    }
    const n = 1 + Math.floor(Math.random() * maxEntries);
    if (taken.has(n)) continue;
    taken.add(n);
    chosen.push(n);
  }
  return chosen;
}

export const addInstantWinPrizes = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const ctx = await requireAdmin(req, "competitions.write");
  const { competitionId } = req.data ?? {};
  if (!competitionId) throw new HttpsError("invalid-argument", "Which raffle?");
  const specs = parseSpecs(req.data?.prizes);

  const db = admin.firestore();
  const compRef = db.collection(Collections.competitions).doc(competitionId);
  const compSnap = await compRef.get();
  if (!compSnap.exists) throw new HttpsError("not-found", "Raffle not found.");
  const c = compSnap.data()!;
  if (["closed", "drawn", "cancelled"].includes(c.status)) {
    throw new HttpsError("failed-precondition", "This raffle is finished.");
  }

  const maxEntries = Number(c.maxEntries ?? 0);
  if (maxEntries < 2) throw new HttpsError("failed-precondition", "Set the total entries first.");

  // Numbers already sealed to a prize, plus numbers already sold. Sold numbers
  // are excluded so nobody can be retro-fitted a win they didn't buy into.
  const taken = new Set<number>();
  const existing = await db.collection(Collections.instantWins)
    .where("competitionId", "==", competitionId).get();
  existing.forEach((d) => taken.add(d.data().entryNumber));
  const sold = await db.collection(Collections.entries)
    .where("competitionId", "==", competitionId).get();
  sold.forEach((d) => taken.add(d.data().entryNumber));

  const total = specs.reduce((n, s) => n + s.quantity, 0);
  const numbers = pickFreeNumbers(total, maxEntries, taken);

  const batch = db.batch();
  let cursor = 0;
  let valueTotal = 0;
  for (const spec of specs) {
    for (let i = 0; i < spec.quantity; i++) {
      const entryNumber = numbers[cursor++];
      valueTotal += spec.valuePence;
      // Deterministic id: one prize per number per raffle, enforced by the key.
      const ref = db.collection(Collections.instantWins).doc(`${competitionId}_${entryNumber}`);
      batch.set(ref, {
        competitionId,
        competitionTitle: c.title ?? "",
        entryNumber,
        prizeName: spec.prizeName,
        valuePence: spec.valuePence,
        imageUrl: spec.imageUrl || null,
        status: "unclaimed",
        wonBy: null,
        wonByName: null,
        wonAt: null,
        orderId: null,
        claimStatus: null,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        createdBy: ctx.uid,
      });
    }
  }
  batch.update(compRef, {
    instantWinCount: admin.firestore.FieldValue.increment(total),
    instantWinValuePence: admin.firestore.FieldValue.increment(valueTotal),
    hasInstantWins: true,
  });
  await batch.commit();

  await writeAudit({
    action: "instantwin.added",
    actorId: ctx.uid, actorRole: "admin",
    objectType: "competition", objectId: competitionId,
    // The numbers themselves stay out of the audit body: an audit reader must
    // not be able to learn which tickets are loaded.
    newValue: { prizes: specs.map((s) => ({ prizeName: s.prizeName, quantity: s.quantity, valuePence: s.valuePence })) },
  });
  return { added: total };
});

/** Admin view. Unclaimed prizes are summarised, never enumerated with numbers. */
export const listInstantWins = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  await requireAdmin(req, "competitions.write");
  const { competitionId } = req.data ?? {};
  if (!competitionId) throw new HttpsError("invalid-argument", "Which raffle?");

  const db = admin.firestore();
  const snap = await db.collection(Collections.instantWins)
    .where("competitionId", "==", competitionId).get();

  const unclaimed: Record<string, { prizeName: string; valuePence: number; count: number }> = {};
  const won: unknown[] = [];
  snap.forEach((d) => {
    const p = d.data();
    if (p.status === "won") {
      won.push({
        id: d.id,
        prizeName: p.prizeName,
        valuePence: p.valuePence,
        entryNumber: p.entryNumber,
        wonByName: p.wonByName,
        wonAtMillis: p.wonAt?.toMillis?.() ?? null,
        claimStatus: p.claimStatus ?? "pending",
      });
    } else {
      const key = `${p.prizeName}|${p.valuePence}`;
      unclaimed[key] ??= { prizeName: p.prizeName, valuePence: p.valuePence, count: 0 };
      unclaimed[key].count++;
    }
  });
  return { unclaimed: Object.values(unclaimed), won };
});

/** Removing is only ever allowed while a prize is still unclaimed. */
export const removeInstantWinPrizes = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const ctx = await requireAdmin(req, "competitions.write");
  const { competitionId, prizeName, count } = req.data ?? {};
  if (!competitionId || !prizeName) throw new HttpsError("invalid-argument", "Which prize?");
  const want = Math.max(1, Math.min(Number(count ?? 1), 5000));

  const db = admin.firestore();
  const snap = await db.collection(Collections.instantWins)
    .where("competitionId", "==", competitionId)
    .where("prizeName", "==", prizeName)
    .where("status", "==", "unclaimed")
    .limit(want).get();
  if (snap.empty) throw new HttpsError("not-found", "No unclaimed prizes of that name.");

  const batch = db.batch();
  let value = 0;
  snap.forEach((d) => { value += d.data().valuePence ?? 0; batch.delete(d.ref); });
  batch.update(db.collection(Collections.competitions).doc(competitionId), {
    instantWinCount: admin.firestore.FieldValue.increment(-snap.size),
    instantWinValuePence: admin.firestore.FieldValue.increment(-value),
  });
  await batch.commit();

  await writeAudit({
    action: "instantwin.removed", actorId: ctx.uid, actorRole: "admin",
    objectType: "competition", objectId: competitionId,
    newValue: { prizeName, removed: snap.size },
  });
  return { removed: snap.size };
});

/** Marks a won prize as dispatched or fulfilled, for the claims list. */
export const setInstantWinClaimStatus = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const ctx = await requireAdmin(req, "competitions.write");
  const { instantWinId, claimStatus, note } = req.data ?? {};
  const allowed = ["pending", "contacted", "dispatched", "fulfilled"];
  if (!allowed.includes(claimStatus)) throw new HttpsError("invalid-argument", "Unknown claim status.");

  const ref = admin.firestore().collection(Collections.instantWins).doc(instantWinId);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "Prize not found.");
  if (snap.data()!.status !== "won") throw new HttpsError("failed-precondition", "Nobody has won this prize yet.");

  await ref.update({
    claimStatus,
    claimNote: typeof note === "string" ? note.slice(0, 500) : null,
    claimUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
    claimUpdatedBy: ctx.uid,
  });
  await writeAudit({
    action: "instantwin.claim.updated", actorId: ctx.uid, actorRole: "admin",
    objectType: "instantWin", objectId: instantWinId,
    previousValue: { claimStatus: snap.data()!.claimStatus ?? null }, newValue: { claimStatus },
  });
  return { ok: true };
});

export interface AwardedInstantWin {
  id: string;
  entryNumber: number;
  prizeName: string;
  valuePence: number;
  imageUrl: string | null;
}

/**
 * Called as entries are materialised after payment. Each prize is claimed in
 * its own transaction, so a webhook replay or two overlapping orders can never
 * award the same prize twice: the second attempt sees status "won" and stops.
 */
export async function awardInstantWins(params: {
  competitionId: string;
  numbers: number[];
  userId: string;
  userDisplayName: string;
  orderId: string;
}): Promise<AwardedInstantWin[]> {
  const db = admin.firestore();
  const awarded: AwardedInstantWin[] = [];

  for (const n of params.numbers) {
    const ref = db.collection(Collections.instantWins).doc(`${params.competitionId}_${n}`);
    try {
      const prize = await db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        if (!snap.exists) return null;
        const p = snap.data()!;
        if (p.status !== "unclaimed") return null;
        tx.update(ref, {
          status: "won",
          wonBy: params.userId,
          wonByName: params.userDisplayName,
          wonAt: admin.firestore.FieldValue.serverTimestamp(),
          orderId: params.orderId,
          claimStatus: "pending",
        });
        return {
          id: ref.id,
          entryNumber: n,
          prizeName: p.prizeName as string,
          valuePence: (p.valuePence as number) ?? 0,
          imageUrl: (p.imageUrl as string | null) ?? null,
        };
      });
      if (prize) awarded.push(prize);
    } catch (err) {
      // A prize that fails to award must never fail the customer's order; the
      // entry is already theirs. It stays unclaimed and is picked up by audit.
      console.error("instant win award failed", { competitionId: params.competitionId, entryNumber: n, err });
    }
  }

  if (awarded.length) {
    const total = awarded.reduce((sum, a) => sum + a.valuePence, 0);
    await writeAudit({
      action: "instantwin.awarded",
      actorId: "system", actorRole: "system",
      objectType: "order", objectId: params.orderId,
      newValue: {
        userId: params.userId,
        prizes: awarded.map((a) => ({ prizeName: a.prizeName, entryNumber: a.entryNumber, valuePence: a.valuePence })),
      },
    });
    const headline = awarded.length === 1
      ? `You've won ${awarded[0].prizeName}!`
      : `You've won ${awarded.length} instant prizes!`;
    const body = `${headline} Worth £${(total / 100).toFixed(2)}. Check My Wins for the details.`;
    await createUserNotification(params.userId, {
      category: "win",
      title: "Instant win!",
      body,
      deepLink: `rrr://orders/${params.orderId}`,
    });
    await pushToUser(params.userId, "win", "Instant win!", body, { orderId: params.orderId });
  }
  return awarded;
}
