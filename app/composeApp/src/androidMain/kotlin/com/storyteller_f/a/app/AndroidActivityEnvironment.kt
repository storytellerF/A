package com.storyteller_f.a.app

import android.app.NotificationManager
import android.content.Context
import androidx.activity.ComponentActivity
import com.kdroid.composenotification.builder.AndroidChannelConfig
import com.kdroid.composenotification.builder.NotificationInitializer.notificationInitializer
import com.storyteller_f.a.app.core.components.bindActivity
import io.github.aakira.napier.Napier
import org.unifiedpush.android.connector.UnifiedPush

fun ComponentActivity.initFromContext() {
    bindActivity(this)
    notificationInitializer(
        defaultChannelConfig = AndroidChannelConfig(
            channelId = "Regular",
            channelName = "Regular",
            channelDescription = "Regular",
            channelImportance = NotificationManager.IMPORTANCE_DEFAULT,
            smallIcon = com.storyteller_f.a.app.android_library.R.drawable.ic_notify
        )
    )
}

fun registerDevice(context: Context) {
    try {
        val distributor = UnifiedPush.getAckDistributor(context)
            ?: UnifiedPush.getDistributors(context).firstOrNull()?.let { instance ->
                UnifiedPush.saveDistributor(context, instance)
                instance
            }
        if (distributor != null) {
            UnifiedPush.register(context, distributor, "A")
            Napier.i(tag = "distributor") {
                "distributor $distributor"
            }
        } else {
            Napier.i(tag = "distributor") {
                "distributor not found"
            }
        }
    } catch (e: Exception) {
        Napier.e(throwable = e, tag = "distributor") {
            "register error $e"
        }
    }
}
