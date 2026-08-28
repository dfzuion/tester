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
import uk.co.rodrunners.raffles.data.model.CreditCoupon
import uk.co.rodrunners.raffles.data.model.CreditEntry

/**
 * Site credit. The balance itself lives on the user profile; this repository
 * covers the ledger and the actions that move it. Nothing here writes a
 * balance - every movement goes through a callable so it can be validated and
 * recorded server-side.
 */
@Singleton
class CreditRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) {
    fun ledger(uid: String, limit: Long = 50): Flow<List<CreditEntry>> = callbackFlow {
        val reg = db.collection(Collections.USERS).document(uid)
            .collection(Collections.CREDIT_LEDGER)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toObjects(CreditEntry::class.java) ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    /** Returns the value added and the new balance. */
    suspend fun redeemCoupon(code: String): Pair<Int, Int> {
        val result = functions.getHttpsCallable(Functions.REDEEM_COUPON)
            .call(mapOf("code" to code.trim().uppercase())).await()
        val data = result.getData() as? Map<*, *> ?: emptyMap<String, Any>()
        val value = (data["valuePence"] as? Number)?.toInt() ?: 0
        val balance = (data["balancePence"] as? Number)?.toInt() ?: 0
        return value to balance
    }

    // ---- Admin -------------------------------------------------------

    suspend fun createCoupon(code: String, valuePence: Int, maxRedemptions: Int, expiresAtMillis: Long?) {
        functions.getHttpsCallable(Functions.CREATE_COUPON).call(
            mapOf(
                "code" to code.trim().uppercase(),
                "valuePence" to valuePence,
                "maxRedemptions" to maxRedemptions,
                "expiresAtMillis" to expiresAtMillis,
            )
        ).await()
    }

    suspend fun coupons(): List<CreditCoupon> {
        val result = functions.getHttpsCallable(Functions.LIST_COUPONS).call().await()
        val rows = (result.getData() as? Map<*, *>)?.get("coupons") as? List<*> ?: emptyList<Any>()
        return rows.mapNotNull { row ->
            (row as? Map<*, *>)?.let {
                CreditCoupon(
                    code = it["code"] as? String ?: return@let null,
                    valuePence = (it["valuePence"] as? Number)?.toInt() ?: 0,
                    maxRedemptions = (it["maxRedemptions"] as? Number)?.toInt() ?: 0,
                    redemptionsUsed = (it["redemptionsUsed"] as? Number)?.toInt() ?: 0,
                    active = it["active"] as? Boolean ?: true,
                    expiresAtMillis = (it["expiresAtMillis"] as? Number)?.toLong(),
                )
            }
        }
    }

    suspend fun setCouponActive(code: String, active: Boolean) {
        functions.getHttpsCallable(Functions.SET_COUPON_ACTIVE)
            .call(mapOf("code" to code, "active" to active)).await()
    }

    suspend fun grantCredit(email: String, amountPence: Int, note: String) {
        functions.getHttpsCallable(Functions.GRANT_CREDIT)
            .call(mapOf("email" to email.trim(), "amountPence" to amountPence, "note" to note)).await()
    }
}
