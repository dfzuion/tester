import * as admin from "firebase-admin";
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { Collections, ENFORCE_APP_CHECK, REGION } from "./config";
import { requireAuth, assertNotRateLimited } from "./guards";
import { moveCreditStandalone } from "./credits";
import { createUserNotification, notifyAdmins, pushToUser, queueEmail } from "./notifications";
import { writeAudit } from "./audit";

/**
 * The weekly leaderboard for Cast & Catch.
 *
 * Heaviest single fish of the week wins £10 of site credit, paid on Monday
 * morning, and the board starts again.
 *
 * A WORD ON CHEATING. The game runs in the customer's own browser or phone,
 * so the weight arrives from a client that the customer controls, and no
 * amount of validation here changes that. What this does is make cheating
 * not worth the trouble rather than impossible:
 *
 *   - a submitted weight has to be a real weight for a real species, inside
 *     the range that species can actually reach, so nobody wins with a
 *     900lb roach;
 *   - submissions are rate limited, so the board cannot be spammed;
 *   - every submission that beats a customer's own best is written with a
 *     timestamp, and the winning one is recorded in the audit log, so a
 *     suspicious run can be looked at afterwards;
 *   - the prize is deliberately small. £10 a week is worth winning and not
 *     worth writing a script for.
 *
 * If it ever needs to be airtight the game has to be simulated on the
 * server, which is a much bigger piece of work than the prize justifies.
 */

/**
 * Mirrors SPECIES in the clients. The ceiling is what stops silly weights,
 * and it sits a little above the client's own maximum on purpose: the client
 * rounds to two decimals and a fish drawn exactly at the top of its range
 * should not be refused by a rounding hair.
 *
 * A mirror can reach sixty here because a mirror can reach sixty in England.
 * It will happen to roughly one angler in a thousand casts, which is about
 * right for a fish that makes the papers when it comes out for real.
 */
export const SPECIES_RANGE: Record<string, { min: number; max: number }> = {
  "Roach": { min: 0.2, max: 4 },
  "Tench": { min: 1, max: 13 },
  "Bream": { min: 1.5, max: 19 },
  "Common carp": { min: 4, max: 52 },
  "Mirror carp": { min: 5, max: 62 },
  "Leather carp": { min: 4, max: 44 },
};

export const GAME_PRIZE_PENCE = 1000;

/**
 * ISO week in London: "2026-W35". Weeks run Monday to Sunday, which is what
 * "resets each week" means to everyone who is not a computer.
 */
export function londonWeekKey(at: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Europe/London",
    year: "numeric", month: "2-digit", day: "2-digit",
  }).formatToParts(at);
  const get = (t: string) => Number(parts.find((p) => p.type === t)?.value);

  // Work in a UTC date built from the London calendar date, so the week
  // arithmetic below never trips over British Summer Time.
  const local = new Date(Date.UTC(get("year"), get("month") - 1, get("day")));

  // ISO weeks are numbered by the Thursday they contain.
  const day = (local.getUTCDay() + 6) % 7;
  local.setUTCDate(local.getUTCDate() - day + 3);
  const firstThursday = new Date(Date.UTC(local.getUTCFullYear(), 0, 4));
  const firstDay = (firstThursday.getUTCDay() + 6) % 7;
  firstThursday.setUTCDate(firstThursday.getUTCDate() - firstDay + 3);

  const week = 1 + Math.round((local.getTime() - firstThursday.getTime()) / (7 * 86400000));

  return `${local.getUTCFullYear()}-W${String(week).padStart(2, "0")}`;
}

/** The week before the one containing `at`. */
export function previousLondonWeekKey(at: Date = new Date()): string {
  return londonWeekKey(new Date(at.getTime() - 7 * 86400000));
}

/**
 * Records a catch. Only a personal best for the week is kept, so the board is
 * one line per angler and the biggest fish wins.
 */
export const submitGameCatch = onCall(
  { region: REGION, enforceAppCheck: ENFORCE_APP_CHECK },
  async (req: CallableRequest) => {
    const uid = requireAuth(req);
    await assertNotRateLimited(`game:${uid}`, 60, 3600_000);

    const species = String(req.data?.species ?? "");
    const weight = Number(req.data?.weightLb);
    const range = SPECIES_RANGE[species];

    if (!range) throw new HttpsError("invalid-argument", "Unknown species.");
    if (!Number.isFinite(weight) || weight < range.min || weight > range.max) {
      throw new HttpsError(
        "invalid-argument",
        `A ${species.toLowerCase()} weighs between ${range.min} and ${range.max} lb.`
      );
    }

    const rounded = Math.round(weight * 100) / 100;
    const week = londonWeekKey();
    const db = admin.firestore();
    const ref = db.collection(Collections.gameScores).doc(`${uid}_${week}`);

    const user = (await db.collection(Collections.users).doc(uid).get()).data();
    const displayName = String(user?.displayName ?? "Angler");

    const result = await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      const standing = snap.exists ? Number(snap.data()?.bestWeightLb ?? 0) : 0;
      const catches = snap.exists ? Number(snap.data()?.catches ?? 0) : 0;

      if (rounded <= standing) {
        // Still worth counting the fish, just not a new best.
        tx.set(ref, {
          userId: uid, displayName, weekKey: week,
          catches: catches + 1,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        }, { merge: true });

        return { best: standing, improved: false };
      }

      tx.set(ref, {
        userId: uid,
        displayName,
        weekKey: week,
        bestWeightLb: rounded,
        bestSpecies: species,
        catches: catches + 1,
        // The moment it was landed, which is the tie-break: first to the
        // weight keeps it.
        bestAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        createdAt: snap.exists
          ? snap.data()?.createdAt ?? admin.firestore.FieldValue.serverTimestamp()
          : admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });

      return { best: rounded, improved: true };
    });

    return { weekKey: week, ...result };
  }
);

/** The current week's board, and last week's winner. Signed in or not. */
export const gameLeaderboard = onCall(
  { region: REGION, enforceAppCheck: ENFORCE_APP_CHECK },
  async (req: CallableRequest) => {
    const db = admin.firestore();
    const week = londonWeekKey();

    const snap = await db.collection(Collections.gameScores)
      .where("weekKey", "==", week)
      .orderBy("bestWeightLb", "desc")
      .orderBy("bestAt", "asc")
      .limit(20)
      .get();

    const board = snap.docs.map((d, i) => {
      const s = d.data();
      return {
        position: i + 1,
        userId: s.userId,
        displayName: s.displayName ?? "Angler",
        weightLb: s.bestWeightLb ?? 0,
        species: s.bestSpecies ?? "",
        catches: s.catches ?? 0,
      };
    });

    const last = await db.collection(Collections.gameWeeks)
      .doc(previousLondonWeekKey()).get();

    return {
      weekKey: week,
      prizePence: GAME_PRIZE_PENCE,
      board,
      // The board is public, so the caller's own row is marked rather than
      // the client having to guess which one is theirs.
      you: req.auth?.uid ?? null,
      lastWeek: last.exists
        ? {
            weekKey: last.id,
            displayName: last.data()?.displayName ?? "",
            weightLb: last.data()?.weightLb ?? 0,
            species: last.data()?.species ?? "",
          }
        : null,
    };
  }
);

/**
 * Pays the week out. Monday at ten past midnight London, which is after the
 * week has ended and long before anyone is awake to notice a gap.
 *
 * The week document is written inside the same transaction that decides the
 * winner, and its existence is the guard: if this runs twice, the second run
 * finds the week already settled and pays nobody.
 */
export const awardGameWeek = onSchedule(
  { region: REGION, schedule: "10 0 * * 1", timeZone: "Europe/London" },
  async () => {
    await settleGameWeek(previousLondonWeekKey());
  }
);

export async function settleGameWeek(weekKey: string): Promise<{ settled: boolean; winner?: string }> {
  const db = admin.firestore();
  const weekRef = db.collection(Collections.gameWeeks).doc(weekKey);

  const top = await db.collection(Collections.gameScores)
    .where("weekKey", "==", weekKey)
    .orderBy("bestWeightLb", "desc")
    .orderBy("bestAt", "asc")
    .limit(1)
    .get();

  if (top.empty) {
    await weekRef.set({
      weekKey, winnerUserId: null, settledAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });

    return { settled: false };
  }

  const best = top.docs[0].data();
  const uid = String(best.userId);

  // Claim the week before paying. A second run finds it taken and stops.
  const claimed = await db.runTransaction(async (tx) => {
    const snap = await tx.get(weekRef);
    if (snap.exists && snap.data()?.paidAt) return false;

    tx.set(weekRef, {
      weekKey,
      winnerUserId: uid,
      displayName: best.displayName ?? "Angler",
      weightLb: best.bestWeightLb ?? 0,
      species: best.bestSpecies ?? "",
      prizePence: GAME_PRIZE_PENCE,
      paidAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });

    return true;
  });

  if (!claimed) return { settled: false };

  const weight = Number(best.bestWeightLb ?? 0).toFixed(2);
  const species = String(best.bestSpecies ?? "fish");
  const name = String(best.displayName ?? "Angler");

  await moveCreditStandalone({
    uid,
    deltaPence: GAME_PRIZE_PENCE,
    reason: "game_leaderboard",
    description: `Cast & Catch, week ${weekKey}: ${species}, ${weight} lb`,
    metadata: { weekKey, weightLb: best.bestWeightLb, species },
  });

  const user = (await db.collection(Collections.users).doc(uid).get()).data();

  await createUserNotification(uid, {
    category: "game",
    title: "You won the week",
    body: `Your ${species.toLowerCase()} at ${weight} lb topped Cast & Catch. £10 of site credit is in your account.`,
  });
  await pushToUser(uid, "game", "You won the week", `£10 of credit for a ${weight} lb ${species.toLowerCase()}.`);
  await queueEmail(user?.email, "game_week_won", {
    displayName: name, species, weight, weekKey,
  });

  await notifyAdmins(
    "Cast & Catch week paid",
    `${name} won week ${weekKey} with a ${weight} lb ${species.toLowerCase()}. £10 credit granted.`
  );

  await writeAudit({
    action: "game.week.paid",
    actorId: "system", actorRole: "system",
    objectType: "gameWeek", objectId: weekKey,
    newValue: { winnerUserId: uid, weightLb: best.bestWeightLb, species, prizePence: GAME_PRIZE_PENCE },
  });

  return { settled: true, winner: uid };
}
