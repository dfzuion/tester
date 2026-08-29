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
/** Either provider works; whichever key is set is the one used. */
export const RESEND_API_KEY = defineSecret("RESEND_API_KEY");

export const SUPPORT_EMAIL = defineString("SUPPORT_EMAIL", { default: "support@rodrunners.co.uk" });
export const FROM_EMAIL = defineString("FROM_EMAIL", { default: "no-reply@rodrunners.co.uk" });
export const REGION = "europe-west2"; // London

/** What a referral pays once the referred customer's first order is paid. */
export const REFERRAL_REFERRER_PENCE = 500;
export const REFERRAL_REFEREE_PENCE = 500;

/**
 * App Check proves a call came from the real app rather than a script. Release
 * builds get a token from Play Integrity; a debug build needs a debug token
 * registered by hand, which isn't practical while testing from CI.
 *
 * Deploy with RRR_ENFORCE_APP_CHECK=false to relax it, and leave it unset (the
 * default) for production. Auth and Firestore rules are unaffected either way.
 */
export const ENFORCE_APP_CHECK = process.env.RRR_ENFORCE_APP_CHECK !== "false";

export const Collections = {
  users: "users",
  competitions: "competitions",
  prizes: "prizes",
  entries: "entries",
  orders: "orders",
  payments: "payments",
  winners: "winners",
  instantWins: "instantWins",
  creditCoupons: "creditCoupons",
  couponRedemptions: "couponRedemptions",
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
  dailySpins: "dailySpins",
} as const;
