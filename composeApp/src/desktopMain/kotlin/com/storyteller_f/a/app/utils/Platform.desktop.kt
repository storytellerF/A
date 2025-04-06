package com.storyteller_f.a.app.utils

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.awtClipboard
import java.awt.datatransfer.StringSelection

actual val platform: Platform
    get() = Platform(false)

actual fun initEnvironment(context: Any) = Unit

@OptIn(ExperimentalComposeUiApi::class)
actual suspend fun Clipboard.setText(string: String) {
    this.awtClipboard?.setContents(StringSelection(string), null)
}
