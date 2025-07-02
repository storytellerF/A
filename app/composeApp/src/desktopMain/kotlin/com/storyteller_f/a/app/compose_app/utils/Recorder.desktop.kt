package com.storyteller_f.a.app.compose_app.utils

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.io.files.Path
import java.io.File
import javax.sound.sampled.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

actual object Recorder {
    private var dataLine: TargetDataLine? = null
    private val flow = mutableStateOf(false)
    private var output: Path? = null

    @OptIn(ExperimentalUuidApi::class)
    internal actual fun startRecord() {
        val sampleRate = 44100f // 采样率
        val sampleSizeInBits = 16 // 采样大小
        val channels = 2 // 通道数（立体声）
        val signed = true // 是否有符号
        val bigEndian = false // 是否大端字节序
        val audioFormat = AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian)
        val info = DataLine.Info(
            TargetDataLine::class.java,
            audioFormat
        )
        // 检查是否支持音频格式
        if (!AudioSystem.isLineSupported(info)) {
            throw UnsupportedOperationException("unsupported")
        }

        val targetDataLine = AudioSystem.getLine(info) as TargetDataLine
        dataLine = targetDataLine
        targetDataLine.use { line ->
            line.open(audioFormat)
            line.start()
            flow.value = true

            val tempOutput = File(System.getProperty("java.io.tmpdir"), "${Uuid.random()}.wav")
            output = Path(tempOutput.absolutePath)
            AudioInputStream(line).use { ais ->
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, tempOutput)
                flow.value = false
            }
        }
    }

    internal actual fun stopRecord(): Path {
        dataLine?.stop()
        return output!!
    }

    internal actual val isRecording: MutableState<Boolean>
        get() = flow
}
