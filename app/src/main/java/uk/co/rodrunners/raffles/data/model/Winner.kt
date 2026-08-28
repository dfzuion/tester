package uk.co.rodrunners.raffles.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Winner(
    @DocumentId val id: String = "",
    val competitionId: String = "",
    val competitionTitle: String = "",
    val prizeName: String = "",
    val prizeImageUrl: String? = null,
    val drawId: String = "",
    /** "draw" for a drawn raffle, "instant_win" for a prize won on an entry. */
    val winType: String = "draw",
    /** Instant wins only: "item" or "credit", and what the prize was worth. */
    val prizeType: String = "item",
    val prizeValuePence: Int = 0,
    val instantWinId: String = "",
    val orderId: String = "",
    val winningEntryNumber: Int = 0,
    val winnerUserId: String = "",
    val winnerDisplayName: String = "",
    val published: Boolean = false,
    val drawnAt: Timestamp? = null,
) {
    val drawnAtMillis: Long get() = drawnAt?.toDate()?.time ?: 0L
    val isInstantWin: Boolean get() = winType == "instant_win"
    val isCreditPrize: Boolean get() = prizeType == "credit"
}
