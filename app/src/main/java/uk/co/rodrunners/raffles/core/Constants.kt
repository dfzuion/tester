package uk.co.rodrunners.raffles.core

object Collections {
    const val USERS = "users"
    const val USER_SIGNUPS = "userSignups"
    const val COMPETITIONS = "competitions"
    const val ENTRIES = "entries"
    const val ORDERS = "orders"
    const val WINNERS = "winners"
    const val FAVOURITES = "favourites"
    const val NOTIFICATIONS = "notifications"
    const val SUPPORT_TICKETS = "supportTickets"
    const val FAQ = "faq"
    const val APP_CONTENT = "appContent"
    const val ADMIN_USERS = "adminUsers"
    const val PROMOTIONS = "promotions"
    const val AUDIT_LOGS = "auditLogs"
}

object Functions {
    const val REGION = "europe-west2"
    const val QUOTE_BASKET = "quoteBasket"
    const val CREATE_ORDER = "createOrderAndPaymentIntent"
    const val APPLY_PROMO = "applyPromoCode"
    const val REGISTER_TOKEN = "registerFcmToken"
    const val DELETE_ACCOUNT = "deleteMyAccount"
    const val CREATE_TICKET = "createSupportTicket"
    const val REPLY_TICKET = "replyToTicket"
    const val CLOSE_COMPETITION = "closeCompetition"
    const val DRAW_WINNER = "drawWinner"
    const val PUBLISH_RESULT = "publishResult"
    const val SET_COMPETITION_STATUS = "setCompetitionStatus"
    const val REFUND_ORDER = "refundOrder"
    const val SUSPEND_CUSTOMER = "suspendCustomer"
    const val SEED_DEMO = "seedDemoData"
    const val CREATE_COMPETITION = "createCompetition"
    const val UPDATE_COMPETITION = "updateCompetition"
    const val DELETE_DRAFT_COMPETITION = "deleteDraftCompetition"
}

object NotificationChannels {
    const val GENERAL = "rrr_general"
    const val ORDERS = "rrr_orders"
    const val RAFFLES = "rrr_raffles"
    const val WINS = "rrr_wins"
    const val PROMOTIONS = "rrr_promotions"
}

object Topics {
    const val NEW_COMPETITIONS = "new_competitions"
    const val RESULTS = "results"
}
