package uk.co.rodrunners.raffles.data.repository

import com.google.firebase.Timestamp
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
import uk.co.rodrunners.raffles.data.model.CompetitionSort
import uk.co.rodrunners.raffles.data.model.RulesDocument

/**
 * Reads are paginated and capped. Nothing here ever fetches "all competitions";
 * lists page in 12 at a time and images load lazily behind them.
 */
@Singleton
class CompetitionRepository @Inject constructor(
    private val db: FirebaseFirestore,
) {
    private val col get() = db.collection(Collections.COMPETITIONS)

    fun liveCompetitions(limit: Long = 12): Flow<List<Competition>> = snapshotFlow(
        col.whereEqualTo("status", "live")
            .orderBy("closesAt", Query.Direction.ASCENDING)
            .limit(limit)
    )

    fun featured(): Flow<List<Competition>> = snapshotFlow(
        col.whereEqualTo("status", "live").whereEqualTo("featured", true).limit(3)
    )

    fun endingSoon(limit: Long = 6): Flow<List<Competition>> = snapshotFlow(
        col.whereEqualTo("status", "live")
            .whereGreaterThan("closesAt", Timestamp.now())
            .orderBy("closesAt", Query.Direction.ASCENDING)
            .limit(limit)
    )

    fun newest(limit: Long = 6): Flow<List<Competition>> = snapshotFlow(
        col.whereEqualTo("status", "live")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
    )

    fun completed(limit: Long = 20): Flow<List<Competition>> = snapshotFlow(
        col.whereEqualTo("status", "drawn")
            .orderBy("drawnAt", Query.Direction.DESCENDING)
            .limit(limit)
    )

    fun competition(id: String): Flow<Competition?> = callbackFlow {
        val reg = col.document(id).addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            trySend(snap?.toObject(Competition::class.java))
        }
        awaitClose { reg.remove() }
    }

    /** One page of the catalogue. `after` is the last document of the previous page. */
    suspend fun page(
        status: String,
        category: String?,
        sort: CompetitionSort,
        pageSize: Long = 12,
        afterValue: Any? = null,
    ): Pair<List<Competition>, Any?> {
        var q: Query = col.whereEqualTo("status", status)
        if (category != null) q = q.whereEqualTo("category", category)
        q = when (sort) {
            CompetitionSort.ENDING_SOON -> q.orderBy("closesAt", Query.Direction.ASCENDING)
            CompetitionSort.NEWEST -> q.orderBy("createdAt", Query.Direction.DESCENDING)
            CompetitionSort.PRICE_LOW -> q.orderBy("entryPricePence", Query.Direction.ASCENDING)
            CompetitionSort.POPULARITY -> q.orderBy("entriesSold", Query.Direction.DESCENDING)
        }
        if (afterValue != null) q = q.startAfter(afterValue)

        val snap = q.limit(pageSize).get().await()
        val items = snap.toObjects(Competition::class.java)
        val cursor = snap.documents.lastOrNull()?.let {
            when (sort) {
                CompetitionSort.ENDING_SOON -> it.getTimestamp("closesAt")
                CompetitionSort.NEWEST -> it.getTimestamp("createdAt")
                CompetitionSort.PRICE_LOW -> it.getLong("entryPricePence")
                CompetitionSort.POPULARITY -> it.getLong("entriesSold")
            }
        }
        return items to cursor
    }

    /**
     * Firestore has no full-text search. We fetch the live set once and filter
     * client-side, which is correct at this catalogue size (tens of raffles).
     * If the catalogue grows past a few hundred, point this at Algolia or a
     * search extension instead; the call site does not change.
     */
    suspend fun search(term: String): List<Competition> {
        val needle = term.trim().lowercase()
        if (needle.length < 2) return emptyList()
        val snap = col.whereEqualTo("status", "live").limit(100).get().await()
        return snap.toObjects(Competition::class.java).filter {
            it.title.lowercase().contains(needle) ||
                it.brand.lowercase().contains(needle) ||
                it.prizeName.lowercase().contains(needle) ||
                it.category.lowercase().contains(needle)
        }
    }

    suspend fun rules(rulesId: String): RulesDocument? =
        db.collection(Collections.APP_CONTENT).document(rulesId).get().await()
            .toObject(RulesDocument::class.java)

    suspend fun byIds(ids: List<String>): List<Competition> {
        if (ids.isEmpty()) return emptyList()
        // whereIn caps at 30 values, so chunk it.
        return ids.chunked(30).flatMap { chunk ->
            col.whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                .get().await().toObjects(Competition::class.java)
        }
    }

    private fun snapshotFlow(query: Query): Flow<List<Competition>> = callbackFlow {
        val reg = query.addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            trySend(snap?.toObjects(Competition::class.java) ?: emptyList())
        }
        awaitClose { reg.remove() }
    }
}
