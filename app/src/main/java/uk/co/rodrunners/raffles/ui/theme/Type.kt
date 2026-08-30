package uk.co.rodrunners.raffles.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Two roles: a condensed, wide-tracked face for the brand wordmark and section
 * eyebrows, and a neutral sans for everything a customer actually reads.
 * All sizes are in sp so system font scaling works untouched.
 *
 * To swap in a licensed display face, drop the .ttf into res/font and change
 * DisplayFamily - nothing else needs to move.
 */
/**
 * Android ships a condensed grotesque, and headings set in it read as designed
 * rather than as the system default. Nothing is downloaded and nothing is
 * bundled - if the device lacks it, Compose falls back to the regular sans.
 */
val DisplayFamily = FontFamily(Font(DeviceFontFamilyName("sans-serif-condensed")))
val BodyFamily = FontFamily.SansSerif

object RrrType {
    /** The ROD RUNNERS wordmark: heavy, wide letterspacing, always uppercase. */
    val Wordmark = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.Black,
        fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = 3.sp, textAlign = TextAlign.Center,
    )
    val WordmarkSmall = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.Black,
        fontSize = 17.sp, lineHeight = 20.sp, letterSpacing = 2.sp,
    )
    /** Small uppercase labels above sections: "LIVE NOW", "ENDING SOON". */
    val Eyebrow = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, lineHeight = 15.sp, letterSpacing = 1.6.sp,
    )
    /** Prices and entry numbers: tabular feel, slightly tightened. */
    val Numeric = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp,
    )
    val NumericSmall = TextStyle(
        fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 14.5.sp, lineHeight = 18.sp,
    )
}

/*
 * Everything here went up a step. Scott's customers read the app on a phone
 * held at arm's length, usually outside, and the old sizes were set for
 * looking at a screenshot on a desktop.
 */
val RrrTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Black, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = 1.sp),
    headlineMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 33.sp),
    headlineSmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 29.sp),
    titleLarge = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 15.5.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, letterSpacing = 0.4.sp),
)
