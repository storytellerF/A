package com.storyteller_f.a.app

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.kdroid.composenotification.builder.AndroidChannelConfig
import com.kdroid.composenotification.builder.NotificationInitializer.notificationInitializer
import com.storyteller_f.a.app.utils.initEnvironment
import com.storyteller_f.a.app.core.PlaybackService
import com.storyteller_f.a.app.core.commonForActivity
import com.storyteller_f.a.app.core.components.LocalMediaPlayerService
import com.storyteller_f.a.app.core.components.bindActivity
import com.storyteller_f.a.app.core.components.unbindActivity
import com.storyteller_f.shared.isRunningOnRobolectric
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import org.unifiedpush.android.connector.UnifiedPush
import java.util.concurrent.Future

class MainActivity : ComponentActivity(), ClientFileServiceContainer {
    private var controllerFuture: Future<MediaController>? = null

    override fun onStart() {
        super.onStart()
        val sessionToken =
            SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val listener = object : MediaController.Listener {
            override fun onAvailableSessionCommandsChanged(
                controller: MediaController,
                commands: SessionCommands
            ) {
                super.onAvailableSessionCommandsChanged(controller, commands)
                Napier.d {
                    "MediaController onAvailableSessionCommandsChanged"
                }
            }

            override fun onDisconnected(controller: MediaController) {
                super.onDisconnected(controller)
                Napier.d {
                    "MediaController onDisconnected"
                }
            }
        }
        if (!isRunningOnRobolectric) {
            val future =
                MediaController.Builder(this, sessionToken).setListener(listener).buildAsync()
            controllerFuture = future
            future.addListener({
                (application as AApplication).mediaPlayer.controller.value = future.get()
            }, MoreExecutors.directExecutor())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupForSplash()
        FileKit.init(this)
        commonForActivity()
        initEnvironment(this)
        registerDevice(this)
        val receiver = CustomClientFileProvider(this)
        setContent {
            CompositionLocalProvider(
                LocalClientFileProvider provides receiver,
                LocalUiViewModel provides uiViewModel,
                LocalMediaPlayerService provides (application as AApplication).mediaPlayer
            ) {
                App()
            }
        }
    }

    private fun setupForSplash() {
        installSplashScreen()
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindActivity()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    override var binder: FileBinder? = null
    override var isConnecting: Boolean = false
}

fun ComponentActivity.initFromContext() {
    bindActivity(this)
    notificationInitializer(
        defaultChannelConfig = AndroidChannelConfig(
            channelId = "Regular",
            channelName = "Regular",
            channelDescription = "Regular",
            channelImportance = NotificationManager.IMPORTANCE_DEFAULT,
            smallIcon = com.storyteller_f.a.app.R.drawable.ic_notify
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
