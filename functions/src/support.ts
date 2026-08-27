import * as admin from "firebase-admin";
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { Collections, REGION, ENFORCE_APP_CHECK } from "./config";
import { requireAuth, requireAdmin, assertNotRateLimited } from "./guards";
import { createUserNotification, pushToUser, queueEmail } from "./notifications";

const CATEGORIES = ["order", "payment", "competition", "account", "report_problem", "other"];

export const createSupportTicket = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const uid = requireAuth(req);
  await assertNotRateLimited(`support:${uid}`, 5, 3600_000);

  const { category, subject, message, orderId } = req.data ?? {};
  if (!CATEGORIES.includes(category)) throw new HttpsError("invalid-argument", "Pick a category.");
  if (!subject || String(subject).length < 3) throw new HttpsError("invalid-argument", "Add a short subject.");
  if (!message || String(message).length < 10) throw new HttpsError("invalid-argument", "Tell us a bit more so we can help.");

  const db = admin.firestore();
  const user = (await db.collection(Collections.users).doc(uid).get()).data();
  const ref = db.collection(Collections.supportTickets).doc();
  const ticketId = `RRR-S${Date.now().toString(36).toUpperCase()}`;

  await ref.set({
    ticketId, userId: uid, userEmail: user?.email ?? null,
    userDisplayName: user?.displayName ?? "Angler",
    category, subject: String(subject).slice(0, 120), orderId: orderId ?? null,
    status: "open", lastMessageAt: admin.firestore.FieldValue.serverTimestamp(),
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  await ref.collection("messages").add({
    author: "customer", authorId: uid, body: String(message).slice(0, 4000),
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  await queueEmail(user?.email, "support_received", { displayName: user?.displayName ?? "Angler", ticketId, subject });
  return { id: ref.id, ticketId };
});

export const replyToTicket = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const { ticketDocId, message, closeTicket } = req.data ?? {};
  const db = admin.firestore();
  const ref = db.collection(Collections.supportTickets).doc(ticketDocId);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "Ticket not found.");
  const t = snap.data()!;

  let author: "customer" | "admin";
  let authorId: string;
  try {
    const ctx = await requireAdmin(req, "support.write");
    author = "admin"; authorId = ctx.uid;
  } catch {
    const uid = requireAuth(req);
    if (t.userId !== uid) throw new HttpsError("permission-denied", "That isn't your ticket.");
    author = "customer"; authorId = uid;
  }

  await ref.collection("messages").add({
    author, authorId, body: String(message).slice(0, 4000),
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  await ref.update({
    status: closeTicket === true ? "closed" : author === "admin" ? "awaiting_customer" : "open",
    lastMessageAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  if (author === "admin") {
    await createUserNotification(t.userId, {
      category: "support", title: "Support replied",
      body: `We've replied to ticket ${t.ticketId}.`, deepLink: `rrr://support/${ticketDocId}`,
    });
    await pushToUser(t.userId, "support", "Support replied", `Ticket ${t.ticketId} has an update.`);
  }
  return { ok: true };
});
