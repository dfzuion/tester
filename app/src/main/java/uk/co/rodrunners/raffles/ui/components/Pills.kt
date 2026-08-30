package uk.co.rodrunners.raffles.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import uk.co.rodrunners.raffles.ui.theme.RrrColors
import uk.co.rodrunners.raffles.ui.theme.RrrType

/** Status marker on cards: LIVE, ENDING SOON, SOLD OUT, DRAWN. */
@Composable
fun StatusPill(status: String, modifier: Modifier = Modifier) {
    val (label, colour) = when (status) {
        "live" -> "LIVE" to RrrColors.Success
        "ending_soon" -> "ENDING SOON" to RrrColors.Warning
        "sold_out" -> "SOLD OUT" to RrrColors.Danger
        "paused" -> "PAUSED" to RrrColors.Slate
        "closed" -> "CLOSED" to RrrColors.Slate
        "drawn" -> "DRAWN" to RrrColors.Gold
        "won" -> "WON" to RrrColors.Gold
        "not_won" -> "NOT WON" to RrrColors.Slate
        else -> status.uppercase() to RrrColors.Mist
    }
    Box(
        modifier
            .background(colour.copy(alpha = 0.14f), RoundedCornerShape(3.dp))
            .border(BorderStroke(1.dp, colour.copy(alpha = 0.5f)), RoundedCornerShape(3.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(label, style = RrrType.Eyebrow, color = colour)
    }
}

/** Horizontal filter row used for categories and result filters. */
@Composable
fun <T> ChipRow(
    items: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
    allLabel: String? = "All",
) {
    val entries: List<T?> = if (allLabel != null) listOf<T?>(null) + items else items
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        items(entries) { item ->
            val isSelected = item == selected
            Chip(
                text = if (item == null) allLabel.orEmpty() else label(item),
                selected = isSelected,
                onClick = { onSelect(item) },
            )
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val border: Color = if (selected) RrrColors.Gold else RrrColors.Hairline
    Row(
        modifier
            .background(
                if (selected) RrrColors.GoldDim else Color.Transparent,
                RoundedCornerShape(20.dp),
            )
            .border(BorderStroke(1.dp, border), RoundedCornerShape(20.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) RrrColors.GoldBright else RrrColors.Mist,
        )
    }
}

/** The standing notice that appears whenever demo content is on screen. */
@Composable
fun DemoDataBanner(modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(RrrColors.Warning.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, RrrColors.Warning.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Demonstration data. These are not real competitions and no entry can be purchased.",
            style = MaterialTheme.typography.labelSmall,
            color = RrrColors.Warning,
        )
    }
}

/**
 * The lit LIVE marker. Same shape as StatusPill but with a lamp on it that
 * breathes, because a raffle that is open right now should look like it is
 * open right now and not like a label somebody printed.
 */
@Composable
fun LivePill(modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "livePulse")
    val lamp by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "lamp",
    )

    Row(
        modifier
            .background(RrrColors.Ink.copy(alpha = 0.82f), RoundedCornerShape(999.dp))
            .border(BorderStroke(1.dp, RrrColors.KhakiBright.copy(alpha = 0.75f)), RoundedCornerShape(999.dp))
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(7.dp)) {
            drawCircle(color = RrrColors.Success.copy(alpha = lamp * 0.35f), radius = size.minDimension * 0.62f)
            drawCircle(color = RrrColors.Success.copy(alpha = lamp), radius = size.minDimension * 0.32f)
        }

        Spacer(Modifier.width(7.dp))

        Text("LIVE", style = RrrType.Eyebrow, color = RrrColors.Bone)
    }
}

/**
 * How long is left, next to the LIVE lamp. Days until we are inside the last
 * day, then hours, then minutes — "1 day left" for the final four hours is the
 * sort of thing people complain about afterwards.
 *
 * Inside three days it flashes. That is the only thing on a card allowed to
 * move on its own, so it has to earn it.
 */
@Composable
fun CountdownPill(closesAtMillis: Long?, modifier: Modifier = Modifier) {
    if (closesAtMillis == null || closesAtMillis <= 0L) return

    val left = closesAtMillis - System.currentTimeMillis()
    val hours = left / 3_600_000L

    val label = when {
        left <= 0L -> "CLOSED"
        hours < 1L -> "MINUTES LEFT"
        hours < 24L -> if (hours == 1L) "1 HOUR LEFT" else "$hours HOURS LEFT"
        else -> {
            val days = ((left + 86_399_999L) / 86_400_000L)
            if (days == 1L) "1 DAY LEFT" else "$days DAYS LEFT"
        }
    }

    val hurrying = left in 1..(3L * 86_400_000L)

    val flash = rememberInfiniteTransition(label = "countdownFlash")
    val on by flash.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "flash",
    )

    // A hard alternation rather than a fade. A warning light blinks; it does
    // not breathe.
    val lit = hurrying && on < 0.5f

    val edge = if (hurrying) RrrColors.Warning else RrrColors.Khaki
    val fill = if (lit) RrrColors.Warning else RrrColors.Ink.copy(alpha = 0.82f)
    val ink = when {
        lit -> RrrColors.Ink
        hurrying -> RrrColors.Warning
        else -> RrrColors.KhakiBright
    }

    Box(
        modifier
            .background(fill, RoundedCornerShape(999.dp))
            .border(BorderStroke(1.dp, edge), RoundedCornerShape(999.dp))
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(label, style = RrrType.Eyebrow, color = ink)
    }
}
