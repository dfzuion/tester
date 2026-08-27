import * as admin from "firebase-admin";
import { HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { Collections } from "./config";

export type AdminRole = "super_admin" | "admin" | "support" | "content_manager";

/** Permissions per role. Checked server-side on every privileged call. */
export const RolePermissions: Record<AdminRole, string[]> = {
  super_admin: ["*"],
  admin: [
    "competitions.write", "competitions.close", "competitions.draw",
    "orders.read", "orders.refund", "customers.read", "customers.suspend",
    "content.write", "promotions.write", "support.write", "reports.read",
  ],
  support: ["orders.read", "customers.read", "support.write", "reports.read"],
  content_manager: ["content.write", "competitions.write", "promotions.write"],
};

export function requireAuth(req: CallableRequest): string {
  if (!req.auth?.uid) throw new HttpsError("unauthenticated", "Log in to continue.");
  if (req.auth.token.email_verified === false) {
    throw new HttpsError("failed-precondition", "Verify your email address to continue.");
  }
  return req.auth.uid;
}

export interface AdminContext { uid: string; role: AdminRole }

/**
 * Admin identity comes from a custom claim set by a Super Admin, cross-checked
 * against the adminUsers document. A client can never grant itself a role.
 */
export async function requireAdmin(req: CallableRequest, permission: string): Promise<AdminContext> {
  const uid = requireAuth(req);
  const claimRole = req.auth?.token.role as AdminRole | undefined;
  if (!claimRole) throw new HttpsError("permission-denied", "Administrator access required.");

  const snap = await admin.firestore().collection(Collections.adminUsers).doc(uid).get();
  if (!snap.exists || snap.data()!.active !== true || snap.data()!.role !== claimRole) {
    throw new HttpsError("permission-denied", "Administrator access required.");
  }

  const allowed = RolePermissions[claimRole] ?? [];
  if (!allowed.includes("*") && !allowed.includes(permission)) {
    throw new HttpsError("permission-denied", `Your role can't perform: ${permission}`);
  }
  return { uid, role: claimRole };
}

/** Simple Firestore-backed sliding-window rate limiter. */
export async function assertNotRateLimited(key: string, limit: number, windowMs: number): Promise<void> {
  const db = admin.firestore();
  const ref = db.collection(Collections.rateLimits).doc(encodeURIComponent(key));
  const now = Date.now();
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const hits: number[] = (snap.exists ? snap.data()!.hits : []) ?? [];
    const recent = hits.filter((t: number) => now - t < windowMs);
    if (recent.length >= limit) {
      throw new HttpsError("resource-exhausted", "Too many attempts. Wait a moment and try again.");
    }
    recent.push(now);
    tx.set(ref, { hits: recent, updatedAt: now }, { merge: true });
  });
}
