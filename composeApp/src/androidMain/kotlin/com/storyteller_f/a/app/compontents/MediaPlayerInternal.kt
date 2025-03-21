package com.storyteller_f.a.app.compontents

import android.net.Uri
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TapAndPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.media3.common.*
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.dokar.sonner.ToasterState
import com.storyteller_f.a.app.LocalMediaPlaySession
import com.storyteller_f.a.app.LocalToaster
import com.storyteller_f.a.app.MediaPlaySession
import com.storyteller_f.a.app.MediaProvider
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.utils.MarkdownObject
import io.github.aakira.napier.Napier
import io.github.aakira.napier.log
import io.ktor.client.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
fun MediaPlayerInternal(
    id: String,
    block: @Composable (MediaController, MediaPlaySession.Video?, LocalMediaPlaySession) -> Unit
) {
    val uuid = rememberSaveable {
        Uuid.random()
    }
    val currentSession = remember(id, uuid) {
        LocalMediaPlaySession(id, uuid)
    }

    log {
        "MediaPlayerInternal $uuid recomposing"
    }
    val player = MediaProvider.controller ?: return
    val playingSession by savedSession
    val isPip = rememberIsInPipMode()
    LaunchedEffect(playingSession, currentSession) {
        log {
            "MediaPlayerInternal $uuid check switch ${playingSession?.uuid} ${currentSession.uuid}"
        }
        playingSession?.let {
            if (it.uuid == null && it.id == currentSession.id || isPip) {
                MediaProvider.switch(currentSession)
            }
        }
    }
    DisposableEffect(null) {
        onDispose {
            Napier.d {
                "MediaPlayerInternal $uuid dispose $isPip"
            }
            MediaProvider.releaseView(currentSession)
        }
    }
    block(player, playingSession, currentSession)
}

@Composable
fun BoxScope.PlayerOccupy(currentSession: LocalMediaPlaySession) {
    IconButton({
        MediaProvider.switch(currentSession)
    }, modifier = Modifier.Companion.align(Alignment.Center)) {
        Icon(Icons.Default.TapAndPlay, "return")
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
fun BoxScope.PlayerWaiting(
    localMediaPlaySession: LocalMediaPlaySession,
    coverMediaInfo: MediaInfo?,
    obj: RemoteMediaItem
) {
    val contentType = obj.contentType
    if (coverMediaInfo != null) {
        val request = imageRequestInMarkdown(coverMediaInfo)
        AsyncImage(request, contentDescription = "cover", modifier = Modifier.fillMaxSize())
    }
    val scope = rememberCoroutineScope()
    val client = remember {
        HttpClient()
    }
    IconButton({
        scope.launch {
            val playList = if (contentType == M3U8_MIMETYPE) {
                parseM3UPlayList(obj, client)
            } else {
                listOf(PlayItem(obj.url))
            }
            val newSession = MediaPlaySession.Video(
                obj,
                contentType,
                playList,
                coverMediaInfo,
                localMediaPlaySession.uuid,
                null
            )
            MediaProvider.get(newSession) { player, s ->
                player.playNewMedia(s.playList, contentType)
            }
        }
    }, modifier = Modifier.Companion.align(Alignment.Center)) {
        Icon(Icons.Default.PlayArrow, "play")
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
fun listenPlayerState(
    player: MediaController,
    currentSession: LocalMediaPlaySession
): Pair<MutableState<Boolean>, MutableState<Boolean>> {
    val currentLoading = remember {
        mutableStateOf(false)
    }
    val currentIsPlaying = remember {
        mutableStateOf(false)
    }
    val toasterState = LocalToaster.current
    val scope = rememberCoroutineScope()
    DisposableEffect(currentSession, player) {
        val customListener = buildListener(player, currentSession.id, toasterState, scope, object : VideoListener {
            override fun onPlayStateChange(isPlaying: Boolean) {
                currentIsPlaying.value = isPlaying
            }

            override fun onUpdateSize(size: CustomVideoSize) {
                Napier.d {
                    "Video ${currentSession.uuid} updateSize $size"
                }
                MediaProvider.update(currentSession, size)
            }

            override fun onUpdateLoading(isLoading: Boolean) {
                currentLoading.value = isLoading
            }
        })
        player.addListener(customListener)
        onDispose {
            log {
                "Video ${currentSession.uuid} release listener"
            }
            player.removeListener(customListener)
        }
    }
    return Pair(currentLoading, currentIsPlaying)
}

private fun MediaController.playNewMedia(
    playList: List<PlayItem>,
    contentType: String
) {
    clearMediaItems()
    addMediaItems(playList.map { playItem ->
        val url1 = playItem.url
        MediaItem.Builder().setUri(url1)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setArtworkUri(playItem.icon?.let { Uri.parse(it) })
                    .setTitle(playItem.title)
                    .build()
            )
            .apply {
                if (contentType == M3U8_MIMETYPE && !url1.endsWith(".m3u8")) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }.build()
    })
    play()
}

private fun buildListener(
    player: Player,
    id: String,
    toasterState: ToasterState,
    scope: CoroutineScope,
    listener: VideoListener
): Player.Listener {
    return object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            super.onVideoSizeChanged(videoSize)
            listener.onUpdateSize(CustomVideoSize(videoSize.width, videoSize.height))
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            Napier.i {
                "Video $id error $error ${error.errorCode} ${error.errorCodeName}"
            }
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
            ) {
                toasterState.show("source error, restart after 1 seconds")
                scope.launch {
                    delay(1000)
                    player.play()
                }
            } else if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED) {
                toasterState.show("source error, skip to next after 1 seconds")
                scope.launch {
                    delay(1000)
                    player.seekToNext()
                    player.play()
                }
            }
        }

        override fun onIsLoadingChanged(isLoading: Boolean) {
            super.onIsLoadingChanged(isLoading)
            listener.onUpdateLoading(isLoading)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            listener.onPlayStateChange(isPlaying)
        }
    }
}
