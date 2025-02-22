package com.storyteller_f.a.app

import android.R
import android.app.NotificationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowInsetsControllerCompat
import com.kdroid.composenotification.builder.AndroidChannelConfig
import com.kdroid.composenotification.builder.NotificationInitializer.notificationInitializer
import com.storyteller_f.a.app.compontents.bindActivity
import com.storyteller_f.a.app.compontents.unbindActivity
import io.github.vinceglb.filekit.core.FileKit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = false

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

@Preview
@Composable
fun AppAndroidPreview() {
    App()
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
            smallIcon = R.drawable.ic_notification_overlay
        )
    )
}
