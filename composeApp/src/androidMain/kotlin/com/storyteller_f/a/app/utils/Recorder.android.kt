package com.storyteller_f.a.app.utils

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import dev.theolm.record.Record
import dev.theolm.record.config.AudioEncoder
import dev.theolm.record.config.OutputFormat
import dev.theolm.record.config.OutputLocation
import dev.theolm.record.config.RecordConfig
import kotlinx.io.files.Path

actual object Recorder {
    private val flow = mutableStateOf(false)
    internal actual fun startRecord() {
        Record.startRecording()
        flow.value = true
    }

    internal actual fun stopRecord(): Path {
        val result = Record.stopRecording()
        flow.value = false
        return Path(result)
    }

    internal actual val isRecording: MutableState<Boolean>
        get() = flow

    init {
        Record.setConfig(RecordConfig(OutputLocation.Cache, OutputFormat.WAV, AudioEncoder.PCM_16BIT, 44100))
    }
}
