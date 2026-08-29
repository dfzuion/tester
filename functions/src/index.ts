import * as admin from "firebase-admin";
admin.initializeApp();

export {
  quoteBasket,
  createOrderAndPaymentIntent,
  stripeWebhook,
  refundOrder,
  releaseExpiredReservations,
  applyPromoCode,
} from "./payments";

export {
  closeCompetition,
  drawWinner,
  publishResult,
  autoCloseDueCompetitions,
} from "./draw";

export {
  onUserCreated,
  setAdminRole,
  suspendCustomer,
  setCompetitionStatus,
  deleteMyAccount,
  registerFcmToken,
} from "./admin";

export { spinDailyWheel } from "./dailyspin";

export {
  createCompetition,
  updateCompetition,
  deleteDraftCompetition,
} from "./competitions";

export {
  addInstantWinPrizes,
  listInstantWins,
  removeInstantWinPrizes,
  setInstantWinClaimStatus,
} from "./instantwins";

export {
  bootstrapFirstAdmin,
  listAdminUsers,
  grantAdminByEmail,
  revokeAdmin,
} from "./adminusers";

export {
  redeemCreditCoupon,
  createCreditCoupon,
  listCreditCoupons,
  setCreditCouponActive,
  grantCredit,
} from "./credits";

export { notifyEndingSoon, deliverQueuedMail, listMailQueue, retryFailedMail } from "./notifications";
export { seedDemoData } from "./seed";
export { createSupportTicket, replyToTicket } from "./support";
export { submitGameCatch, gameLeaderboard, awardGameWeek } from "./gameleaderboard";
