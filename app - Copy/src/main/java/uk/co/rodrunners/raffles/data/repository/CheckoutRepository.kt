package uk.co.rodrunners.raffles.data.repository

import com.google.firebase.functions.FirebaseFunctions
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import uk.co.rodrunners.raffles.core.Functions
import uk.co.rodrunners.raffles.data.model.PriceBreakdown

data class CreatedOrder(
    val orderId: String,
    val orderNumber: String,
    val clientSecret: String,
    val breakdown: PriceBreakdown,
)

/**
 * Every number on the checkout screen comes from here, i.e. from the server.
 * The app sends a competition id, a quantity and an optional promo code, and
 * displays whatever comes back. It never adds up a total itself.
 */
@Singleton
class CheckoutRepository @Inject constructor(
    private val functions: FirebaseFunctions,
) {
    suspend fun quote(competitionId: String, quantity: Int, promoCode: String?): PriceBreakdown {
        val result = functions.getHttpsCallable(Functions.QUOTE_BASKET)
            .call(mapOf("competitionId" to competitionId, "quantity" to quantity, "promoCode" to promoCode))
            .await()
        return parseBreakdown(result.data as Map<*, *>)
    }

    /**
     * @param idempotencyKey generated once per checkout attempt and reused on
     * retry, so a dropped connection can never create two orders or reserve two
     * sets of entry numbers.
     */
    suspend fun createOrder(
        competitionId: String,
        quantity: Int,
        promoCode: String?,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): CreatedOrder {
        val result = functions.getHttpsCallable(Functions.CREATE_ORDER)
            .call(
                mapOf(
                    "competitionId" to competitionId,
                    "quantity" to quantity,
                    "promoCode" to promoCode,
                    "idempotencyKey" to idempotencyKey,
                )
            ).await()
        val map = result.data as Map<*, *>
        return CreatedOrder(
            orderId = map["orderId"] as String,
            orderNumber = map["orderNumber"] as? String ?: "",
            clientSecret = map["clientSecret"] as String,
            breakdown = parseBreakdown(map["breakdown"] as Map<*, *>),
        )
    }

    suspend fun applyPromo(code: String, competitionId: String, subtotalPence: Int): Int {
        val result = functions.getHttpsCallable(Functions.APPLY_PROMO)
            .call(mapOf("code" to code, "competitionId" to competitionId, "subtotalPence" to subtotalPence))
            .await()
        return ((result.data as Map<*, *>)["discountPence"] as Number).toInt()
    }

    private fun parseBreakdown(m: Map<*, *>) = PriceBreakdown(
        quantity = (m["quantity"] as Number).toInt(),
        unitPricePence = (m["unitPricePence"] as Number).toInt(),
        bundleLabel = m["bundleLabel"] as? String,
        subtotalPence = (m["subtotalPence"] as Number).toInt(),
        bundleDiscountPence = (m["bundleDiscountPence"] as Number).toInt(),
        promoDiscountPence = (m["promoDiscountPence"] as Number).toInt(),
        promoCode = m["promoCode"] as? String,
        feePence = (m["feePence"] as Number).toInt(),
        totalPence = (m["totalPence"] as Number).toInt(),
    )
}
