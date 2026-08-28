package uk.co.rodrunners.raffles.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Tackle-brand palette: khaki green, bark brown and black, the three tones the
 * digital camo is cut from. Green carries the interface - chips, selected
 * states, live badges - brown is kept for money and prizes so it still lands,
 * and everything sits on a green-black base with the camo reading through.
 */
object RrrColors {
    val Ink = Color(0xFF090B07)          // app background, black with a green cast
    val Surface = Color(0xE614180F)      // cards - translucent so the camo shows
    val SurfaceRaised = Color(0xF21E2415) // sheets, dialogs, inputs
    val Hairline = Color(0xFF3E4728)     // 1dp dividers and card borders

    /**
     * Khaki is the interface colour: chips, selected states, primary buttons.
     */
    val Khaki = Color(0xFF8FA84E)
    val KhakiBright = Color(0xFFBCD37B)
    val KhakiDim = Color(0x3D8FA84E)

    /** Bronze-brown stays reserved for money and prizes, so it keeps its punch. */
    val Gold = Color(0xFFA96A2C)
    val GoldBright = Color(0xFFC98B45)
    val GoldDim = Color(0x3DA96A2C)

    val Bone = Color(0xFFF0EBDB)         // primary type
    val Mist = Color(0xFFACB196)         // secondary type
    val Slate = Color(0xFF767C63)        // tertiary type, disabled

    val Success = Color(0xFF9CC258)
    val Warning = Color(0xFFD9903C)
    val Danger = Color(0xFFDE6152)

    /** Deep bark brown, for fills that need weight without going flat black. */
    val Bark = Color(0xFF3A2A1B)

    /** Weed green from the logo - used for subtle "live"/open states. */
    val Weed = Color(0xFF6E8446)

    val ScrimTop = Brush.verticalGradient(
        0f to Color(0xCC000000), 0.45f to Color(0x33000000), 1f to Color(0x00000000)
    )
    val ScrimBottom = Brush.verticalGradient(
        0f to Color(0x00000000), 0.5f to Color(0xB3050704), 1f to Color(0xF2090B07)
    )
    val CardScrim = Brush.horizontalGradient(
        0f to Color(0xE6090B07), 0.55f to Color(0x99090B07), 1f to Color(0x33090B07)
    )
}
