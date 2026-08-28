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
 * Dark by design rather than by system preference: the photography and the gold
 * accent are built for a near-black canvas.
 */
private val RrrDarkScheme = darkColorScheme(
    primary = RrrColors.Gold,
    onPrimary = RrrColors.Ink,
    primaryContainer = RrrColors.GoldDim,
    onPrimaryContainer = RrrColors.GoldBright,
    secondary = RrrColors.Bone,
    onSecondary = RrrColors.Ink,
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
    val minTouchTarget = 48.dp
    val cardRadius = 14.dp
}

/**
 * The digital camo wash. Kept faint on purpose: it should read as fabric under
 * the content, never as a pattern competing with the photography. One tile is
 * shared by every caller, repeated by the shader rather than redrawn.
 */
@Composable
fun Modifier.camoTexture(alpha: Float = 0.10f): Modifier {
    val tile = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.camo_tile)
    val brush = remember(tile) {
        ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
    }
    return this.background(brush, alpha = alpha)
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
        // One camo wash for the whole app. Screens sit on it transparently, so
        // the texture runs edge to edge instead of stopping at each surface.
        Box(
            Modifier
                .fillMaxSize()
                
                .camoTexture(0.22f)
        ) { content() }
    }
}
