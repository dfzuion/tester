package uk.co.rodrunners.raffles.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uk.co.rodrunners.raffles.core.TimeFormat
import uk.co.rodrunners.raffles.ui.theme.RrrColors

/**
 * One coroutine per visible countdown, ticking once a second only while the
 * composable is on screen; scrolling a list of raffles off-screen stops its
 * timers rather than leaving them running.
 */
@Composable
fun rememberCountdown(closesAtMillis: Long): State<Long> {
    val initial = remember(closesAtMillis) {
        mutableLongStateOf((closesAtMillis - System.currentTimeMillis()).coerceAtLeast(0))
    }
    return produceState(initialValue = initial.longValue, closesAtMillis) {
        while (true) {
            val left = (closesAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
            value = left
            if (left == 0L) break
            // Tick every second under an hour, every minute above it.
            delay(if (left < 3_600_000) 1_000 else 60_000)
        }
    }
}

@Composable
fun CountdownText(
    closesAtMillis: Long,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    color: Color = RrrColors.Bone,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelMedium,
) {
    val remaining by rememberCountdown(closesAtMillis)
    val urgent = remaining in 1 until 6 * 3_600_000L
    val text = TimeFormat.remaining(remaining)

    Row(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = if (remaining == 0L) "Closed" else TimeFormat.remainingSpoken(remaining)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcon) {
            Icon(
                Icons.Outlined.Schedule,
                contentDescription = null,
                tint = if (urgent) RrrColors.Warning else RrrColors.Mist,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            style = style,
            color = if (urgent) RrrColors.Warning else color,
        )
    }
}
