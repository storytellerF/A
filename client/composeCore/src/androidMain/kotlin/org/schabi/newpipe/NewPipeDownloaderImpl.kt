package org.schabi.newpipe

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.Stream

object NewPipeDownloaderImpl : Downloader() {
    private val mCookies: MutableMap<String, String> = HashMap()

    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun getCookies(url: String): String {
        val youtubeCookie = if (url.contains(YOUTUBE_DOMAIN)) {
            getCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY)
        } else {
            null
        }

        return Stream.of(youtubeCookie, getCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY))
            .filter { obj: String? -> Objects.nonNull(obj) }
            .flatMap { cookies: String? ->
                Arrays.stream(cookies!!.split("; *".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
            }
            .distinct()
            .collect(Collectors.joining("; "))
    }

    private fun getCookie(key: String): String? {
        return mCookies[key]
    }

    fun setCookie(key: String, cookie: String) {
        mCookies[key] = cookie
    }

    private fun removeCookie(@Suppress("SameParameterValue") key: String) {
        mCookies.remove(key)
    }

    fun updateYoutubeRestrictedModeCookies(context: Context) {
        val restrictedModeEnabled = context.getSharedPreferences("global", Context.MODE_PRIVATE)
            .getBoolean("youtube_restricted_mode_enabled", false)
        updateYoutubeRestrictedModeCookies(restrictedModeEnabled)
    }

    private fun updateYoutubeRestrictedModeCookies(youtubeRestrictedModeEnabled: Boolean) {
        if (youtubeRestrictedModeEnabled) {
            setCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY, YOUTUBE_RESTRICTED_MODE_COOKIE)
        } else {
            removeCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY)
        }
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()

        val requestBody = request.dataToSend()?.toRequestBody()

        val requestBuilder = okhttp3.Request.Builder()
            .method(httpMethod, requestBody)
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        val cookies = getCookies(url)
        if (cookies.isNotEmpty()) {
            requestBuilder.addHeader("Cookie", cookies)
        }

        headers.forEach { (headerName: String?, headerValueList: MutableList<String?>?) ->
            if (headerName != null && headerValueList != null) {
                requestBuilder.removeHeader(headerName)
                headerValueList.forEach(Consumer { headerValue: String? ->
                    requestBuilder.addHeader(headerName, headerValue!!)
                })
            }
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", url)
            }
            val responseBodyToReturn = response.body.use { body ->
                body.string()
            }
            val latestUrl = response.request.url.toString()
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                responseBodyToReturn,
                latestUrl
            )
        }
    }

    private const val USER_AGENT: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    private const val YOUTUBE_RESTRICTED_MODE_COOKIE_KEY: String = "youtube_restricted_mode_key"
    private const val YOUTUBE_RESTRICTED_MODE_COOKIE: String = "PREF=f2=8000000"
    private const val YOUTUBE_DOMAIN: String = "youtube.com"
}
