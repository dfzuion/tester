package uk.co.rodrunners.raffles.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
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
        content = content,
    )
}
