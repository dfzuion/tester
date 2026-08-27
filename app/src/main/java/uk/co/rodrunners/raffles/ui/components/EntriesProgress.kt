package uk.co.rodrunners.raffles.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import uk.co.rodrunners.raffles.ui.theme.RrrColors

/** Entries sold against the cap. */
@Composable
fun EntriesProgress(
    entriesSold: Int,
    maxEntries: Int,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
) {
    val fraction = if (maxEntries == 0) 0f else (entriesSold.toFloat() / maxEntries).coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = fraction, label = "entriesProgress")
    val remaining = (maxEntries - entriesSold).coerceAtLeast(0)
    val nearlyGone = remaining in 1..(maxEntries / 10).coerceAtLeast(1)

    Column(
        modifier.clearAndSetSemantics {
            contentDescription = "$entriesSold of $maxEntries entries sold. $remaining remaining."
        }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(RrrColors.Hairline)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .layout { measurable, constraints ->
                        val width = (constraints.maxWidth * animated).toInt()
                        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                        layout(width, placeable.height) { placeable.place(0, 0) }
                    }
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (nearlyGone) RrrColors.Warning else RrrColors.Gold)
            )
        }
        if (showLabels) {
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$entriesSold / $maxEntries sold",
                    style = MaterialTheme.typography.labelSmall,
                    color = RrrColors.Mist,
                )
                Text(
                    if (remaining == 0) "Sold out" else "$remaining left",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (nearlyGone || remaining == 0) RrrColors.Warning else RrrColors.Mist,
                )
            }
        }
    }
}
