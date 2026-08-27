import * as admin from "firebase-admin";
import { HttpsError } from "firebase-functions/v2/https";
import { Collections } from "./config";

/**
 * All money is calculated here, on the server, in pence.
 * The Android client's basket is treated purely as a request; every price,
 * bundle discount, promo code and fee is re-derived from Firestore before a
 * PaymentIntent is created.
 */

export interface PriceBreakdown {
  quantity: number;
  unitPricePence: number;
  bundleLabel: string | null;
  subtotalPence: number;
  bundleDiscountPence: number;
  promoDiscountPence: number;
  promoCode: string | null;
  feePence: number;
  totalPence: number;
}

export interface Bundle { quantity: number; pricePence: number; label?: string }

export function bestBundlePrice(quantity: number, unitPricePence: number, bundles: Bundle[]) {
  // Greedy over descending bundle sizes, then singles for the remainder.
  const sorted = [...bundles].filter(b => b.quantity > 0).sort((a, b) => b.quantity - a.quantity);
  let remaining = quantity;
  let total = 0;
  let usedLabel: string | null = null;
  for (const b of sorted) {
    while (remaining >= b.quantity) {
      total += b.pricePence;
      remaining -= b.quantity;
      usedLabel = usedLabel ?? (b.label ?? `${b.quantity} for £${(b.pricePence / 100).toFixed(2)}`);
    }
  }
  total += remaining * unitPricePence;
  return { total, usedLabel };
}

export async function computePrice(params: {
  competitionId: string;
  quantity: number;
  promoCode?: string | null;
  userId: string;
}): Promise<{ breakdown: PriceBreakdown; competition: FirebaseFirestore.DocumentData }> {
  const db = admin.firestore();
  const compSnap = await db.collection(Collections.competitions).doc(params.competitionId).get();
  if (!compSnap.exists) throw new HttpsError("not-found", "Raffle not found.");
  const c = compSnap.data()!;

  if (c.status !== "live") throw new HttpsError("failed-precondition", "This raffle is not open for entries.");
  if (params.quantity < 1) throw new HttpsError("invalid-argument", "Choose at least one entry.");
  if (params.quantity > (c.maxEntriesPerCustomer ?? 100)) {
    throw new HttpsError("failed-precondition", `The limit is ${c.maxEntriesPerCustomer} entries per person for this raffle.`);
  }

  // Enforce the per-customer cap across every previous order for this raffle.
  const existing = await db.collection(Collections.entries)
    .where("competitionId", "==", params.competitionId)
    .where("userId", "==", params.userId)
    .count().get();
  const alreadyHeld = existing.data().count;
  if (alreadyHeld + params.quantity > (c.maxEntriesPerCustomer ?? 100)) {
    throw new HttpsError("failed-precondition",
      `You already hold ${alreadyHeld} entries. The limit is ${c.maxEntriesPerCustomer} per person.`);
  }

  const unit: number = c.entryPricePence;
  const bundles: Bundle[] = c.bundles ?? [];
  const { total: bundled, usedLabel } = bestBundlePrice(params.quantity, unit, bundles);
  const listPrice = params.quantity * unit;
  const bundleDiscount = Math.max(0, listPrice - bundled);

  let promoDiscount = 0;
  let promoCode: string | null = null;
  if (params.promoCode) {
    const promo = await validatePromotion(params.promoCode, params.competitionId, params.userId, bundled);
    promoDiscount = promo.discountPence;
    promoCode = promo.code;
  }

  const feePence: number = c.bookingFeePence ?? 0;
  const total = Math.max(0, bundled - promoDiscount) + feePence;
  if (total < 30 && total !== 0) {
    // Stripe's GBP minimum charge.
    throw new HttpsError("failed-precondition", "The minimum card payment is £0.30.");
  }

  return {
    competition: c,
    breakdown: {
      quantity: params.quantity,
      unitPricePence: unit,
      bundleLabel: usedLabel,
      subtotalPence: listPrice,
      bundleDiscountPence: bundleDiscount,
      promoDiscountPence: promoDiscount,
      promoCode,
      feePence,
      totalPence: total,
    },
  };
}

export async function validatePromotion(
  code: string, competitionId: string, userId: string, subtotalPence: number
): Promise<{ code: string; discountPence: number; promotionId: string }> {
  const db = admin.firestore();
  const normalised = code.trim().toUpperCase();
  const snap = await db.collection(Collections.promotions)
    .where("code", "==", normalised).limit(1).get();
  if (snap.empty) throw new HttpsError("not-found", "That code isn't recognised.");

  const p = snap.docs[0].data();
  const now = Date.now();
  if (!p.active) throw new HttpsError("failed-precondition", "That code is no longer active.");
  if (p.startsAt && p.startsAt.toMillis() > now) throw new HttpsError("failed-precondition", "That code isn't active yet.");
  if (p.endsAt && p.endsAt.toMillis() < now) throw new HttpsError("failed-precondition", "That code has expired.");
  if (p.competitionIds?.length && !p.competitionIds.includes(competitionId)) {
    throw new HttpsError("failed-precondition", "That code doesn't apply to this raffle.");
  }
  if (p.minimumSpendPence && subtotalPence < p.minimumSpendPence) {
    throw new HttpsError("failed-precondition", `Spend £${(p.minimumSpendPence / 100).toFixed(2)} to use this code.`);
  }
  if (p.usageLimit && (p.usageCount ?? 0) >= p.usageLimit) {
    throw new HttpsError("resource-exhausted", "That code has been fully redeemed.");
  }
  if (p.perCustomerLimit) {
    const used = await db.collection(Collections.promotionRedemptions)
      .where("promotionId", "==", snap.docs[0].id)
      .where("userId", "==", userId).count().get();
    if (used.data().count >= p.perCustomerLimit) {
      throw new HttpsError("failed-precondition", "You've already used that code.");
    }
  }
  if (p.newCustomersOnly) {
    const orders = await db.collection(Collections.orders)
      .where("userId", "==", userId).where("paymentStatus", "==", "paid").count().get();
    if (orders.data().count > 0) throw new HttpsError("failed-precondition", "That code is for first orders only.");
  }

  let discount = p.type === "percentage"
    ? Math.floor(subtotalPence * (p.value / 100))
    : Math.min(p.value, subtotalPence);
  if (p.maximumDiscountPence) discount = Math.min(discount, p.maximumDiscountPence);

  return { code: normalised, discountPence: discount, promotionId: snap.docs[0].id };
}
