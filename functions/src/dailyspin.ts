import * as admin from "firebase-admin";
import * as crypto from "crypto";
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { Collections, REGION, ENFORCE_APP_CHECK } from "./config";
import { requireAuth, assertNotRateLimited } from "./guards";
import { prepareCreditMove } from "./credits";
import { writeAudit } from "./audit";

/**
 * DAILY SPIN
 * ------------------------------------------------------------------
 * One spin per customer per day, paying site credit. Credit buys entries and
 * nothing else, so this is a promotion rather than a cash prize.
 *
 * The segment is chosen here and nowhere else. A client that picked its own
 * prize could pick £2 every time, so the wheel the customer watches is only an
 * animation of a result the server has already decided.
 *
 * One spin per day is enforced by the document id - uid plus the London date -
 * created inside the same transaction that moves the credit. Two taps that race
 * each other cannot both win, because the second transaction sees the first
 * one's document.
 */

export interface SpinSegment {
  pence: number;
  label: string;
  /** Relative chance. These are whole numbers so the draw can stay integer. */
  weight: number;
}

/**
 * Weights are the business decision here, not a technical one. As written the
 * average spin costs 23.5p, so a hundred daily players cost about £23.50 a day.
 * Change the weights, not the code, to change that.
 */
export const SPIN_WHEEL: SpinSegment[] = [
  { pence: 5, label: "5p", weight: 40 },
  { pence: 10, label: "10p", weight: 25 },
  { pence: 20, label: "20p", weight: 15 },
  { pence: 50, label: "50p", weight: 12 },
  { pence: 100, label: "£1", weight: 6 },
  { pence: 200, label: "£2", weight: 2 },
];

/** The advertised ceiling. A wheel that could pay more than this is a bug. */
export const SPIN_MAX_PENCE = 200;

/** YYYY-MM-DD in London, so the day rolls over at UK midnight all year. */
export function londonDayKey(at: Date = new Date()): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Europe/London",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(at);
}

/**
 * The instant the London day next rolls over, found by bisection rather than
 * by adding 24 hours - the clocks change twice a year and those days are 23 or
 * 25 hours long.
 */
export function nextLondonMidnight(from: Date = new Date()): Date {
  const today = londonDayKey(from);
  let low = from.getTime();
  let high = low + 36 * 3600_000;

  while (high - low > 1_000) {
    const mid = Math.floor((low + high) / 2);
    if (londonDayKey(new Date(mid)) === today) {
      low = mid;
    } else {
      high = mid;
    }
  }

  // `high` is the first probe that landed on the next day, within a second of
  // the boundary. Midnight is a whole minute, so flooring lands exactly on it.
  // Rounding up instead would step a minute into the new day.
  return new Date(Math.floor(high / 60_000) * 60_000);
}

export function pickSegment(): SpinSegment {
  const total = SPIN_WHEEL.reduce((sum, s) => sum + s.weight, 0);
  let ticket = crypto.randomInt(0, total);

  for (const segment of SPIN_WHEEL) {
    ticket -= segment.weight;
    if (ticket < 0) {
      return segment;
    }
  }

  return SPIN_WHEEL[SPIN_WHEEL.length - 1];
}

export const spinDailyWheel = onCall(
  { region: REGION, enforceAppCheck: ENFORCE_APP_CHECK },
  async (req: CallableRequest) => {
    const uid = requireAuth(req);
    await assertNotRateLimited(`spin:${uid}`, 5, 60_000);

    const db = admin.firestore();
    const dayKey = londonDayKey();
    const spinRef = db.collection(Collections.dailySpins).doc(`${uid}_${dayKey}`);

    const outcome = await db.runTransaction(async (tx) => {
      const existing = await tx.get(spinRef);
      if (existing.exists) {
        const d = existing.data()!;
        return {
          alreadySpun: true,
          pence: Number(d.pence ?? 0),
          label: String(d.label ?? ""),
          balanceAfterPence: null as number | null,
        };
      }

      const segment = pickSegment();
      if (segment.pence > SPIN_MAX_PENCE) {
        throw new HttpsError("internal", "The wheel is misconfigured.");
      }

      // Every read first: the spin document above, then the balance.
      const move = await prepareCreditMove(tx, {
        uid,
        deltaPence: segment.pence,
        reason: "daily_spin",
        description: `Daily spin - ${segment.label}`,
      });

      tx.set(spinRef, {
        userId: uid,
        dayKey,
        pence: segment.pence,
        label: segment.label,
        spunAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      move.apply(tx);

      return {
        alreadySpun: false,
        pence: segment.pence,
        label: segment.label,
        balanceAfterPence: move.balanceAfterPence,
      };
    });

    if (!outcome.alreadySpun) {
      await writeAudit({
        action: "credit.daily_spin",
        actorId: uid,
        actorRole: "customer",
        objectType: "user",
        objectId: uid,
        newValue: { dayKey, pence: outcome.pence },
      });
    }

    return {
      ...outcome,
      dayKey,
      nextSpinAtMillis: nextLondonMidnight().getTime(),
      // So the client can draw the same wheel the server drew from.
      wheel: SPIN_WHEEL.map((s) => ({ pence: s.pence, label: s.label })),
    };
  }
);
