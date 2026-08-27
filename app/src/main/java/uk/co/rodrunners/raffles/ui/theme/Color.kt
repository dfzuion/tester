package uk.co.rodrunners.raffles.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Palette taken from the brand direction: near-black river at night, brushed
 * bronze hardware, bone-white type. Gold is used sparingly - a single accent
 * per screen region - so it reads as premium hardware rather than casino.
 */
object RrrColors {
    val Ink = Color(0xFF0B0B0A)          // app background
    val Surface = Color(0xFF15140F)      // cards
    val SurfaceRaised = Color(0xFF201E16) // sheets, dialogs, inputs
    val Hairline = Color(0xFF33301F)     // 1dp dividers and card borders

    val Gold = Color(0xFFC8913F)         // primary accent
    val GoldBright = Color(0xFFE0B36B)   // pressed / focus
    val GoldDim = Color(0x33C8913F)      // tint fills

    val Bone = Color(0xFFF2E9D8)         // primary type
    val Mist = Color(0xFFB2A891)         // secondary type
    val Slate = Color(0xFF7A7360)        // tertiary type, disabled

    val Success = Color(0xFF7D9A4E)
    val Warning = Color(0xFFD4923F)
    val Danger = Color(0xFFC4564B)

    /** Weed green from the logo - used for subtle "live"/open states. */
    val Weed = Color(0xFF5A6B3B)

    val ScrimTop = Brush.verticalGradient(
        0f to Color(0xCC000000), 0.45f to Color(0x33000000), 1f to Color(0x00000000)
    )
    val ScrimBottom = Brush.verticalGradient(
        0f to Color(0x00000000), 0.5f to Color(0xB3000000), 1f to Color(0xF20B0B0A)
    )
    val CardScrim = Brush.horizontalGradient(
        0f to Color(0xE60B0B0A), 0.55f to Color(0x990B0B0A), 1f to Color(0x330B0B0A)
    )
}
