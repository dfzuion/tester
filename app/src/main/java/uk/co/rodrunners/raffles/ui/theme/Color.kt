package uk.co.rodrunners.raffles.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Palette taken from the brand direction: near-black river at night, brushed
 * bronze hardware, bone-white type. Gold is used sparingly - a single accent
 * per screen region - so it reads as premium hardware rather than casino.
 */
object RrrColors {
    val Ink = Color(0xFF0A0C08)          // app background
    val Surface = Color(0xFF12150E)      // cards
    val SurfaceRaised = Color(0xFF1A1E14) // sheets, dialogs, inputs
    val Hairline = Color(0xFF333A26)     // 1dp dividers and card borders

    val Gold = Color(0xFFC8913F)         // primary accent
    val GoldBright = Color(0xFFE3B366)   // pressed / focus
    val GoldDim = Color(0x33C8913F)      // tint fills

    val Bone = Color(0xFFEFE7D6)         // primary type
    val Mist = Color(0xFFADA791)         // secondary type
    val Slate = Color(0xFF726C59)        // tertiary type, disabled

    val Success = Color(0xFF7D9A4E)
    val Warning = Color(0xFFD4923F)
    val Danger = Color(0xFFC4564B)

    /**
     * Khaki is the bank-side colour: chips, selected states, the camo texture.
     * Bronze stays reserved for money and prizes, so it keeps its punch.
     */
    val Khaki = Color(0xFF6B7A3F)
    val KhakiBright = Color(0xFFA3B36A)
    val KhakiDim = Color(0x336B7A3F)

    /** Weed green from the logo - used for subtle "live"/open states. */
    val Weed = Color(0xFF5A6B3B)

    val ScrimTop = Brush.verticalGradient(
        0f to Color(0xCC000000), 0.45f to Color(0x33000000), 1f to Color(0x00000000)
    )
    val ScrimBottom = Brush.verticalGradient(
        0f to Color(0x00000000), 0.5f to Color(0xB3000000), 1f to Color(0xF20A0C08)
    )
    val CardScrim = Brush.horizontalGradient(
        0f to Color(0xE60A0C08), 0.55f to Color(0x990A0C08), 1f to Color(0x330A0C08)
    )
}
