package uk.co.rodrunners.raffles.ui.screens.game

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.co.rodrunners.raffles.ui.components.Wordmark
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random

/**
 * Cast and catch, the same game as the one on the website and with the same
 * numbers behind it, so a personal best set on one feels like the same game
 * on the other.
 *
 * Nothing here touches the server and nothing here pays out. The daily spin is
 * the thing that gives credit and it is decided server-side; keeping this free
 * of money means it can be played as often as you like.
 */

private data class Species(
    val name: String,
    val min: Float,
    val max: Float,
    val fight: Float,
)

private val SPECIES = listOf(
    Species("Roach", 0.4f, 1.2f, 0.55f),
    Species("Tench", 2f, 6f, 0.8f),
    Species("Bream", 3f, 9f, 0.7f),
    Species("Common carp", 8f, 24f, 1f),
    Species("Mirror carp", 12f, 34f, 1.15f),
    Species("Leather carp", 18f, 42f, 1.3f),
)

private const val PREFS = "rr_game"
private const val BEST_NAME = "best_name"
private const val BEST_WEIGHT = "best_weight"

private enum class Phase { Ready, Power, Waiting, Hooked, Landed, Lost }

/** A longer cast reaches the deeper water, where the better fish sit. */
private fun pickSpecies(power: Float): Species {
    val reach = (power * SPECIES.size).toInt().coerceAtMost(SPECIES.size - 1)
    val from = (reach - 1).coerceAtLeast(0)
    return SPECIES.subList(from, reach + 1).random()
}

private fun weigh(species: Species): Float {
    // Two rolls averaged, so the middle of the range is the common result and
    // a personal best actually means something.
    val roll = (Random.nextFloat() + Random.nextFloat()) / 2f
    return species.min + (species.max - species.min) * roll
}

private fun readBest(context: Context): Pair<String, Float>? {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val name = prefs.getString(BEST_NAME, null) ?: return null
    return name to prefs.getFloat(BEST_WEIGHT, 0f)
}

private fun writeBest(context: Context, name: String, weight: Float) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(BEST_NAME, name)
        .putFloat(BEST_WEIGHT, weight)
        .apply()
}

private fun formatWeight(weight: Float): String = String.format(Locale.UK, "%.2f lb", weight)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var phase by remember { mutableStateOf(Phase.Ready) }
    var message by remember { mutableStateOf("Tap the water to cast") }
    var power by remember { mutableFloatStateOf(0f) }
    var tension by remember { mutableFloatStateOf(0f) }
    var reeled by remember { mutableFloatStateOf(0f) }
    var swim by remember { mutableFloatStateOf(0f) }
    var caught by remember { mutableStateOf<Pair<String, Float>?>(null) }
    var best by remember { mutableStateOf(readBest(context)) }

    // Held outside snapshot state: the frame loop reads and writes these every
    // frame and nothing draws from them directly.
    val hidden = remember { Hidden() }

    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
            last = now
            swim += dt

            when (phase) {
                Phase.Power -> {
                    power += hidden.powerDir * dt * 1.15f
                    if (power >= 1f) {
                        power = 1f
                        hidden.powerDir = -1f
                    }
                    if (power <= 0f) {
                        power = 0f
                        hidden.powerDir = 1f
                    }
                }

                Phase.Waiting -> {
                    hidden.sinceCast += dt
                    hidden.taking = hidden.sinceCast >= hidden.biteAt &&
                        hidden.sinceCast <= hidden.biteAt + 1.1f
                    if (hidden.sinceCast > hidden.biteAt + 1.1f) {
                        phase = Phase.Ready
                        message = "It let go. Tap to cast again."
                    }
                }

                Phase.Hooked -> {
                    val fight = hidden.species?.fight ?: 1f
                    if (hidden.pulling) {
                        tension += dt * (0.36f + fight * 0.24f)
                        reeled += dt * 0.26f
                    } else {
                        tension -= dt * 0.62f
                        reeled -= dt * 0.04f
                    }
                    tension = tension.coerceIn(0f, 1.2f)
                    reeled = reeled.coerceIn(0f, 1f)

                    if (tension >= 1f) {
                        phase = Phase.Lost
                        message = "Line snapped. Tap to cast again."
                    } else if (reeled >= 1f) {
                        val species = hidden.species ?: SPECIES.first()
                        val weight = weigh(species)
                        caught = species.name to weight
                        val standing = best
                        if (standing == null || weight > standing.second) {
                            best = species.name to weight
                            writeBest(context, species.name, weight)
                        }
                        phase = Phase.Landed
                        message = "${species.name}, ${formatWeight(weight)}. Tap to cast again."
                    }
                }

                else -> Unit
            }
        }
    }

    fun press() {
        when (phase) {
            Phase.Ready, Phase.Landed, Phase.Lost -> {
                power = 0f
                hidden.powerDir = 1f
                hidden.taking = false
                phase = Phase.Power
                message = "Tap again to set the cast"
            }

            Phase.Power -> {
                hidden.sinceCast = 0f
                hidden.biteAt = 0.9f + Random.nextFloat() * 2.6f
                hidden.species = pickSpecies(power)
                phase = Phase.Waiting
                message = "Watch the float"
            }

            Phase.Waiting -> {
                if (hidden.taking) {
                    tension = 0.25f
                    reeled = 0f
                    hidden.pulling = false
                    phase = Phase.Hooked
                    message = "Hold to reel. Keep the line out of the red."
                } else {
                    phase = Phase.Ready
                    message = "Struck too early. Tap to cast again."
                }
            }

            Phase.Hooked -> hidden.pulling = true
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Wordmark(compact = true, height = 44.dp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go back", tint = RrrColors.Bone)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = RrrColors.Bone,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimens.gutter),
        ) {
            Text(
                "Cast & catch",
                style = MaterialTheme.typography.headlineMedium,
                color = RrrColors.Bone,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "Set the cast, strike when the float goes, then play it in " +
                    "without snapping the line. No prizes and no entries, just " +
                    "something to do while you wait for the draw.",
                style = MaterialTheme.typography.bodyMedium,
                color = RrrColors.Mist,
                modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
            )

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.05f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                press()
                                // Reeling is a hold, so the release matters as
                                // much as the press.
                                tryAwaitRelease()
                                hidden.pulling = false
                            },
                        )
                    },
            ) {
                drawScene(
                    phase = phase,
                    power = power,
                    tension = tension,
                    reeled = reeled,
                    taking = hidden.taking,
                    clock = swim,
                )
            }

            Text(
                message,
                style = MaterialTheme.typography.titleSmall,
                color = when (phase) {
                    Phase.Landed -> RrrColors.KhakiBright
                    Phase.Lost -> RrrColors.Danger
                    else -> RrrColors.Bone
                },
                modifier = Modifier.padding(top = 16.dp),
            )

            Spacer(Modifier.height(18.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Stat("Last catch", caught, Modifier.weight(1f))
                Stat("Personal best", best, Modifier.weight(1f))
            }
        }
    }
}

/** Values the frame loop owns. Kept off the snapshot so they do not recompose. */
private class Hidden {
    var powerDir = 1f
    var sinceCast = 0f
    var biteAt = 0f
    var taking = false
    var pulling = false
    var species: Species? = null
}

@Composable
private fun Stat(label: String, value: Pair<String, Float>?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = RrrColors.Slate,
        )
        Text(
            value?.let { "${it.first} · ${formatWeight(it.second)}" } ?: "Nothing yet",
            style = MaterialTheme.typography.titleMedium,
            color = RrrColors.Bone,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun DrawScope.drawScene(
    phase: Phase,
    power: Float,
    tension: Float,
    reeled: Float,
    taking: Boolean,
    clock: Float,
) {
    val w = size.width
    val h = size.height
    val waterTop = h * 0.42f

    drawRect(RrrColors.HeaderTop, size = Size(w, waterTop))
    drawRect(
        Color(0xFF12200F),
        topLeft = Offset(0f, waterTop),
        size = Size(w, h - waterTop),
    )

    // Ripples. The offset per line keeps them from marching in step.
    for (i in 0 until 7) {
        val y = waterTop + 16f + i * (h - waterTop - 30f) / 7f
        val path = Path()
        path.moveTo(0f, y)
        var x = 0f
        while (x <= w) {
            path.lineTo(x, y + sin(x / 46f + clock * 1.1f + i) * 3.4f)
            x += 8f
        }
        drawPath(
            path,
            color = RrrColors.Khaki.copy(alpha = 0.10f + i * 0.012f),
            style = Stroke(width = 1.4f),
        )
    }

    val rodX = w * 0.16f
    val rodBottom = waterTop + 40f
    val rodTop = h * 0.20f
    drawLine(
        color = RrrColors.Mist,
        start = Offset(rodX, rodBottom),
        end = Offset(rodX, rodTop),
        strokeWidth = 5f,
    )

    val reach = if (phase == Phase.Ready || phase == Phase.Power) 0.16f else 0.2f + power * 0.7f
    val floatX = rodX + (w - rodX - 40f) * reach
    val floatY = waterTop + 34f

    val sag = if (phase == Phase.Hooked) 10f + (1f - tension) * 34f else 26f
    // Walked as straight segments rather than a curve primitive: the line has
    // to bend by tension, and stepping the quadratic keeps that in one place.
    val controlX = (rodX + floatX) / 2f
    val controlY = rodTop + sag
    val line = Path()
    line.moveTo(rodX, rodTop)
    for (step in 1..24) {
        val t = step / 24f
        val u = 1f - t
        line.lineTo(
            u * u * rodX + 2f * u * t * controlX + t * t * floatX,
            u * u * rodTop + 2f * u * t * controlY + t * t * floatY,
        )
    }
    drawPath(
        line,
        color = if (phase == Phase.Hooked && tension > 0.75f) RrrColors.Danger else RrrColors.Slate,
        style = Stroke(
            width = if (phase == Phase.Hooked) 2.4f else 1.4f,
        ),
    )

    if (phase != Phase.Ready && phase != Phase.Power) {
        drawCircle(
            color = if (taking) RrrColors.KhakiBright else RrrColors.Khaki,
            radius = if (taking) 9f else 6f,
            center = Offset(floatX, if (taking) floatY + 8f else floatY),
        )
        if (taking) {
            drawCircle(
                color = RrrColors.KhakiBright.copy(alpha = 0.55f),
                radius = 16f + sin(clock * 9f) * 8f,
                center = Offset(floatX, floatY + 8f),
                style = Stroke(width = 2f),
            )
        }
    }

    if (phase == Phase.Hooked) {
        val fishX = floatX - reeled * (floatX - rodX - 30f)
        drawOval(
            color = RrrColors.KhakiBright.copy(alpha = 0.85f),
            topLeft = Offset(fishX - 20f, waterTop + 52f + sin(clock * 7f) * 4f),
            size = Size(40f, 15f),
        )
    }

    if (phase == Phase.Power) {
        meter(w, h - 34f, power, RrrColors.Khaki)
    }

    if (phase == Phase.Hooked) {
        meter(w, h - 62f, tension.coerceAtMost(1f), RrrColors.Danger)
        meter(w, h - 30f, reeled, RrrColors.Khaki)
    }
}

private fun DrawScope.meter(
    width: Float,
    y: Float,
    value: Float,
    colour: Color,
) {
    val left = 24f
    val barWidth = width - 48f
    drawRect(RrrColors.Ink.copy(alpha = 0.8f), Offset(left, y), Size(barWidth, 14f))
    drawRect(colour, Offset(left, y), Size(barWidth * value, 14f))
    drawRect(
        RrrColors.Hairline,
        Offset(left, y),
        Size(barWidth, 14f),
        style = Stroke(width = 1f),
    )
}
