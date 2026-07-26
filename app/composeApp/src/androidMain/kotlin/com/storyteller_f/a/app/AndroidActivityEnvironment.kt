package com.storyteller_f.a.app

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import com.mmk.kmpnotifier.KMPNotifier
import com.mmk.kmpnotifier.extensions.onCreateOrOnNewIntent
import com.storyteller_f.a.app.utils.initializeAppNotifications
import com.storyteller_f.a.client.compose_core.components.bindActivity
import io.github.aakira.napier.Napier
import org.unifiedpush.android.connector.UnifiedPush

fun ComponentActivity.initFromContext() {
    bindActivity(this)
    initializeAppNotifications()
    KMPNotifier.onCreateOrOnNewIntent(intent)
}

fun handleAppNotificationIntent(intent: Intent) {
    if (KMPNotifier.isInitialized) {
        KMPNotifier.onCreateOrOnNewIntent(intent)
    }
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
