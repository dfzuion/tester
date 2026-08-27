package uk.co.rodrunners.raffles.messaging

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uk.co.rodrunners.raffles.MainActivity
import uk.co.rodrunners.raffles.R
import uk.co.rodrunners.raffles.core.NotificationChannels
import uk.co.rodrunners.raffles.data.repository.AccountRepository
import uk.co.rodrunners.raffles.data.repository.AuthRepository

@AndroidEntryPoint
class RrrMessagingService : FirebaseMessagingService() {

    @Inject lateinit var accounts: AccountRepository
    @Inject lateinit var auth: AuthRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        // Tokens rotate; the server keeps an array per user and prunes dead ones.
        if (auth.currentUid == null) return
        scope.launch { runCatching { accounts.syncPushToken() } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val category = message.data["category"] ?: "account"
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            message.data["competitionId"]?.let { data = "rrr://competition/$it".toUri() }
            message.data["orderId"]?.let { data = "rrr://orders/$it".toUri() }
            message.data["winnerId"]?.let { data = "rrr://results/$it".toUri() }
        }
        val pending = PendingIntent.getActivity(
            this, Random.nextInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelFor(category))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(getColor(R.color.brand_gold))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(if (category == "win") NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(this).notify(Random.nextInt(), notification)
    }

    private fun channelFor(category: String) = when (category) {
        "win" -> NotificationChannels.WINS
        "purchase", "payment", "refund" -> NotificationChannels.ORDERS
        "ending_soon", "new_competition" -> NotificationChannels.RAFFLES
        "promotion" -> NotificationChannels.PROMOTIONS
        else -> NotificationChannels.GENERAL
    }
}
