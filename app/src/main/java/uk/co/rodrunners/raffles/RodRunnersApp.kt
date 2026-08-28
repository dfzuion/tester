package uk.co.rodrunners.raffles

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.ktx.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import uk.co.rodrunners.raffles.core.NotificationChannels
import uk.co.rodrunners.raffles.payment.StripePaymentGateway

private const val TAG = "RodRunnersApp"

@HiltAndroidApp
class RodRunnersApp : Application() {

    @Inject lateinit var stripe: StripePaymentGateway

    override fun onCreate() {
        super.onCreate()

        // Nothing here is worth crashing the app on launch for. A bad Stripe
        // key, App Check being unavailable on the device, or a notification
        // channel failing should degrade that one feature, not stop someone
        // opening the app at all.
        runCatching {
            // App Check makes every callable function reject traffic that
            // didn't come from a genuine install of this app.
            Firebase.appCheck.installAppCheckProviderFactory(
                if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance()
                else PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }.onFailure { Log.e(TAG, "App Check unavailable - callable functions may be rejected", it) }

        // Staging is a test build we need diagnostics from; a local debug build
        // still reports nothing.
        runCatching {
            Firebase.crashlytics.setCrashlyticsCollectionEnabled(
                !BuildConfig.DEBUG || BuildConfig.FLAVOR == "staging"
            )
        }.onFailure { Log.e(TAG, "Crashlytics could not be configured", it) }

        runCatching { stripe.initialise() }
            .onFailure { Log.e(TAG, "Stripe could not be initialised - checkout will not work", it) }

        runCatching { createNotificationChannels() }
            .onFailure { Log.e(TAG, "Notification channels could not be created", it) }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channels = listOf(
            channel(NotificationChannels.WINS, "Wins", "When one of your entries wins", NotificationManager.IMPORTANCE_HIGH),
            channel(NotificationChannels.ORDERS, "Orders and payments", "Order confirmations, refunds and payment problems", NotificationManager.IMPORTANCE_HIGH),
            channel(NotificationChannels.RAFFLES, "Raffle updates", "New raffles and raffles closing soon", NotificationManager.IMPORTANCE_DEFAULT),
            channel(NotificationChannels.PROMOTIONS, "Offers", "Discount codes and promotions", NotificationManager.IMPORTANCE_LOW),
            channel(NotificationChannels.GENERAL, "General", "Account messages and announcements", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannels(channels)
    }

    private fun channel(id: String, name: String, description: String, importance: Int) =
        NotificationChannel(id, name, importance).apply { this.description = description }
}
