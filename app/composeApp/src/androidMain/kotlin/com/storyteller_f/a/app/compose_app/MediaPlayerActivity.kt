package com.storyteller_f.a.app.compose_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import com.storyteller_f.a.app.core.components.CenterBox
import com.storyteller_f.a.app.core.components.LocalMediaPlayerService
import com.storyteller_f.a.app.core.components.RemoteMediaItem
import com.storyteller_f.shared.commonJson

class MediaPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        commonForActivity()
        setContent {
            val json = intent.getStringExtra("json")
            if (json == null) {
                CenterBox {
                    Text("invalid")
                }
            } else {
                val remoteMediaItem = commonJson.decodeFromString<RemoteMediaItem>(json)
                CompositionLocalProvider(
                    LocalUiViewModel provides uiViewModel,
                    LocalMediaPlayerService provides (application as AApplication).mediaPlayer
                ) {
                    MediaPlayerPage(remoteMediaItem)
                }
            }
        }
    }
}
