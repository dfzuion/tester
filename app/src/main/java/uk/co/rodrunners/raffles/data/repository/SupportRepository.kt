package uk.co.rodrunners.raffles.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import uk.co.rodrunners.raffles.core.Collections
import uk.co.rodrunners.raffles.core.Functions
import uk.co.rodrunners.raffles.data.model.SupportTicket
import uk.co.rodrunners.raffles.data.model.TicketMessage

@Singleton
class SupportRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) {
    fun myTickets(uid: String): Flow<List<SupportTicket>> = callbackFlow {
        val reg = db.collection(Collections.SUPPORT_TICKETS)
            .whereEqualTo("userId", uid)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toObjects(SupportTicket::class.java) ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    /** One ticket, live; so a staff reply appears in the thread without a refresh. */
    fun ticket(ticketDocId: String): Flow<SupportTicket?> = callbackFlow {
        val reg = db.collection(Collections.SUPPORT_TICKETS).document(ticketDocId)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toObject(SupportTicket::class.java))
            }
        awaitClose { reg.remove() }
    }

    fun messages(ticketDocId: String): Flow<List<TicketMessage>> = callbackFlow {
        val reg = db.collection(Collections.SUPPORT_TICKETS).document(ticketDocId)
            .collection("messages").orderBy("createdAt")
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toObjects(TicketMessage::class.java) ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    suspend fun createTicket(category: String, subject: String, message: String, orderId: String?): String {
        val result = functions.getHttpsCallable(Functions.CREATE_TICKET).call(
            mapOf("category" to category, "subject" to subject, "message" to message, "orderId" to orderId)
        ).await()
        return (result.getData() as Map<*, *>)["ticketId"] as String
    }

    suspend fun reply(ticketDocId: String, message: String) {
        functions.getHttpsCallable(Functions.REPLY_TICKET)
            .call(mapOf("ticketDocId" to ticketDocId, "message" to message)).await()
    }
}
