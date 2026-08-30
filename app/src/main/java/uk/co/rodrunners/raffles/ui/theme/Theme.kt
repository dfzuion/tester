package uk.co.rodrunners.raffles.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.imageResource
import uk.co.rodrunners.raffles.R
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Dark by design rather than by system preference: the photography, the camo
 * and the khaki accent are all built for a near-black canvas.
 */
private val RrrDarkScheme = darkColorScheme(
    primary = RrrColors.Khaki,
    onPrimary = RrrColors.Ink,
    primaryContainer = RrrColors.KhakiDim,
    onPrimaryContainer = RrrColors.KhakiBright,
    secondary = RrrColors.Gold,
    onSecondary = RrrColors.Ink,
    secondaryContainer = RrrColors.GoldDim,
    onSecondaryContainer = RrrColors.GoldBright,
    tertiary = RrrColors.Bone,
    onTertiary = RrrColors.Ink,
    background = RrrColors.Ink,
    onBackground = RrrColors.Bone,
    surface = RrrColors.Surface,
    onSurface = RrrColors.Bone,
    surfaceVariant = RrrColors.SurfaceRaised,
    onSurfaceVariant = RrrColors.Mist,
    outline = RrrColors.Hairline,
    outlineVariant = RrrColors.Hairline,
    error = RrrColors.Danger,
    onError = Color.White,
    scrim = Color(0xCC000000),
)

val RrrShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

object Dimens {
    val gutter = 20.dp
    val cardGap = 12.dp
    val sectionGap = 28.dp
    /** Raised with the type. Buttons on a phone held outdoors want the room. */
    val minTouchTarget = 54.dp
    val cardRadius = 14.dp
}

/**
 * The masthead treatment: a warm dark gradient with the camo fading out as it
 * descends, so the pattern reads at the top of the screen and has gone by the
 * time it reaches the content.
 */
@Composable
fun Modifier.camoHeader(): Modifier {
    val tile = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.camo_tile)
    val shader = remember(tile) {
        ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
    }
    return this
        .background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to RrrColors.HeaderTop, 1f to RrrColors.Ink,
            )
        )
        .drawWithContent {
            drawContent()
            drawRect(
                brush = shader,
                alpha = 0.20f,
                blendMode = androidx.compose.ui.graphics.BlendMode.SrcOver,
            )
            // Fade the pattern out towards the bottom edge.
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color.Transparent, 1f to RrrColors.Ink,
                )
            )
        }
}

@Composable
fun RodRunnersTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = RrrDarkScheme,
        typography = RrrTypography,
        shapes = RrrShapes,
    ) {
        // A calm base. The camo lives in the masthead now, not behind
        // everything - see camoHeader.
        Box(Modifier.fillMaxSize().background(RrrColors.Ink)) { content() }
    }
}
