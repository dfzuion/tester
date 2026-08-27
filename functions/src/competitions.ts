import * as admin from "firebase-admin";
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { Collections, REGION } from "./config";
import { requireAdmin } from "./guards";
import { writeAudit } from "./audit";

/**
 * COMPETITION AUTHORING
 * ------------------------------------------------------------------
 * Raffles are created and edited here rather than by hand in the console, so
 * every change is validated, attributed and audited.
 *
 * A raffle is always born as a draft. Structural fields - the ones customers
 * rely on being fixed once they have paid - can only be changed while no
 * entries have been sold. Cosmetic fields stay editable for the whole life of
 * the raffle.
 */

const STRUCTURAL = [
  "entryPricePence", "maxEntries", "allocationMode", "bundles",
  "maxEntriesPerCustomer", "bookingFeePence",
];

interface Bundle { quantity: number; pricePence: number; label: string | null }

function cleanString(v: unknown, field: string, max: number, required = true): string {
  const s = typeof v === "string" ? v.trim() : "";
  if (!s) {
    if (required) throw new HttpsError("invalid-argument", `${field} is required.`);
    return "";
  }
  if (s.length > max) throw new HttpsError("invalid-argument", `${field} is too long.`);
  return s;
}

function wholePence(v: unknown, field: string, min: number, max: number): number {
  const n = Number(v);
  if (!Number.isInteger(n) || n < min || n > max) {
    throw new HttpsError("invalid-argument", `${field} must be a whole number of pence between ${min} and ${max}.`);
  }
  return n;
}

function cleanBundles(v: unknown, entryPricePence: number): Bundle[] {
  if (v == null) return [];
  if (!Array.isArray(v)) throw new HttpsError("invalid-argument", "Bundles must be a list.");
  if (v.length > 8) throw new HttpsError("invalid-argument", "Eight bundles is the maximum.");
  return v.map((b: any, i: number) => {
    const quantity = Number(b?.quantity);
    if (!Number.isInteger(quantity) || quantity < 2 || quantity > 1000) {
      throw new HttpsError("invalid-argument", `Bundle ${i + 1}: quantity must be between 2 and 1000.`);
    }
    const pricePence = wholePence(b?.pricePence, `Bundle ${i + 1} price`, 1, 10_000_00);
    // A bundle that costs more than buying singly is almost always a typo.
    if (pricePence > quantity * entryPricePence) {
      throw new HttpsError("invalid-argument", `Bundle ${i + 1} costs more than ${quantity} single entries.`);
    }
    return { quantity, pricePence, label: b?.label ? cleanString(b.label, "Bundle label", 40, false) : null } as Bundle;
  });
}

/** Shared validation for create and edit. Returns only the fields supplied. */
function buildPayload(data: any, partial: boolean): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  const has = (k: string) => data[k] !== undefined;
  const need = (k: string) => !partial || has(k);

  if (need("title")) out.title = cleanString(data.title, "Title", 120);
  if (need("prizeName")) out.prizeName = cleanString(data.prizeName, "Prize name", 120);
  if (need("heroImageUrl")) out.heroImageUrl = cleanString(data.heroImageUrl, "Hero image", 500);
  if (has("description")) out.description = cleanString(data.description, "Description", 4000, false);
  if (has("brand")) out.brand = cleanString(data.brand, "Brand", 60, false);
  if (has("category")) out.category = cleanString(data.category, "Category", 40, false);
  if (has("galleryImageUrls")) {
    const g = data.galleryImageUrls;
    if (!Array.isArray(g) || g.length > 10) throw new HttpsError("invalid-argument", "Up to ten gallery images.");
    out.galleryImageUrls = g.map((u: unknown) => cleanString(u, "Gallery image", 500));
  }
  if (has("retailValuePence")) out.retailValuePence = wholePence(data.retailValuePence, "Retail value", 0, 1_000_000_00);
  if (need("entryPricePence")) out.entryPricePence = wholePence(data.entryPricePence, "Entry price", 1, 1_000_00);
  if (has("bookingFeePence")) out.bookingFeePence = wholePence(data.bookingFeePence, "Booking fee", 0, 1_000_00);

  if (need("maxEntries")) {
    const n = Number(data.maxEntries);
    if (!Number.isInteger(n) || n < 2 || n > 1_000_000) {
      throw new HttpsError("invalid-argument", "Total entries must be between 2 and 1,000,000.");
    }
    out.maxEntries = n;
  }
  if (has("maxEntriesPerCustomer")) {
    const n = Number(data.maxEntriesPerCustomer);
    if (!Number.isInteger(n) || n < 0 || n > 100_000) {
      throw new HttpsError("invalid-argument", "Per-customer limit looks wrong.");
    }
    out.maxEntriesPerCustomer = n;
  }
  if (has("allocationMode")) {
    if (!["sequential", "random"].includes(data.allocationMode)) {
      throw new HttpsError("invalid-argument", "Allocation must be sequential or random.");
    }
    out.allocationMode = data.allocationMode;
  }
  if (has("bundles")) {
    const price = typeof out.entryPricePence === "number" ? out.entryPricePence : Number(data.entryPricePence ?? 0);
    out.bundles = cleanBundles(data.bundles, price);
  }
  if (need("closesAtMillis")) {
    const ms = Number(data.closesAtMillis);
    if (!Number.isFinite(ms) || ms <= Date.now()) {
      throw new HttpsError("invalid-argument", "The closing time must be in the future.");
    }
    if (ms > Date.now() + 400 * 24 * 3600 * 1000) {
      throw new HttpsError("invalid-argument", "The closing time is more than a year away.");
    }
    out.closesAt = admin.firestore.Timestamp.fromMillis(ms);
  }
  if (has("opensAtMillis") && data.opensAtMillis != null) {
    out.opensAt = admin.firestore.Timestamp.fromMillis(Number(data.opensAtMillis));
  }
  if (has("featured")) out.featured = data.featured === true;
  if (has("rulesId")) out.rulesId = cleanString(data.rulesId, "Rules", 60, false) || null;
  if (has("minimumAge")) out.minimumAge = Number(data.minimumAge) || 18;
  return out;
}

export const createCompetition = onCall({ region: REGION, enforceAppCheck: true }, async (req: CallableRequest) => {
  const ctx = await requireAdmin(req, "competitions.write");
  const payload = buildPayload(req.data ?? {}, false);

  const db = admin.firestore();
  const ref = db.collection(Collections.competitions).doc();
  await ref.set({
    ...payload,
    status: "draft",
    entriesSold: 0,
    isDemo: false,
    resultPublished: false,
    winnerMechanism: "random_eligible_entry",
    winnerNameDisplay: "first_name_last_initial",
    geoRestriction: "GB",
    allocationMode: payload.allocationMode ?? "sequential",
    minimumAge: payload.minimumAge ?? 18,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    createdBy: ctx.uid,
  });

  await writeAudit({
    action: "competition.created",
    actorId: ctx.uid, actorRole: "admin",
    objectType: "competition", objectId: ref.id,
    newValue: payload,
  });
  return { competitionId: ref.id };
});

export const updateCompetition = onCall({ region: REGION, enforceAppCheck: true }, async (req: CallableRequest) => {
  const ctx = await requireAdmin(req, "competitions.write");
  const { competitionId } = req.data ?? {};
  if (!competitionId) throw new HttpsError("invalid-argument", "Which raffle?");

  const db = admin.firestore();
  const ref = db.collection(Collections.competitions).doc(competitionId);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "Raffle not found.");
  const current = snap.data()!;
  if (["closed", "drawn", "cancelled"].includes(current.status)) {
    throw new HttpsError("failed-precondition", "This raffle is finished and can't be edited.");
  }

  const payload = buildPayload(req.data ?? {}, true);
  // Once someone has paid, the deal they bought into is fixed.
  if ((current.entriesSold ?? 0) > 0) {
    const blocked = STRUCTURAL.filter((f) => payload[f] !== undefined);
    if (blocked.length) {
      throw new HttpsError(
        "failed-precondition",
        `Entries have been sold, so ${blocked.join(", ")} can no longer be changed.`
      );
    }
  }

  await ref.update({
    ...payload,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedBy: ctx.uid,
  });
  await writeAudit({
    action: "competition.updated",
    actorId: ctx.uid, actorRole: "admin",
    objectType: "competition", objectId: competitionId,
    previousValue: current, newValue: payload,
  });
  return { ok: true };
});

/** Only ever allowed for a draft nobody has entered. */
export const deleteDraftCompetition = onCall({ region: REGION, enforceAppCheck: true }, async (req: CallableRequest) => {
  const ctx = await requireAdmin(req, "competitions.write");
  const { competitionId } = req.data ?? {};
  const db = admin.firestore();
  const ref = db.collection(Collections.competitions).doc(competitionId);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "Raffle not found.");
  const c = snap.data()!;
  if (c.status !== "draft" || (c.entriesSold ?? 0) > 0) {
    throw new HttpsError("failed-precondition", "Only an unentered draft can be deleted.");
  }
  await ref.delete();
  await writeAudit({
    action: "competition.deleted", actorId: ctx.uid, actorRole: "admin",
    objectType: "competition", objectId: competitionId, previousValue: c,
  });
  return { ok: true };
});
