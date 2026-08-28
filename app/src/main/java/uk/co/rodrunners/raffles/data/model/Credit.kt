package uk.co.rodrunners.raffles.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

/** One movement of site credit. The ledger is append-only and server-written. */
@IgnoreExtraProperties
data class CreditEntry(
    @DocumentId val id: String = "",
    val deltaPence: Int = 0,
    val balanceAfterPence: Int = 0,
    val reason: String = "",
    val description: String = "",
    val orderId: String? = null,
    val createdAt: Timestamp? = null,
) {
    val createdAtMillis: Long get() = createdAt?.toDate()?.time ?: 0L
    val isCredit: Boolean get() = deltaPence > 0

    val reasonLabel: String get() = when (reason) {
        "coupon" -> "Coupon"
        "referral" -> "Referral reward"
        "instant_win" -> "Instant win"
        "admin_grant" -> "Adjustment"
        "order_spend" -> "Spent on entries"
        "order_refund" -> "Refunded"
        "order_released" -> "Returned"
        else -> "Credit"
    }
}

/** A coupon as the admin screen sees it. */
data class CreditCoupon(
    val code: String = "",
    val valuePence: Int = 0,
    val maxRedemptions: Int = 0,
    val redemptionsUsed: Int = 0,
    val active: Boolean = true,
    val expiresAtMillis: Long? = null,
) {
    val remaining: Int? get() = if (maxRedemptions <= 0) null else (maxRedemptions - redemptionsUsed).coerceAtLeast(0)
    val statusLabel: String get() = when {
        !active -> "Switched off"
        expiresAtMillis != null && expiresAtMillis < System.currentTimeMillis() -> "Expired"
        remaining == 0 -> "Fully claimed"
        else -> "Live"
    }
}
