import * as admin from "firebase-admin";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { onCall, CallableRequest } from "firebase-functions/v2/https";
import { BUSINESS_EMAIL, Collections, ENFORCE_APP_CHECK, FROM_EMAIL, REGION, RESEND_API_KEY, SENDGRID_API_KEY } from "./config";
import { requireAdmin } from "./guards";

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
    // deliverQueuedMail picks these up. Queuing is deliberately separated from
    // sending: the secrets live on that one function, so every caller can queue
    // an email without declaring a provider key, and a provider outage can
    // never fail a draw or a checkout.
    deliveryStatus: "pending",
    attempts: 0,
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
    const told = new Set<string>();

    await Promise.all(admins.docs.map(async (d) => {
      await createUserNotification(d.id, { category: "admin", title, body, deepLink: data.deepLink });
      await pushToUser(d.id, "admin", title, body, data);
      const email = (d.data().email as string | undefined)?.trim().toLowerCase();
      if (email) {
        told.add(email);
        await queueEmail(email, "admin_alert", { title, body });
      }
    }));

    // And the business inbox, so there is one place every win lands whoever
    // happens to be an administrator that week. Skipped if an administrator
    // already holds that address, rather than sending the same mail twice.
    const business = BUSINESS_EMAIL.value().trim().toLowerCase();
    if (business && !told.has(business)) {
      await queueEmail(business, "admin_alert", { title, body });
    }
  } catch (err) {
    console.error("admin notification failed", { title, err });
  }
}

// ------------------------------------------------------------------ delivery

interface OutboundEmail {
  to: string;
  from: string;
  subject: string;
  html: string;
  text: string;
}

/**
 * Hands one email to whichever provider is configured. Returns null on success
 * or a reason on failure, so the caller can record it against the queued
 * document rather than losing it to a log line.
 *
 * Node 20 has fetch built in, so neither provider needs a dependency.
 */
/**
 * A secret has to exist in Secret Manager before a function that declares it
 * can be deployed at all, so the ones we have not signed up for yet are seeded
 * with the word "placeholder". That is not a key: treat it as unset, otherwise
 * every email would be posted to Resend with a nonsense bearer token.
 */
function configured(value: string | undefined): string {
  const v = (value ?? "").trim();
  return v === "placeholder" ? "" : v;
}

async function sendViaProvider(email: OutboundEmail): Promise<string | null> {
  const resend = configured(RESEND_API_KEY.value());
  const sendgrid = configured(SENDGRID_API_KEY.value());

  if (resend) {
    const res = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: { Authorization: `Bearer ${resend}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        from: email.from, to: [email.to],
        subject: email.subject, html: email.html, text: email.text,
      }),
    });
    if (res.ok) return null;
    return `resend ${res.status}: ${(await res.text()).slice(0, 300)}`;
  }

  if (sendgrid) {
    const res = await fetch("https://api.sendgrid.com/v3/mail/send", {
      method: "POST",
      headers: { Authorization: `Bearer ${sendgrid}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        personalizations: [{ to: [{ email: email.to }] }],
        from: { email: email.from },
        subject: email.subject,
        content: [
          { type: "text/plain", value: email.text },
          { type: "text/html", value: email.html },
        ],
      }),
    });
    if (res.ok || res.status === 202) return null;
    return `sendgrid ${res.status}: ${(await res.text()).slice(0, 300)}`;
  }

  return "no email provider configured - set RESEND_API_KEY or SENDGRID_API_KEY in Secret Manager";
}

/**
 * Drains the mail queue.
 *
 * Every email the app writes used to land in this collection and stay there:
 * delivery depended on the Trigger Email extension, which was never installed,
 * so no winner has ever actually been emailed. This is that missing half.
 *
 * Runs every minute, five attempts per message, and marks what it could not
 * send so a failure is visible in Firestore instead of silently vanishing.
 */
export const deliverQueuedMail = onSchedule(
  {
    region: REGION,
    schedule: "every 1 minutes",
    secrets: [RESEND_API_KEY, SENDGRID_API_KEY],
  },
  async () => {
    const db = admin.firestore();
    const pending = await db.collection(Collections.mail)
      .where("deliveryStatus", "in", ["pending", "retry"])
      .orderBy("createdAt")
      .limit(50)
      .get();
    if (pending.empty) return;

    for (const doc of pending.docs) {
      const d = doc.data();
      const attempts = Number(d.attempts ?? 0);
      const to = Array.isArray(d.to) ? d.to[0] : d.to;
      if (!to) {
        await doc.ref.update({ deliveryStatus: "abandoned", error: "no recipient" });
        continue;
      }

      let failure: string | null;
      try {
        failure = await sendViaProvider({
          to: String(to),
          from: String(d.from ?? FROM_EMAIL.value()),
          subject: String(d.message?.subject ?? ""),
          html: String(d.message?.html ?? ""),
          text: String(d.message?.text ?? ""),
        });
      } catch (err) {
        failure = err instanceof Error ? err.message : String(err);
      }

      if (failure === null) {
        await doc.ref.update({
          deliveryStatus: "sent",
          attempts: attempts + 1,
          sentAt: admin.firestore.FieldValue.serverTimestamp(),
          error: admin.firestore.FieldValue.delete(),
        });
      } else {
        // Five attempts is roughly five minutes; past that it is a
        // configuration problem, not a blip, and retrying forever just hides it.
        const done = attempts + 1 >= 5;
        await doc.ref.update({
          deliveryStatus: done ? "failed" : "retry",
          attempts: attempts + 1,
          error: failure,
          lastAttemptAt: admin.firestore.FieldValue.serverTimestamp(),
        });
        console.error("email delivery failed", { id: doc.id, attempts: attempts + 1, failure });
      }
    }
  }
);

/* =========================================================
   THE QUEUE, FROM THE ADMIN PANEL

   Every email the site sends lands in the mail collection first and is
   delivered a minute later. When delivery fails - a revoked provider key,
   an unverified sending domain - the reason is written onto the document
   and nowhere else, which means nobody finds out until a customer says
   they never got their entry numbers.

   These two put the queue in front of an administrator: what is waiting,
   what went out, and the provider's own words for anything that did not.
========================================================= */

export const listMailQueue = onCall(
  { region: REGION, enforceAppCheck: ENFORCE_APP_CHECK },
  async (req: CallableRequest) => {
    await requireAdmin(req, "reports.read");

    const db = admin.firestore();
    const snap = await db.collection(Collections.mail)
      .orderBy("createdAt", "desc").limit(60).get();

    const counts: Record<string, number> = {};
    const messages = snap.docs.map((d) => {
      const m = d.data();
      const status = String(m.deliveryStatus ?? "pending");
      counts[status] = (counts[status] ?? 0) + 1;

      return {
        id: d.id,
        to: Array.isArray(m.to) ? m.to[0] : m.to ?? "",
        subject: m.message?.subject ?? "",
        templateId: m.templateId ?? "",
        status,
        attempts: m.attempts ?? 0,
        // The provider's message, verbatim. A paraphrase of "401 invalid
        // grant" would lose the one word that says which thing is wrong.
        error: m.error ?? null,
        createdAtMillis: m.createdAt?.toMillis?.() ?? null,
        sentAtMillis: m.sentAt?.toMillis?.() ?? null,
      };
    });

    return { counts, messages, from: FROM_EMAIL.value(), business: BUSINESS_EMAIL.value() };
  }
);

/**
 * Puts failed messages back in the queue. Nothing is rewritten - the same
 * message is tried again - so this is what you run after fixing a provider
 * key, to send the mail that should have gone out at the time.
 */
export const retryFailedMail = onCall(
  { region: REGION, enforceAppCheck: ENFORCE_APP_CHECK },
  async (req: CallableRequest) => {
    const ctx = await requireAdmin(req, "*");
    const only = typeof req.data?.mailId === "string" ? req.data.mailId : null;

    const db = admin.firestore();
    const target = only
      ? [await db.collection(Collections.mail).doc(only).get()]
      : (await db.collection(Collections.mail)
          .where("deliveryStatus", "in", ["failed", "abandoned"])
          .limit(200).get()).docs;

    const batch = db.batch();
    let queued = 0;

    for (const doc of target) {
      if (!doc.exists) continue;
      const status = String(doc.data()?.deliveryStatus ?? "");
      if (status !== "failed" && status !== "abandoned") continue;

      batch.update(doc.ref, {
        deliveryStatus: "pending",
        attempts: 0,
        error: admin.firestore.FieldValue.delete(),
        requeuedAt: admin.firestore.FieldValue.serverTimestamp(),
        requeuedBy: ctx.uid,
      });
      queued++;
    }

    if (queued > 0) await batch.commit();

    return { queued };
  }
);
