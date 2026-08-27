package uk.co.rodrunners.raffles.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Rules, terms, privacy and FAQs all live in Firestore so the business can
 * change wording without shipping an app update, and so no legal copy is
 * hard-coded into the binary.
 */
@IgnoreExtraProperties
data class RulesDocument(
    @DocumentId val id: String = "",
    val title: String = "Competition rules",
    val version: String = "",
    val sections: List<RulesSection> = emptyList(),
    val legalReviewRequired: Boolean = false,
    val note: String? = null,
)

data class RulesSection(val heading: String = "", val body: String = "")

@IgnoreExtraProperties
data class Banner(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val imageUrl: String = "",
    val deepLink: String? = null,
    val active: Boolean = true,
)

@IgnoreExtraProperties
data class BannerSet(val banners: List<Banner> = emptyList())

@IgnoreExtraProperties
data class FaqItem(
    @DocumentId val id: String = "",
    val question: String = "",
    val answer: String = "",
    val category: String = "general",
    val order: Int = 0,
    val published: Boolean = true,
)

@IgnoreExtraProperties
data class LegalDocument(
    @DocumentId val id: String = "",
    val title: String = "",
    val version: String = "",
    val body: String = "",
)

@IgnoreExtraProperties
data class CompanyInfo(
    val tradingName: String = "Rod Runners Raffles",
    val registeredName: String = "",
    val companyNumber: String = "",
    val registeredAddress: String = "",
    val supportEmail: String = "",
)

@IgnoreExtraProperties
data class SupportTicket(
    @DocumentId val id: String = "",
    val ticketId: String = "",
    val userId: String = "",
    val category: String = "other",
    val subject: String = "",
    val orderId: String? = null,
    val status: String = "open",
    val lastMessageAt: Timestamp? = null,
    val createdAt: Timestamp? = null,
) {
    val updatedAtMillis: Long get() = (lastMessageAt ?: createdAt)?.toDate()?.time ?: 0L

    val statusLabel: String get() = when (status) {
        "open" -> "Open"
        "awaiting_customer" -> "Reply needed"
        "closed" -> "Closed"
        else -> status
    }
}

@IgnoreExtraProperties
data class TicketMessage(
    @DocumentId val id: String = "",
    val author: String = "customer",
    val authorId: String = "",
    val body: String = "",
    val createdAt: Timestamp? = null,
) {
    val createdAtMillis: Long get() = createdAt?.toDate()?.time ?: 0L

    /** Anything not written by staff is the customer's own message. */
    val authorRole: String get() = if (author == "admin" || author == "support") "staff" else "customer"
}

data class SupportCategory(val key: String, val label: String)

object SupportCategories {
    val all: List<SupportCategory> get() = ALL.map { SupportCategory(it.first, it.second) }

    val ALL = listOf(
        "order" to "Problem with an order",
        "payment" to "Payment or refund",
        "competition" to "Question about a raffle",
        "account" to "Account or login",
        "report_problem" to "Report a problem",
        "other" to "Something else",
    )
}
