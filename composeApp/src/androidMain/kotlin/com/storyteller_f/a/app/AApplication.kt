package com.storyteller_f.a.app

import android.app.Application
import android.content.Context
import android.os.StrictMode
import com.storyteller_f.a.app.utils.restoreFromStorage
import com.storyteller_f.shared.contextRef
import com.storyteller_f.shared.loadIfNeed
import io.github.aakira.napier.Napier
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.ReCaptchaActivity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.unifiedpush.android.connector.UnifiedPush
import java.lang.ref.WeakReference

class AApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy())
                .detectLeakedClosableObjects()
                .build()
        )
        contextRef = WeakReference(this)
        loadIfNeed()
        restoreFromStorage()
        setCookiesToDownloader(DownloaderImpl)
        NewPipe.init(DownloaderImpl, Localization.DEFAULT, ContentCountry.DEFAULT)
    }

    private fun setCookiesToDownloader(downloader: DownloaderImpl) {
        val prefs = getSharedPreferences("global", Context.MODE_PRIVATE)
        val string = prefs.getString("recaptcha_cookies_key", null) ?: return
        downloader.setCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY, string)
        downloader.updateYoutubeRestrictedModeCookies(this)
    }
}
