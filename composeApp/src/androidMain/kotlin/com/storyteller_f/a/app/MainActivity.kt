package com.storyteller_f.a.app

import android.app.NotificationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsControllerCompat
import com.kdroid.composenotification.builder.AndroidChannelConfig
import com.kdroid.composenotification.builder.NotificationInitializer.notificationInitializer
import com.storyteller_f.a.app.compontents.bindActivity
import com.storyteller_f.a.app.compontents.unbindActivity
import io.github.vinceglb.filekit.core.FileKit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        commonForActivity()

        initFromContext()

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindActivity()
    }
}

fun ComponentActivity.initFromContext() {
    bindActivity(this)
    FileKit.init(this)
    notificationInitializer(
        defaultChannelConfig = AndroidChannelConfig(
            channelId = "Regular",
            channelName = "Regular",
            channelDescription = "Regular",
            channelImportance = NotificationManager.IMPORTANCE_DEFAULT,
            smallIcon = android.R.drawable.ic_notification_overlay
        )
    )
}

fun ComponentActivity.commonForActivity() {
    enableEdgeToEdge()
    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = false
}
