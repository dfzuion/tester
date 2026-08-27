package uk.co.rodrunners.raffles.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import uk.co.rodrunners.raffles.core.Collections
import uk.co.rodrunners.raffles.data.model.Competition
import uk.co.rodrunners.raffles.data.model.Entry
import uk.co.rodrunners.raffles.data.model.TicketGroup

@Singleton
class EntryRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val competitions: CompetitionRepository,
) {
    private val col get() = db.collection(Collections.ENTRIES)

    fun myEntries(uid: String, limit: Long = 200): Flow<List<Entry>> = callbackFlow {
        val reg = col.whereEqualTo("userId", uid)
            .orderBy("purchasedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toObjects(Entry::class.java) ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    suspend fun myEntryCountFor(uid: String, competitionId: String): Int =
        col.whereEqualTo("userId", uid).whereEqualTo("competitionId", competitionId)
            .count().get(com.google.firebase.firestore.AggregateSource.SERVER).await()
            .count.toInt()

    /** Folds a flat entry list into the per-raffle cards My Tickets shows. */
    suspend fun groupTickets(entries: List<Entry>): List<TicketGroup> {
        if (entries.isEmpty()) return emptyList()
        val comps = competitions.byIds(entries.map { it.competitionId }.distinct())
            .associateBy { it.id }

        return entries.groupBy { it.competitionId }.map { (compId, list) ->
            val c: Competition? = comps[compId]
            TicketGroup(
                competitionId = compId,
                competitionTitle = c?.title ?: list.first().competitionTitle,
                imageUrl = c?.heroImageUrl.orEmpty(),
                entryNumbers = list.map { it.entryNumber }.sorted(),
                orderIds = list.map { it.orderId }.toSet(),
                purchasedAtMillis = list.maxOf { it.purchasedAtMillis },
                competitionStatus = c?.status ?: "unknown",
                closesAtMillis = c?.closesAtMillis ?: 0L,
                winningEntryNumber = c?.winningEntryNumber,
            )
        }.sortedByDescending { it.purchasedAtMillis }
    }
}
