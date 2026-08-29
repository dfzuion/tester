package uk.co.rodrunners.raffles.ui.screens.spin

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.rodrunners.raffles.core.Money
import uk.co.rodrunners.raffles.data.model.SPIN_SEGMENTS
import uk.co.rodrunners.raffles.ui.components.GoldButton
import uk.co.rodrunners.raffles.ui.components.Wordmark
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors

/**
 * One spin a day for site credit.
 *
 * The wheel is equal slices so every prize is equally easy to read, which is
 * deliberately not the same as equally likely - the odds are weights on the
 * server. Nothing here decides a prize; it animates one that has already been
 * granted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpinScreen(
    onBack: () -> Unit,
    viewModel: SpinViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val landed = state.today
    val index = SPIN_SEGMENTS.indexOfFirst { it.first == (landed?.pence ?: -1) }
    val slice = 360f / SPIN_SEGMENTS.size
    val target = if (landed != null && index >= 0) {
        6 * 360f - (index * slice + slice / 2f)
    } else {
        0f
    }
    val angle by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (state.revealed != null) 4000 else 0),
        label = "wheel",
    )

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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Daily spin",
                style = MaterialTheme.typography.headlineMedium,
                color = RrrColors.Bone,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "One spin a day, worth up to £2 of site credit. " +
                    "Credit buys entries into any raffle. Resets at midnight.",
                style = MaterialTheme.typography.bodyMedium,
                color = RrrColors.Mist,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )

            Box(contentAlignment = Alignment.Center) {
                Canvas(
                    Modifier
                        .size(240.dp)
                        .graphicsLayer { rotationZ = angle }
                ) {
                    val sweep = 360f / SPIN_SEGMENTS.size
                    SPIN_SEGMENTS.forEachIndexed { i, _ ->
                        drawArc(
                            color = if (i % 2 == 0) RrrColors.Surface else RrrColors.SurfaceRaised,
                            startAngle = i * sweep - 90f,
                            sweepAngle = sweep,
                            useCenter = true,
                        )
                    }
                }
                Canvas(Modifier.size(16.dp)) {
                    drawCircle(color = RrrColors.Khaki)
                }
            }

            // A plain Row rather than FlowRow: FlowRow is still an experimental
            // API and nothing else in the app opts into it. Six short labels fit.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                SPIN_SEGMENTS.forEach { (pence, label) ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (landed?.pence == pence) RrrColors.Khaki else RrrColors.Mist,
                    )
                }
            }

            state.error?.let { err ->
                Text(
                    err.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RrrColors.Danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }

            when {
                state.revealed != null -> Text(
                    "You won ${Money.formatCompact(state.revealed!!.pence)} of site credit. " +
                        "It's in your account now.",
                    style = MaterialTheme.typography.titleMedium,
                    color = RrrColors.Khaki,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 22.dp),
                )

                state.used -> Text(
                    "Today's spin is used. You won " +
                        "${Money.formatCompact(landed?.pence ?: 0)}. Come back tomorrow.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RrrColors.Mist,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 22.dp),
                )

                else -> GoldButton(
                    text = "Spin the wheel",
                    onClick = viewModel::spin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 22.dp),
                    enabled = state.checked && !state.spinning,
                    loading = state.spinning,
                )
            }

            Text(
                "Site credit can be spent on raffle entries. It is not cash and " +
                    "cannot be withdrawn.",
                style = MaterialTheme.typography.labelSmall,
                color = RrrColors.Slate,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp),
            )
        }
    }
}
