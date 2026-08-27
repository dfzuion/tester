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

export { notifyEndingSoon } from "./notifications";
export { seedDemoData } from "./seed";
export { createSupportTicket, replyToTicket } from "./support";
