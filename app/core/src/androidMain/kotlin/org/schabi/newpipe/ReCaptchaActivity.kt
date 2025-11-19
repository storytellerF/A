package org.schabi.newpipe

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.core.content.edit
import com.kevinnzou.web.*
import org.schabi.newpipe.extractor.utils.Utils

class ReCaptchaActivity : ComponentActivity() {
    private var foundCookies = ""

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = sanitizeRecaptchaUrl(intent.getStringExtra(RECAPTCHA_URL_EXTRA))
        // set return to Cancel by default
        setResult(RESULT_CANCELED)

        setContent {
            val state = rememberWebViewState(url)
            val navigator = rememberWebViewNavigator()
            Scaffold(topBar = {
                TopAppBar({
                    Text("Error")
                }, navigationIcon = {
                    IconButton({
                        saveCookiesAndFinish(state.lastLoadedUrl, navigator)
                    }) {
                        Icon(Icons.Default.Done, "done")
                    }
                })
            }) { paddingValues ->
                WebView(state, onCreated = { it.settings.javaScriptEnabled = true }, client = object :
                    AccompanistWebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        Log.d(TAG, "shouldOverrideUrlLoading: url=" + request.url.toString())

                        handleCookiesFromUrl(request.url.toString())
                        return false
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        handleCookiesFromUrl(url)
                    }
                }, navigator = navigator, modifier = Modifier.padding(paddingValues))
            }
        }
        CookieManager.getInstance().removeAllCookies(null)
    }

    private fun saveCookiesAndFinish(currentUrl: String?, navigator: WebViewNavigator) {
        // try to get cookies of unclosed page
        handleCookiesFromUrl(currentUrl)
        Log.d(TAG, "saveCookiesAndFinish: foundCookies=$foundCookies")

        if (foundCookies.isNotEmpty()) {
            // save cookies to preferences
            val prefs = this.getSharedPreferences("global", MODE_PRIVATE)
            prefs.edit { putString("recaptcha_cookies_key", foundCookies) }

            // give cookies to Downloader class
            NewPipeDownloaderImpl.setCookie(RECAPTCHA_COOKIES_KEY, foundCookies)
            setResult(RESULT_OK)
        }

        // Navigate to blank page (unloads YouTube to prevent background playback)
        navigator.loadUrl("about:blank")

        finish()
    }

    private fun handleCookiesFromUrl(url: String?) {
        Log.d(TAG, "handleCookiesFromUrl: url=" + (url ?: "null"))

        if (url == null) {
            return
        }

        val cookies = CookieManager.getInstance().getCookie(url)
        handleCookies(cookies)

        // sometimes cookies are inside the url
        val abuseStart = url.indexOf("google_abuse=")
        if (abuseStart != -1) {
            val abuseEnd = url.indexOf("+path")

            try {
                handleCookies(Utils.decodeUrlUtf8(url.substring(abuseStart + 13, abuseEnd)))
            } catch (e: StringIndexOutOfBoundsException) {
                Log.e(
                    TAG,
                    ("handleCookiesFromUrl: invalid google abuse starting at " +
                        abuseStart + " and ending at " + abuseEnd + " for url " + url),
                    e
                )
            }
        }
    }

    private fun handleCookies(cookies: String?) {
        Log.d(TAG, "handleCookies: cookies=" + (cookies ?: "null"))

        if (cookies == null) {
            return
        }

        addYoutubeCookies(cookies)
        // add here methods to extract cookies for other services
    }

    private fun addYoutubeCookies(cookies: String) {
        if (cookies.contains("s_gl=") || cookies.contains("goojf=") ||
            cookies.contains("VISITOR_INFO1_LIVE=") ||
            cookies.contains("GOOGLE_ABUSE_EXEMPTION=")
        ) {
            // YouTube seems to also need the other cookies:
            addCookie(cookies)
        }
    }

    private fun addCookie(cookie: String) {
        if (foundCookies.contains(cookie)) {
            return
        }

        foundCookies += if (foundCookies.isEmpty() || foundCookies.endsWith("; ")) {
            cookie
        } else if (foundCookies.endsWith(";")) {
            " $cookie"
        } else {
            "; $cookie"
        }
    }

    companion object {
        const val RECAPTCHA_URL_EXTRA: String = "recaptcha_url_extra"
        private const val TAG = "ReCaptchaActivity"
        private const val YT_URL: String = "https://www.youtube.com"
        const val RECAPTCHA_COOKIES_KEY: String = "recaptcha_cookies"

        fun sanitizeRecaptchaUrl(url: String?): String {
            return if (url == null || url.trim { it <= ' ' }.isEmpty()) {
                YT_URL // YouTube is the most likely service to have thrown a recaptcha
            } else {
                // remove "pbj=1" parameter from YouTube urls, as it makes the page JSON and not HTML
                url.replace("&pbj=1", "").replace("pbj=1&", "").replace("?pbj=1", "")
            }
        }
    }
}
