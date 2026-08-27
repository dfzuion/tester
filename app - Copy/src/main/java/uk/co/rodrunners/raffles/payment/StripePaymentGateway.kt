package uk.co.rodrunners.raffles.payment

import android.content.Context
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.rodrunners.raffles.BuildConfig

/**
 * Card details are collected by Stripe's own PaymentSheet and go straight to
 * Stripe. This app never sees, handles or stores a card number, which keeps the
 * business out of PCI-DSS scope beyond SAQ-A.
 *
 * Just as importantly: a PaymentSheetResult.Completed here does NOT mean the
 * order is paid. It means the customer finished the sheet. The order is only
 * paid when our webhook has verified the PaymentIntent with Stripe and flipped
 * the order document, which is what the confirmation screen waits for.
 */
@Singleton
class StripePaymentGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun initialise() {
        PaymentConfiguration.init(context, BuildConfig.STRIPE_PUBLISHABLE_KEY)
    }

    fun configuration(customerName: String?, email: String?): PaymentSheet.Configuration =
        PaymentSheet.Configuration.Builder("Rod Runners Raffles")
            .defaultBillingDetails(
                PaymentSheet.BillingDetails(name = customerName, email = email)
            )
            .allowsDelayedPaymentMethods(false) // entry numbers must not be held against a slow method
            .googlePay(
                PaymentSheet.GooglePayConfiguration(
                    environment = if (BuildConfig.DEBUG) PaymentSheet.GooglePayConfiguration.Environment.Test
                    else PaymentSheet.GooglePayConfiguration.Environment.Production,
                    countryCode = "GB",
                    currencyCode = "GBP",
                )
            )
            .appearance(RrrStripeAppearance.appearance())
            .build()
}

sealed interface PaymentOutcome {
    data object SheetCompleted : PaymentOutcome
    data object Cancelled : PaymentOutcome
    data class Failed(val message: String) : PaymentOutcome
}

fun PaymentSheetResult.toOutcome(): PaymentOutcome = when (this) {
    is PaymentSheetResult.Completed -> PaymentOutcome.SheetCompleted
    is PaymentSheetResult.Canceled -> PaymentOutcome.Cancelled
    is PaymentSheetResult.Failed -> PaymentOutcome.Failed(
        error.localizedMessage ?: "The payment couldn't be completed."
    )
}
