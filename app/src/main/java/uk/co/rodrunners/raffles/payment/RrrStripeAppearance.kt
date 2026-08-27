package uk.co.rodrunners.raffles.payment

import androidx.compose.ui.graphics.toArgb
import com.stripe.android.paymentsheet.PaymentSheet
import uk.co.rodrunners.raffles.ui.theme.RrrColors

/** Makes Stripe's sheet look like the rest of the app rather than a bolt-on. */
object RrrStripeAppearance {
    fun appearance(): PaymentSheet.Appearance {
        val colors = PaymentSheet.Colors(
            primary = RrrColors.Gold.toArgb(),
            surface = RrrColors.SurfaceRaised.toArgb(),
            component = RrrColors.Surface.toArgb(),
            componentBorder = RrrColors.Hairline.toArgb(),
            componentDivider = RrrColors.Hairline.toArgb(),
            onComponent = RrrColors.Bone.toArgb(),
            subtitle = RrrColors.Mist.toArgb(),
            placeholderText = RrrColors.Slate.toArgb(),
            onSurface = RrrColors.Bone.toArgb(),
            appBarIcon = RrrColors.Bone.toArgb(),
            error = RrrColors.Danger.toArgb(),
        )
        return PaymentSheet.Appearance(
            colorsLight = colors,
            colorsDark = colors,
            shapes = PaymentSheet.Shapes(cornerRadiusDp = 12f, borderStrokeWidthDp = 1f),
            primaryButton = PaymentSheet.PrimaryButton(
                shape = PaymentSheet.PrimaryButtonShape(cornerRadiusDp = 12f),
            ),
        )
    }
}
