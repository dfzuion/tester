import * as admin from "firebase-admin";
import { HttpsError } from "firebase-functions/v2/https";
import { Collections } from "./config";
import { writeAudit } from "./audit";

/**
 * ENTRY NUMBER ALLOCATION
 * ------------------------------------------------------------------
 * Every raffle has a fixed pool of numbers 1..maxEntries. We must never
 * issue the same number twice, never skip a number, and never lose numbers
 * when hundreds of people check out at the same moment.
 *
 * The counter `entriesSold` on the competition document is the single source
 * of truth and is only ever advanced inside a Firestore transaction, so
 * concurrent checkouts are serialised by the database itself.
 *
 * SEQUENTIAL mode issues the raw counter values.
 * RANDOM mode maps each counter value through a keyed format-preserving
 * permutation (a small Feistel network over the domain 0..maxEntries-1).
 * A permutation is a bijection, so the numbers look scattered to customers
 * while remaining collision-free and exhaustive by construction - which is
 * far safer than drawing random numbers and re-rolling on clashes.
 */

const ROUNDS = 6;

function fmix(x: number, key: number, round: number): number {
  // 32-bit avalanche mix (murmur3 finaliser) keyed per round.
  let h = (x ^ (key + round * 0x9e3779b1)) >>> 0;
  h = Math.imul(h ^ (h >>> 16), 0x85ebca6b) >>> 0;
  h = Math.imul(h ^ (h >>> 13), 0xc2b2ae35) >>> 0;
  return (h ^ (h >>> 16)) >>> 0;
}

/** Feistel permutation over [0, domain) using cycle-walking for non-power-of-two domains. */
export function permute(index: number, domain: number, key: number): number {
  if (domain <= 1) return 0;
  const half = Math.ceil(Math.log2(domain) / 2);
  const mask = (1 << half) - 1;
  const bits = half * 2;
  let value = index;
  // Cycle-walk: keep permuting until the result lands inside the real domain.
  for (let guard = 0; guard < 512; guard++) {
    let left = value >>> half;
    let right = value & mask;
    for (let r = 0; r < ROUNDS; r++) {
      const next = (left ^ (fmix(right, key, r) & mask)) & mask;
      left = right;
      right = next;
    }
    const out = ((left << half) | right) & ((1 << bits) - 1);
    if (out < domain) return out;
    value = out;
  }
  throw new HttpsError("internal", "Entry number allocation failed to converge.");
}

export interface AllocationResult {
  numbers: number[];
  firstIndex: number;
}

/**
 * Reserves `quantity` entry numbers for an order. Runs inside the caller's
 * transaction so reservation, order state and the sold counter all commit
 * atomically or not at all.
 */
export async function allocateEntryNumbers(
  tx: FirebaseFirestore.Transaction,
  competitionRef: FirebaseFirestore.DocumentReference,
  quantity: number
): Promise<AllocationResult> {
  const snap = await tx.get(competitionRef);
  if (!snap.exists) throw new HttpsError("not-found", "Raffle not found.");
  const c = snap.data() as any;

  if (c.status !== "live") throw new HttpsError("failed-precondition", "This raffle is not open for entries.");
  if (c.closesAt && c.closesAt.toMillis() <= Date.now()) {
    throw new HttpsError("failed-precondition", "This raffle has closed.");
  }

  const sold: number = c.entriesSold ?? 0;
  const max: number = c.maxEntries;
  if (sold + quantity > max) {
    throw new HttpsError("resource-exhausted", `Only ${max - sold} entries left.`);
  }

  const mode: string = c.allocationMode ?? "sequential";
  const key: number = c.allocationKey ?? 0;
  const numbers: number[] = [];
  for (let i = 0; i < quantity; i++) {
    const index = sold + i;
    numbers.push(mode === "random" ? permute(index, max, key) + 1 : index + 1);
  }

  tx.update(competitionRef, {
    entriesSold: admin.firestore.FieldValue.increment(quantity),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  return { numbers, firstIndex: sold };
}

/** Releases a reservation when a payment fails, is cancelled or expires. */
export async function releaseReservation(
  tx: FirebaseFirestore.Transaction,
  competitionRef: FirebaseFirestore.DocumentReference,
  quantity: number
): Promise<void> {
  tx.update(competitionRef, {
    entriesSold: admin.firestore.FieldValue.increment(-quantity),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
}

/** Writes the individual immutable entry documents once payment is confirmed. */
export async function materialiseEntries(params: {
  orderId: string;
  userId: string;
  userDisplayName: string;
  competitionId: string;
  competitionTitle: string;
  numbers: number[];
}): Promise<void> {
  const db = admin.firestore();
  const batch = db.batch();
  const now = admin.firestore.FieldValue.serverTimestamp();

  for (const n of params.numbers) {
    // Deterministic ID + set(): a webhook replay rewrites the same document
    // rather than creating a second copy of the same entry number.
    const id = `${params.competitionId}_${n}`;
    const ref = db.collection(Collections.entries).doc(id);
    batch.set(ref, {
      entryNumber: n,
      competitionId: params.competitionId,
      competitionTitle: params.competitionTitle,
      userId: params.userId,
      userDisplayName: params.userDisplayName,
      orderId: params.orderId,
      status: "active",
      isWinner: false,
      purchasedAt: now,
    });
  }
  await batch.commit();
  await writeAudit({
    action: "entries.allocated",
    actorId: "system",
    actorRole: "system",
    objectType: "order",
    objectId: params.orderId,
    newValue: { competitionId: params.competitionId, numbers: params.numbers },
  });
}
