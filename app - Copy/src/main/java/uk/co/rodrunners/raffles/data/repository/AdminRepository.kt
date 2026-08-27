package uk.co.rodrunners.raffles.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import uk.co.rodrunners.raffles.core.Collections
import uk.co.rodrunners.raffles.core.Functions
import uk.co.rodrunners.raffles.data.model.Competition
import uk.co.rodrunners.raffles.data.model.Order

data class DashboardStats(
    val revenuePence: Long,
    val paidOrders: Int,
    val entriesSold: Int,
    val liveCompetitions: Int,
    val completedCompetitions: Int,
    val failedPayments: Int,
)

data class DrawOutcome(
    val winningEntryNumber: Int,
    val eligibleEntryCount: Int,
    val seedHash: String,
)

/**
 * Admin actions are thin wrappers over callable functions. Nothing privileged
 * is done directly against Firestore from the app, so a compromised device
 * still cannot close a raffle or pick a winner.
 */
@Singleton
class AdminRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) {
    suspend fun dashboard(): DashboardStats {
        val paid = db.collection(Collections.ORDERS)
            .whereEqualTo("paymentStatus", "paid")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(500).get().await()
            .toObjects(Order::class.java)
        val live = db.collection(Collections.COMPETITIONS).whereEqualTo("status", "live").count()
            .get(com.google.firebase.firestore.AggregateSource.SERVER).await().count
        val drawn = db.collection(Collections.COMPETITIONS).whereEqualTo("status", "drawn").count()
            .get(com.google.firebase.firestore.AggregateSource.SERVER).await().count
        val failed = db.collection(Collections.ORDERS).whereEqualTo("paymentStatus", "failed").count()
            .get(com.google.firebase.firestore.AggregateSource.SERVER).await().count

        return DashboardStats(
            revenuePence = paid.sumOf { (it.totalPence - it.refundedPence).toLong() },
            paidOrders = paid.size,
            entriesSold = paid.sumOf { it.quantity },
            liveCompetitions = live.toInt(),
            completedCompetitions = drawn.toInt(),
            failedPayments = failed.toInt(),
        )
    }

    suspend fun recentOrders(limit: Long = 25): List<Order> =
        db.collection(Collections.ORDERS).orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit).get().await().toObjects(Order::class.java)

    suspend fun manageableCompetitions(): List<Competition> =
        db.collection(Collections.COMPETITIONS).orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50).get().await().toObjects(Competition::class.java)

    suspend fun setStatus(competitionId: String, status: String) {
        functions.getHttpsCallable(Functions.SET_COMPETITION_STATUS)
            .call(mapOf("competitionId" to competitionId, "status" to status)).await()
    }

    suspend fun close(competitionId: String, reason: String?) {
        functions.getHttpsCallable(Functions.CLOSE_COMPETITION)
            .call(mapOf("competitionId" to competitionId, "reason" to reason)).await()
    }

    suspend fun draw(competitionId: String, publishImmediately: Boolean): DrawOutcome {
        val result = functions.getHttpsCallable(Functions.DRAW_WINNER)
            .call(mapOf("competitionId" to competitionId, "publishImmediately" to publishImmediately)).await()
        val m = result.data as Map<*, *>
        return DrawOutcome(
            winningEntryNumber = (m["winningEntryNumber"] as Number).toInt(),
            eligibleEntryCount = (m["eligibleEntryCount"] as Number).toInt(),
            seedHash = m["seedHash"] as String,
        )
    }

    suspend fun publishResult(competitionId: String) {
        functions.getHttpsCallable(Functions.PUBLISH_RESULT)
            .call(mapOf("competitionId" to competitionId)).await()
    }

    suspend fun refund(orderId: String, amountPence: Int?, reason: String) {
        functions.getHttpsCallable(Functions.REFUND_ORDER)
            .call(mapOf("orderId" to orderId, "amountPence" to amountPence, "reason" to reason)).await()
    }

    suspend fun seedDemoData() {
        functions.getHttpsCallable(Functions.SEED_DEMO).call().await()
    }
}
