package uk.co.rodrunners.raffles.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import uk.co.rodrunners.raffles.R
import uk.co.rodrunners.raffles.ui.theme.RrrColors
import uk.co.rodrunners.raffles.ui.theme.RrrType

/**
 * The wordmark: two stacked lines of heavy condensed caps, with RAFFLES set
 * smaller between two gold rules. That rule pair is the brand's signature and
 * reappears as the section divider throughout the app.
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier, compact: Boolean = false) {
    Image(
        painter = painterResource(R.drawable.logo_full),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .height(if (compact) 34.dp else 108.dp)
            .clearAndSetSemantics { contentDescription = "Rod Runners Raffles" },
    )
}

/** Text lockup, for the few places the artwork is too heavy - receipts, rows. */
@Composable
fun WordmarkText(modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = "Rod Runners Raffles" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "ROD RUNNERS",
            style = if (compact) RrrType.WordmarkSmall else RrrType.Wordmark,
            color = RrrColors.Bone,
        )
        Spacer(Modifier.height(if (compact) 3.dp else 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoldRule(width = if (compact) 16.dp else 34.dp)
            Text(
                text = "RAFFLES",
                style = RrrType.Eyebrow,
                color = RrrColors.Gold,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            GoldRule(width = if (compact) 16.dp else 34.dp)
        }
    }
}

@Composable
fun GoldRule(width: androidx.compose.ui.unit.Dp = 34.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(width)
            .height(1.dp)
            .drawBehind {
                drawLine(
                    color = RrrColors.Gold,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = size.height,
                )
            }
    )
}

/** Section heading with the same rule device, used above every home row. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoldRule(width = 18.dp)
            Spacer(Modifier.width(10.dp))
            Text(title.uppercase(), style = RrrType.Eyebrow, color = RrrColors.Bone)
        }
        action?.invoke()
    }
}
