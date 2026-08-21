/*
 * This is a private project. All rights reserved.
*/

package com.storyteller_f.a.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.storyteller_f.a.app.App
import com.storyteller_f.a.app.IAccountInstance
import com.storyteller_f.a.app.LocalUiViewModel
import com.storyteller_f.a.app.UIViewModel
import com.storyteller_f.a.app.getWasmServerUrl
import com.storyteller_f.a.app.getWasmWsServerUrl
import com.storyteller_f.a.client.compose_core.components.ConstPlayItem
import com.storyteller_f.a.client.compose_core.components.LocalMediaPlaySession
import com.storyteller_f.a.client.compose_core.components.LocalMediaPlayerService
import com.storyteller_f.a.client.compose_core.components.MediaPlayerService
import com.storyteller_f.a.client.compose_core.components.RemoteMediaItem
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        val httpUrl = wasmQueryParameter("appiumHttpUrl") ?: getWasmServerUrl()
        val wsUrl = wasmQueryParameter("appiumWsUrl") ?: getWasmWsServerUrl()
        val uiViewModel = remember(httpUrl, wsUrl) { UIViewModel(MainScope(), wsUrl, httpUrl) }
        LaunchedEffect(uiViewModel) {
            if (window.location.search.contains("appium=true")) {
                uiViewModel.instance.collectLatest { instance ->
                    localStorage.setItem(
                        "appium.session_ready",
                        (instance !is IAccountInstance.None).toString(),
                    )
                }
            }
        }
        CompositionLocalProvider(
            LocalUiViewModel provides uiViewModel,
            LocalMediaPlayerService provides WasmMediaPlayerService,
        ) {
            App()
        }
    }
}

private object WasmMediaPlayerService : MediaPlayerService() {
    override val enablePip = false

    override fun fullscreen(remoteMediaItem: RemoteMediaItem) = Unit

    override suspend fun start(
        remoteMediaItem: RemoteMediaItem,
        localMediaPlaySession: LocalMediaPlaySession,
        playList: List<ConstPlayItem>,
    ) = Unit
}

private fun wasmQueryParameter(name: String): String? =
    window.location.search
    .removePrefix("?")
    .split('&')
    .firstOrNull { it.substringBefore('=') == name }
    ?.substringAfter('=', missingDelimiterValue = "")
    ?.takeIf(String::isNotEmpty)
