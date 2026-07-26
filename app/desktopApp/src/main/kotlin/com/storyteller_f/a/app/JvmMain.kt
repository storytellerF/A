package com.storyteller_f.a.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.storyteller_f.a.app.common.Downloader
import com.storyteller_f.a.app.common.DownloaderImpl
import com.storyteller_f.a.app.common.ExternalUriHandler
import com.storyteller_f.a.app.common.SimpleTaskRegister
import com.storyteller_f.a.app.common.Uploader
import com.storyteller_f.a.app.common.UploaderImpl
import com.storyteller_f.a.app.utils.AppPlatformImpl
import com.storyteller_f.a.app.utils.appPlatformImpl
import com.storyteller_f.a.client.compose_core.components.ConstPlayItem
import com.storyteller_f.a.client.compose_core.components.LocalMediaPlaySession
import com.storyteller_f.a.client.compose_core.components.LocalMediaPlayerService
import com.storyteller_f.a.client.compose_core.components.MediaPlaySession
import com.storyteller_f.a.client.compose_core.components.MediaPlayerService
import com.storyteller_f.a.client.compose_core.components.RemoteMediaItem
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.setupKmpLogger
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.safeMessage
import dev.datlag.kcef.KCEF
import io.github.aakira.napier.Napier
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Button
import java.awt.Desktop
import java.awt.Dialog
import java.awt.Frame
import java.awt.TextArea
import java.io.File
import kotlin.system.exitProcess
import kotlin.uuid.ExperimentalUuidApi

@OptIn(DelicateCoroutinesApi::class)
val uiViewModel by lazy {
    UIViewModel(
        GlobalScope,
        getDesktopWsServerUrl(),
        getDesktopServerUrl()
    )
}

object JvmAppPlatformImpl : AppPlatformImpl {
    override fun startCall(roomId: PrimaryKey) = Unit
    override suspend fun notifyNotification(room: RoomInfo, bitmap: ImageBitmap?) = Unit
}

@OptIn(ExperimentalUuidApi::class)
private val jvmMediaPlayerService = object : MediaPlayerService() {
    override val enablePip: Boolean
        get() = false

    override fun fullscreen(remoteMediaItem: RemoteMediaItem) = Unit

    override suspend fun start(
        remoteMediaItem: RemoteMediaItem,
        localMediaPlaySession: LocalMediaPlaySession,
        playList: List<ConstPlayItem>
    ) {
        state.value = MediaPlaySession(
            remoteMediaItem = remoteMediaItem,
            playList = playList,
            uuids = listOf(localMediaPlaySession.uuid),
            videoSize = null
        )
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun main(args: Array<String>) {
    appPlatformImpl = JvmAppPlatformImpl
    setupKmpLogger()
    initForJvmMain(args)
    loadCryptoLibIfNeed()
    val taskRegister = SimpleTaskRegister(GlobalScope)
    val provider = object : ClientFileProvider {
        override suspend fun getDownloader(): Downloader = DownloaderImpl(uiViewModel, taskRegister)

        override suspend fun getUploader(): Uploader = UploaderImpl(uiViewModel, taskRegister)
    }
    application {
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                KCEF.init(
                    builder = {
                        val installationDirectory = File(System.getProperty("java.io.tmpdir"), "kcef")
                        installDir(installationDirectory)
                        settings { cachePath = File(installationDirectory, "cache").absolutePath }
                    },
                    onError = { error -> Napier.e(error) { "Failed to initialize in-app WebView" } },
                )
            }
        }
        Window(
            onCloseRequest = ::exitApplication,
            title = "A",
        ) {
            CompositionLocalProvider(
                LocalClientFileProvider provides provider,
                LocalUiViewModel provides uiViewModel,
                LocalMediaPlayerService provides jvmMediaPlayerService
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
}
