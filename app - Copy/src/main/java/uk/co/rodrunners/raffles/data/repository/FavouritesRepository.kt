package uk.co.rodrunners.raffles.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import uk.co.rodrunners.raffles.core.Collections

@Singleton
class FavouritesRepository @Inject constructor(private val db: FirebaseFirestore) {

    /** Deterministic id keeps a double-tap from creating two favourites. */
    private fun docId(uid: String, competitionId: String) = "${uid}_$competitionId"

    fun favouriteIds(uid: String): Flow<Set<String>> = callbackFlow {
        val reg = db.collection(Collections.FAVOURITES).whereEqualTo("userId", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull { it.getString("competitionId") }?.toSet() ?: emptySet())
            }
        awaitClose { reg.remove() }
    }

    suspend fun toggle(uid: String, competitionId: String, makeFavourite: Boolean) {
        val ref = db.collection(Collections.FAVOURITES).document(docId(uid, competitionId))
        if (makeFavourite) {
            ref.set(
                mapOf(
                    "userId" to uid,
                    "competitionId" to competitionId,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } else {
            ref.delete().await()
        }
    }
}
