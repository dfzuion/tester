import * as admin from "firebase-admin";
import { onCall, onRequest, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import Stripe from "stripe";
import { Collections, REGION, REFERRAL_REFERRER_PENCE, REFERRAL_REFEREE_PENCE, STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET, ENFORCE_APP_CHECK } from "./config";
import { allocateEntryNumbers, materialiseEntries, releaseReservation } from "./allocation";
import { moveCredit, moveCreditStandalone, prepareCreditMove, payReferralIfDue } from "./credits";
import { awardInstantWins } from "./instantwins";
import { computePrice, validatePromotion } from "./pricing";
import { writeAudit } from "./audit";
import { queueEmail, pushToUser, createUserNotification } from "./notifications";
import { assertNotRateLimited, requireAuth, requireAdmin } from "./guards";

let stripeClient: Stripe | null = null;
function stripe(): Stripe {
  // Pinned deliberately: bumping this is a decision, not something that should
  // change under us when the SDK is updated.
  if (!stripeClient) stripeClient = new Stripe(STRIPE_SECRET_KEY.value(), { apiVersion: "2025-02-24.acacia" });
  return stripeClient;
}

/**
 * Every customer who ever pays gets a Stripe Customer record the first time
 * they do, so a card can be saved against something. Cheap to call on every
 * checkout - it only actually talks to Stripe the first time for a given
 * user, after that it is just a Firestore read.
 */
async function getOrCreateStripeCustomer(uid: string, email: string | null | undefined): Promise<string> {
  const db = admin.firestore();
  const userRef = db.collection(Collections.users).doc(uid);
  const userSnap = await userRef.get();
  const existing = userSnap.data()?.stripeCustomerId;
  if (typeof existing === "string" && existing) return existing;

  const customer = await stripe().customers.create({
    email: email ?? undefined,
    metadata: { firebaseUID: uid },
  });
  await userRef.update({ stripeCustomerId: customer.id, updatedAt: admin.firestore.FieldValue.serverTimestamp() });
  return customer.id;
}

/**
 * A saved card only ever gets used by the customer it belongs to. Every
 * callable below that acts on an existing payment method re-derives the
 * customer from Firestore and checks Stripe's own record of who owns it -
 * a uid can never act on a payment method by guessing its id.
 */
async function requireOwnedPaymentMethod(uid: string, paymentMethodId: string): Promise<Stripe.PaymentMethod> {
  if (!paymentMethodId || typeof paymentMethodId !== "string") {
    throw new HttpsError("invalid-argument", "Missing payment method.");
  }
  const userSnap = await admin.firestore().collection(Collections.users).doc(uid).get();
  const customerId = userSnap.data()?.stripeCustomerId;
  const method = await stripe().paymentMethods.retrieve(paymentMethodId).catch(() => null);
  if (!method || !customerId || method.customer !== customerId) {
    throw new HttpsError("permission-denied", "That card isn't on your account.");
  }
  return method;
}

const RESERVATION_TTL_MS = 15 * 60 * 1000;

/** Step 1 of checkout: price the basket. Read-only, no numbers reserved yet. */
/** Stripe's floor for a GBP charge. Nothing to do with what a raffle may cost. */
const STRIPE_MINIMUM_PENCE = 30;

export const quoteBasket = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const uid = requireAuth(req);
  const { competitionId, quantity, promoCode } = req.data ?? {};
  const { breakdown } = await computePrice({ competitionId, quantity: Number(quantity), promoCode, userId: uid });
  return breakdown;
});

/**
 * Step 2: create the order, reserve the entry numbers, and open a Stripe
 * PaymentIntent. The numbers are held but the entry documents do not exist
 * until Stripe tells us - over its own webhook - that the money cleared.
 */
export const createOrderAndPaymentIntent = onCall(
  { region: REGION, secrets: [STRIPE_SECRET_KEY], enforceAppCheck: ENFORCE_APP_CHECK },
  async (req: CallableRequest) => {
    const uid = requireAuth(req);
    await assertNotRateLimited(`checkout:${uid}`, 10, 60_000);

    const db = admin.firestore();
    const { competitionId, quantity, promoCode, idempotencyKey, creditToApplyPence } = req.data ?? {};
    const qty = Number(quantity);
    if (!competitionId || !Number.isInteger(qty) || qty < 1) {
      throw new HttpsError("invalid-argument", "Choose how many entries you'd like.");
    }
    if (!idempotencyKey || typeof idempotencyKey !== "string") {
      throw new HttpsError("invalid-argument", "Missing idempotency key.");
    }

    // A retry of the same tap returns the original order rather than a second one.
    const replay = await db.collection(Collections.orders)
      .where("userId", "==", uid).where("idempotencyKey", "==", idempotencyKey).limit(1).get();
    if (!replay.empty) {
      // The replay has to answer in the same shape as a fresh order, or a
      // retried credit-only purchase comes back with no client secret and
      // paidWithCredit missing, and the client tries to confirm a payment
      // that was never created.
      const o = replay.docs[0].data();
      return {
        orderId: replay.docs[0].id,
        orderNumber: o.orderNumber ?? null,
        clientSecret: o.stripeClientSecret ?? null,
        paidWithCredit: o.paidWith === "credit",
        creditAppliedPence: o.creditAppliedPence ?? 0,
        amountDuePence: o.amountDuePence ?? o.breakdown?.totalPence ?? 0,
        breakdown: o.breakdown,
        entryNumbersPending: Array.isArray(o.entryNumbers) ? o.entryNumbers.length : 0,
      };
    }

    const { breakdown, competition } = await computePrice({ competitionId, quantity: qty, promoCode, userId: uid });
    const userSnap = await db.collection(Collections.users).doc(uid).get();
    const user = userSnap.data() ?? {};
    if (user.suspended === true) throw new HttpsError("permission-denied", "This account can't enter raffles. Contact support.");
    if (user.ageConfirmed !== true) throw new HttpsError("failed-precondition", "Confirm you're 18 or over before entering.");

    const orderRef = db.collection(Collections.orders).doc();
    const orderNumber = await nextOrderNumber();
    const compRef = db.collection(Collections.competitions).doc(competitionId);

    // Credit is reserved at the same moment as the entry numbers. Deducting it
    // later would let two checkouts spend the same balance; deducting it here
    // means an abandoned order has to give it back, which releaseReservation
    // and the failure paths below both do.
    const requestedCredit = Math.max(0, Math.floor(Number(creditToApplyPence ?? 0)));
    const availableCredit = Math.max(0, Number(user.creditBalancePence ?? 0));
    const creditApplied = Math.min(requestedCredit, availableCredit, breakdown.totalPence);
    const amountDue = breakdown.totalPence - creditApplied;

    // Stripe will not take less than 30p in GBP. Checked here rather than on the
    // basket total, so a penny raffle is fine as long as the card is asked for
    // at least 30p - and fine at any size when credit covers the lot. Checked
    // before the transaction, so a rejected order reserves no entry numbers.
    if (amountDue > 0 && amountDue < STRIPE_MINIMUM_PENCE) {
      throw new HttpsError(
        "failed-precondition",
        `Card payments start at £${(STRIPE_MINIMUM_PENCE / 100).toFixed(2)}. ` +
        "Add a few more entries, or use your site credit."
      );
    }

    const numbers = await db.runTransaction(async (tx) => {
      // Firestore will not allow a read after a write in the same
      // transaction. The credit move reads the user and the allocation reads
      // the raffle, so both reads happen here, and the credit is only written
      // once allocateEntryNumbers has done its own reading.
      const spend = creditApplied > 0
        ? await prepareCreditMove(tx, {
            uid,
            deltaPence: -creditApplied,
            reason: "order_spend",
            description: `Order ${orderNumber}`,
            orderId: orderRef.id,
          })
        : null;
      const alloc = await allocateEntryNumbers(tx, compRef, qty);
      spend?.apply(tx);
      tx.set(orderRef, {
        orderNumber,
        userId: uid,
        userEmail: user.email ?? req.auth?.token.email ?? null,
        userDisplayName: user.displayName ?? "Angler",
        competitionId,
        competitionTitle: competition.title,
        competitionImageUrl: competition.heroImageUrl ?? null,
        quantity: qty,
        entryNumbers: alloc.numbers,
        breakdown,
        totalPence: breakdown.totalPence,
        creditAppliedPence: creditApplied,
        amountDuePence: amountDue,
        currency: "gbp",
        paymentStatus: "pending",
        orderStatus: "reserved",
        idempotencyKey,
        reservedUntil: admin.firestore.Timestamp.fromMillis(Date.now() + RESERVATION_TTL_MS),
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      return alloc.numbers;
    });

    // Fully covered by credit: there is nothing for Stripe to do, so the order
    // is settled here. This is the one place other than the webhook that may
    // mark an order paid, and it can only do so because no card is involved.
    if (amountDue === 0) {
      await orderRef.update({
        paymentStatus: "paid",
        orderStatus: "confirmed",
        paidAt: admin.firestore.FieldValue.serverTimestamp(),
        paidWith: "credit",
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      await settlePaidOrder(orderRef.id);
      await writeAudit({
        action: "order.created", actorId: uid, actorRole: "customer",
        objectType: "order", objectId: orderRef.id,
        newValue: { orderNumber, competitionId, quantity: qty, paidWithCreditPence: creditApplied },
      });
      return {
        orderId: orderRef.id,
        orderNumber,
        clientSecret: null,
        paidWithCredit: true,
        creditAppliedPence: creditApplied,
        amountDuePence: 0,
        breakdown,
        entryNumbersPending: numbers.length,
      };
    }

    // A saved card is only ever reused after Stripe itself has confirmed it
    // belongs to this customer - requireOwnedPaymentMethod throws otherwise.
    // Attaching the customer to every card payment (not just ones being
    // saved) is what lets "save this card" work at all: Stripe can only save
    // a card against a customer it already knows about.
    const { paymentMethodId, savePaymentMethod } = req.data ?? {};
    const customerId = await getOrCreateStripeCustomer(uid, user.email ?? req.auth?.token.email ?? null);
    if (paymentMethodId) await requireOwnedPaymentMethod(uid, paymentMethodId);

    const intent = await stripe().paymentIntents.create(
      {
        amount: amountDue,
        currency: "gbp",
        customer: customerId,
        automatic_payment_methods: { enabled: true },
        ...(paymentMethodId ? { payment_method: paymentMethodId } : {}),
        ...(savePaymentMethod === true && !paymentMethodId ? { setup_future_usage: "off_session" as const } : {}),
        metadata: { orderId: orderRef.id, userId: uid, competitionId, quantity: String(qty) },
        description: `Rod Runners Raffles order ${orderNumber}`,
      },
      { idempotencyKey: `order_${orderRef.id}` }
    );

    await orderRef.update({
      stripePaymentIntentId: intent.id,
      stripeClientSecret: intent.client_secret,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    await writeAudit({
      action: "order.created", actorId: uid, actorRole: "customer",
      objectType: "order", objectId: orderRef.id,
      newValue: { orderNumber, competitionId, quantity: qty, totalPence: breakdown.totalPence },
    });

    return {
      orderId: orderRef.id,
      orderNumber,
      clientSecret: intent.client_secret,
      paidWithCredit: false,
      creditAppliedPence: creditApplied,
      amountDuePence: amountDue,
      publishableKeyHint: "Set STRIPE_PUBLISHABLE_KEY in local.properties",
      breakdown,
      entryNumbersPending: numbers.length,
    };
  }
);

/**
 * Buying site credit directly, with no raffle involved - what a customer
 * needs before they can play a paid instant-win game for the first time,
 * or just wants a balance sitting ready. Deliberately its own function
 * rather than a variant of createOrderAndPaymentIntent: that one reserves
 * entry numbers and prices a competition, neither of which apply here, and
 * bolting a "no raffle" branch onto it would make the entry-number path
 * harder to trust, not easier.
 */
const TOPUP_MIN_PENCE = STRIPE_MINIMUM_PENCE;
const TOPUP_MAX_PENCE = 500_00;

export const createCreditTopUpIntent = onCall(
  { region: REGION, secrets: [STRIPE_SECRET_KEY], enforceAppCheck: ENFORCE_APP_CHECK },
  async (req: CallableRequest) => {
    const uid = requireAuth(req);
    await assertNotRateLimited(`topup:${uid}`, 10, 60_000);

    const db = admin.firestore();
    const { amountPence, idempotencyKey } = req.data ?? {};
    const amount = Number(amountPence);
    if (!Number.isInteger(amount) || amount < TOPUP_MIN_PENCE || amount > TOPUP_MAX_PENCE) {
      throw new HttpsError(
        "invalid-argument",
        `Choose an amount between £${(TOPUP_MIN_PENCE / 100).toFixed(2)} and £${(TOPUP_MAX_PENCE / 100).toFixed(2)}.`
      );
    }
    if (!idempotencyKey || typeof idempotencyKey !== "string") {
      throw new HttpsError("invalid-argument", "Missing idempotency key.");
    }

    const replay = await db.collection(Collections.creditTopUps)
      .where("userId", "==", uid).where("idempotencyKey", "==", idempotencyKey).limit(1).get();
    if (!replay.empty) {
      const t = replay.docs[0].data();
      return { topUpId: replay.docs[0].id, clientSecret: t.stripeClientSecret ?? null, amountPence: t.amountPence };
    }

    const ref = db.collection(Collections.creditTopUps).doc();
    await ref.set({
      userId: uid,
      amountPence: amount,
      status: "pending",
      idempotencyKey,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    const { paymentMethodId, savePaymentMethod } = req.data ?? {};
    const customerId = await getOrCreateStripeCustomer(uid, req.auth?.token.email ?? null);
    if (paymentMethodId) await requireOwnedPaymentMethod(uid, paymentMethodId);

    const intent = await stripe().paymentIntents.create(
      {
        amount,
        currency: "gbp",
        customer: customerId,
        automatic_payment_methods: { enabled: true },
        ...(paymentMethodId ? { payment_method: paymentMethodId } : {}),
        ...(savePaymentMethod === true && !paymentMethodId ? { setup_future_usage: "off_session" as const } : {}),
        metadata: { kind: "credit_topup", topUpId: ref.id, userId: uid },
        description: "Rod Runners Raffles - site credit top-up",
      },
      { idempotencyKey: `topup_${ref.id}` }
    );

    await ref.update({
      stripePaymentIntentId: intent.id,
      stripeClientSecret: intent.client_secret,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    return { topUpId: ref.id, clientSecret: intent.client_secret, amountPence: amount };
  }
);

/**
 * A card only ever shows up here once it has actually been used to pay for
 * something with "save this card" ticked, or been added from the payment
 * methods screen - there's no separate "add a card with nothing to pay"
 * flow yet, so this list is always cards Stripe already confirmed worked.
 */
export const listSavedPaymentMethods = onCall(
  { region: REGION, secrets: [STRIPE_SECRET_KEY], enforceAppCheck: ENFORCE_APP_CHECK },
  async (req: CallableRequest) => {
    const uid = requireAuth(req);
    const userSnap = await admin.firestore().collection(Collections.users).doc(uid).get();
    const customerId = userSnap.data()?.stripeCustomerId;
    if (!customerId) return { methods: [] };

    const [methods, customer] = await Promise.all([
      stripe().paymentMethods.list({ customer: customerId, type: "card" }),
      stripe().customers.retrieve(customerId),
    ]);
    const defaultId = !customer.deleted ? (customer.invoice_settings?.default_payment_method as string | null) : null;

    return {
      methods: methods.data.map((m) => ({
        id: m.id,
        brand: m.card?.brand ?? "card",
        last4: m.card?.last4 ?? "····",
        expMonth: m.card?.exp_month ?? null,
        expYear: m.card?.exp_year ?? null,
        isDefault: m.id === defaultId,
      })),
    };
  }
);

export const deleteSavedPaymentMethod = onCall(
  { region: REGION, secrets: [STRIPE_SECRET_KEY], enforceAppCheck: ENFORCE_APP_CHECK },
  async (req: CallableRequest) => {
    const uid = requireAuth(req);
    const { paymentMethodId } = req.data ?? {};
    await requireOwnedPaymentMethod(uid, paymentMethodId);
    await stripe().paymentMethods.detach(paymentMethodId);
    return { removed: true };
  }
);

export const setDefaultPaymentMethod = onCall(
  { region: REGION, secrets: [STRIPE_SECRET_KEY], enforceAppCheck: ENFORCE_APP_CHECK },
  async (req: CallableRequest) => {
    const uid = requireAuth(req);
    const { paymentMethodId } = req.data ?? {};
    const method = await requireOwnedPaymentMethod(uid, paymentMethodId);
    await stripe().customers.update(method.customer as string, {
      invoice_settings: { default_payment_method: paymentMethodId },
    });
    return { defaultId: paymentMethodId };
  }
);

/**
 * The only place an order becomes "paid". Signature-verified, so the client
 * can never claim a successful payment.
 */
export const stripeWebhook = onRequest(
  { region: REGION, secrets: [STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET], cors: false },
  async (req, res) => {
    const signature = req.headers["stripe-signature"];
    let event: Stripe.Event;
    try {
      event = stripe().webhooks.constructEvent(req.rawBody, signature as string, STRIPE_WEBHOOK_SECRET.value());
    } catch (err) {
      console.error("Rejected webhook with a bad signature", err);
      res.status(400).send("Invalid signature");
      return;
    }

    const db = admin.firestore();
    // Idempotency: Stripe retries, so every event id is recorded once.
    const eventRef = db.collection(Collections.payments).doc(event.id);
    const seen = await eventRef.get();
    if (seen.exists) { res.json({ received: true, duplicate: true }); return; }
    await eventRef.set({
      type: event.type,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      payload: { id: (event.data.object as any).id, amount: (event.data.object as any).amount ?? null },
    });

    try {
      switch (event.type) {
        case "payment_intent.succeeded": {
          const intent = event.data.object as Stripe.PaymentIntent;
          if (intent.metadata?.kind === "credit_topup") await handleTopUpSucceeded(intent);
          else await handleSucceeded(intent);
          break;
        }
        case "payment_intent.payment_failed": {
          const intent = event.data.object as Stripe.PaymentIntent;
          if (intent.metadata?.kind === "credit_topup") await handleTopUpFailedOrCancelled(intent, "failed");
          else await handleFailedOrCancelled(intent, "failed");
          break;
        }
        case "payment_intent.canceled": {
          const intent = event.data.object as Stripe.PaymentIntent;
          if (intent.metadata?.kind === "credit_topup") await handleTopUpFailedOrCancelled(intent, "cancelled");
          else await handleFailedOrCancelled(intent, "cancelled");
          break;
        }
        case "charge.refunded":
          await handleRefund(event.data.object as Stripe.Charge); break;
        default:
          break;
      }
      res.json({ received: true });
    } catch (err) {
      console.error("Webhook handler failed", err);
      // 500 makes Stripe retry, which is what we want for a transient failure.
      res.status(500).send("Handler error");
    }
  }
);

async function handleSucceeded(intent: Stripe.PaymentIntent) {
  const db = admin.firestore();
  const orderId = intent.metadata.orderId;
  if (!orderId) return;
  const orderRef = db.collection(Collections.orders).doc(orderId);

  const result = await db.runTransaction(async (tx) => {
    const snap = await tx.get(orderRef);
    if (!snap.exists) return null;
    const o = snap.data()!;
    if (o.paymentStatus === "paid") return null; // already processed
    const expectedPence = Number(o.amountDuePence ?? o.totalPence);
    if (intent.amount_received !== expectedPence) {
      // Amount mismatch: never fulfil, flag for manual review.
      tx.update(orderRef, { paymentStatus: "review", orderStatus: "on_hold", updatedAt: admin.firestore.FieldValue.serverTimestamp() });
      return null;
    }
    tx.update(orderRef, {
      paymentStatus: "paid",
      orderStatus: "complete",
      paidAt: admin.firestore.FieldValue.serverTimestamp(),
      reservedUntil: admin.firestore.FieldValue.delete(),
      stripeChargeId: typeof intent.latest_charge === "string" ? intent.latest_charge : null,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    return o;
  });

  if (!result) return;

  await settlePaidOrder(orderId, { paymentIntentId: intent.id, amountPence: intent.amount_received });
}

/**
 * Everything that has to happen once an order is genuinely paid, whether that
 * was a card or a credit balance. Kept in one place so the two payment routes
 * can never drift apart and hand out entries on one path but not the other.
 */
async function settlePaidOrder(
  orderId: string,
  payment?: { paymentIntentId: string; amountPence: number | null }
): Promise<void> {
  const db = admin.firestore();
  const snap = await db.collection(Collections.orders).doc(orderId).get();
  const result = snap.data();
  if (!result) return;

  await materialiseEntries({
    orderId,
    userId: result.userId,
    userDisplayName: result.userDisplayName,
    competitionId: result.competitionId,
    competitionTitle: result.competitionTitle,
    numbers: result.entryNumbers,
  });

  // Instant wins are settled after the entries exist, so a prize can only ever
  // attach to a number the customer genuinely holds.
  const instantWins = await awardInstantWins({
    competitionId: result.competitionId,
    numbers: result.entryNumbers,
    userId: result.userId,
    userDisplayName: result.userDisplayName,
    orderId,
  });
  if (instantWins.length) {
    await db.collection(Collections.orders).doc(orderId).update({
      instantWins: instantWins.map((w) => ({
        instantWinId: w.id,
        entryNumber: w.entryNumber,
        prizeName: w.prizeName,
        valuePence: w.valuePence,
        imageUrl: w.imageUrl,
      })),
    });
  }

  if (result.breakdown?.promoCode) await recordRedemption(result.breakdown.promoCode, result.userId, orderId);

  // A referral only pays out once the referred customer has actually bought
  // something, which is what stops people farming codes with empty accounts.
  await payReferralIfDue({
    userId: result.userId,
    orderId,
    referrerRewardPence: REFERRAL_REFERRER_PENCE,
    refereeRewardPence: REFERRAL_REFEREE_PENCE,
  });

  await writeAudit({
    action: "payment.confirmed", actorId: "system", actorRole: "system",
    objectType: "order", objectId: orderId,
    newValue: {
      amountPence: payment?.amountPence ?? 0,
      paymentIntent: payment?.paymentIntentId ?? null,
      creditAppliedPence: result.creditAppliedPence ?? 0,
    },
  });

  await createUserNotification(result.userId, {
    category: "purchase",
    title: "Entries confirmed",
    body: `${result.quantity} ${result.quantity === 1 ? "entry" : "entries"} in ${result.competitionTitle}. Your numbers are in My Tickets.`,
    deepLink: `rrr://orders/${orderId}`,
  });
  await pushToUser(result.userId, "purchase", "Entries confirmed",
    `You're in the draw for ${result.competitionTitle}.`, { orderId });
  await queueEmail(result.userEmail, "purchase_confirmation", {
    displayName: result.userDisplayName,
    orderNumber: result.orderNumber,
    competitionTitle: result.competitionTitle,
    quantity: result.quantity,
    entryNumbers: result.entryNumbers.join(", "),
    total: `£${(result.totalPence / 100).toFixed(2)}`,
  });
}

/** The credit_topup twin of handleSucceeded - same shape, no entries or stock. */
async function handleTopUpSucceeded(intent: Stripe.PaymentIntent) {
  const db = admin.firestore();
  const topUpId = intent.metadata.topUpId;
  if (!topUpId) return;
  const ref = db.collection(Collections.creditTopUps).doc(topUpId);

  const result = await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) return null;
    const t = snap.data()!;
    if (t.status === "paid") return null; // already processed
    if (intent.amount_received !== Number(t.amountPence)) {
      tx.update(ref, { status: "review", updatedAt: admin.firestore.FieldValue.serverTimestamp() });
      return null;
    }
    const move = await prepareCreditMove(tx, {
      uid: t.userId,
      deltaPence: t.amountPence,
      reason: "credit_topup",
      description: "Site credit top-up",
      orderId: topUpId,
    });
    move.apply(tx);
    tx.update(ref, {
      status: "paid",
      paidAt: admin.firestore.FieldValue.serverTimestamp(),
      stripeChargeId: typeof intent.latest_charge === "string" ? intent.latest_charge : null,
      balanceAfterPence: move.balanceAfterPence,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    return t;
  });

  if (!result) return;

  await writeAudit({
    action: "credit.topup", actorId: "system", actorRole: "system",
    objectType: "creditTopUp", objectId: topUpId,
    newValue: { amountPence: result.amountPence, userId: result.userId },
  });
  await createUserNotification(result.userId, {
    category: "purchase",
    title: "Credit added",
    body: `£${(Number(result.amountPence) / 100).toFixed(2)} of site credit has been added to your account.`,
    deepLink: "rrr://account",
  });
  await pushToUser(result.userId, "purchase", "Credit added",
    `£${(Number(result.amountPence) / 100).toFixed(2)} of site credit is in your account.`, {});
}

async function handleTopUpFailedOrCancelled(intent: Stripe.PaymentIntent, status: "failed" | "cancelled") {
  const topUpId = intent.metadata.topUpId;
  if (!topUpId) return;
  await admin.firestore().collection(Collections.creditTopUps).doc(topUpId).update({
    status,
    failureMessage: intent.last_payment_error?.message ?? null,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
}

async function handleFailedOrCancelled(intent: Stripe.PaymentIntent, status: "failed" | "cancelled") {
  const db = admin.firestore();
  const orderId = intent.metadata.orderId;
  if (!orderId) return;
  const orderRef = db.collection(Collections.orders).doc(orderId);

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(orderRef);
    if (!snap.exists) return;
    const o = snap.data()!;
    if (o.paymentStatus === "paid" || o.orderStatus === "released") return;
    // Credit was reserved when the order was created, so a dead order has to
    // give it back - otherwise a failed card silently costs the customer.
    const credit = Number(o.creditAppliedPence ?? 0);
    if (credit > 0) {
      await moveCredit(tx, {
        uid: o.userId, deltaPence: credit, reason: "order_released",
        description: `Order ${o.orderNumber} did not complete`, orderId: orderRef.id,
      });
    }
    const compRef = db.collection(Collections.competitions).doc(o.competitionId);
    await releaseReservation(tx, compRef, o.quantity);
    tx.update(orderRef, {
      paymentStatus: status,
      orderStatus: "released",
      entryNumbers: [],
      failureMessage: intent.last_payment_error?.message ?? null,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  });

  await writeAudit({
    action: "payment.failed", actorId: "system", actorRole: "system",
    objectType: "order", objectId: orderId, newValue: { status },
  });
}

async function handleRefund(charge: Stripe.Charge) {
  const db = admin.firestore();
  const orderId = charge.metadata?.orderId
    ?? (await db.collection(Collections.orders).where("stripeChargeId", "==", charge.id).limit(1).get()).docs[0]?.id;
  if (!orderId) {
    await handleTopUpRefund(charge);
    return;
  }
  const full = charge.amount_refunded >= charge.amount;

  await db.collection(Collections.orders).doc(orderId).update({
    paymentStatus: full ? "refunded" : "partially_refunded",
    refundedPence: charge.amount_refunded,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  if (full) {
    // A fully refunded order forfeits its entries; the numbers stay retired so
    // the draw record for the raffle remains a faithful history.
    const entries = await db.collection(Collections.entries).where("orderId", "==", orderId).get();
    const batch = db.batch();
    entries.forEach(d => batch.update(d.ref, { status: "void", voidReason: "refunded" }));
    await batch.commit();

    // Stripe only ever refunds what Stripe took. Any part of the order paid
    // with credit has to be returned as credit, or the customer is simply out
    // of pocket for it.
    const order = (await db.collection(Collections.orders).doc(orderId).get()).data();
    const credit = Number(order?.creditAppliedPence ?? 0);
    if (credit > 0 && order?.creditReturnedAt == null) {
      await moveCreditStandalone({
        uid: order!.userId,
        deltaPence: credit,
        reason: "order_refund",
        description: `Refund of order ${order!.orderNumber}`,
        orderId,
      });
      await db.collection(Collections.orders).doc(orderId).update({
        creditReturnedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    }
  }

  await writeAudit({
    action: "refund.processed", actorId: "system", actorRole: "system",
    objectType: "order", objectId: orderId, newValue: { refundedPence: charge.amount_refunded, full },
  });
}

/** The credit_topup twin of a refund - claws back what's still there. If the
 *  customer has already spent the credit, moveCreditStandalone refuses to
 *  take the balance negative, so that case is flagged for manual review
 *  rather than silently forced through. */
async function handleTopUpRefund(charge: Stripe.Charge) {
  const db = admin.firestore();
  const snap = (await db.collection(Collections.creditTopUps)
    .where("stripeChargeId", "==", charge.id).limit(1).get()).docs[0];
  if (!snap) return;

  const topUp = snap.data();
  const full = charge.amount_refunded >= charge.amount;

  await snap.ref.update({
    status: full ? "refunded" : "partially_refunded",
    refundedPence: charge.amount_refunded,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  try {
    await moveCreditStandalone({
      uid: topUp.userId,
      deltaPence: -charge.amount_refunded,
      reason: "credit_topup",
      description: "Refund of a site credit top-up",
      orderId: snap.id,
    });
    await writeAudit({
      action: "credit.topup", actorId: "system", actorRole: "system",
      objectType: "creditTopUp", objectId: snap.id,
      newValue: { refundedPence: charge.amount_refunded, full },
    });
  } catch (err) {
    // Balance couldn't cover the clawback - already spent. Flag it instead
    // of crashing the webhook (Stripe would just retry and fail the same way).
    await writeAudit({
      action: "credit.topup", actorId: "system", actorRole: "system",
      objectType: "creditTopUp", objectId: snap.id,
      newValue: { refundClawbackFailed: true, refundedPence: charge.amount_refunded },
    });
  }
}

/** Admin-initiated refund. Stripe is the source of truth; the webhook finishes the job. */
export const refundOrder = onCall(
  { region: REGION, secrets: [STRIPE_SECRET_KEY], enforceAppCheck: ENFORCE_APP_CHECK },
  async (req: CallableRequest) => {
    const admin_ = await requireAdmin(req, "orders.refund");
    const { orderId, amountPence, reason } = req.data ?? {};
    const db = admin.firestore();
    const snap = await db.collection(Collections.orders).doc(orderId).get();
    if (!snap.exists) throw new HttpsError("not-found", "Order not found.");
    const o = snap.data()!;
    if (o.paymentStatus !== "paid" && o.paymentStatus !== "partially_refunded") {
      throw new HttpsError("failed-precondition", "Only a paid order can be refunded.");
    }
    const refund = await stripe().refunds.create({
      payment_intent: o.stripePaymentIntentId,
      amount: amountPence ? Number(amountPence) : undefined,
      metadata: { orderId, reason: reason ?? "", refundedBy: admin_.uid },
    });
    return { refundId: refund.id, status: refund.status };
  }
);

/** Releases numbers from checkouts that were abandoned or interrupted. */
export const releaseExpiredReservations = onSchedule(
  { region: REGION, schedule: "every 5 minutes" },
  async () => {
    const db = admin.firestore();
    const stale = await db.collection(Collections.orders)
      .where("orderStatus", "==", "reserved")
      .where("reservedUntil", "<", admin.firestore.Timestamp.now())
      .limit(200).get();

    for (const doc of stale.docs) {
      await db.runTransaction(async (tx) => {
        const fresh = await tx.get(doc.ref);
        const o = fresh.data();
        if (!o || o.orderStatus !== "reserved") return;
        const credit = Number(o.creditAppliedPence ?? 0);
        if (credit > 0) {
          await moveCredit(tx, {
            uid: o.userId, deltaPence: credit, reason: "order_released",
            description: `Order ${o.orderNumber} expired before payment`, orderId: doc.ref.id,
          });
        }
        const compRef = db.collection(Collections.competitions).doc(o.competitionId);
        await releaseReservation(tx, compRef, o.quantity);
        tx.update(doc.ref, {
          orderStatus: "expired", paymentStatus: "cancelled", entryNumbers: [],
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        });
      });
    }
    console.log(`Released ${stale.size} expired reservations`);
  }
);

async function nextOrderNumber(): Promise<string> {
  const db = admin.firestore();
  const ref = db.collection("counters").doc("orders");
  const value = await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const next = (snap.exists ? snap.data()!.value : 100000) + 1;
    tx.set(ref, { value: next }, { merge: true });
    return next;
  });
  return `RRR-${value}`;
}

async function recordRedemption(code: string, userId: string, orderId: string) {
  const db = admin.firestore();
  const snap = await db.collection(Collections.promotions).where("code", "==", code).limit(1).get();
  if (snap.empty) return;
  await db.collection(Collections.promotionRedemptions).doc(orderId).set({
    promotionId: snap.docs[0].id, code, userId, orderId,
    redeemedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  await snap.docs[0].ref.update({ usageCount: admin.firestore.FieldValue.increment(1) });
}

export const applyPromoCode = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const uid = requireAuth(req);
  const { code, competitionId, subtotalPence } = req.data ?? {};
  return validatePromotion(String(code), String(competitionId), uid, Number(subtotalPence));
});
