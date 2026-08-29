package uk.co.rodrunners.raffles.data.model

data class LeaderboardRow(
    val position: Int = 0,
    val userId: String = "",
    val displayName: String = "Angler",
    val weightLb: Float = 0f,
    val species: String = "",
)

/** Who won the week before, kept so the board can show what it takes to win. */
data class PastWeek(
    val displayName: String = "",
    val weightLb: Float = 0f,
    val species: String = "",
)

data class LeaderboardWeek(
    val weekKey: String = "",
    val prizePence: Int = 1000,
    /** The signed-in customer's uid, so their own row can be marked. */
    val you: String? = null,
    val rows: List<LeaderboardRow> = emptyList(),
    val lastWeek: PastWeek? = null,
)
