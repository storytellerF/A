package com.storyteller_f.a.app.compose_app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.StrictMode
import com.storyteller_f.a.app.compose_app.components.mainAppRef
import com.storyteller_f.a.app.core.components.LocalMediaPlaySession
import com.storyteller_f.a.app.core.components.MediaPlayerService
import com.storyteller_f.a.app.core.components.RemoteMediaItem
import com.storyteller_f.a.app.core.components.startPlay
import com.storyteller_f.shared.appContextRef
import com.storyteller_f.shared.commonJson
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.shared.setupKmpLogger
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import org.schabi.newpipe.NewPipeDownloaderImpl
import org.schabi.newpipe.ReCaptchaActivity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.lang.ref.WeakReference
import kotlin.uuid.ExperimentalUuidApi

@OptIn(DelicateCoroutinesApi::class)
val uiViewModel by lazy {
    UIViewModel(GlobalScope, AppConfig.WS_SERVER_URL, AppConfig.SERVER_URL)
}

class AApplication : Application() {
    @OptIn(DelicateCoroutinesApi::class)
    val mediaPlayer = object : MediaPlayerService() {
        override fun fullscreen(remoteMediaItem: RemoteMediaItem) {
            val context = mainAppRef?.get() ?: return
            context.startActivity(Intent(context, MediaPlayerActivity::class.java).apply {
//                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                putExtra("json", commonJson.encodeToString<RemoteMediaItem>(remoteMediaItem))
            })
        }

        override suspend fun start(
            remoteMediaItem: RemoteMediaItem,
            localMediaPlaySession: LocalMediaPlaySession
        ) {
            val service = this
            val instance = uiViewModel.instance.value
            instance.controller.startPlayMedia(
                remoteMediaItem,
                localMediaPlaySession,
                service,
                this@AApplication
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        setupKmpLogger()
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy())
                .detectLeakedClosableObjects()
                .build()
        )

        appContextRef = WeakReference(this)
        loadCryptoLibIfNeed()
        setCookiesToDownloader(NewPipeDownloaderImpl)
        NewPipe.init(NewPipeDownloaderImpl, Localization.DEFAULT, ContentCountry.DEFAULT)
    }

    private fun setCookiesToDownloader(downloader: NewPipeDownloaderImpl) {
        val prefs = getSharedPreferences("global", MODE_PRIVATE)
        val string = prefs.getString("recaptcha_cookies_key", null) ?: return
        downloader.setCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY, string)
        downloader.updateYoutubeRestrictedModeCookies(this)
    }
}

@OptIn(ExperimentalUuidApi::class)
private suspend fun AppGlobalDialogController.startPlayMedia(
    remoteMediaItem: RemoteMediaItem,
    localMediaPlaySession: LocalMediaPlaySession,
    mediaPlayerService: MediaPlayerService,
    context: Context
) {
    val contentType = remoteMediaItem.contentType
    useResult {
        mediaPlayerService.startPlay(contentType, remoteMediaItem, context, localMediaPlaySession)
    }
}
