package uk.co.rodrunners.raffles.ui.screens.game

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.rodrunners.raffles.core.Money
import uk.co.rodrunners.raffles.data.model.LeaderboardWeek
import uk.co.rodrunners.raffles.ui.components.Wordmark
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors
import uk.co.rodrunners.raffles.ui.theme.RrrShapes
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
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

/**
 * depth is the whole body depth as a fraction of length: a bream is a slab, a
 * roach is not. scales is the pattern - even and fine on a roach or a common,
 * scattered plates on a mirror, next to nothing on a leather.
 *
 * These are the same figures the website uses, so a fish of a given weight is
 * the same fish on either.
 */
private data class Species(
    val name: String,
    val min: Float,
    val max: Float,
    val fight: Float,
    val depth: Float,
    val scales: String,
    val back: Color,
    val flank: Color,
    val belly: Color,
    val fin: Color,
)

private val SPECIES = listOf(
    Species(
        "Roach", 0.25f, 3.5f, 0.5f, 0.30f, "small",
        Color(0xFF3E4C55), Color(0xFFA9B4B8), Color(0xFFDDE2DE), Color(0xFFB0604C),
    ),
    Species(
        "Tench", 1.2f, 12f, 0.85f, 0.33f, "small",
        Color(0xFF1B2715), Color(0xFF3E5A2B), Color(0xFF7A7F44), Color(0xFF1E2A16),
    ),
    Species(
        "Bream", 1.5f, 18f, 0.7f, 0.44f, "small",
        Color(0xFF332E1F), Color(0xFF8A7C55), Color(0xFFC7C0A6), Color(0xFF2A261B),
    ),
    Species(
        "Leather carp", 5f, 42f, 1.25f, 0.38f, "none",
        Color(0xFF241F13), Color(0xFF5C5330), Color(0xFF8E8256), Color(0xFF2A2416),
    ),
    Species(
        "Common carp", 5f, 50f, 1.05f, 0.36f, "small",
        Color(0xFF33351A), Color(0xFF8B7A36), Color(0xFFC6B478), Color(0xFF4A3F20),
    ),
    Species(
        "Mirror carp", 6f, 60f, 1.2f, 0.385f, "plates",
        Color(0xFF2A2C16), Color(0xFF6F6331), Color(0xFFA2946A), Color(0xFF3A3218),
    ),
)

private const val PREFS = "rr_game"

private enum class Phase { Ready, Power, Waiting, Hooked, Landed, Lost }

/**
 * Bait is a set of odds, not a menu. Every bait can catch every fish here,
 * the way it works on the bank: a carp will take a grain of corn meant for
 * the roach, and a bream will pick up a boilie if it finds one first. What
 * changes is how likely each is, how long you wait, and how often the swim
 * gives you nothing at all.
 *
 * Deliberately unlabelled. Working out what to put on the hook is the part of
 * fishing worth keeping, and a caption saying "use this one for the big ones"
 * throws it away. Same odds and same waits as the website.
 */
private data class Bait(
    val id: String,
    val name: String,
    val note: String,
    val odds: Map<String, Int>,
    val soonest: Float,
    val latest: Float,
    val blank: Float,
)

private val BAITS = listOf(
    Bait(
        "corn", "Sweetcorn", "A grain on the hair",
        mapOf(
            "Roach" to 34, "Tench" to 18, "Bream" to 24,
            "Leather carp" to 2, "Common carp" to 16, "Mirror carp" to 6,
        ),
        2.0f, 6.0f, 0.18f,
    ),
    Bait(
        "worm", "Lobworm", "Lively on the hook",
        mapOf(
            "Roach" to 22, "Tench" to 30, "Bream" to 26,
            "Leather carp" to 2, "Common carp" to 14, "Mirror carp" to 6,
        ),
        2.2f, 6.5f, 0.20f,
    ),
    Bait(
        "boilie", "Boilie", "Rolled, boiled, hard",
        mapOf(
            "Roach" to 2, "Tench" to 8, "Bream" to 6,
            "Leather carp" to 14, "Common carp" to 38, "Mirror carp" to 32,
        ),
        3.5f, 11f, 0.30f,
    ),
)

private fun baitById(id: String): Bait = BAITS.firstOrNull { it.id == id } ?: BAITS.first()

/**
 * Which fish finds the bait. The odds come from what is on the hook; the cast
 * then tilts them, because the far bank is where the older fish sit and a bait
 * dropped short is a bait among the small stuff.
 *
 * The tilt is a multiplier that grows with the species' place in the list, so
 * a long cast does not make small fish impossible - only less likely, which is
 * the honest version.
 */
private fun pickSpecies(power: Float, baitId: String): Species {
    val odds = baitById(baitId).odds
    val weights = SPECIES.mapIndexed { index, species ->
        (odds[species.name] ?: 0) * (1f + power * index * 0.35f)
    }

    var roll = Random.nextFloat() * weights.sum()

    for (i in SPECIES.indices) {
        roll -= weights[i]
        if (roll <= 0f) {
            return SPECIES[i]
        }
    }

    return SPECIES.first()
}

/**
 * How heavy it is.
 *
 * Three random numbers multiplied together, which sounds arbitrary and is not:
 * it produces a curve shaped like a real head of fish. Most of what you catch
 * sits near the bottom of the range, a twenty is a good day, a thirty is a
 * talking point, and the top of the range is a fish most anglers never see. On
 * the carp curve that is roughly one fish in six over twenty pounds, one in
 * twenty over thirty, and a fifty about once in seven hundred.
 *
 * A good cast lifts the whole curve slightly rather than adding pounds to the
 * answer, so distance improves the odds without buying a fish.
 *
 * The same three lines run on the website. They have to stay the same, and the
 * ranges have to stay inside SPECIES_RANGE in gameleaderboard.ts, or the
 * server refuses the fish when it goes to the board.
 */
private fun weigh(species: Species, power: Float): Float {
    val roll = Random.nextFloat() * Random.nextFloat() * Random.nextFloat()
    val lifted = roll.toDouble().pow((1f - power * 0.22f).toDouble()).toFloat()

    return species.min + (species.max - species.min) * lifted
}

/**
 * How big that weight looks. Mass goes with the cube of length, so a fish four
 * times the weight is only about one and a half times as long - but it is a
 * great deal deeper, and depth is what the eye reads as size. Both come off
 * the same number, so a fifty cannot be drawn as a long thin thing.
 */
private data class Build(val length: Float, val depth: Float)

private fun proportions(species: Species, weight: Float): Build {
    val average = (species.min + species.max * 0.35f) / 2f
    val ratio = maxOf(0.2f, weight / maxOf(0.1f, average)).toDouble()

    return Build(
        length = ratio.pow(1.0 / 3.0).toFloat(),
        depth = minOf(1.42f, ratio.pow(1.0 / 5.0).toFloat()),
    )
}

/**
 * The log is one best weight per species, which is how an angler actually
 * keeps score. It also gives the game somewhere to go: six species, six
 * personal bests, and no note anywhere saying how to fill them in.
 */
private fun readLog(context: Context): Map<String, Float> {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    return SPECIES.mapNotNull { species ->
        val weight = prefs.getFloat("best_${species.name}", 0f)
        if (weight > 0f) species.name to weight else null
    }.toMap()
}

private fun writeLog(context: Context, name: String, weight: Float) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putFloat("best_$name", weight)
        .apply()
}

/** The heaviest thing in the log, whatever it was. */
private fun bestOverall(log: Map<String, Float>): Pair<String, Float>? =
    log.maxByOrNull { it.value }?.let { it.key to it.value }

private fun formatWeight(weight: Float): String = String.format(Locale.UK, "%.2f lb", weight)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    onBack: () -> Unit,
    viewModel: GameViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val board by viewModel.board.collectAsStateWithLifecycle()
    val boardNote by viewModel.note.collectAsStateWithLifecycle()

    var phase by remember { mutableStateOf(Phase.Ready) }
    var message by remember { mutableStateOf("Tap the water to cast") }
    var power by remember { mutableFloatStateOf(0f) }
    var tension by remember { mutableFloatStateOf(0f) }
    var reeled by remember { mutableFloatStateOf(0f) }
    var swim by remember { mutableFloatStateOf(0f) }
    var caught by remember { mutableStateOf<Pair<String, Float>?>(null) }
    var log by remember { mutableStateOf(readLog(context)) }
    var bait by remember { mutableStateOf(BAITS.first().id) }
    var landedCount by remember { mutableIntStateOf(0) }
    var lostCount by remember { mutableIntStateOf(0) }
    var sessionWeight by remember { mutableFloatStateOf(0f) }

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
                        hidden.sinceCast <= hidden.biteEnds
                    hidden.dipping = hidden.liners.any {
                        hidden.sinceCast >= it && hidden.sinceCast <= it + 0.26f
                    }

                    if (hidden.sinceCast > hidden.biteEnds) {
                        phase = Phase.Ready
                        hidden.species = null
                        message = "Too slow, it let go. Tap to cast again."
                    } else if (hidden.sinceCast > hidden.blankAt) {
                        phase = Phase.Ready
                        hidden.species = null
                        message = "Nothing doing in that swim. Tap to cast again."
                    }
                }

                Phase.Hooked -> {
                    val species = hidden.species ?: SPECIES.first()
                    val fight = species.fight

                    // How big this one is for its kind, nought to one. A fish
                    // near the top of its range runs harder, runs more often
                    // and gives up less.
                    val heft = (hidden.weight / species.max).coerceIn(0f, 1f)

                    hidden.sinceCast += dt

                    if (hidden.running && hidden.sinceCast >= hidden.runUntil) {
                        hidden.running = false
                        hidden.nextRun = hidden.sinceCast + 0.8f +
                            Random.nextFloat() * (2.4f - heft * 1.2f)
                        message = "Now. Pump it in."
                    } else if (
                        !hidden.running &&
                        hidden.nextRun > 0f &&
                        hidden.sinceCast >= hidden.nextRun &&
                        reeled < 0.9f
                    ) {
                        hidden.running = true
                        hidden.runUntil = hidden.sinceCast + 0.5f +
                            Random.nextFloat() * (0.6f + 1.1f * heft)
                        message = "Running again. Ease off."
                    }

                    // Holding on gains line - but not while it is running,
                    // when all you can do is keep the rod bent and let it go.
                    // That is what playing a big fish is, and it is why a
                    // fifty takes a while to come in.
                    if (hidden.pulling) {
                        tension += dt * (
                            0.40f + fight * 0.28f + if (hidden.running) 0.80f + heft else 0f
                            )
                        reeled += dt * (if (hidden.running) 0.015f else 0.21f - heft * 0.08f)
                    } else {
                        tension -= dt * 0.72f
                        reeled -= dt * (if (hidden.running) 0.15f + heft * 0.15f else 0.045f)
                    }

                    if (hidden.running && !hidden.pulling) {
                        tension += dt * (0.22f + heft * 0.34f)
                    }

                    tension = tension.coerceIn(0f, 1.25f)
                    reeled = reeled.coerceIn(0f, 1f)

                    // Two ways to lose it, pulling against each other. Hold
                    // too hard and the hook comes out; give it too much and it
                    // buries itself in the far bank. Neither is instant -
                    // there is a moment to feel it and put it right, which is
                    // the difference between difficult and unfair.
                    hidden.overFor = if (tension >= 1f) hidden.overFor + dt else 0f
                    hidden.slackFor = if (tension <= 0.10f) hidden.slackFor + dt else 0f

                    if (hidden.overFor > 0.52f || hidden.slackFor > 1.3f) {
                        val pulled = hidden.overFor > 0.52f

                        lostCount += 1
                        hidden.running = false
                        hidden.pulling = false
                        hidden.species = null
                        phase = Phase.Lost
                        message = if (pulled) {
                            "Hook pulled. Tap to cast again."
                        } else {
                            "Slack line, and it found the snags. Tap to cast again."
                        }
                    } else if (reeled >= 1f) {
                        // The weight was settled when the cast went out, so
                        // the fish you fought is the fish you land.
                        val weight = hidden.weight

                        caught = species.name to weight
                        landedCount += 1
                        sessionWeight += weight

                        val standing = log[species.name] ?: 0f
                        val record = weight > standing

                        if (record) {
                            log = log + (species.name to weight)
                            writeLog(context, species.name, weight)
                        }

                        // Onto the weekly board as well as the local log.
                        viewModel.record(species.name, weight)

                        hidden.running = false
                        hidden.pulling = false
                        phase = Phase.Landed
                        message = if (record) {
                            "${species.name}, ${formatWeight(weight)}. Your best yet."
                        } else {
                            "${species.name}, ${formatWeight(weight)}. Tap to cast again."
                        }
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
                hidden.dipping = false
                hidden.species = null
                phase = Phase.Power
                message = "Tap again to set the cast"
            }

            Phase.Power -> {
                val chosen = baitById(bait)
                val species = pickSpecies(power, bait)
                val wait = chosen.soonest + Random.nextFloat() * (chosen.latest - chosen.soonest)

                hidden.sinceCast = 0f
                hidden.species = species
                hidden.weight = weigh(species, power)
                hidden.pulling = false
                hidden.running = false
                hidden.overFor = 0f
                hidden.slackFor = 0f

                hidden.liners = if (Random.nextFloat() < 0.55f) {
                    List(1 + Random.nextInt(2)) {
                        0.5f + Random.nextFloat() * maxOf(0.4f, wait - 0.3f)
                    }.sorted()
                } else {
                    emptyList()
                }

                if (Random.nextFloat() < chosen.blank) {
                    // Nothing is coming to this one. You still have to fish it
                    // out to find that, which is the point of it being there.
                    hidden.biteAt = Float.MAX_VALUE
                    hidden.biteEnds = Float.MAX_VALUE
                    hidden.blankAt = chosen.latest + 1.8f
                } else {
                    hidden.biteAt = wait
                    // A take is a moment, not the second and a bit this used
                    // to allow. A confident fish gives a little longer than a
                    // fussy one.
                    hidden.biteEnds = wait + 0.26f + species.fight * 0.18f
                    hidden.blankAt = Float.MAX_VALUE
                }

                phase = Phase.Waiting
                message = "Watch the float"
            }

            Phase.Waiting -> {
                if (hidden.taking) {
                    val fight = hidden.species?.fight ?: 1f

                    tension = 0.42f
                    reeled = 0f
                    hidden.pulling = false
                    hidden.running = true
                    hidden.sinceCast = 0f
                    hidden.runUntil = 0.7f + Random.nextFloat() * 0.5f * fight
                    hidden.nextRun = 0f
                    hidden.overFor = 0f
                    hidden.slackFor = 0f
                    phase = Phase.Hooked
                    message = "It is running. Give it line."
                } else {
                    val liner = hidden.liners.any {
                        hidden.sinceCast >= it && hidden.sinceCast <= it + 0.26f
                    }

                    phase = Phase.Ready
                    hidden.species = null
                    message = when {
                        liner -> "Liner - nothing on the hook. Tap to cast again."
                        else -> "Struck at nothing. Tap to cast again."
                    }
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutter),
        ) {
            Text(
                "Cast & catch",
                style = MaterialTheme.typography.headlineMedium,
                color = RrrColors.Bone,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "Pick a bait, set the cast, strike when the float goes, then " +
                    "play it in without snapping the line. No prizes and no " +
                    "entries, just something to do while you wait for the draw.",
                style = MaterialTheme.typography.bodyMedium,
                color = RrrColors.Mist,
                modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BAITS.forEach { item ->
                    BaitCard(
                        bait = item,
                        selected = item.id == bait,
                        // Changing bait mid-cast would mean the fish already
                        // chosen no longer matches what is on the hook.
                        enabled = phase != Phase.Waiting && phase != Phase.Hooked,
                        modifier = Modifier.weight(1f),
                        onClick = { bait = item.id },
                    )
                }
            }

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.88f)
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
                    species = hidden.species,
                    power = power,
                    tension = tension,
                    reeled = reeled,
                    taking = hidden.taking || hidden.dipping,
                    weight = hidden.weight,
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
                Stat("Personal best", bestOverall(log), Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "THIS SESSION",
                        style = MaterialTheme.typography.labelSmall,
                        color = RrrColors.Slate,
                    )
                    Text(
                        if (landedCount == 0) {
                            "Nothing yet"
                        } else {
                            "$landedCount landed · ${formatWeight(sessionWeight)}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = RrrColors.Bone,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        "LINE SNAPPED",
                        style = MaterialTheme.typography.labelSmall,
                        color = RrrColors.Slate,
                    )
                    Text(
                        lostCount.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = RrrColors.Bone,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            WeeklyBoard(board, boardNote)

            Spacer(Modifier.height(26.dp))

            Text(
                "The log",
                style = MaterialTheme.typography.titleLarge,
                color = RrrColors.Bone,
            )
            Text(
                "Your best of each. The last two only come to a boilie, and " +
                    "only on a long cast.",
                style = MaterialTheme.typography.bodySmall,
                color = RrrColors.Mist,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            SPECIES.forEach { species ->
                LogRow(species, log[species.name])
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

/** Values the frame loop owns. Kept off the snapshot so they do not recompose. */
private class Hidden {
    var powerDir = 1f
    var sinceCast = 0f

    /** When the take starts and ends, in seconds since the cast landed. */
    var biteAt = 0f
    var biteEnds = 0f

    /** When to give up on a swim that was never going to produce anything. */
    var blankAt = Float.MAX_VALUE

    /**
     * Liners: a fish brushing the line rather than taking the bait. Each entry
     * is the second it starts, and it moves the float exactly the way a bite
     * does. Striking one costs the cast, and nothing on screen tells them
     * apart - which is the difficulty of float fishing, put into the game.
     */
    var liners: List<Float> = emptyList()

    var taking = false
    var dipping = false
    var pulling = false

    /** A hooked fish runs, and while it runs you cannot gain any line. */
    var running = false
    var runUntil = 0f
    var nextRun = 0f

    /** How long the line has been over-loaded or slack, for the two ways to lose it. */
    var overFor = 0f
    var slackFor = 0f

    var species: Species? = null

    /** Settled when the cast goes out, so the fish you play is the fish you land. */
    var weight = 0f
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

/**
 * The weekly board. Heaviest single fish wins ten pounds of site credit,
 * settled automatically on Monday morning.
 */
@Composable
private fun WeeklyBoard(board: LeaderboardWeek, note: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "This week",
            style = MaterialTheme.typography.titleLarge,
            color = RrrColors.Bone,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "${Money.formatCompact(board.prizePence)} SITE CREDIT",
            style = MaterialTheme.typography.labelSmall,
            color = RrrColors.Ink,
            modifier = Modifier
                .clip(RrrShapes.small)
                .background(RrrColors.Khaki)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }

    Text(
        "Heaviest single fish of the week wins. Paid into your account " +
            "automatically on Monday morning, and the board starts again.",
        style = MaterialTheme.typography.bodySmall,
        color = RrrColors.Mist,
        modifier = Modifier.padding(top = 6.dp),
    )

    if (note.isNotBlank()) {
        Text(
            note,
            style = MaterialTheme.typography.labelLarge,
            color = RrrColors.KhakiBright,
            modifier = Modifier.padding(top = 10.dp),
        )
    }

    Spacer(Modifier.height(12.dp))

    if (board.rows.isEmpty()) {
        Text(
            "Nobody has landed one yet this week. First fish takes the lead.",
            style = MaterialTheme.typography.bodySmall,
            color = RrrColors.Mist,
        )
    }

    board.rows.forEach { row ->
        val yours = row.userId == board.you

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RrrShapes.small)
                .background(if (yours) RrrColors.KhakiDim else RrrColors.Surface)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                row.position.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = if (row.position == 1) RrrColors.KhakiBright else RrrColors.Slate,
                modifier = Modifier.width(26.dp),
            )
            Text(
                row.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = RrrColors.Bone,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                row.species,
                style = MaterialTheme.typography.labelSmall,
                color = RrrColors.Slate,
                modifier = Modifier.padding(end = 10.dp),
            )
            Text(
                formatWeight(row.weightLb),
                style = MaterialTheme.typography.labelLarge,
                color = RrrColors.KhakiBright,
            )
        }
    }

    board.lastWeek?.let { last ->
        if (last.displayName.isNotBlank()) {
            Text(
                "Last week: ${last.displayName} with a " +
                    "${formatWeight(last.weightLb)} ${last.species.lowercase()}.",
                style = MaterialTheme.typography.bodySmall,
                color = RrrColors.Mist,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun BaitCard(
    bait: Bait,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RrrShapes.medium)
            .background(if (selected) RrrColors.Khaki else RrrColors.Surface)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(vertical = 12.dp, horizontal = 10.dp),
    ) {
        Text(
            bait.name,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) RrrColors.Ink else RrrColors.Bone,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            bait.note,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) RrrColors.Ink.copy(alpha = 0.75f) else RrrColors.Mist,
        )
    }
}

/** One species in the log: a colour swatch, the name, and your best of it. */
@Composable
private fun LogRow(species: Species, weight: Float?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RrrShapes.small)
            .background(RrrColors.Surface)
            .alpha(if (weight == null) 0.5f else 1f)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(species.flank),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            species.name,
            style = MaterialTheme.typography.bodyMedium,
            color = RrrColors.Bone,
            modifier = Modifier.weight(1f),
        )
        Text(
            weight?.let { formatWeight(it) } ?: "Not caught",
            style = MaterialTheme.typography.labelLarge,
            color = if (weight == null) RrrColors.Slate else RrrColors.KhakiBright,
        )
    }
}

/* =========================================================
   DRAWING
========================================================= */

/**
 * Compose's Path has cubicTo but its quadratic call has been renamed between
 * versions, and Path does not expose the point it is currently at. This keeps
 * the current point itself and converts each quadratic to the cubic that draws
 * exactly the same curve, so the shapes below do not depend on which Compose
 * the app is built against.
 */
private class Pen {
    val path = Path()
    private var x = 0f
    private var y = 0f

    fun moveTo(toX: Float, toY: Float) {
        path.moveTo(toX, toY)
        x = toX
        y = toY
    }

    fun lineTo(toX: Float, toY: Float) {
        path.lineTo(toX, toY)
        x = toX
        y = toY
    }

    fun quadTo(controlX: Float, controlY: Float, toX: Float, toY: Float) {
        path.cubicTo(
            x + 2f / 3f * (controlX - x),
            y + 2f / 3f * (controlY - y),
            toX + 2f / 3f * (controlX - toX),
            toY + 2f / 3f * (controlY - toY),
            toX,
            toY,
        )
        x = toX
        y = toY
    }

    fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, toX: Float, toY: Float) {
        path.cubicTo(c1x, c1y, c2x, c2y, toX, toY)
        x = toX
        y = toY
    }

    fun close() {
        path.close()
    }
}

private class Tree(val x: Float, val height: Float, val width: Float)
private class Reed(val x: Float, val height: Float, val lean: Float, val depth: Float, val head: Boolean)
private class Lily(val x: Float, val depth: Float, val size: Float)

/**
 * The far bank is generated once from a fixed sequence rather than from
 * Random, so it is the same bank every frame instead of boiling.
 */
private object Scenery {
    val trees: List<Tree>
    val reeds: List<Reed>
    val lilies: List<Lily>

    init {
        var seed = 20250829L
        fun next(): Float {
            seed = (seed * 1664525 + 1013904223) and 0xFFFFFFFFL
            return seed.toFloat() / 4294967296f
        }

        trees = buildList {
            var x = -0.04f
            while (x < 1.06f) {
                add(
                    Tree(
                        x = x,
                        height = (if (next() > 0.78f) 0.75f else 0.18f) + next() * 0.46f,
                        width = 0.55f + next() * 1.15f,
                    )
                )
                x += 0.028f
            }
        }

        // Reeds belong at the edges of the swim and along the far bank, not
        // dotted across open water; anything else reads as telegraph poles.
        reeds = buildList {
            for (i in 0 until 34) {
                val edge = next()
                val bank = i % 3 == 0
                add(
                    Reed(
                        x = if (edge < 0.5f) edge * 0.20f else 1f - (1f - edge) * 0.22f,
                        height = 0.10f + next() * 0.22f,
                        lean = next() * 0.5f - 0.25f,
                        depth = if (bank) next() * 0.16f else 0.45f + next() * 0.55f,
                        head = next() > 0.5f,
                    )
                )
            }
        }

        lilies = buildList {
            for (i in 0 until 7) {
                add(Lily(0.06f + next() * 0.88f, next(), 0.6f + next() * 0.7f))
            }
        }
    }
}

private fun DrawScope.drawScene(
    phase: Phase,
    species: Species?,
    power: Float,
    tension: Float,
    reeled: Float,
    taking: Boolean,
    weight: Float,
    clock: Float,
) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.34f

    drawSky(w, h, horizon)
    drawTreeline(w, horizon)
    drawWater(w, h, horizon, clock)

    val cast = if (phase == Phase.Ready || phase == Phase.Power) 0.14f else 0.22f + power * 0.66f
    val floatX = w * (0.22f + cast * 0.68f)
    val floatY = horizon + (h - horizon) * (0.62f - cast * 0.40f)
    val distance = (floatY - horizon) / (h - horizon)

    drawLilies(w, h, horizon, clock)

    val mouth = drawFish(phase, species, weight, w, h, floatX, floatY, distance, reeled, clock)

    drawRodAndLine(phase, tension, w, h, mouth?.x ?: floatX, mouth?.y ?: floatY, clock)

    if (mouth == null) {
        drawFloat(phase, floatX, floatY, distance, taking, clock)
    }

    drawReeds(w, h, horizon, clock)

    if (phase == Phase.Power) {
        meter(34f, h - 44f, w - 68f, 16f, power, RrrColors.Khaki)
    }

    if (phase == Phase.Hooked) {
        meter(34f, h - 74f, w - 68f, 15f, tension.coerceAtMost(1f), RrrColors.Danger)
        meter(34f, h - 40f, w - 68f, 15f, reeled, RrrColors.Khaki)
    }
}

private fun DrawScope.drawSky(w: Float, h: Float, horizon: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color(0xFF0C1119),
            0.55f to Color(0xFF1B2530),
            0.86f to Color(0xFF41443A),
            1f to Color(0xFF6E5F42),
            startY = 0f,
            endY = horizon,
        ),
        size = Size(w, horizon),
    )

    val sun = Offset(w * 0.74f, horizon - h * 0.045f)

    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0x8CE2C484),
                Color(0x2EB49860),
                Color(0x00B49860),
            ),
            center = sun,
            radius = h * 0.30f,
        ),
        size = Size(w, horizon + h * 0.10f),
    )

    drawCircle(Color(0xD9EED8A8), h * 0.028f, sun)

    // Mist on the water: the thing that makes a picture read as dawn rather
    // than as dusk.
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color(0x00BEBAA6),
            0.6f to Color(0x29BEBAA6),
            1f to Color(0x0DBEBAA6),
            startY = horizon - h * 0.08f,
            endY = horizon + h * 0.03f,
        ),
        topLeft = Offset(0f, horizon - h * 0.08f),
        size = Size(w, h * 0.11f),
    )
}

private fun DrawScope.drawTreeline(w: Float, horizon: Float) {
    val wood = Color(0xFF0E1511)

    Scenery.trees.forEachIndexed { i, tree ->
        val x = tree.x * w
        val top = tree.height * horizon * 0.42f
        val spread = 12f * tree.width

        drawRect(wood, Offset(x - 1.4f, horizon - top * 0.5f), Size(2.8f, top * 0.5f))
        drawOval(
            wood,
            Offset(x - spread, horizon - top * 0.62f - top * 0.44f),
            Size(spread * 2, top * 0.88f),
        )
        drawOval(
            wood,
            Offset(
                x + (if (i % 2 == 0) -spread * 0.5f else spread * 0.5f) - spread * 0.72f,
                horizon - top * 0.40f - top * 0.32f,
            ),
            Size(spread * 1.44f, top * 0.64f),
        )
    }

    drawRect(Color(0xFF0A0D09), Offset(0f, horizon - 3f), Size(w, 6f))
    drawRect(Color(0x8C0C120D), Offset(0f, horizon + 3f), Size(w, 14f))
}

private fun DrawScope.drawWater(w: Float, h: Float, horizon: Float, t: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color(0xFF26302A),
            0.18f to Color(0xFF1A231C),
            0.6f to Color(0xFF121A14),
            1f to Color(0xFF0A100C),
            startY = horizon,
            endY = h,
        ),
        topLeft = Offset(0f, horizon),
        size = Size(w, h - horizon),
    )

    // The sun's path, broken into rows that widen as they come towards you.
    // That widening is what makes it read as a reflection rather than a beam.
    val sunX = w * 0.74f

    for (i in 0 until 34) {
        val p = i / 34f
        val y = horizon + (h - horizon) * p * p
        val spread = 6f + p * 120f
        val wobble = sin(t * 1.6f + i * 0.9f) * (2f + p * 26f)
        val height = 1.6f + p * 3f

        drawOval(
            Color(0xFFE2C484).copy(alpha = 0.15f * (1f - p * 0.75f)),
            Offset(sunX + wobble - spread / 2f, y - height / 2f),
            Size(spread, height),
        )
    }

    // Wave lines. Amplitude and spacing both grow with distance from the
    // horizon, which is the whole trick to perspective on flat water.
    for (i in 0 until 30) {
        val p = i / 30f
        val y = horizon + (h - horizon) * (p * p * 0.94f + 0.02f)
        val amp = 1f + p * 7f
        val step = 10f + p * 22f
        val pen = Pen()
        var x = 0f
        var first = true

        while (x <= w + step) {
            val yy = y + sin(x / (36f + p * 70f) + t * (0.7f + p * 0.5f) + i) * amp

            if (first) {
                pen.moveTo(x, yy)
                first = false
            } else {
                pen.lineTo(x, yy)
            }

            x += step
        }

        drawPath(
            pen.path,
            Color(0xFF96A884).copy(alpha = 0.05f + p * 0.09f),
            style = Stroke(width = 0.8f + p * 1.1f),
        )
    }
}

private fun DrawScope.drawLilies(w: Float, h: Float, horizon: Float, t: Float) {
    Scenery.lilies.forEachIndexed { i, lily ->
        val y = horizon + (h - horizon) * (0.30f + lily.depth * 0.55f)
        val p = (y - horizon) / (h - horizon)
        val rx = (10f + p * 34f) * lily.size
        val ry = rx * 0.34f
        val bob = sin(t * 1.1f + i) * (0.5f + p * 2f)
        val cx = lily.x * w
        val cy = y + bob

        drawOval(
            Color(0x59000000),
            Offset(cx - rx + 2f, cy + ry * 0.5f - ry * 0.7f),
            Size(rx * 2, ry * 1.4f),
        )

        // The notch is what tells you it is a lily pad and not a stone.
        val pen = Pen()
        pen.moveTo(cx, cy)
        pen.lineTo(cx + rx * cos(0.5f), cy + ry * sin(0.5f))
        for (step in 1..40) {
            val a = 0.5f + (2 * PI.toFloat() - 0.4f) * step / 40f
            pen.lineTo(cx + rx * cos(a), cy + ry * sin(a))
        }
        pen.close()

        drawPath(pen.path, if (i % 3 == 0) Color(0xFF2E3B22) else Color(0xFF38472A))
        drawPath(pen.path, Color(0x598CA06E), style = Stroke(width = 0.8f))
    }
}

private fun DrawScope.drawReeds(w: Float, h: Float, horizon: Float, t: Float) {
    Scenery.reeds.forEachIndexed { i, reed ->
        val base = horizon + (h - horizon) * (0.05f + reed.depth * reed.depth * 1.02f)
        val p = (base - horizon) / (h - horizon)
        val height = reed.height * h * (0.4f + p * 1.5f)
        val x = reed.x * w
        val sway = sin(t * 0.9f + i * 1.3f) * (2f + p * 9f)

        val pen = Pen()
        pen.moveTo(x, base)
        pen.quadTo(x + sway * 0.4f, base - height * 0.6f, x + sway, base - height)

        drawPath(
            pen.path,
            Color(0xFF0E140D).copy(alpha = 0.55f + p * 0.45f),
            style = Stroke(width = 1.2f + p * 3f),
        )

        if (reed.head) {
            val hw = 1.6f + p * 3.4f
            val hh = 6f + p * 13f

            drawOval(
                Color(0xFF1E2618).copy(alpha = 0.6f + p * 0.4f),
                Offset(x + sway - hw, base - height - hh),
                Size(hw * 2, hh * 2),
            )
        }
    }
}

/**
 * A carp, drawn nose-first along the positive x axis and then placed by the
 * caller. The tail is a separate transform hinged at the wrist, which is what
 * makes it beat rather than slide.
 *
 * depth is the whole body depth, so the outline is built from half of it.
 * Using the whole figure for each half makes a carp into a dinner plate.
 */
private fun DrawScope.drawCarp(
    species: Species,
    length: Float,
    flex: Float,
    sink: Float,
    depthMul: Float = 1f,
) {
    // depthMul carries the weight of this particular fish. A forty is not
    // just a longer twenty - it is a far deeper one, and depth is what the
    // eye reads as size. Without it every carp looked identical whatever the
    // scales said, which made the number under the fish meaningless.
    val d = length * species.depth * depthMul * 0.5f
    val edge = maxOf(0.6f, length * 0.006f)

    val body = Pen()
    body.moveTo(0f, 0f)
    body.cubicTo(length * 0.16f, -d * 0.62f, length * 0.34f, -d * 0.98f, length * 0.50f, -d * 0.96f)
    body.cubicTo(length * 0.66f, -d * 0.94f, length * 0.76f, -d * 0.52f, length * 0.83f, -d * 0.24f)
    body.cubicTo(length * 0.86f, -d * 0.14f, length * 0.86f, d * 0.14f, length * 0.83f, d * 0.24f)
    body.cubicTo(length * 0.74f, d * 0.66f, length * 0.60f, d * 0.92f, length * 0.44f, d * 0.90f)
    body.cubicTo(length * 0.26f, d * 0.88f, length * 0.10f, d * 0.50f, 0f, 0f)
    body.close()

    drawPath(
        body.path,
        brush = Brush.verticalGradient(
            0f to species.back,
            0.42f to species.flank,
            0.78f to species.belly,
            1f to species.belly,
            startY = -d,
            endY = d,
        ),
    )

    clipPath(body.path) {
        if (species.scales == "small") {
            for (col in 0 until 11) {
                for (row in -3..3) {
                    val x = length * (0.14f + col * 0.065f)
                    val y = d * row * 0.26f + sin(col.toFloat()) * d * 0.03f
                    val r = length * 0.030f

                    drawArc(
                        color = Color(0x38000000),
                        startAngle = -63f,
                        sweepAngle = 126f,
                        useCenter = false,
                        topLeft = Offset(x - r, y - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(width = maxOf(0.5f, length * 0.004f)),
                    )
                }
            }
        }

        if (species.scales == "plates") {
            // A mirror's scaling is scattered plates, heaviest along the back
            // and the lateral line. Fixed positions, not random, so it does
            // not shimmer between frames.
            val plates = listOf(
                0.22f to -0.72f, 0.31f to -0.80f, 0.40f to -0.74f, 0.50f to -0.82f,
                0.60f to -0.66f, 0.28f to -0.10f, 0.42f to 0.02f, 0.56f to -0.06f,
                0.68f to -0.18f, 0.36f to 0.44f,
            )

            plates.forEachIndexed { i, (px, py) ->
                val rx = length * 0.036f
                val ry = d * 0.20f
                val at = Offset(length * px - rx, d * py - ry)

                drawOval(
                    if (i % 2 == 0) Color(0x5CBAB086) else Color(0x6BD6CEA6),
                    at,
                    Size(rx * 2, ry * 2),
                )
                drawOval(Color(0x59000000), at, Size(rx * 2, ry * 2), style = Stroke(edge * 0.7f))
            }
        }

        val line = Pen()
        line.moveTo(length * 0.10f, -d * 0.06f)
        line.quadTo(length * 0.45f, d * 0.10f, length * 0.82f, d * 0.02f)
        drawPath(line.path, Color(0x47000000), style = Stroke(edge))

        // Wet highlight along the top of the back.
        drawPath(
            body.path,
            brush = Brush.verticalGradient(
                0f to Color(0x29FFFFFF),
                1f to Color(0x00FFFFFF),
                startY = -d,
                endY = 0f,
            ),
        )
    }

    drawPath(body.path, Color(0x80000000), style = Stroke(width = maxOf(0.7f, length * 0.007f)))

    // Fins are close in tone to the water they are in, so they get a pale
    // edge. Without it the dorsal simply disappears and the fish looks finless.
    val finEdge = Color(0x4DE2E8D0)

    val dorsal = Pen()
    dorsal.moveTo(length * 0.34f, -d * 0.94f)
    dorsal.quadTo(length * 0.50f, -d * 1.62f, length * 0.72f, -d * 0.62f)
    dorsal.lineTo(length * 0.70f, -d * 0.60f)
    dorsal.quadTo(length * 0.52f, -d * 1.20f, length * 0.36f, -d * 0.86f)
    dorsal.close()
    drawPath(dorsal.path, species.fin)
    drawPath(dorsal.path, finEdge, style = Stroke(edge))

    val pectoral = Pen()
    pectoral.moveTo(length * 0.24f, d * 0.42f)
    pectoral.quadTo(length * 0.30f, d * 1.02f, length * 0.14f, d * 0.86f)
    pectoral.quadTo(length * 0.18f, d * 0.60f, length * 0.24f, d * 0.42f)
    pectoral.close()
    drawPath(pectoral.path, species.fin)
    drawPath(pectoral.path, finEdge, style = Stroke(edge))

    val pelvic = Pen()
    pelvic.moveTo(length * 0.50f, d * 0.88f)
    pelvic.quadTo(length * 0.52f, d * 1.32f, length * 0.38f, d * 1.10f)
    pelvic.close()
    drawPath(pelvic.path, species.fin)
    drawPath(pelvic.path, finEdge, style = Stroke(edge))

    val anal = Pen()
    anal.moveTo(length * 0.72f, d * 0.52f)
    anal.quadTo(length * 0.72f, d * 1.06f, length * 0.60f, d * 0.86f)
    anal.close()
    drawPath(anal.path, species.fin)
    drawPath(anal.path, finEdge, style = Stroke(edge))

    // Tail, hinged at the wrist so it sweeps rather than slides.
    withTransform({
        translate(length * 0.84f, 0f)
        rotate(flex * 180f / PI.toFloat(), Offset.Zero)
    }) {
        val tail = Pen()
        tail.moveTo(0f, -d * 0.20f)
        tail.quadTo(length * 0.18f, -d * 0.40f, length * 0.26f, -d * 1.20f)
        tail.quadTo(length * 0.16f, -d * 0.34f, length * 0.12f, 0f)
        tail.quadTo(length * 0.16f, d * 0.34f, length * 0.26f, d * 1.20f)
        tail.quadTo(length * 0.18f, d * 0.40f, 0f, d * 0.20f)
        tail.close()
        drawPath(tail.path, species.fin)
        drawPath(tail.path, Color(0x47E2E8D0), style = Stroke(maxOf(0.5f, length * 0.005f)))
    }

    val gill = Pen()
    gill.moveTo(length * 0.15f, -d * 0.62f)
    gill.quadTo(length * 0.19f, 0f, length * 0.14f, d * 0.52f)
    drawPath(gill.path, Color(0x59000000), style = Stroke(edge))

    val mouth = Pen()
    mouth.moveTo(length * 0.005f, d * 0.02f)
    mouth.quadTo(length * 0.045f, d * 0.16f, length * 0.085f, d * 0.11f)
    drawPath(mouth.path, Color(0x8C000000), style = Stroke(edge))

    // Barbels. Only the carp family has them, and they are what makes a
    // drawing of a carp look like a carp rather than a generic fish.
    if (species.name.contains("carp") || species.name == "Tench") {
        val barbelA = Pen()
        barbelA.moveTo(length * 0.03f, d * 0.10f)
        barbelA.quadTo(length * 0.06f, d * 0.34f, length * 0.13f, d * 0.36f)
        drawPath(barbelA.path, Color(0x80000000), style = Stroke(maxOf(0.5f, length * 0.004f)))

        val barbelB = Pen()
        barbelB.moveTo(length * 0.06f, d * 0.14f)
        barbelB.quadTo(length * 0.10f, d * 0.42f, length * 0.19f, d * 0.44f)
        drawPath(barbelB.path, Color(0x80000000), style = Stroke(maxOf(0.5f, length * 0.004f)))
    }

    val eye = Offset(length * 0.115f, -d * 0.30f)
    drawCircle(Color(0xFFC6B87E), length * 0.021f, eye)
    drawCircle(Color(0xFF0B0C0A), length * 0.012f, eye)
    drawCircle(Color(0xB3FFFFFF), length * 0.005f, Offset(eye.x - length * 0.005f, eye.y - d * 0.06f))

    // Everything below the surface loses contrast with depth.
    if (sink > 0f) {
        drawPath(body.path, Color(0xFF101A12).copy(alpha = sink))
    }
}

private fun DrawScope.drawFish(
    phase: Phase,
    species: Species?,
    weight: Float,
    w: Float,
    h: Float,
    floatX: Float,
    floatY: Float,
    distance: Float,
    reeled: Float,
    t: Float,
): Offset? {
    if (species == null) {
        return null
    }

    // How this particular fish is built, from what it actually weighs.
    val build = proportions(species, weight)

    if (phase == Phase.Waiting) {
        // Only a shape moving under the surface, which is all you would see -
        // and it is the true size of the fish down there, so a good one is
        // worth spotting before it ever takes.
        val x = floatX - 50f + sin(t * 0.8f) * 26f
        val y = floatY + 22f + sin(t * 1.3f) * 4f
        val length = (46f + distance * 54f) * build.length

        withTransform({ translate(x, y) }) {
            drawCarp(species, length, sin(t * 3f) * 0.28f, 0.78f, build.depth)
        }

        return null
    }

    if (phase != Phase.Hooked && phase != Phase.Landed) {
        return null
    }

    // Reeled in, the fish travels from where it took towards the rod and rises
    // towards the surface, so it gets nearer, larger and clearer at once.
    val progress = if (phase == Phase.Landed) 1f else reeled
    val rodX = w * 0.13f
    val x = floatX + (rodX + 90f - floatX) * progress
    val surface = floatY + 20f
    val y = surface + (h * 0.74f - surface) * progress * 0.62f
    val length = (54f + distance * 56f) * (1f + progress * 0.85f) * build.length
    val beat = sin(t * (7f - progress * 2f)) * (0.34f - progress * 0.12f)

    // A played fish comes in head-up and rolling; a landed one is held
    // flank-on. Both are small angles - a fish standing on its nose looks
    // like a mistake, because it is one.
    val roll = if (phase == Phase.Landed) -0.10f else -0.14f + sin(t * 2.2f) * 0.09f

    // A fish being played thrashes at the surface, and the water shows it.
    if (progress > 0.45f) {
        for (i in 0 until 3) {
            val r = 16f + i * 22f + (t * 60f) % 40f

            drawOval(
                Color(0xFFD2E0BA).copy(alpha = (progress - 0.45f) * 0.6f),
                Offset(x - r, y - length * 0.1f - r * 0.30f),
                Size(r * 2, r * 0.60f),
                style = Stroke(width = 1.6f),
            )
        }
    }

    withTransform({
        translate(x, y)
        rotate(roll * 180f / PI.toFloat(), Offset.Zero)
    }) {
        drawCarp(species, length, beat, maxOf(0f, 0.50f - progress * 0.50f), build.depth)
    }

    // The nose is the origin the fish is drawn from, so it is also where the
    // hook is and where the line has to end.
    return Offset(x, y)
}

private fun DrawScope.drawRodAndLine(
    phase: Phase,
    tension: Float,
    w: Float,
    h: Float,
    endX: Float,
    endY: Float,
    t: Float,
) {
    val buttX = w * 0.055f
    val buttY = h * 0.99f
    val tipX = w * 0.15f
    val bend = if (phase == Phase.Hooked) tension * 0.9f else 0.06f
    val tipY = h * 0.30f + bend * h * 0.10f
    val pullX = tipX + bend * (endX - tipX) * 0.16f

    // The blank is drawn as a taper - two edges meeting at the tip - because a
    // constant-width line looks like a stick, not a rod.
    val blank = Pen()
    blank.moveTo(buttX - 5f, buttY)
    blank.quadTo((buttX + pullX) / 2f - 3.4f, (buttY + tipY) / 2f, pullX - 0.9f, tipY)
    blank.lineTo(pullX + 0.9f, tipY)
    blank.quadTo((buttX + pullX) / 2f + 3.4f, (buttY + tipY) / 2f, buttX + 5f, buttY)
    blank.close()

    drawPath(blank.path, Color(0xFF171C13))
    drawPath(blank.path, Color(0x33BEC8A8), style = Stroke(width = 1f))

    for (i in 1..5) {
        val p = i / 6f
        val x = buttX + (pullX - buttX) * (1f - p) + (p * p - p) * 6f
        val y = buttY + (tipY - buttY) * (1f - p)
        val rx = 2.2f + p * 3.4f
        val ry = 3f + p * 4.4f

        drawOval(
            Color(0x8C9B9B7E),
            Offset(x - rx, y - ry),
            Size(rx * 2, ry * 2),
            style = Stroke(width = 1.3f),
        )
    }

    val reel = Offset(buttX + 14f, buttY - h * 0.14f)
    drawOval(Color(0xFF20261A), Offset(reel.x - 13f, reel.y - 17f), Size(26f, 34f))
    drawOval(
        Color(0x739B9B7E),
        Offset(reel.x - 13f, reel.y - 17f),
        Size(26f, 34f),
        style = Stroke(width = 1.4f),
    )
    drawOval(Color(0x479B9B7E), Offset(reel.x - 5f, reel.y - 7f), Size(10f, 14f))

    // Slack hangs; under tension the line straightens and goes pale, then red.
    val sag = if (phase == Phase.Hooked) {
        12f + (1f - tension) * 46f
    } else {
        34f + sin(t * 0.9f) * 3f
    }

    val line = Pen()
    line.moveTo(pullX, tipY)
    line.quadTo((pullX + endX) / 2f, tipY + sag, endX, endY)

    drawPath(
        line.path,
        if (phase == Phase.Hooked && tension > 0.78f) {
            Color(0xE6DE6152)
        } else {
            Color(0x80E2E8D0)
        },
        style = Stroke(width = if (phase == Phase.Hooked) 1.6f else 1f),
    )
}

private fun DrawScope.drawFloat(
    phase: Phase,
    x: Float,
    y: Float,
    distance: Float,
    taking: Boolean,
    t: Float,
) {
    if (phase == Phase.Ready || phase == Phase.Power) {
        return
    }

    val scale = 1.0f + distance * 1.1f
    val dip = if (taking) 10f * scale else sin(t * 1.6f) * 1.4f
    val cy = y + dip
    val rings = if (taking) 4 else 2

    for (i in 0 until rings) {
        val age = (t * (if (taking) 1.6f else 0.5f) + i.toFloat() / rings) % 1f
        val rx = (8f + age * 46f) * scale
        val ry = (2.6f + age * 15f) * scale

        drawOval(
            Color(0xFFCEDEB2).copy(alpha = (if (taking) 0.5f else 0.22f) * (1f - age)),
            Offset(x - rx, cy + 3f * scale - ry),
            Size(rx * 2, ry * 2),
            style = Stroke(width = 1.2f),
        )
    }

    // A waggler: black body in the water, bright tip above it.
    drawOval(
        Color(0xFF0D110C),
        Offset(x - 2.6f * scale, cy + 2f * scale - 7f * scale),
        Size(5.2f * scale, 14f * scale),
    )
    drawRect(
        if (taking) Color(0xFFE9A25E) else Color(0xFF9B9B7E),
        Offset(x - 1.5f * scale, cy - 14f * scale),
        Size(3f * scale, 13f * scale),
    )
    drawRect(
        Color(0xFF0B0C0A),
        Offset(x - 1.5f * scale, cy - 6f * scale),
        Size(3f * scale, 2.4f * scale),
    )
}

private fun DrawScope.meter(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    value: Float,
    colour: Color,
) {
    drawRect(Color(0xB8060906), Offset(x, y), Size(width, height))
    drawRect(colour, Offset(x, y), Size(width * value.coerceIn(0f, 1f), height))
    drawRect(Color(0x47E2E8D0), Offset(x, y), Size(width, height), style = Stroke(width = 1f))
}
