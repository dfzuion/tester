package uk.co.rodrunners.raffles.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.co.rodrunners.raffles.core.AppError
import uk.co.rodrunners.raffles.core.UiState
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import uk.co.rodrunners.raffles.R
import uk.co.rodrunners.raffles.ui.theme.RrrColors

/**
 * Single entry point for rendering a screen's state. Because every screen goes
 * through this, no screen can end up blank: loading, empty and error all have a
 * designed presentation and a way forward.
 */
@Composable
fun <T> StateHost(
    state: UiState<T>,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onEmptyAction: () -> Unit = {},
    loading: @Composable () -> Unit = { SkeletonList() },
    content: @Composable (T) -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        when (state) {
            is UiState.Loading -> loading()
            is UiState.Empty -> EmptyState(state.title, state.body, state.actionLabel, onEmptyAction)
            is UiState.Error -> ErrorState(state.error, onRetry)
            is UiState.Success -> content(state.data)
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The empty state is the first thing a new customer sees, so it carries
        // the brand rather than apologising in plain text.
        Image(
            painter = painterResource(R.drawable.logo_full),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alpha = 0.92f,
            modifier = Modifier.fillMaxWidth(0.86f),
        )
        Spacer(Modifier.height(18.dp))
        GoldRule(width = 28.dp)
        Spacer(Modifier.height(20.dp))
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = RrrColors.Bone, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = RrrColors.Mist, textAlign = TextAlign.Center)
        if (actionLabel != null) {
            Spacer(Modifier.height(24.dp))
            GoldButton(text = actionLabel, onClick = onAction, modifier = Modifier.fillMaxWidth(0.7f))
        }
    }
}

@Composable
fun ErrorState(error: AppError, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(error.title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = RrrColors.Bone, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(error.message, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = RrrColors.Mist, textAlign = TextAlign.Center)
        if (error.retryable) {
            Spacer(Modifier.height(24.dp))
            OutlineButton(text = "Try again", onClick = onRetry, modifier = Modifier.fillMaxWidth(0.6f))
        }
    }
}

/** Shimmering placeholders sized like the real cards, so nothing jumps on load. */
@Composable
fun SkeletonList(rows: Int = 4, rowHeight: androidx.compose.ui.unit.Dp = 108.dp) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).semantics {
            contentDescription = "Loading"
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(rows) { ShimmerBlock(Modifier.fillMaxWidth().height(rowHeight)) }
    }
}

@Composable
fun ShimmerBlock(modifier: Modifier = Modifier, corner: androidx.compose.ui.unit.Dp = 14.dp) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "phase",
    )
    val colour: Color = lerp(RrrColors.Surface, RrrColors.SurfaceRaised, phase)
    Box(modifier.clip(RoundedCornerShape(corner)).background(colour))
}
