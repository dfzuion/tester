package uk.co.rodrunners.raffles.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import coil.compose.SubcomposeAsyncImage
import coil.request.CachingPolicy
import coil.request.ImageRequest
import coil.size.Scale
import uk.co.rodrunners.raffles.ui.theme.RrrColors

/**
 * All prize photography goes through here. Requests are downsampled to the
 * composable's own size and cached to disk, so a list of raffles never pulls
 * full-resolution images or decodes more than it draws.
 */
@Composable
fun PrizeImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    crossfade: Boolean = true,
) {
    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(if (crossfade) 220 else 0)
            .scale(Scale.FILL)
            .memoryCachePolicy(CachingPolicy.ENABLED)
            .diskCachePolicy(CachingPolicy.ENABLED)
            .build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = { ShimmerBlock(Modifier.fillMaxSize(), corner = 0.dp) },
        error = { PhotoFallback() },
    )
}

/** Shown when a photo is missing or fails: quiet, never a broken-image icon. */
@Composable
private fun PhotoFallback() {
    Box(
        Modifier
            .fillMaxSize()
            .background(RrrColors.SurfaceRaised)
            .clearAndSetSemantics { contentDescription = "Photograph unavailable" }
    )
}
