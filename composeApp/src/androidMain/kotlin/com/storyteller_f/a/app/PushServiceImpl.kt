package com.storyteller_f.a.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.storyteller_f.a.client_lib.addDevice
import com.storyteller_f.a.client_lib.defaultClientConfigure
import com.storyteller_f.a.client_lib.getClient
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
    val client = getClient {
        defaultClientConfigure(httpUrl = AppConfig.SERVER_URL)
    }

    override fun onMessage(message: PushMessage, instance: String) {
        Napier.i(tag = "push") {
            "receive message $message"
        }
        //发系统通知

        //发通知
        val context = this
        //检查channel 是否存在，如果不存在创建一个
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
                .setSmallIcon(android.R.drawable.ic_notification_overlay)
                .setContentTitle("New message")
            notificationManager.notify(1, notification.build())
        }
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        Napier.i(tag = "push") {
            "receive endpoint $endpoint"
        }
        launch {
            client.addDevice(endpoint.url)
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
