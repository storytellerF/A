package com.storyteller_f.a.app

import android.app.Application
import android.content.Intent
import android.os.StrictMode
import com.storyteller_f.a.app.core.components.ConstPlayItem
import com.storyteller_f.a.app.core.components.LocalMediaPlaySession
import com.storyteller_f.a.app.core.components.MediaPlayerService
import com.storyteller_f.a.app.core.components.RemoteMediaItem
import com.storyteller_f.a.app.core.components.mainActivityRef
import com.storyteller_f.a.app.core.startPlayMedia
import com.storyteller_f.shared.appContextRef
import com.storyteller_f.shared.commonJson
import com.storyteller_f.shared.loadCryptoLibIfNeed
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import org.schabi.newpipe.NewPipeDownloaderImpl
import org.schabi.newpipe.ReCaptchaActivity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.lang.ref.WeakReference

@OptIn(DelicateCoroutinesApi::class)
val uiViewModel by lazy {
    UIViewModel(GlobalScope, AppConfig.WS_SERVER_URL, AppConfig.SERVER_URL)
}

class AApplication : Application() {
    @OptIn(DelicateCoroutinesApi::class)
    val mediaPlayer = buildMediaPlayer()

    override fun onCreate() {
        super.onCreate()
        setupAppLogger(this)
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

private fun buildMediaPlayer(): MediaPlayerService = object : MediaPlayerService() {
    override fun fullscreen(remoteMediaItem: RemoteMediaItem) {
        val context = mainActivityRef?.get() ?: return
        context.startActivity(Intent(context, MediaPlayerActivity::class.java).apply {
//                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            putExtra("json", commonJson.encodeToString<RemoteMediaItem>(remoteMediaItem))
        })
    }

    override suspend fun start(
        remoteMediaItem: RemoteMediaItem,
        localMediaPlaySession: LocalMediaPlaySession,
        playList: List<ConstPlayItem>
    ) {
        val instance = uiViewModel.instance.value
        instance.controller.startPlayMedia(remoteMediaItem, localMediaPlaySession, this, playList)
    }

    override val enablePip: Boolean
        get() = true
}
