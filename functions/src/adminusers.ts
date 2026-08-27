import * as admin from "firebase-admin";
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { Collections, REGION, ENFORCE_APP_CHECK } from "./config";
import { AdminRole, RolePermissions, requireAdmin, requireAuth } from "./guards";
import { writeAudit } from "./audit";

/**
 * The first administrator is a chicken-and-egg problem: setAdminRole needs a
 * Super Admin to call it, and on a fresh project there isn't one.
 *
 * This solves it once and then closes behind itself. It works only while the
 * adminUsers collection is empty, and only for a caller who knows the
 * ADMIN_BOOTSTRAP_KEY secret, which lives in Secret Manager and never in the
 * app. After the first Super Admin exists the call refuses everyone, for good.
 */
export const ADMIN_BOOTSTRAP_KEY = defineSecret("ADMIN_BOOTSTRAP_KEY");

export const bootstrapFirstAdmin = onCall(
  { region: REGION, secrets: [ADMIN_BOOTSTRAP_KEY] },
  async (req: CallableRequest) => {
    const uid = requireAuth(req);
    const supplied = String(req.data?.key ?? "");
    const expected = ADMIN_BOOTSTRAP_KEY.value();
    if (!expected) throw new HttpsError("failed-precondition", "Bootstrap is not configured.");
    // Length-independent comparison: never leak the key through timing.
    if (supplied.length !== expected.length || !supplied.length) {
      throw new HttpsError("permission-denied", "That setup key is not right.");
    }
    let diff = 0;
    for (let i = 0; i < expected.length; i++) diff |= supplied.charCodeAt(i) ^ expected.charCodeAt(i);
    if (diff !== 0) throw new HttpsError("permission-denied", "That setup key is not right.");

    const db = admin.firestore();
    const existing = await db.collection(Collections.adminUsers).limit(1).get();
    if (!existing.empty) {
      throw new HttpsError("failed-precondition", "This project already has an administrator.");
    }

    await admin.auth().setCustomUserClaims(uid, { role: "super_admin", admin: true });
    await db.collection(Collections.adminUsers).doc(uid).set({
      role: "super_admin",
      active: true,
      permissions: RolePermissions.super_admin,
      email: req.auth?.token.email ?? null,
      displayName: req.auth?.token.name ?? null,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedBy: uid,
    });
    await writeAudit({
      action: "admin.role.changed", actorId: uid, actorRole: "admin",
      objectType: "adminUser", objectId: uid,
      newValue: { role: "super_admin", bootstrap: true },
    });
    // The claim only reaches the device on the next token refresh.
    return { ok: true, message: "You are now Super Admin. Sign out and back in to pick up the change." };
  }
);

/** Everyone with a role, for the admin management screen. */
export const listAdminUsers = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  await requireAdmin(req, "*");
  const snap = await admin.firestore().collection(Collections.adminUsers).get();
  return {
    admins: snap.docs.map((d) => {
      const a = d.data();
      return {
        uid: d.id,
        email: a.email ?? null,
        displayName: a.displayName ?? null,
        role: a.role ?? "support",
        active: a.active !== false,
      };
    }),
  };
});

/**
 * Grants a role by email address, so a Super Admin never has to hunt for a uid.
 * The person must already have signed up - we promote an account, never create
 * one, and an admin account with no password of its own is one fewer way in.
 */
export const grantAdminByEmail = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const ctx = await requireAdmin(req, "*");
  if (ctx.role !== "super_admin") throw new HttpsError("permission-denied", "Only a Super Admin can change roles.");

  const email = String(req.data?.email ?? "").trim().toLowerCase();
  const role = String(req.data?.role ?? "");
  if (!email) throw new HttpsError("invalid-argument", "Enter their email address.");
  if (!Object.keys(RolePermissions).includes(role)) throw new HttpsError("invalid-argument", "Unknown role.");

  let user;
  try {
    user = await admin.auth().getUserByEmail(email);
  } catch {
    throw new HttpsError("not-found", "Nobody has signed up with that email address yet.");
  }
  if (user.uid === ctx.uid) throw new HttpsError("failed-precondition", "You can't change your own role.");

  const db = admin.firestore();
  const previous = (await db.collection(Collections.adminUsers).doc(user.uid).get()).data() ?? null;

  await admin.auth().setCustomUserClaims(user.uid, { role, admin: true });
  await db.collection(Collections.adminUsers).doc(user.uid).set({
    role: role as AdminRole,
    active: true,
    permissions: RolePermissions[role as AdminRole],
    email: user.email ?? email,
    displayName: user.displayName ?? null,
    updatedBy: ctx.uid,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  }, { merge: true });

  await writeAudit({
    action: "admin.role.changed", actorId: ctx.uid, actorRole: "admin",
    objectType: "adminUser", objectId: user.uid,
    previousValue: previous, newValue: { role, active: true },
  });
  return { uid: user.uid, message: `${email} is now ${role.replace("_", " ")}.` };
});

/** Revokes admin rights. The customer account itself is untouched. */
export const revokeAdmin = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const ctx = await requireAdmin(req, "*");
  if (ctx.role !== "super_admin") throw new HttpsError("permission-denied", "Only a Super Admin can change roles.");
  const targetUid = String(req.data?.uid ?? "");
  if (!targetUid) throw new HttpsError("invalid-argument", "Which administrator?");
  if (targetUid === ctx.uid) throw new HttpsError("failed-precondition", "You can't remove yourself.");

  const db = admin.firestore();
  const ref = db.collection(Collections.adminUsers).doc(targetUid);
  const previous = (await ref.get()).data() ?? null;

  // Never leave the project with no way in.
  const supers = await db.collection(Collections.adminUsers)
    .where("role", "==", "super_admin").where("active", "==", true).get();
  if (previous?.role === "super_admin" && supers.size <= 1) {
    throw new HttpsError("failed-precondition", "That's the last Super Admin - promote someone else first.");
  }

  await admin.auth().setCustomUserClaims(targetUid, {});
  await ref.set({ active: false, updatedBy: ctx.uid, updatedAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });
  await writeAudit({
    action: "admin.role.changed", actorId: ctx.uid, actorRole: "admin",
    objectType: "adminUser", objectId: targetUid,
    previousValue: previous, newValue: { active: false },
  });
  return { ok: true };
});
