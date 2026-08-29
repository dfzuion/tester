package uk.co.rodrunners.raffles.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Tackle-brand palette: khaki green, bronze and near-black.
 *
 * The camo used to sit behind the whole app at half strength. The logo is a
 * detailed illustration and the prize photography is the point of the screen,
 * so a busy texture behind both fought them and made everything look muddy.
 * Camo is now a header band only, and the base is a calm near-black that lets
 * the photography carry the richness.
 */
object RrrColors {
    val Ink = Color(0xFF0B0C0A)          // app background
    val Surface = Color(0xFF11140E)      // cards, opaque now the camo is out from behind them
    val SurfaceRaised = Color(0xFF161A11) // sheets, dialogs, inputs
    val Hairline = Color(0xFF232819)     // 1dp dividers and card borders

    /**
     * Khaki is the interface colour: chips, selected states, primary buttons.
     */
    val Khaki = Color(0xFFA5C05C)
    val KhakiBright = Color(0xFFC8DC90)
    val KhakiDim = Color(0x3DA5C05C)

    /**
     * Bronze is out. Scott asked for no gold anywhere, on the app as well as
     * the site, so these now carry the khaki family. The names stay put: 84
     * call sites use them, and renaming would be a large diff for no gain.
     */
    val Gold = Color(0xFFA5C05C)
    val GoldBright = Color(0xFFC8DC90)
    val GoldDim = Color(0x3DA5C05C)

    val Bone = Color(0xFFF0EBDB)         // primary type
    val Mist = Color(0xFF8E9678)         // secondary type
    val Slate = Color(0xFF5E664F)        // tertiary type, disabled

    val Success = Color(0xFF9CC258)

    /**
     * Caution: ending soon, nearly gone, sold out. It was #D9903C, which sat a
     * shade off the bronze that has been taken out of the app, so it read as
     * gold on a khaki screen. This is unambiguously orange instead, and still
     * clear of both the khaki that means "fine" and the red that means "no".
     */
    val Warning = Color(0xFFE07A45)
    val Danger = Color(0xFFDE6152)

    /** Top of the masthead gradient - warm, so the camo band reads as fabric. */
    val HeaderTop = Color(0xFF161A11)

    /** A dark olive for fills that need weight without going flat black. */
    val Bark = Color(0xFF23291B)

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
