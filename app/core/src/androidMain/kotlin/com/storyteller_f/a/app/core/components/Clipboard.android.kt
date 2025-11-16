package com.storyteller_f.a.app.core.components

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.setClipEntry

actual suspend fun Clipboard.setText(string: String) {
    setClipEntry(ClipEntry(ClipData.newPlainText("text", string)))
}
