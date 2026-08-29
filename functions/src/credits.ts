import * as admin from "firebase-admin";
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { Collections, REGION, ENFORCE_APP_CHECK } from "./config";
import { requireAdmin, requireAuth, assertNotRateLimited } from "./guards";
import { writeAudit } from "./audit";

/**
 * SITE CREDIT
 * ------------------------------------------------------------------
 * Credit is a promotional balance: it buys entries and nothing else. It is
 * never withdrawable and never refundable to a card, which keeps it clear of
 * e-money rules - the terms have to say so too.
 *
 * The balance on the user document is a cache for reading. The ledger is the
 * truth: every movement writes an immutable row with the reason and the
 * balance after it, so any balance can be reconstructed and any dispute
 * answered. Balance and ledger are only ever written together, inside a
 * transaction, so they cannot drift.
 */

export type CreditReason =
  | "coupon" | "referral" | "instant_win" | "admin_grant"
  | "order_spend" | "order_refund" | "order_released" | "daily_spin";

export interface CreditMovement {
  uid: string;
  deltaPence: number;
  reason: CreditReason;
  description: string;
  orderId?: string | null;
  metadata?: Record<string, unknown>;
}

/** A credit move that has done its reading and is waiting to be written. */
export interface PreparedCreditMove {
  /** The balance this movement will leave behind. */
  balanceAfterPence: number;
  /** Queues the balance update and the ledger row. Reads nothing. */
  apply(tx: FirebaseFirestore.Transaction): void;
}

/**
 * Reads what a credit movement needs and returns it ready to write.
 *
 * Firestore forbids a read after a write inside the same transaction, and
 * moveCredit does both. That made it impossible to combine safely with any
 * other read - paying with credit read the user, wrote the balance, and then
 * allocateEntryNumbers tried to read the raffle, which aborted the whole
 * transaction as INTERNAL. Splitting the phases lets a caller do every read
 * first and then every write, which is the rule.
 *
 * Refuses to let a balance go negative - a spend that cannot be covered is a
 * bug, and silently allowing it would hand out free entries.
 */
export async function prepareCreditMove(
  tx: FirebaseFirestore.Transaction,
  m: CreditMovement
): Promise<PreparedCreditMove> {
  if (!Number.isInteger(m.deltaPence) || m.deltaPence === 0) {
    throw new HttpsError("invalid-argument", "Credit movements must be a non-zero whole number of pence.");
  }
  const db = admin.firestore();
  const userRef = db.collection(Collections.users).doc(m.uid);
  const snap = await tx.get(userRef);
  if (!snap.exists) throw new HttpsError("not-found", "That account no longer exists.");

  const before = Number(snap.data()!.creditBalancePence ?? 0);
  const after = before + m.deltaPence;
  if (after < 0) {
    throw new HttpsError("failed-precondition", "That's more credit than the account holds.");
  }

  return {
    balanceAfterPence: after,
    apply(writeTx: FirebaseFirestore.Transaction) {
      writeTx.update(userRef, {
        creditBalancePence: after,
        creditUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      writeTx.set(userRef.collection("creditLedger").doc(), {
        deltaPence: m.deltaPence,
        balanceAfterPence: after,
        reason: m.reason,
        description: m.description,
        orderId: m.orderId ?? null,
        metadata: m.metadata ?? {},
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    },
  };
}

/**
 * Reads and writes a credit movement in one go. Safe only when the caller has
 * no further reads to make; if it does, use prepareCreditMove and apply it
 * after the last read.
 */
export async function moveCredit(
  tx: FirebaseFirestore.Transaction,
  m: CreditMovement
): Promise<number> {
  const prepared = await prepareCreditMove(tx, m);
  prepared.apply(tx);
  return prepared.balanceAfterPence;
}

/** Convenience for callers that aren't already inside a transaction. */
export async function moveCreditStandalone(m: CreditMovement): Promise<number> {
  return admin.firestore().runTransaction((tx) => moveCredit(tx, m));
}

// ---------------------------------------------------------------- coupons

function normaliseCode(raw: unknown): string {
  const code = String(raw ?? "").trim().toUpperCase();
  if (!/^[A-Z0-9-]{4,24}$/.test(code)) {
    throw new HttpsError("invalid-argument", "Coupon codes are 4-24 letters, numbers or dashes.");
  }
  return code;
}

export const redeemCreditCoupon = onCall(
  { region: REGION, enforceAppCheck: ENFORCE_APP_CHECK },
  async (req: CallableRequest) => {
    const uid = requireAuth(req);
    // Codes are guessable by design, so brute force is the obvious attack.
    await assertNotRateLimited(`coupon:${uid}`, 8, 60_000);
    const code = normaliseCode(req.data?.code);

    const db = admin.firestore();
    const couponRef = db.collection(Collections.creditCoupons).doc(code);
    // One redemption doc per code per user: the id itself enforces "once each".
    const redemptionRef = db.collection(Collections.couponRedemptions).doc(`${code}_${uid}`);

    const result = await db.runTransaction(async (tx) => {
      const couponSnap = await tx.get(couponRef);
      if (!couponSnap.exists) throw new HttpsError("not-found", "That code isn't recognised.");
      const c = couponSnap.data()!;

      if (c.active === false) throw new HttpsError("failed-precondition", "That code is no longer active.");
      if (c.expiresAt && c.expiresAt.toMillis() <= Date.now()) {
        throw new HttpsError("failed-precondition", "That code has expired.");
      }
      const used = Number(c.redemptionsUsed ?? 0);
      const cap = Number(c.maxRedemptions ?? 0);
      if (cap > 0 && used >= cap) {
        throw new HttpsError("resource-exhausted", "That code has been fully claimed.");
      }

      const already = await tx.get(redemptionRef);
      if (already.exists) throw new HttpsError("already-exists", "You've already used that code.");

      const valuePence = Number(c.valuePence ?? 0);
      if (valuePence <= 0) throw new HttpsError("failed-precondition", "That code has no value on it.");

      const balance = await moveCredit(tx, {
        uid,
        deltaPence: valuePence,
        reason: "coupon",
        description: `Coupon ${code}`,
        metadata: { code },
      });

      tx.set(redemptionRef, {
        code, userId: uid, valuePence,
        redeemedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      tx.update(couponRef, { redemptionsUsed: admin.firestore.FieldValue.increment(1) });

      return { valuePence, balancePence: balance };
    });

    await writeAudit({
      action: "credit.coupon.redeemed", actorId: uid, actorRole: "customer",
      objectType: "coupon", objectId: code,
      newValue: { valuePence: result.valuePence },
    });
    return result;
  }
);

export const createCreditCoupon = onCall(
  { region: REGION, enforceAppCheck: ENFORCE_APP_CHECK },
  async (req: CallableRequest) => {
    const ctx = await requireAdmin(req, "promotions.write");
    const code = normaliseCode(req.data?.code);
    const valuePence = Number(req.data?.valuePence);
    if (!Number.isInteger(valuePence) || valuePence < 1 || valuePence > 100_000) {
      throw new HttpsError("invalid-argument", "Coupon value must be between 1p and £1000.");
    }
    const maxRedemptions = Number(req.data?.maxRedemptions ?? 0);
    if (!Number.isInteger(maxRedemptions) || maxRedemptions < 0 || maxRedemptions > 1_000_000) {
      throw new HttpsError("invalid-argument", "That redemption cap looks wrong.");
    }
    const expiresAtMillis = req.data?.expiresAtMillis ? Number(req.data.expiresAtMillis) : null;
    if (expiresAtMillis !== null && (!Number.isFinite(expiresAtMillis) || expiresAtMillis <= Date.now())) {
      throw new HttpsError("invalid-argument", "The expiry has to be in the future.");
    }

    const db = admin.firestore();
    const ref = db.collection(Collections.creditCoupons).doc(code);
    if ((await ref.get()).exists) throw new HttpsError("already-exists", "That code already exists.");

    await ref.set({
      code,
      valuePence,
      maxRedemptions,
      redemptionsUsed: 0,
      active: true,
      expiresAt: expiresAtMillis ? admin.firestore.Timestamp.fromMillis(expiresAtMillis) : null,
      createdBy: ctx.uid,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    await writeAudit({
      action: "credit.coupon.created", actorId: ctx.uid, actorRole: "admin",
      objectType: "coupon", objectId: code,
      newValue: { valuePence, maxRedemptions, expiresAtMillis },
    });
    return { code };
  }
);

export const listCreditCoupons = onCall(
  { region: REGION, enforceAppCheck: ENFORCE_APP_CHECK },
  async (req: CallableRequest) => {
    await requireAdmin(req, "promotions.write");
    const snap = await admin.firestore().collection(Collections.creditCoupons)
      .orderBy("createdAt", "desc").limit(100).get();
    return {
      coupons: snap.docs.map((d) => {
        const c = d.data();
        return {
          code: d.id,
          valuePence: c.valuePence ?? 0,
          maxRedemptions: c.maxRedemptions ?? 0,
          redemptionsUsed: c.redemptionsUsed ?? 0,
          active: c.active !== false,
          expiresAtMillis: c.expiresAt?.toMillis?.() ?? null,
        };
      }),
    };
  }
);

export const setCreditCouponActive = onCall(
  { region: REGION, enforceAppCheck: ENFORCE_APP_CHECK },
  async (req: CallableRequest) => {
    const ctx = await requireAdmin(req, "promotions.write");
    const code = normaliseCode(req.data?.code);
    const active = req.data?.active === true;
    await admin.firestore().collection(Collections.creditCoupons).doc(code)
      .update({ active, updatedBy: ctx.uid, updatedAt: admin.firestore.FieldValue.serverTimestamp() });
    await writeAudit({
      action: "credit.coupon.updated", actorId: ctx.uid, actorRole: "admin",
      objectType: "coupon", objectId: code, newValue: { active },
    });
    return { ok: true };
  }
);

/** Manual adjustment, for support: goodwill, compensation, correcting a mistake. */
export const grantCredit = onCall(
  { region: REGION, enforceAppCheck: ENFORCE_APP_CHECK },
  async (req: CallableRequest) => {
    const ctx = await requireAdmin(req, "customers.read");
    const email = String(req.data?.email ?? "").trim().toLowerCase();
    const amountPence = Number(req.data?.amountPence);
    const note = String(req.data?.note ?? "").slice(0, 200);
    if (!email) throw new HttpsError("invalid-argument", "Whose account?");
    if (!Number.isInteger(amountPence) || amountPence === 0 || Math.abs(amountPence) > 100_000) {
      throw new HttpsError("invalid-argument", "Amount must be a non-zero number of pence, up to £1000.");
    }

    let user;
    try {
      user = await admin.auth().getUserByEmail(email);
    } catch {
      throw new HttpsError("not-found", "No account with that email address.");
    }

    const balance = await moveCreditStandalone({
      uid: user.uid,
      deltaPence: amountPence,
      reason: "admin_grant",
      description: note || `Adjustment by ${ctx.uid}`,
      metadata: { by: ctx.uid },
    });
    await writeAudit({
      action: "credit.granted", actorId: ctx.uid, actorRole: "admin",
      objectType: "user", objectId: user.uid,
      newValue: { amountPence, note },
    });
    return { balancePence: balance };
  }
);

/**
 * Pays out a referral once the referred customer's first order is genuinely
 * paid. Rewarding at signup would just pay people to create empty accounts.
 * The referral document's status is the guard against paying twice.
 */
export async function payReferralIfDue(params: {
  userId: string;
  orderId: string;
  referrerRewardPence: number;
  refereeRewardPence: number;
}): Promise<void> {
  const db = admin.firestore();
  const userSnap = await db.collection(Collections.users).doc(params.userId).get();
  const referredBy = userSnap.data()?.referredBy;
  if (!referredBy) return;

  const referralQuery = await db.collection(Collections.referrals)
    .where("referredUserId", "==", params.userId).limit(1).get();
  if (referralQuery.empty) return;
  const referralRef = referralQuery.docs[0].ref;
  if (referralQuery.docs[0].data().status === "rewarded") return;

  // Who owns the code? Referral codes are unique per user.
  const referrerQuery = await db.collection(Collections.users)
    .where("referralCode", "==", referredBy).limit(1).get();
  if (referrerQuery.empty) return;
  const referrerId = referrerQuery.docs[0].id;
  if (referrerId === params.userId) return; // nobody refers themselves

  try {
    await db.runTransaction(async (tx) => {
      const fresh = await tx.get(referralRef);
      if (fresh.data()?.status === "rewarded") return;

      // Both sides are read first, then both are written. Paying one and then
      // reading the other aborted the transaction, so referral rewards were
      // never actually landing.
      const toReferrer = await prepareCreditMove(tx, {
        uid: referrerId,
        deltaPence: params.referrerRewardPence,
        reason: "referral",
        description: "Someone you referred made their first order",
        orderId: params.orderId,
      });
      const toReferee = await prepareCreditMove(tx, {
        uid: params.userId,
        deltaPence: params.refereeRewardPence,
        reason: "referral",
        description: `Welcome bonus for using code ${referredBy}`,
        orderId: params.orderId,
      });
      toReferrer.apply(tx);
      toReferee.apply(tx);
      tx.update(referralRef, {
        status: "rewarded",
        rewardedAt: admin.firestore.FieldValue.serverTimestamp(),
        referrerUserId: referrerId,
        referrerRewardPence: params.referrerRewardPence,
        refereeRewardPence: params.refereeRewardPence,
      });
    });
  } catch (err) {
    // A referral that fails to pay must never fail the customer's order.
    console.error("referral payout failed", { userId: params.userId, err });
  }
}
