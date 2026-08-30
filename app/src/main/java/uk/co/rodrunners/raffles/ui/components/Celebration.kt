package uk.co.rodrunners.raffles.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.co.rodrunners.raffles.core.Money
import uk.co.rodrunners.raffles.ui.theme.RrrColors
import uk.co.rodrunners.raffles.ui.theme.RrrType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * What a prize looks like to this screen. Deliberately not the Firestore
 * model — the celebration should not care where the win came from, and the
 * same panel is used after a purchase and from a push notification.
 */
data class WonPrize(
    val prizeName: String,
    val entryNumber: Int?,
    val valuePence: Long,
)

// Brand colours plus a couple of naturals. No gold, no bronze.
private val CONFETTI = listOf(
    Color(0xFF51513F),
    Color(0xFF9B9B7E),
    Color(0xFFEFE9DA),
    Color(0xFFE07A45),
    Color(0xFFC8D48A),
    Color(0xFF6E7350),
    Color(0xFFFFFFFF),
)

private const val PIECES = 150

/** One piece of paper, with everything about it fixed at birth. */
private class Bit(random: Random) {
    val lane = random.nextFloat()
    val drift = (random.nextFloat() - 0.5f) * 0.55f
    val fall = 0.32f + random.nextFloat() * 0.55f
    val start = random.nextFloat()
    val wide = 0.010f + random.nextFloat() * 0.014f
    val tall = 0.016f + random.nextFloat() * 0.022f
    val spin = 3.5f + random.nextFloat() * 9f
    val flutter = 2.2f + random.nextFloat() * 4.5f
    val tilt = random.nextFloat() * 6.28f
    val colour = CONFETTI[random.nextInt(CONFETTI.size)]
}

/**
 * Confetti over the whole screen.
 *
 * Each piece is a flat rectangle tumbling on two axes, so it flashes edge-on
 * as it falls. That flicker is the entire reason paper confetti reads as
 * confetti and a field of coloured dots does not.
 *
 * One canvas and one animation drive the lot. Doing this with a hundred and
 * fifty composables would drop frames on the phones most of our customers are
 * actually holding.
 */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val bits = remember { List(PIECES) { Bit(Random(it * 7919)) } }

    val clock = rememberInfiniteTransition(label = "confetti")
    val t by clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "confettiClock",
    )

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        for (bit in bits) {
            // Wrapped rather than respawned, so the screen stays busy for as
            // long as the panel is up.
            val progress = (bit.start + t * bit.fall) % 1f
            val y = progress * (h + h * 0.3f) - h * 0.15f
            val x = (bit.lane + bit.drift * progress) * w +
                sin(progress * bit.flutter * 6.28f) * w * 0.035f

            val turn = bit.tilt + progress * bit.spin * 6.28f
            val face = abs(cos(turn))

            val pw = bit.wide * w
            val ph = bit.tall * h * face

            if (ph < 0.5f) continue

            rotate(degrees = turn * 12f, pivot = Offset(x, y)) {
                drawRect(
                    color = bit.colour.copy(alpha = 0.55f + face * 0.45f),
                    topLeft = Offset(x - pw / 2f, y - ph / 2f),
                    size = Size(pw, ph),
                )
            }
        }
    }
}

/**
 * The panel that says what was won, over the confetti.
 *
 * If somebody has won more than one thing, all of them are listed and the
 * total is added up. Being told about one prize when you have won three is
 * worse than not being told at all.
 */
@Composable
fun InstantWinCelebration(
    prizes: List<WonPrize>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (prizes.isEmpty()) return

    val many = prizes.size > 1
    val total = prizes.sumOf { it.valuePence }

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xB8050704)),
        contentAlignment = Alignment.Center,
    ) {
        Confetti()

        Column(
            Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(RrrColors.Surface)
                .border(BorderStroke(1.dp, RrrColors.Khaki), RoundedCornerShape(16.dp))
                .verticalScroll(rememberScrollState())
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (many) "${prizes.size} INSTANT WINS" else "INSTANT WIN",
                style = RrrType.Eyebrow,
                color = RrrColors.KhakiBright,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                if (many) "You have won them all" else "You have won",
                style = MaterialTheme.typography.headlineMedium,
                color = RrrColors.Bone,
            )

            Spacer(Modifier.height(20.dp))

            prizes.forEach { prize ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(RrrColors.Ink)
                        .border(BorderStroke(1.dp, RrrColors.Hairline), RoundedCornerShape(11.dp))
                        .padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            prize.prizeName,
                            style = MaterialTheme.typography.titleMedium,
                            color = RrrColors.Bone,
                        )

                        Spacer(Modifier.height(3.dp))

                        Text(
                            buildString {
                                append(
                                    if (prize.entryNumber != null) {
                                        "On number ${prize.entryNumber}"
                                    } else {
                                        "On one of your numbers"
                                    }
                                )
                                if (prize.valuePence > 0) {
                                    append(" · ")
                                    append(Money.format(prize.valuePence))
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = RrrColors.Mist,
                        )
                    }
                }
            }

            if (many && total > 0) {
                Spacer(Modifier.height(4.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text("ALTOGETHER", style = RrrType.Eyebrow, color = RrrColors.Mist)

                    Text(
                        Money.format(total),
                        style = MaterialTheme.typography.headlineSmall,
                        color = RrrColors.Success,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(16.dp))
            }

            Text(
                "We have it on record. You will get an email, and it is in your " +
                    "account under Orders — nothing to claim.",
                style = MaterialTheme.typography.bodySmall,
                color = RrrColors.Mist,
            )

            Spacer(Modifier.height(20.dp))

            GoldButton(
                text = "BRILLIANT",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
