package com.storyteller_f.a.app.compose_app.utils

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.awtClipboard
import dev.jordond.connectivity.Connectivity
import java.awt.datatransfer.StringSelection

actual val platform: Platform
    get() = Platform(false, debug = true)

actual fun initEnvironment(context: Any) = Unit

@OptIn(ExperimentalComposeUiApi::class)
actual suspend fun Clipboard.setText(string: String) {
    awtClipboard?.setContents(StringSelection(string), null)
}

actual fun createConnectivity(): Connectivity {
    return Connectivity {
        autoStart = true
        urls("cloudflare.com")
        port = 80
        pollingIntervalMs = 10.minutes
        timeoutMs = 5.seconds
    }
}
