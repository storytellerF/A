package com.storyteller_f.a.app.compose_app.utils

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import space.kodio.core.AudioRecordingSession
import space.kodio.core.SystemAudioSystem
import space.kodio.core.io.files.AudioFileFormat
import space.kodio.core.io.files.writeToFile
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object Recorder {
    var recording: AudioRecordingSession? = null
    suspend fun startRecord() {
        if (isRecording.value){
            return
        }
        isRecording.value = true
        val session = SystemAudioSystem.createRecordingSession()
        recording = session
        session.start()
    }
    @OptIn(ExperimentalUuidApi::class)
    suspend fun stopRecord(): Path? {
        if (!isRecording.value){
            return null
        }
        val session = recording ?: return null
        session.stop()
        isRecording.value = false
        val flow = session.audioFlow.value ?: return null
        val path = Path(SystemTemporaryDirectory, "${Uuid.random().toHexString()}.wav")
        flow.writeToFile(AudioFileFormat.Wav, path)
        return path
    }
    val isRecording: MutableState<Boolean> = mutableStateOf(false)
}
