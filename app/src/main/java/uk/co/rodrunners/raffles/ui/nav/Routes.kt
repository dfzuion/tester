package uk.co.rodrunners.raffles.ui.nav

object Routes {
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot"
    const val VERIFY_EMAIL = "verify"

    const val HOME = "home"
    const val COMPETITIONS = "competitions"
    const val RESULTS = "results"
    const val ACCOUNT = "account"

    const val SEARCH = "search"
    const val NOTIFICATIONS = "notifications"
    const val FAVOURITES = "favourites"
    const val TICKETS = "tickets"
    const val ORDERS = "orders"
    const val MY_WINS = "wins"
    const val PERSONAL_DETAILS = "account/details"
    const val NOTIFICATION_PREFS = "account/notifications"
    const val SECURITY = "account/security"
    const val PAYMENT_METHODS = "account/payment-methods"
    const val SUPPORT = "support"
    const val FAQ = "faq"
    const val ADMIN = "admin"
    const val ADMIN_NEW_COMPETITION = "admin/competition/new"
    const val ADMIN_EDIT_COMPETITION = "admin/competition/{competitionId}"
    fun adminEditCompetition(id: String) = "admin/competition/$id"
    const val ADMIN_INSTANT_WINS = "admin/competition/{competitionId}/instant-wins"
    fun adminInstantWins(id: String) = "admin/competition/$id/instant-wins"

    const val COMPETITION_DETAIL = "competition/{competitionId}"
    fun competitionDetail(id: String) = "competition/$id"

    const val CHECKOUT = "checkout/{competitionId}"
    fun checkout(id: String) = "checkout/$id"

    const val ORDER_DETAIL = "orders/{orderId}"
    fun orderDetail(id: String) = "orders/$id"

    const val LEGAL = "legal/{documentId}"
    fun legal(docId: String) = "legal/$docId"

    const val RULES = "rules/{competitionId}"
    fun rules(competitionId: String) = "rules/$competitionId"

    const val TICKET_THREAD = "support/{ticketId}"
    fun ticketThread(id: String) = "support/$id"
}
