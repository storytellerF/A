package com.storyteller_f.a.app.compose_app

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.storyteller_f.a.client.core.addDevice
import com.storyteller_f.a.client.core.buildWebSocketUrl
import com.storyteller_f.a.client.core.createUserSessionManager
import com.storyteller_f.a.client.core.start
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import kotlin.coroutines.CoroutineContext

class PushServiceImpl : PushService(), CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job
    val userSession = createUserSessionManager(buildWebSocketUrl(AppConfig.WS_SERVER_URL), { model, cookie ->
        buildHttpClient(AppConfig.SERVER_URL, cookie, model)
    }, { _, _ -> })

    override fun onCreate() {
        super.onCreate()
        userSession.start()
    }

    override fun onMessage(message: PushMessage, instance: String) {
        Napier.i(tag = "push") {
            "receive message $message"
        }
        val context = this
        val channel = "Regular"
        val notificationManager = NotificationManagerCompat.from(context)
        val notificationChannel = notificationManager.getNotificationChannel(channel)
        if (notificationChannel == null) {
            val channelBuilder = NotificationChannelCompat.Builder(
                channel,
                NotificationManagerCompat.IMPORTANCE_DEFAULT
            )
            channelBuilder.setName("Regular")
            channelBuilder.setDescription("Regular")
            notificationManager.createNotificationChannel(channelBuilder.build())
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val notification = NotificationCompat.Builder(context, channel)
                .setSmallIcon(com.storyteller_f.a.app.R.drawable.ic_notify)
                .setContentTitle("New message")
            notificationManager.notify(1, notification.build())
        }
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        Napier.i(tag = "push") {
            "receive endpoint $endpoint"
        }
        launch {
            userSession.addDevice(endpoint.url)
        }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Napier.i(tag = "push") {
            "receive failed $reason"
        }
    }

    override fun onUnregistered(instance: String) {
        Napier.i(tag = "push") {
            "receive unregistered"
        }
        val context = this
        UnifiedPush.getDistributors(context).firstOrNull()?.let { instance ->
            UnifiedPush.saveDistributor(context, instance)
            UnifiedPush.register(context, instance, "A")
            instance
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
