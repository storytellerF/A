package com.storyteller_f.a.app.compose_app

import android.R
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.kdroid.composenotification.builder.AndroidChannelConfig
import com.kdroid.composenotification.builder.NotificationInitializer.notificationInitializer
import com.storyteller_f.a.app.compose_app.compontents.bindActivity
import com.storyteller_f.a.app.compose_app.compontents.unbindActivity
import com.storyteller_f.a.app.compose_app.utils.initEnvironment
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import org.unifiedpush.android.connector.UnifiedPush
import java.util.concurrent.Future

class MainActivity : ComponentActivity() {
    private var controllerFuture: Future<MediaController>? = null

    override fun onStart() {
        super.onStart()
        val sessionToken =
            SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val listener = object : MediaController.Listener {
            override fun onAvailableSessionCommandsChanged(controller: MediaController, commands: SessionCommands) {
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
            val future = MediaController.Builder(this, sessionToken).setListener(listener).buildAsync()
            controllerFuture = future
            future.addListener({
                MediaProvider.controller = future.get()
            }, MoreExecutors.directExecutor())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupForSplash()
        FileKit.init(this)
        commonForActivity()
        initEnvironment(this)
        setContent {
            App()
        }
    }

    private fun setupForSplash() {
        installSplashScreen()
        if (AppConfig.ENABLE_LOGIN_CHECK) {
            val content = findViewById<View>(R.id.content)
            content.viewTreeObserver.addOnPreDrawListener(
                object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        // Check whether the initial data is ready.
                        content.viewTreeObserver.removeOnPreDrawListener(this)
                        return true
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindActivity()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}

fun ComponentActivity.initFromContext() {
    bindActivity(this)
    notificationInitializer(
        defaultChannelConfig = AndroidChannelConfig(
            channelId = "Regular",
            channelName = "Regular",
            channelDescription = "Regular",
            channelImportance = NotificationManager.IMPORTANCE_DEFAULT,
            smallIcon = R.drawable.ic_notification_overlay
        )
    )
}

fun ComponentActivity.commonForActivity() {
    enableEdgeToEdge()
    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = false
}

val isRunningOnRobolectric: Boolean
    get() = try {
        Class.forName("org.robolectric.Robolectric")
        true
    } catch (_: Exception) {
        false
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
