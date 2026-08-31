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

/**
 * The business inbox. Everything an administrator is told about - a draw, an
 * instant win, anything needing attention - is copied here as well, so the
 * record does not depend on which administrator happened to be listed at the
 * time or on anyone remembering to check the app.
 *
 * A plain constant, deliberately, not a defineString parameter. Adding a new
 * parameter makes the CLI want a value for it at deploy time, and in
 * non-interactive mode that fails the whole deploy listing every parameter it
 * would otherwise have asked about. This is not a secret and it changes about
 * never, so an environment variable with a sensible fallback is the right
 * shape for it.
 */
export const BUSINESS_EMAIL = process.env.BUSINESS_EMAIL || "info@rodrunners.co.uk";
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
  gameScores: "gameScores",
  gameWeeks: "gameWeeks",
  instantWinGames: "instantWinGames",
  instantWinGamePlays: "instantWinGamePlays",
} as const;
