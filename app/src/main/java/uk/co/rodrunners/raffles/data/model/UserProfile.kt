package uk.co.rodrunners.raffles.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val email: String? = null,
    val displayName: String = "Angler",
    val phone: String? = null,
    val address: Address? = null,
    val photoUrl: String? = null,
    val ageConfirmed: Boolean = false,
    val marketingOptIn: Boolean = false,
    val suspended: Boolean = false,
    val referralCode: String? = null,
    /** Promotional balance, in pence. Buys entries; never withdrawable. */
    val creditBalancePence: Int = 0,
    val referredBy: String? = null,
    val notificationPreferences: Map<String, Boolean> = emptyMap(),
    val createdAt: Timestamp? = null,
) {
    fun prefers(category: String): Boolean = notificationPreferences[category] ?: true
}

data class Address(
    val line1: String = "",
    val line2: String? = null,
    val town: String = "",
    val county: String? = null,
    val postcode: String = "",
    val country: String = "United Kingdom",
) {
    fun singleLine(): String = listOfNotNull(line1, line2, town, county, postcode)
        .filter { it.isNotBlank() }.joinToString(", ")
}

@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val category: String = "account",
    val title: String = "",
    val body: String = "",
    val deepLink: String? = null,
    val read: Boolean = false,
    val createdAt: Timestamp? = null,
) {
    val createdAtMillis: Long get() = createdAt?.toDate()?.time ?: 0L
}

data class NotificationCategory(
    val key: String,
    val title: String,
    val description: String,
    val defaultOn: Boolean = true,
    /** Wins, receipts and payment problems are sent regardless of preference. */
    val mandatory: Boolean = false,
)

object NotificationCategories {
    val all: List<NotificationCategory> = listOf(
        NotificationCategory("win", "Wins", "When one of your numbers is drawn.", mandatory = true),
        NotificationCategory("purchase", "Order confirmations", "Receipts and your entry numbers.", mandatory = true),
        NotificationCategory("payment", "Payment problems", "If a payment fails or needs action.", mandatory = true),
        NotificationCategory("ending_soon", "Raffles closing soon", "A nudge before entries close."),
        NotificationCategory("new_competition", "New raffles", "When new tackle goes live."),
        NotificationCategory("promotion", "Offers and promotions", "Occasional discounts and bundles.", defaultOn = false),
        NotificationCategory("refund", "Refunds", "When money goes back to your card."),
        NotificationCategory("account", "Account and security", "Sign-in and account changes."),
        NotificationCategory("support", "Support replies", "When we reply to your message."),
    )

    val ALL = listOf(
        "purchase" to "Order confirmations",
        "win" to "Wins",
        "ending_soon" to "Raffles closing soon",
        "new_competition" to "New raffles",
        "promotion" to "Offers and promotions",
        "payment" to "Payment problems",
        "refund" to "Refunds",
        "account" to "Account and security",
        "support" to "Support replies",
    )
}
