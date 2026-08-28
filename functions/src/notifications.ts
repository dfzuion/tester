import * as admin from "firebase-admin";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { Collections, FROM_EMAIL, REGION } from "./config";

/**
 * Push goes out through FCM to per-user device tokens.
 * Email is queued into the `mail` collection, which the Firebase "Trigger
 * Email" extension (or any SMTP worker) drains. Templates live in Firestore
 * so marketing can edit copy without an app release.
 */

export interface UserNotification {
  category: "purchase" | "win" | "ending_soon" | "new_competition" | "promotion" | "account" | "payment" | "refund" | "support" | "admin";
  title: string;
  body: string;
  deepLink?: string;
}

export async function createUserNotification(userId: string, n: UserNotification) {
  await admin.firestore().collection(Collections.users).doc(userId)
    .collection(Collections.notifications).add({
      ...n, read: false, createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
}

export async function pushToUser(
  userId: string, category: string, title: string, body: string, data: Record<string, string> = {}
) {
  const db = admin.firestore();
  const userSnap = await db.collection(Collections.users).doc(userId).get();
  const user = userSnap.data();
  if (!user) return;

  // Respect the customer's per-category preferences.
  const prefs = user.notificationPreferences ?? {};
  if (prefs[category] === false) return;

  const tokens: string[] = user.fcmTokens ?? [];
  if (tokens.length === 0) return;

  const res = await admin.messaging().sendEachForMulticast({
    tokens,
    notification: { title, body },
    data: { category, ...data },
    android: { priority: "high", notification: { channelId: channelFor(category), color: "#C6A15B" } },
  });

  // Prune tokens the device no longer honours.
  const dead: string[] = [];
  res.responses.forEach((r, i) => {
    if (!r.success) {
      const code = (r.error as any)?.code ?? "";
      if (code.includes("registration-token-not-registered") || code.includes("invalid-argument")) dead.push(tokens[i]);
    }
  });
  if (dead.length) {
    await userSnap.ref.update({ fcmTokens: admin.firestore.FieldValue.arrayRemove(...dead) });
  }
}

export async function pushToTopic(topic: string, title: string, body: string, data: Record<string, string> = {}) {
  await admin.messaging().send({
    topic,
    notification: { title, body },
    data,
    android: { priority: "normal", notification: { channelId: "rrr_general", color: "#C6A15B" } },
  });
}

function channelFor(category: string): string {
  switch (category) {
    case "win": return "rrr_wins";
    case "purchase": case "payment": case "refund": return "rrr_orders";
    case "ending_soon": case "new_competition": return "rrr_raffles";
    case "promotion": return "rrr_promotions";
    default: return "rrr_general";
  }
}

/** Renders a Firestore-stored template and queues it for delivery. */

/**
 * Built-in copy for the emails the app has to be able to send.
 *
 * Templates used to come only from Firestore, and the only thing that ever
 * wrote them was the demo seeder - so on a real project every template was
 * missing and queueEmail dropped the message with a console warning. Nobody
 * ever got a winner email. A template stored in Firestore still wins, so the
 * copy stays editable; these are the floor, not a replacement.
 */
const DEFAULT_TEMPLATES: Record<string, { subject: string; html: string }> = {
  winner_notification: {
    subject: "You've won {{prizeName}}",
    html: `<p>Hi {{displayName}},</p>
<p>Entry number <strong>{{entryNumber}}</strong> has won <strong>{{prizeName}}</strong> on {{competitionTitle}}.</p>
<p>We'll be in touch shortly to arrange getting the prize to you. Congratulations.</p>
<p>Rod Runners Raffles</p>`,
  },
  instant_win_notification: {
    subject: "Instant win: {{prizeName}}",
    html: `<p>Hi {{displayName}},</p>
<p>You've just won <strong>{{prizeName}}</strong> on {{competitionTitle}} with entry {{entryNumber}}.</p>
<p>{{settlement}}</p>
<p>Rod Runners Raffles</p>`,
  },
  welcome: {
    subject: "Welcome to Rod Runners Raffles",
    html: `<p>Hi {{displayName}},</p>
<p>Your account is ready. Every raffle shows its entry price, how many entries there are and when it closes, and the full rules sit on each raffle page.</p>
<p>Tight lines.</p>
<p>Rod Runners Raffles</p>`,
  },
  purchase_confirmation: {
    subject: "Your entries for {{competitionTitle}}",
    html: `<p>Hi {{displayName}},</p>
<p>Order {{orderNumber}} is confirmed: <strong>{{quantity}}</strong> entries for {{competitionTitle}}, {{total}}.</p>
<p>Your entry numbers are {{entryNumbers}}.</p>
<p>Rod Runners Raffles</p>`,
  },
  support_received: {
    subject: "We've got your message",
    html: `<p>Hi {{displayName}},</p>
<p>Thanks for getting in touch. Your ticket reference is {{ticketId}} and we'll reply by email.</p>
<p>Rod Runners Raffles</p>`,
  },
  admin_alert: {
    subject: "[Rod Runners] {{title}}",
    html: `<p>{{title}}</p><p>{{body}}</p><p>Sent automatically by Rod Runners Raffles.</p>`,
  },
};

export async function queueEmail(to: string | null | undefined, templateId: string, vars: Record<string, string | number>) {
  if (!to) return;
  const db = admin.firestore();
  const tpl = await db.collection(Collections.emailTemplates).doc(templateId).get();
  const fallback = DEFAULT_TEMPLATES[templateId];
  if (!tpl.exists && !fallback) {
    console.warn(`Email template missing and no built-in default: ${templateId}`);
    return;
  }
  const t = (tpl.exists ? tpl.data()! : fallback!) as {
    subject: string; html: string; text?: string; enabled?: boolean;
  };
  if (t.enabled === false) return;

  await db.collection(Collections.mail).add({
    to: [to],
    from: FROM_EMAIL.value(),
    message: {
      subject: render(t.subject, vars),
      html: render(t.html, vars),
      text: render(t.text ?? stripHtml(t.html), vars),
    },
    templateId,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });
}

function render(template: string, vars: Record<string, string | number>): string {
  return String(template).replace(/\{\{(\w+)\}\}/g, (_, k) => String(vars[k] ?? ""));
}
function stripHtml(html: string): string {
  return String(html ?? "").replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim();
}

/** Warns people holding entries, and anyone who favourited, before a raffle closes. */
export const notifyEndingSoon = onSchedule({ region: REGION, schedule: "every 30 minutes" }, async () => {
  const db = admin.firestore();
  const soon = admin.firestore.Timestamp.fromMillis(Date.now() + 6 * 60 * 60 * 1000);
  const comps = await db.collection(Collections.competitions)
    .where("status", "==", "live")
    .where("closesAt", "<=", soon)
    .where("endingSoonNotified", "==", false)
    .limit(20).get();

  for (const doc of comps.docs) {
    const c = doc.data();
    const favs = await db.collection(Collections.favourites).where("competitionId", "==", doc.id).get();
    const userIds = new Set<string>(favs.docs.map(d => d.data().userId));
    for (const uid of userIds) {
      await createUserNotification(uid, {
        category: "ending_soon",
        title: "Closing soon",
        body: `${c.title} closes in under 6 hours. ${c.maxEntries - c.entriesSold} entries left.`,
        deepLink: `rrr://competition/${doc.id}`,
      });
      await pushToUser(uid, "ending_soon", "Closing soon", `${c.title} closes in under 6 hours.`, { competitionId: doc.id });
    }
    await doc.ref.update({ endingSoonNotified: true });
  }
});

/**
 * Tells every active administrator that something needs their attention.
 * Wins were going only to the customer, so nobody on the business side knew a
 * prize had to be posted until someone happened to look at the admin panel.
 * Failures here never propagate - a notification must not undo a win.
 */
export async function notifyAdmins(
  title: string,
  body: string,
  data: Record<string, string> = {}
): Promise<void> {
  try {
    const db = admin.firestore();
    const admins = await db.collection(Collections.adminUsers).where("active", "==", true).get();
    await Promise.all(admins.docs.map(async (d) => {
      await createUserNotification(d.id, { category: "admin", title, body, deepLink: data.deepLink });
      await pushToUser(d.id, "admin", title, body, data);
      const email = d.data().email as string | undefined;
      if (email) await queueEmail(email, "admin_alert", { title, body });
    }));
  } catch (err) {
    console.error("admin notification failed", { title, err });
  }
}
