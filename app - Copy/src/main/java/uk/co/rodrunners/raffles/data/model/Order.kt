package uk.co.rodrunners.raffles.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Order(
    @DocumentId val id: String = "",
    val orderNumber: String = "",
    val userId: String = "",
    val userEmail: String? = null,
    val competitionId: String = "",
    val competitionTitle: String = "",
    val competitionImageUrl: String? = null,
    val quantity: Int = 0,
    val entryNumbers: List<Int> = emptyList(),
    val breakdown: PriceBreakdown? = null,
    val totalPence: Int = 0,
    val currency: String = "gbp",
    val paymentStatus: String = "pending",
    val orderStatus: String = "reserved",
    val failureMessage: String? = null,
    val refundedPence: Int = 0,
    val createdAt: Timestamp? = null,
    val paidAt: Timestamp? = null,
) {
    val createdAtMillis: Long get() = createdAt?.toDate()?.time ?: 0L
    val isPaid: Boolean get() = paymentStatus == "paid"
    val statusLabel: String get() = when (paymentStatus) {
        "paid" -> "Paid"
        "pending" -> "Awaiting payment"
        "failed" -> "Payment failed"
        "cancelled" -> "Cancelled"
        "refunded" -> "Refunded"
        "partially_refunded" -> "Partly refunded"
        "review" -> "Under review"
        else -> paymentStatus.replaceFirstChar { it.uppercase() }
    }
}

/** Mirrors the server's PriceBreakdown exactly. The client never recomputes it. */
@IgnoreExtraProperties
data class PriceBreakdown(
    val quantity: Int = 0,
    val unitPricePence: Int = 0,
    val bundleLabel: String? = null,
    val subtotalPence: Int = 0,
    val bundleDiscountPence: Int = 0,
    val promoDiscountPence: Int = 0,
    val promoCode: String? = null,
    val feePence: Int = 0,
    val totalPence: Int = 0,
)
