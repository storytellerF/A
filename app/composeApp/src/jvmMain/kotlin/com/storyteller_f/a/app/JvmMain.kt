package com.storyteller_f.a.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kdroid.composenotification.builder.AppConfig
import com.kdroid.composenotification.builder.NotificationInitializer
import com.storyteller_f.a.app.common.Downloader
import com.storyteller_f.a.app.common.DownloaderImpl
import com.storyteller_f.a.app.common.ExternalUriHandler
import com.storyteller_f.a.app.common.SimpleTaskRegister
import com.storyteller_f.a.app.common.Uploader
import com.storyteller_f.a.app.common.UploaderImpl
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.shared.setupKmpLogger
import com.storyteller_f.shared.utils.safeMessage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import org.jetbrains.compose.resources.stringResource
import java.awt.BorderLayout
import java.awt.Button
import java.awt.Desktop
import java.awt.Dialog
import java.awt.Frame
import java.awt.TextArea
import java.awt.Toolkit
import kotlin.math.ceil
import kotlin.system.exitProcess

@OptIn(DelicateCoroutinesApi::class)
val uiViewModel by lazy {
    UIViewModel(
        GlobalScope,
        com.storyteller_f.a.app.AppConfig.WS_SERVER_URL,
        com.storyteller_f.a.app.AppConfig.SERVER_URL
    )
}

@OptIn(DelicateCoroutinesApi::class)
fun main(args: Array<String>) {
    setupKmpLogger()
    initForJvmMain(args)
    loadCryptoLibIfNeed()
    val taskRegister = SimpleTaskRegister(GlobalScope)
    val provider = object : ClientFileProvider {
        override suspend fun getDownloader(): Downloader = DownloaderImpl(uiViewModel, taskRegister)

        override suspend fun getUploader(): Uploader = UploaderImpl(uiViewModel, taskRegister)
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = stringResource(Res.string.app_name),
        ) {
            CompositionLocalProvider(
                LocalClientFileProvider provides provider,
                LocalUiViewModel provides uiViewModel
            ) {
                App()
            }
        }
    }
}

private fun initForJvmMain(args: Array<String>) {
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        Napier.e(e) {
            "uncaught exception"
        }
        Dialog(Frame(), e.safeMessage()).apply {
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
    if (System.getProperty("os.name").indexOf("Mac") > -1) {
        Desktop.getDesktop().setOpenURIHandler { uri ->
            ExternalUriHandler.onNewUri(uri.uri.toString())
        }
    } else {
        args.getOrNull(0)?.let {
            ExternalUriHandler.onNewUri(it)
        }
    }

    val dpi: Int = Toolkit.getDefaultToolkit().screenResolution
    val uiScale = ceil(dpi.toFloat() / 100)
    println("Screen DPI: $dpi $uiScale")
    println(System.getProperty("sun.java2d.uiScale.enabled"))
    println(System.getProperty("sun.java2d.uiScale"))
//    System.setProperty("sun.java2d.uiScale.enabled", "true")
//    System.setProperty("sun.java2d.uiScale", "$uiScale")
//    UIManager.put("swing.boldMetal", "false")
//    System.setProperty("awt.useSystemAAFontSettings", "on")

    NotificationInitializer.configure(
        AppConfig(appName = "A")
    )
}
