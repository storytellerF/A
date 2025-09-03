package com.storyteller_f.a.app.compose_app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kdroid.composenotification.builder.AppConfig
import com.kdroid.composenotification.builder.NotificationInitializer
import com.storyteller_f.a.app.compose_app.common.DownloaderImpl
import com.storyteller_f.a.app.compose_app.common.UploaderImpl
import com.storyteller_f.shared.loadCryptoLibIfNeed
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import java.awt.BorderLayout
import java.awt.Button
import java.awt.Dialog
import java.awt.Frame
import java.awt.TextArea
import java.awt.Toolkit
import kotlin.math.ceil
import kotlin.system.exitProcess

@OptIn(DelicateCoroutinesApi::class)
val uiViewModel = UIViewModel(
    GlobalScope,
    com.storyteller_f.a.app.compose_app.AppConfig.WS_SERVER_URL,
    com.storyteller_f.a.app.compose_app.AppConfig.SERVER_URL
)

@OptIn(DelicateCoroutinesApi::class)
fun main() {
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        Dialog(Frame(), e.message ?: "Error").apply {
            layout = BorderLayout()
            val label = TextArea(e.stackTraceToString())
            add(label, BorderLayout.CENTER)
            val button = Button("OK").apply {
                addActionListener {
                    dispose()
                    exitProcess(1)
                }
            }
            add(button, BorderLayout.SOUTH)
            setSize(300, 300)
            isVisible = true
        }
    }

    val dpi: Int = Toolkit.getDefaultToolkit().screenResolution
    val uiScale = ceil(dpi.toFloat() / 100)
    println("Screen DPI: $dpi $uiScale")
//    println(System.getProperty("sun.java2d.uiScale.enabled"))
//    println(System.getProperty("sun.java2d.uiScale"))
//    System.setProperty("sun.java2d.uiScale.enabled", "true")
//    System.setProperty("sun.java2d.uiScale", "$uiScale")
//    UIManager.put("swing.boldMetal", "false")
//    System.setProperty("awt.useSystemAAFontSettings", "on")

    NotificationInitializer.configure(
        AppConfig(
            appName = "My awesome app",
        )
    )
    val downloader = DownloaderImpl(GlobalScope, uiViewModel)
    val uploader = UploaderImpl(GlobalScope, uiViewModel)
    val provider = object : ClientFileProvider {
        override fun getDownloader() = downloader

        override fun getUploader() = uploader

    }
    loadCryptoLibIfNeed()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "A",
        ) {
            CompositionLocalProvider(LocalClientFileProvider provides provider) {
                App()
            }
        }
    }
}
