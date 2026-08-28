package uk.co.rodrunners.raffles.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Palette taken from the brand direction: near-black river at night, brushed
 * bronze hardware, bone-white type. Gold is used sparingly - a single accent
 * per screen region - so it reads as premium hardware rather than casino.
 */
object RrrColors {
    val Ink = Color(0xFF0C0F0A)          // app background
    val Surface = Color(0xFF171C12)      // cards
    val SurfaceRaised = Color(0xFF222819) // sheets, dialogs, inputs
    val Hairline = Color(0xFF3E4A2C)     // 1dp dividers and card borders

    val Gold = Color(0xFFE0A03F)         // primary accent
    val GoldBright = Color(0xFFFFC463)   // pressed / focus
    val GoldDim = Color(0x3DE0A03F)      // tint fills

    val Bone = Color(0xFFF6EFE0)         // primary type
    val Mist = Color(0xFFBDB69D)         // secondary type
    val Slate = Color(0xFF867F68)        // tertiary type, disabled

    val Success = Color(0xFF95BB55)
    val Warning = Color(0xFFF0A73F)
    val Danger = Color(0xFFDE6152)

    /**
     * Khaki is the bank-side colour: chips, selected states, the camo texture.
     * Bronze stays reserved for money and prizes, so it keeps its punch.
     */
    val Khaki = Color(0xFF8CA34B)
    val KhakiBright = Color(0xFFBDD17A)
    val KhakiDim = Color(0x3D8CA34B)

    /** Weed green from the logo - used for subtle "live"/open states. */
    val Weed = Color(0xFF6E8446)

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
