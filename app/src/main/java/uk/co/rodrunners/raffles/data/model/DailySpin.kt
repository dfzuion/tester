package uk.co.rodrunners.raffles.data.model

/**
 * The result of a daily spin. The prize is decided by the spinDailyWheel
 * function and never here - the wheel the customer watches is an animation of
 * a result the server has already committed to.
 */
data class SpinOutcome(
    val alreadySpun: Boolean = false,
    val pence: Int = 0,
    val label: String = "",
)

/**
 * Mirrors SPIN_WHEEL in functions/src/dailyspin.ts, and is used only to draw
 * the wheel. If the segments change on the server they must change here too,
 * or the wheel will show one set of prizes and pay another.
 */
val SPIN_SEGMENTS: List<Pair<Int, String>> = listOf(
    5 to "5p",
    10 to "10p",
    20 to "20p",
    50 to "50p",
    100 to "£1",
    200 to "£2",
)
