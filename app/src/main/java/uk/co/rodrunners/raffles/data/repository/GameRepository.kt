package uk.co.rodrunners.raffles.data.repository

import com.google.firebase.functions.FirebaseFunctions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import uk.co.rodrunners.raffles.core.Functions
import uk.co.rodrunners.raffles.data.model.LeaderboardRow
import uk.co.rodrunners.raffles.data.model.LeaderboardWeek
import uk.co.rodrunners.raffles.data.model.PastWeek

/**
 * The Cast & Catch weekly board.
 *
 * Nothing here writes a score directly. The weight goes through a callable
 * that checks it against what the species can actually reach, because the
 * week pays real site credit and a client that could write to the board could
 * simply type its way to the prize.
 */
@Singleton
class GameRepository @Inject constructor(private val functions: FirebaseFunctions) {

    /** Returns true when this was a new best for the week. */
    suspend fun submitCatch(species: String, weightLb: Float): Boolean {
        val result = functions.getHttpsCallable(Functions.SUBMIT_GAME_CATCH)
            .call(mapOf("species" to species, "weightLb" to weightLb.toDouble()))
            .await()
        val data = result.getData() as? Map<*, *> ?: return false
        return data["improved"] == true
    }

    suspend fun leaderboard(): LeaderboardWeek {
        val result = functions.getHttpsCallable(Functions.GAME_LEADERBOARD).call().await()
        val data = result.getData() as? Map<*, *> ?: return LeaderboardWeek()

        val rows = (data["board"] as? List<*>).orEmpty().mapNotNull { entry ->
            val row = entry as? Map<*, *> ?: return@mapNotNull null
            LeaderboardRow(
                position = (row["position"] as? Number)?.toInt() ?: 0,
                userId = row["userId"] as? String ?: "",
                displayName = row["displayName"] as? String ?: "Angler",
                weightLb = (row["weightLb"] as? Number)?.toFloat() ?: 0f,
                species = row["species"] as? String ?: "",
            )
        }

        val last = data["lastWeek"] as? Map<*, *>

        return LeaderboardWeek(
            weekKey = data["weekKey"] as? String ?: "",
            prizePence = (data["prizePence"] as? Number)?.toInt() ?: 1000,
            you = data["you"] as? String,
            rows = rows,
            lastWeek = last?.let {
                PastWeek(
                    displayName = it["displayName"] as? String ?: "",
                    weightLb = (it["weightLb"] as? Number)?.toFloat() ?: 0f,
                    species = it["species"] as? String ?: "",
                )
            },
        )
    }
}
