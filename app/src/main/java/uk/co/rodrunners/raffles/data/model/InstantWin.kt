package uk.co.rodrunners.raffles.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

/** A prize won instantly, as recorded on the order that won it. */
@IgnoreExtraProperties
data class InstantWinAward(
    val instantWinId: String = "",
    val entryNumber: Int = 0,
    val prizeName: String = "",
    val valuePence: Int = 0,
    val imageUrl: String? = null,
)

/** Admin view: how many of each prize are still out there, unclaimed. */
data class InstantWinStock(
    val prizeName: String = "",
    val valuePence: Int = 0,
    val count: Int = 0,
    /** "credit" pays straight into the winner's balance; "item" is posted out. */
    val prizeType: String = "item",
)

/** Admin view: a prize that has been won, and where its claim has got to. */
data class InstantWinClaim(
    val id: String = "",
    val prizeName: String = "",
    val valuePence: Int = 0,
    val entryNumber: Int = 0,
    val wonByName: String? = null,
    val wonAtMillis: Long? = null,
    val claimStatus: String = "pending",
) {
    companion object {
        val STATUSES = listOf(
            "pending" to "To contact",
            "contacted" to "Contacted",
            "dispatched" to "Dispatched",
            "fulfilled" to "Fulfilled",
        )
    }
}

/** An instant win as the customer sees it in My Wins. */
@IgnoreExtraProperties
data class MyInstantWin(
    @com.google.firebase.firestore.DocumentId val id: String = "",
    val competitionId: String = "",
    val competitionTitle: String = "",
    val entryNumber: Int = 0,
    val prizeName: String = "",
    val valuePence: Int = 0,
    val imageUrl: String? = null,
    val claimStatus: String = "pending",
    val wonAt: com.google.firebase.Timestamp? = null,
) {
    val wonAtMillis: Long get() = wonAt?.toDate()?.time ?: 0L
    val claimLabel: String get() = when (claimStatus) {
        "contacted" -> "We've been in touch"
        "dispatched" -> "On its way"
        "fulfilled" -> "Delivered"
        else -> "We'll contact you"
    }
}
