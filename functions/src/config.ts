import { defineSecret, defineString } from "firebase-functions/params";

/**
 * Secrets are stored in Secret Manager, never in source control.
 *   firebase functions:secrets:set STRIPE_SECRET_KEY
 *   firebase functions:secrets:set STRIPE_WEBHOOK_SECRET
 *   firebase functions:secrets:set SENDGRID_API_KEY
 */
export const STRIPE_SECRET_KEY = defineSecret("STRIPE_SECRET_KEY");
export const STRIPE_WEBHOOK_SECRET = defineSecret("STRIPE_WEBHOOK_SECRET");
export const SENDGRID_API_KEY = defineSecret("SENDGRID_API_KEY");

export const SUPPORT_EMAIL = defineString("SUPPORT_EMAIL", { default: "support@rodrunnersraffles.co.uk" });
export const FROM_EMAIL = defineString("FROM_EMAIL", { default: "no-reply@rodrunnersraffles.co.uk" });
export const REGION = "europe-west2"; // London

export const Collections = {
  users: "users",
  competitions: "competitions",
  prizes: "prizes",
  entries: "entries",
  orders: "orders",
  payments: "payments",
  winners: "winners",
  draws: "draws",
  notifications: "notifications",
  favourites: "favourites",
  promotions: "promotions",
  promotionRedemptions: "promotionRedemptions",
  referrals: "referrals",
  supportTickets: "supportTickets",
  faq: "faq",
  appContent: "appContent",
  auditLogs: "auditLogs",
  adminUsers: "adminUsers",
  emailTemplates: "emailTemplates",
  mail: "mail",
  rateLimits: "rateLimits",
} as const;
