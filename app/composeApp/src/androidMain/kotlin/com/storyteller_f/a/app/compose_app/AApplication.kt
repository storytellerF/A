package com.storyteller_f.a.app.compose_app

import android.app.Application
import android.os.StrictMode
import com.storyteller_f.shared.appContextRef
import com.storyteller_f.shared.kmpLogger
import com.storyteller_f.shared.loadCryptoLibIfNeed
import io.github.aakira.napier.Napier
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.ReCaptchaActivity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.lang.ref.WeakReference

class AApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Napier.base(kmpLogger)
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy())
                .detectLeakedClosableObjects()
                .build()
        )

        appContextRef = WeakReference(this)
        loadCryptoLibIfNeed()
        setCookiesToDownloader(DownloaderImpl)
        NewPipe.init(DownloaderImpl, Localization.DEFAULT, ContentCountry.DEFAULT)
    }

    private fun setCookiesToDownloader(downloader: DownloaderImpl) {
        val prefs = getSharedPreferences("global", MODE_PRIVATE)
        val string = prefs.getString("recaptcha_cookies_key", null) ?: return
        downloader.setCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY, string)
        downloader.updateYoutubeRestrictedModeCookies(this)
    }
}
