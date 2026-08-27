package uk.co.rodrunners.raffles.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Palette taken from the brand direction: near-black river at night, brushed
 * bronze hardware, bone-white type. Gold is used sparingly - a single accent
 * per screen region - so it reads as premium hardware rather than casino.
 */
object RrrColors {
    val Ink = Color(0xFF0B0B0C)          // app background
    val Surface = Color(0xFF141416)      // cards
    val SurfaceRaised = Color(0xFF1C1D1F) // sheets, dialogs, inputs
    val Hairline = Color(0xFF2A2B2E)     // 1dp dividers and card borders

    val Gold = Color(0xFFC6A15B)         // primary accent
    val GoldBright = Color(0xFFDCBE84)   // pressed / focus
    val GoldDim = Color(0x33C6A15B)      // tint fills

    val Bone = Color(0xFFF5F3EF)         // primary type
    val Mist = Color(0xFFA7A6A2)         // secondary type
    val Slate = Color(0xFF6E6E71)        // tertiary type, disabled

    val Success = Color(0xFF6FA36A)
    val Warning = Color(0xFFD4923F)
    val Danger = Color(0xFFC4564B)

    val ScrimTop = Brush.verticalGradient(
        0f to Color(0xCC000000), 0.45f to Color(0x33000000), 1f to Color(0x00000000)
    )
    val ScrimBottom = Brush.verticalGradient(
        0f to Color(0x00000000), 0.5f to Color(0xB3000000), 1f to Color(0xF20B0B0C)
    )
    val CardScrim = Brush.horizontalGradient(
        0f to Color(0xE60B0B0C), 0.55f to Color(0x990B0B0C), 1f to Color(0x330B0B0C)
    )
}
