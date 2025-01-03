package com.storyteller_f.a.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowInsetsControllerCompat
import com.storyteller_f.a.app.compontents.bindActivity
import com.storyteller_f.a.app.compontents.unbindActivity
import io.github.vinceglb.filekit.core.FileKit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindActivity(this)
        FileKit.init(this)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = false

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
