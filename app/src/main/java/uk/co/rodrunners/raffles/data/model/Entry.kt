package uk.co.rodrunners.raffles.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Entry(
    @DocumentId val id: String = "",
    val entryNumber: Int = 0,
    val competitionId: String = "",
    val competitionTitle: String = "",
    val userId: String = "",
    val userDisplayName: String = "",
    val orderId: String = "",
    val status: String = "active",
    val isWinner: Boolean = false,
    val purchasedAt: Timestamp? = null,
) {
    val purchasedAtMillis: Long get() = purchasedAt?.toDate()?.time ?: 0L
}

/** Entries grouped per raffle, which is how My Tickets presents them. */
data class TicketGroup(
    val competitionId: String,
    val competitionTitle: String,
    val imageUrl: String,
    val entryNumbers: List<Int>,
    val orderIds: Set<String>,
    val purchasedAtMillis: Long,
    val competitionStatus: String,
    val closesAtMillis: Long,
    val winningEntryNumber: Int?,
) {
    val hasWon: Boolean get() = winningEntryNumber != null && entryNumbers.contains(winningEntryNumber)
    val isSettled: Boolean get() = competitionStatus == "drawn"
    val state: TicketState get() = when {
        hasWon -> TicketState.WON
        isSettled -> TicketState.NOT_WON
        else -> TicketState.ACTIVE
    }
}

enum class TicketState(val label: String) { ACTIVE("Active"), WON("Won"), NOT_WON("Not won") }
