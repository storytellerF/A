package com.storyteller_f.a.app.compose_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.storyteller_f.a.app.compose_app.compontents.CenterBox
import com.storyteller_f.a.app.compose_app.pages.media.MediaPage
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
                val session = commonJson.decodeFromString<MultiMediaInfo>(json)
                CommonEntry({
                    MediaPage(session)
                })
            }
        }
    }
}
