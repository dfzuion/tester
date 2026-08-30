package uk.co.rodrunners.raffles.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
        // Water in a channel, not a line: a green body with a crest running
        // across the surface and a bright lip at the waterline, so how far a
        // raffle has sold reads at a glance and matches the website.
        val swell = rememberInfiniteTransition(label = "entriesWater")
        val phase by swell.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "swellPhase",
        )

        val body = if (nearlyGone) RrrColors.Warning else RrrColors.Khaki
        val crest = if (nearlyGone) RrrColors.Warning else RrrColors.KhakiBright

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(RrrColors.Ink)
        ) {
            // DrawScope works in physical pixels, so everything below is
            // scaled off the height it was actually given rather than off a
            // dp number, or it disappears on a dense screen.
            val h = size.height
            val filled = size.width * animated

            if (filled <= 0f) return@Canvas

            drawRect(
                brush = Brush.verticalGradient(
                    0f to crest.copy(alpha = 0.85f),
                    0.55f to body,
                    1f to RrrColors.Bark,
                ),
                size = androidx.compose.ui.geometry.Size(filled, h),
            )

            // The crest: one wave rolling along the top of the water.
            val wavelength = h * 3.4f
            val shift = phase * wavelength
            val path = Path()
            var x = -wavelength + shift
            path.moveTo(x, h * 0.36f)

            while (x < filled + wavelength) {
                path.relativeQuadraticTo(wavelength * 0.25f, -h * 0.26f, wavelength * 0.5f, 0f)
                path.relativeQuadraticTo(wavelength * 0.25f, h * 0.26f, wavelength * 0.5f, 0f)
                x += wavelength
            }

            clipRect(right = filled) {
                drawPath(path, color = RrrColors.Bone.copy(alpha = 0.42f), style = Stroke(width = h * 0.10f))
            }

            // The waterline itself.
            drawLine(
                color = RrrColors.Bone,
                start = Offset(filled - h * 0.10f, 0f),
                end = Offset(filled - h * 0.10f, h),
                strokeWidth = h * 0.20f,
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
