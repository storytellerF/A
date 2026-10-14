/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.app.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.storyteller_f.a.app.LocalGlobalDialog
import com.storyteller_f.a.app.LocalSessionManager
import com.storyteller_f.a.app.common.ActivityPreviewHost
import com.storyteller_f.a.client.core.getJoinedPinnedTopics
import com.storyteller_f.a.client.core.getRoomActivities
import com.storyteller_f.a.client.core.getUnreadRoomActivities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

// One application-owned coordination dispatcher; requests and preview processing use worker threads.
private val previewCoordination = Dispatchers.Default.limitedParallelism(1)

@Composable
internal fun rememberActivityPreviewHost(): ActivityPreviewHost {
    val session = LocalSessionManager.current
    val scope = rememberCoroutineScope()
    val host =
        remember(session) {
            ActivityPreviewHost(
                scope,
                previewCoordination,
                Dispatchers.Default,
                session::getRoomActivities,
                session::getUnreadRoomActivities,
                session::getJoinedPinnedTopics,
            )
        }
    DisposableEffect(host) { onDispose { host.close() } }
    return host
}

@Composable
internal fun ObserveActivityEvents(
    host: ActivityPreviewHost,
    context: Boolean = false,
    events: Flow<Any> = LocalGlobalDialog.current.context.events,
) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(host, owner, context, events) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                if (context) {
                    host.refreshUnread()
                    host.refreshPinned()
                } else {
                    host.refreshRows()
                }
                events.collect { host.onEvent(it, context) }
            } finally {
                host.pause()
            }
        }
    }
}
