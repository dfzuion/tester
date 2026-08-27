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
import uk.co.rodrunners.raffles.data.model.Winner

@Singleton
class ResultsRepository @Inject constructor(private val db: FirebaseFirestore) {
    private val col get() = db.collection(Collections.WINNERS)

    fun latestWinners(limit: Long = 20): Flow<List<Winner>> = callbackFlow {
        val reg = col.whereEqualTo("published", true)
            .orderBy("drawnAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toObjects(Winner::class.java) ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    fun myWins(uid: String): Flow<List<Winner>> = callbackFlow {
        val reg = col.whereEqualTo("winnerUserId", uid)
            .orderBy("drawnAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toObjects(Winner::class.java) ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    suspend fun winnerFor(competitionId: String): Winner? =
        col.whereEqualTo("competitionId", competitionId).whereEqualTo("published", true)
            .limit(1).get().await().toObjects(Winner::class.java).firstOrNull()

    suspend fun searchResults(term: String, limit: Long = 60): List<Winner> {
        val needle = term.trim().lowercase()
        val all = col.whereEqualTo("published", true)
            .orderBy("drawnAt", Query.Direction.DESCENDING).limit(limit).get().await()
            .toObjects(Winner::class.java)
        if (needle.isBlank()) return all
        return all.filter {
            it.competitionTitle.lowercase().contains(needle) ||
                it.prizeName.lowercase().contains(needle) ||
                it.winningEntryNumber.toString() == needle
        }
    }
}
