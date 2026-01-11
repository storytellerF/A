package com.storyteller_f.a.panel

import PanelFilePreviewPage
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import com.storyteller_f.a.app.core.commonForActivity
import com.storyteller_f.a.app.core.components.CenterBox
import com.storyteller_f.a.app.core.components.LocalMediaPlayerService

class PanelMediaPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        commonForActivity()
        setContent {
            val fileId = intent.getLongExtra("id", 0)
            if (fileId == 0L) {
                CenterBox {
                    Text("Invalid")
                }
            } else {
                CompositionLocalProvider(
                    LocalMediaPlayerService provides (application as PanelApplication).mediaPlayer
                ) {
                    PanelFilePreviewPage(fileId)
                }
            }
        }
    }
}
