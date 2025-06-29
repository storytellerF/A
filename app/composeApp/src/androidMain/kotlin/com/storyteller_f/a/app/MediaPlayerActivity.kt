package com.storyteller_f.a.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.storyteller_f.a.app.compontents.CenterBox
import com.storyteller_f.a.app.pages.media.MediaPage
import kotlinx.serialization.json.Json

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
                val httpUrl = AppConfig.SERVER_URL
                val wsServerUrl = AppConfig.WS_SERVER_URL
                val session = Json.decodeFromString<MediaPlaySession>(json)
                CommonEntry(httpUrl, wsServerUrl, {
                    MediaPage(session)
                })
            }
        }
    }
}
