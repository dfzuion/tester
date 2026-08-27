package uk.co.rodrunners.raffles.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
