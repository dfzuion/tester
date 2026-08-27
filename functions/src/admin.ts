import * as admin from "firebase-admin";
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { Collections, REGION, ENFORCE_APP_CHECK } from "./config";
import { requireAdmin, requireAuth, AdminRole, RolePermissions } from "./guards";
import { writeAudit } from "./audit";
import { queueEmail, pushToTopic } from "./notifications";

/** Creates the customer profile the moment an account is registered. */
export const onUserCreated = onDocumentCreated(
  { region: REGION, document: "userSignups/{uid}" },
  async (event) => {
    const uid = event.params.uid;
    const data = event.data?.data() ?? {};
    const db = admin.firestore();
    await db.collection(Collections.users).doc(uid).set({
      email: data.email ?? null,
      displayName: data.displayName ?? "Angler",
      phone: null,
      address: null,
      photoUrl: null,
      ageConfirmed: data.ageConfirmed === true,
      marketingOptIn: data.marketingOptIn === true,
      suspended: false,
      referralCode: generateReferralCode(),
      referredBy: data.referredBy ?? null,
      notificationPreferences: {
        purchase: true, win: true, ending_soon: true,
        new_competition: true, promotion: data.marketingOptIn === true,
        account: true, payment: true, refund: true, support: true,
      },
      fcmTokens: [],
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });

    if (data.referredBy) {
      await db.collection(Collections.referrals).add({
        referrerCode: data.referredBy, referredUserId: uid,
        status: "pending", rewardIssued: false,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    }
    await queueEmail(data.email, "welcome", { displayName: data.displayName ?? "Angler" });
  }
);

function generateReferralCode(): string {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  return Array.from({ length: 7 }, () => alphabet[Math.floor(Math.random() * alphabet.length)]).join("");
}

/** Only a Super Admin can hand out roles, and every change is logged. */
export const setAdminRole = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const ctx = await requireAdmin(req, "*");
  if (ctx.role !== "super_admin") throw new HttpsError("permission-denied", "Only a Super Admin can change roles.");

  const { targetUid, role, active } = req.data ?? {};
  if (!Object.keys(RolePermissions).includes(role)) throw new HttpsError("invalid-argument", "Unknown role.");

  const db = admin.firestore();
  const previous = (await db.collection(Collections.adminUsers).doc(targetUid).get()).data() ?? null;

  await admin.auth().setCustomUserClaims(targetUid, active === false ? {} : { role, admin: true });
  await db.collection(Collections.adminUsers).doc(targetUid).set({
    role: role as AdminRole,
    active: active !== false,
    permissions: RolePermissions[role as AdminRole],
    updatedBy: ctx.uid,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  }, { merge: true });

  await writeAudit({
    action: "admin.role.changed", actorId: ctx.uid, actorRole: "admin",
    objectType: "adminUser", objectId: targetUid,
    previousValue: previous, newValue: { role, active: active !== false },
  });
  return { ok: true };
});

export const suspendCustomer = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const ctx = await requireAdmin(req, "customers.suspend");
  const { userId, suspended, reason } = req.data ?? {};
  const db = admin.firestore();
  const prev = (await db.collection(Collections.users).doc(userId).get()).data();
  await db.collection(Collections.users).doc(userId).update({
    suspended: suspended === true, suspensionReason: reason ?? null,
    suspendedAt: suspended === true ? admin.firestore.FieldValue.serverTimestamp() : null,
  });
  await writeAudit({
    action: "account.suspended", actorId: ctx.uid, actorRole: "admin",
    objectType: "user", objectId: userId,
    previousValue: { suspended: prev?.suspended === true }, newValue: { suspended: suspended === true, reason },
  });
  return { ok: true };
});

/** Publishing a raffle is a server action so status transitions stay legal. */
export const setCompetitionStatus = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const ctx = await requireAdmin(req, "competitions.write");
  const { competitionId, status } = req.data ?? {};
  const allowed = ["draft", "scheduled", "live", "paused", "cancelled"];
  if (!allowed.includes(status)) throw new HttpsError("invalid-argument", "Unknown status.");

  const db = admin.firestore();
  const ref = db.collection(Collections.competitions).doc(competitionId);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "Raffle not found.");
  const c = snap.data()!;
  if (c.status === "closed" || c.status === "drawn") {
    throw new HttpsError("failed-precondition", "A closed raffle can't be reopened.");
  }
  if (status === "live") {
    for (const field of ["title", "entryPricePence", "maxEntries", "closesAt", "heroImageUrl", "rulesId"]) {
      if (c[field] == null) throw new HttpsError("failed-precondition", `Set ${field} before publishing.`);
    }
  }

  await ref.update({ status, updatedAt: admin.firestore.FieldValue.serverTimestamp(), publishedBy: ctx.uid });
  await writeAudit({
    action: status === "live" ? "competition.published" : "competition.updated",
    actorId: ctx.uid, actorRole: "admin", objectType: "competition", objectId: competitionId,
    previousValue: { status: c.status }, newValue: { status },
  });

  if (status === "live") {
    await pushToTopic("new_competitions", "New raffle live", `${c.title} — entries from £${(c.entryPricePence / 100).toFixed(2)}.`,
      { competitionId });
  }
  return { ok: true };
});

/** Account deletion: anonymises the customer but preserves the entry ledger. */
export const deleteMyAccount = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const uid = requireAuth(req);
  const db = admin.firestore();

  const openEntries = await db.collection(Collections.entries)
    .where("userId", "==", uid).where("status", "==", "active").count().get();
  const live = openEntries.data().count;

  await db.collection(Collections.users).doc(uid).update({
    email: null, displayName: "Deleted account", phone: null, address: null,
    photoUrl: null, fcmTokens: [], deleted: true,
    deletedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  await admin.auth().deleteUser(uid);

  await writeAudit({
    action: "account.suspended", actorId: uid, actorRole: "customer",
    objectType: "user", objectId: uid, newValue: { deleted: true, openEntriesAtDeletion: live },
  });
  return { ok: true, openEntriesRetained: live };
});

export const registerFcmToken = onCall({ region: REGION }, async (req: CallableRequest) => {
  const uid = requireAuth(req);
  const { token } = req.data ?? {};
  if (!token) throw new HttpsError("invalid-argument", "Missing token.");
  await admin.firestore().collection(Collections.users).doc(uid).update({
    fcmTokens: admin.firestore.FieldValue.arrayUnion(token),
  });
  return { ok: true };
});
