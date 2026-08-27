package uk.co.rodrunners.raffles.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

object TimeFormat {
    private val zone: ZoneId = ZoneId.of("Europe/London")
    private val dateTime = DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm", Locale.UK)
    private val dateOnly = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

    fun full(epochMillis: Long): String =
        dateTime.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    fun date(epochMillis: Long): String =
        dateOnly.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    fun short(epochMillis: Long): String =
        shortDate.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    /** "2d 6h 30m" / "4h 12m" / "38m 04s"; the format used on raffle cards. */
    /** "3 minutes ago", "Yesterday", or a date once it's older than a week. */
    fun relative(epochMillis: Long): String {
        if (epochMillis <= 0L) return ""
        val delta = System.currentTimeMillis() - epochMillis
        return when {
            delta < 60_000L -> "Just now"
            delta < 3_600_000L -> "${delta / 60_000L}m ago"
            delta < 86_400_000L -> "${delta / 3_600_000L}h ago"
            delta < 172_800_000L -> "Yesterday"
            delta < 604_800_000L -> "${delta / 86_400_000L} days ago"
            else -> date(epochMillis)
        }
    }

    fun remaining(millisLeft: Long): String {
        val ms = max(0L, millisLeft)
        val totalSeconds = ms / 1000
        val days = totalSeconds / 86_400
        val hours = (totalSeconds % 86_400) / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return when {
            ms == 0L -> "Closed"
            days > 0 -> "${days}d ${hours}h ${minutes}m"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds.toString().padStart(2, '0')}s"
            else -> "${seconds}s"
        }
    }

    /** Screen-reader friendly version of the same value. */
    fun remainingSpoken(millisLeft: Long): String {
        val totalSeconds = max(0L, millisLeft) / 1000
        val days = totalSeconds / 86_400
        val hours = (totalSeconds % 86_400) / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        return buildString {
            if (days > 0) append("$days ${if (days == 1L) "day" else "days"} ")
            if (hours > 0) append("$hours ${if (hours == 1L) "hour" else "hours"} ")
            append("$minutes ${if (minutes == 1L) "minute" else "minutes"} remaining")
        }
    }
}
