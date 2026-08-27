package uk.co.rodrunners.raffles.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import uk.co.rodrunners.raffles.core.Collections
import uk.co.rodrunners.raffles.core.Functions
import uk.co.rodrunners.raffles.data.model.Address
import uk.co.rodrunners.raffles.data.model.AppNotification
import uk.co.rodrunners.raffles.data.model.UserProfile

@Singleton
class AccountRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val messaging: FirebaseMessaging,
) {
    fun profile(uid: String): Flow<UserProfile?> = callbackFlow {
        val reg = db.collection(Collections.USERS).document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toObject(UserProfile::class.java))
            }
        awaitClose { reg.remove() }
    }

    suspend fun updateDetails(uid: String, displayName: String, phone: String?) {
        db.collection(Collections.USERS).document(uid).update(
            mapOf(
                "displayName" to displayName.trim(),
                "phone" to phone?.trim(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    suspend fun updateAddress(uid: String, address: Address) {
        db.collection(Collections.USERS).document(uid).update(
            mapOf("address" to address, "updatedAt" to FieldValue.serverTimestamp())
        ).await()
    }

    suspend fun setNotificationPreference(uid: String, category: String, enabled: Boolean) {
        db.collection(Collections.USERS).document(uid).update(
            mapOf(
                "notificationPreferences.$category" to enabled,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    fun notifications(uid: String, limit: Long = 50): Flow<List<AppNotification>> = callbackFlow {
        val reg = db.collection(Collections.USERS).document(uid)
            .collection(Collections.NOTIFICATIONS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toObjects(AppNotification::class.java) ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    suspend fun markNotificationRead(uid: String, notificationId: String) {
        db.collection(Collections.USERS).document(uid)
            .collection(Collections.NOTIFICATIONS).document(notificationId)
            .update("read", true).await()
    }

    /** Registers this device for push and subscribes to broadcast topics. */
    suspend fun syncPushToken() {
        val token = messaging.token.await()
        functions.getHttpsCallable(Functions.REGISTER_TOKEN)
            .call(mapOf("token" to token)).await()
        messaging.subscribeToTopic(uk.co.rodrunners.raffles.core.Topics.NEW_COMPETITIONS).await()
        messaging.subscribeToTopic(uk.co.rodrunners.raffles.core.Topics.RESULTS).await()
    }

    suspend fun deleteAccount(): Int {
        val result = functions.getHttpsCallable(Functions.DELETE_ACCOUNT).call().await()
        return ((result.data as Map<*, *>)["openEntriesRetained"] as? Number)?.toInt() ?: 0
    }
}
