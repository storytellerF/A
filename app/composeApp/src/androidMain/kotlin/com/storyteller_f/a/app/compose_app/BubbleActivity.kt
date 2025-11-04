package com.storyteller_f.a.app.compose_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import com.storyteller_f.a.app.core.components.CenterBox

class BubbleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val roomId = intent.getLongExtra("roomId", 0)
        setContent {
            if (roomId == 0L) {
                CenterBox {
                    Text("invalid roomId")
                }
            } else {
                CompositionLocalProvider(
                    LocalUiViewModel provides uiViewModel
                ) {
                    BubblePage(roomId)
                }
            }
        }
    }
}
