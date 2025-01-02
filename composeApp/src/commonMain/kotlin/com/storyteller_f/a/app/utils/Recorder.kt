package com.storyteller_f.a.app.utils

import androidx.compose.runtime.MutableState
import kotlinx.io.files.Path

expect object Recorder {
    internal fun startRecord()
    internal fun stopRecord(): Path
    internal val isRecording: MutableState<Boolean>
}