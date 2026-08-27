package uk.co.rodrunners.raffles.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import uk.co.rodrunners.raffles.core.Collections
import uk.co.rodrunners.raffles.data.model.Order

@Singleton
class OrderRepository @Inject constructor(private val db: FirebaseFirestore) {

    fun myOrders(uid: String, limit: Long = 50): Flow<List<Order>> = callbackFlow {
        val reg = db.collection(Collections.ORDERS)
            .whereEqualTo("userId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toObjects(Order::class.java) ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    /**
     * Live view of a single order. The confirmation screen watches this and only
     * declares success once paymentStatus flips to "paid"; which only the
     * Stripe webhook can do.
     */
    fun order(orderId: String): Flow<Order?> = callbackFlow {
        val reg = db.collection(Collections.ORDERS).document(orderId)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toObject(Order::class.java))
            }
        awaitClose { reg.remove() }
    }
}
