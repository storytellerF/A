/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.client.compose_core.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.LoadedFont
import com.storyteller_f.a.client.compose_core.Res
import com.storyteller_f.shared.commonJson
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.jetbrains.compose.resources.MissingResourceException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsException
import kotlin.js.JsString
import kotlin.js.Promise

actual fun loadFontFromLocal(path: String): FontFamily? = null

@OptIn(ExperimentalWasmJsInterop::class)
@JsModule("local-font-access")
private external object LocalFontAccess {
    fun isSupported(): Boolean

    fun loadPreferredFontJsonAfterUserActivation(): Promise<JsString>

    fun cancelLocalFontQuery()
}

/**
 * Loads a browser-local CJK font after user activation when permitted, with a bundled font as fallback.
 */
@Composable
@OptIn(ExperimentalTextApi::class)
@Suppress("LibraryEntitiesShouldNotBePublic")
actual fun rememberPlatformDefaultFontFamily(): FontFamily? {
    val fallbackFont = rememberBundledFallbackFont()
    val localFont = rememberPreferredLocalFont()
    val selectedFont = localFont ?: fallbackFont
    return remember(selectedFont) {
        selectedFont?.toFontFamily()
    }
}

@Composable
private fun rememberBundledFallbackFont(): FontBinary? {
    var fallbackFont by remember { mutableStateOf<FontBinary?>(null) }

    LaunchedEffect(Unit) {
        try {
            fallbackFont =
                FontBinary(
                    identity = FALLBACK_FONT_IDENTITY,
                    bytes = Res.readBytes(FALLBACK_FONT_RESOURCE),
                )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: MissingResourceException) {
            Napier.w(exception) { "Unable to load the bundled Wasm CJK fallback font." }
        }
    }
    return fallbackFont
}

@Composable
@OptIn(ExperimentalEncodingApi::class, ExperimentalWasmJsInterop::class)
private fun rememberPreferredLocalFont(): FontBinary? {
    var localFont by remember { mutableStateOf<FontBinary?>(null) }
    LaunchedEffect(Unit) {
        if (LocalFontAccess.isSupported()) {
            localFont = loadPreferredLocalFont()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            LocalFontAccess.cancelLocalFontQuery()
        }
    }
    return localFont
}

@OptIn(ExperimentalEncodingApi::class, ExperimentalWasmJsInterop::class)
private suspend fun loadPreferredLocalFont(): FontBinary? {
    val fontQuery = LocalFontAccess.loadPreferredFontJsonAfterUserActivation()
    return try {
        val selectedFont =
            commonJson.decodeFromString<LocalFontBinaryMetadata?>(
                fontQuery.await().toString(),
            )
        selectedFont?.toFontBinary()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: SerializationException) {
        logLocalFontFailure(exception)
        null
    } catch (exception: IllegalArgumentException) {
        logLocalFontFailure(exception)
        null
    } catch (exception: JsException) {
        logLocalFontFailure(exception)
        null
    }
}

@OptIn(ExperimentalTextApi::class)
private fun FontBinary.toFontFamily(): FontFamily = FontFamily(toLoadedFont())

@OptIn(ExperimentalTextApi::class)
private fun FontBinary.toLoadedFont() = LoadedFont(identity, { bytes }, FontWeight.Normal, FontStyle.Normal)

@OptIn(ExperimentalEncodingApi::class)
private fun LocalFontBinaryMetadata.toFontBinary(): FontBinary = FontBinary(identity, Base64.decode(base64))

private fun logLocalFontFailure(exception: Throwable) {
    Napier.w(exception) { "Unable to load a local CJK font; using the bundled fallback." }
}

@Serializable
private data class LocalFontBinaryMetadata(val identity: String, val base64: String)

private data class FontBinary(val identity: String, val bytes: ByteArray)

private const val FALLBACK_FONT_IDENTITY = "NotoSansSC-Regular"
private const val FALLBACK_FONT_RESOURCE = "files/fonts/noto_sans_sc_regular.otf"
