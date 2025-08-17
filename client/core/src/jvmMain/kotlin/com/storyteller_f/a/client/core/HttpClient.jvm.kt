package com.storyteller_f.a.client.core

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

actual fun getClient(block: HttpClientConfig<*>.() -> Unit): HttpClient {
    val removeHeaderInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()

        // 创建一个新的请求，移除特定头
        val modifiedRequest = originalRequest.newBuilder()
            .removeHeader("Accept-Charset") // 移除 Accept-Charset 头
            .build()

        // 继续请求链
        chain.proceed(modifiedRequest)
    }
    return HttpClient(OkHttp) {
        engine {
            preconfigured = OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .build()
            addInterceptor(removeHeaderInterceptor)
        }
        block()
    }
}