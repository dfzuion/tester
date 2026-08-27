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

export { notifyEndingSoon } from "./notifications";
export { seedDemoData } from "./seed";
export { createSupportTicket, replyToTicket } from "./support";
