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
import uk.co.rodrunners.raffles.data.model.InstantWinClaim
import uk.co.rodrunners.raffles.data.model.InstantWinStock
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
        val m = result.getData() as Map<*, *>
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

    /**
     * Creates a draft raffle. Everything is validated again server-side; this
     * only shapes the payload.
     */
    suspend fun createCompetition(draft: CompetitionDraft): String {
        val result = functions.getHttpsCallable(Functions.CREATE_COMPETITION)
            .call(draft.toPayload()).await()
        return ((result.getData() as Map<*, *>)["competitionId"] as? String).orEmpty()
    }

    suspend fun updateCompetition(competitionId: String, draft: CompetitionDraft) {
        functions.getHttpsCallable(Functions.UPDATE_COMPETITION)
            .call(draft.toPayload() + mapOf("competitionId" to competitionId)).await()
    }

    suspend fun deleteDraft(competitionId: String) {
        functions.getHttpsCallable(Functions.DELETE_DRAFT_COMPETITION)
            .call(mapOf("competitionId" to competitionId)).await()
    }

    // ---- Instant wins -------------------------------------------------

    suspend fun addInstantWins(competitionId: String, prizes: List<InstantWinStock>): Int {
        val result = functions.getHttpsCallable(Functions.ADD_INSTANT_WINS).call(
            mapOf(
                "competitionId" to competitionId,
                "prizes" to prizes.map {
                    mapOf(
                        "prizeName" to it.prizeName.trim(),
                        "valuePence" to it.valuePence,
                        "quantity" to it.count,
                    )
                },
            )
        ).await()
        return ((result.getData() as Map<*, *>)["added"] as? Number)?.toInt() ?: 0
    }

    suspend fun instantWins(competitionId: String): InstantWinOverview {
        val result = functions.getHttpsCallable(Functions.LIST_INSTANT_WINS)
            .call(mapOf("competitionId" to competitionId)).await()
        val data = result.getData() as Map<*, *>
        val stock = (data["unclaimed"] as? List<*>).orEmpty().mapNotNull { row ->
            (row as? Map<*, *>)?.let {
                InstantWinStock(
                    prizeName = it["prizeName"] as? String ?: return@let null,
                    valuePence = (it["valuePence"] as? Number)?.toInt() ?: 0,
                    count = (it["count"] as? Number)?.toInt() ?: 0,
                )
            }
        }
        val claims = (data["won"] as? List<*>).orEmpty().mapNotNull { row ->
            (row as? Map<*, *>)?.let {
                InstantWinClaim(
                    id = it["id"] as? String ?: return@let null,
                    prizeName = it["prizeName"] as? String ?: "",
                    valuePence = (it["valuePence"] as? Number)?.toInt() ?: 0,
                    entryNumber = (it["entryNumber"] as? Number)?.toInt() ?: 0,
                    wonByName = it["wonByName"] as? String,
                    wonAtMillis = (it["wonAtMillis"] as? Number)?.toLong(),
                    claimStatus = it["claimStatus"] as? String ?: "pending",
                )
            }
        }
        return InstantWinOverview(stock, claims)
    }

    suspend fun removeInstantWins(competitionId: String, prizeName: String, count: Int) {
        functions.getHttpsCallable(Functions.REMOVE_INSTANT_WINS).call(
            mapOf("competitionId" to competitionId, "prizeName" to prizeName, "count" to count)
        ).await()
    }

    suspend fun setInstantWinClaimStatus(instantWinId: String, claimStatus: String) {
        functions.getHttpsCallable(Functions.SET_INSTANT_WIN_CLAIM).call(
            mapOf("instantWinId" to instantWinId, "claimStatus" to claimStatus)
        ).await()
    }

    suspend fun seedDemoData() {
        functions.getHttpsCallable(Functions.SEED_DEMO).call().await()
    }
}

/** What the admin instant-win screen needs in one round trip. */
data class InstantWinOverview(
    val stock: List<InstantWinStock> = emptyList(),
    val claims: List<InstantWinClaim> = emptyList(),
) {
    val unclaimedCount: Int get() = stock.sumOf { it.count }
    val unclaimedValuePence: Int get() = stock.sumOf { it.valuePence * it.count }
}
