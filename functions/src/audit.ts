import * as admin from "firebase-admin";
import { Collections } from "./config";

export type AuditAction =
  | "competition.created" | "competition.updated" | "competition.published"
  | "competition.unpublished" | "competition.paused" | "competition.closed"
  | "competition.cancelled" | "competition.deleted" | "order.created" | "payment.confirmed"
  | "payment.failed" | "entries.allocated" | "refund.processed"
  | "winner.selected" | "result.published" | "account.suspended"
  | "admin.role.changed" | "content.updated" | "promotion.created"
  | "instantwin.added" | "instantwin.removed" | "instantwin.awarded"
  | "instantwin.claim.updated"
  | "credit.coupon.created" | "credit.coupon.updated" | "credit.coupon.redeemed"
  | "credit.granted" | "credit.spent" | "credit.returned"
  | "credit.daily_spin"
  | "game.week.paid"
  | "instantwingame.created" | "instantwingame.updated" | "instantwingame.played" | "instantwingame.claim.updated";

export interface AuditParams {
  action: AuditAction;
  actorId: string;
  actorRole: "customer" | "admin" | "system";
  objectType: string;
  objectId: string;
  previousValue?: unknown;
  newValue?: unknown;
  metadata?: Record<string, unknown>;
}

/**
 * Audit logs are append-only. Firestore rules deny update/delete on this
 * collection for every client, including admins; only the Admin SDK writes here.
 */
export async function writeAudit(p: AuditParams, tx?: FirebaseFirestore.Transaction): Promise<void> {
  const db = admin.firestore();
  const ref = db.collection(Collections.auditLogs).doc();
  const payload = {
    ...p,
    previousValue: p.previousValue ?? null,
    newValue: p.newValue ?? null,
    metadata: p.metadata ?? {},
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  };
  if (tx) tx.set(ref, payload);
  else await ref.set(payload);
}
