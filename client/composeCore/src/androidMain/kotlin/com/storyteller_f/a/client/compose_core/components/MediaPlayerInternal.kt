/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.client.compose_core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TapAndPlay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.storyteller_f.a.client.core.LoadingState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Composable
fun MediaPlayerFilled(
    remoteMediaItem: RemoteMediaItem,
    block: @Composable ((MediaPlaySession?, LocalMediaPlaySession) -> Unit),
) {
    MediaPlayerInternal(remoteMediaItem, true, block)
}

@Composable
fun MediaPlayerEmbed(
    remoteMediaItem: RemoteMediaItem,
    block: @Composable ((MediaPlaySession?, LocalMediaPlaySession) -> Unit),
) {
    MediaPlayerInternal(remoteMediaItem, false) { session, localSession ->
        EmbedMediaPlayerContainer(session, localSession, block)
    }
}

@Composable
fun MediaPlayerFullScreen(
    remoteMediaItem: RemoteMediaItem,
    block: @Composable ((MediaPlaySession?, LocalMediaPlaySession) -> Unit),
) {
    MediaPlayerInternal(remoteMediaItem, true, block)
}

@OptIn(ExperimentalUuidApi::class)
@Composable
fun MediaPlayerInternal(
    remoteMediaItem: RemoteMediaItem,
    isSingleton: Boolean,
    block: @Composable (MediaPlaySession?, LocalMediaPlaySession) -> Unit,
) {
    val uuid =
        rememberSaveable {
            Uuid.random()
        }
    val localMediaPlaySession =
        remember(remoteMediaItem, uuid) {
            LocalMediaPlaySession(remoteMediaItem.url, uuid)
        }

    Napier.i(tag = "MediaPlayer") {
        "MediaPlayerInternal $uuid recomposing"
    }
    val mediaPlayerService = LocalMediaPlayerService.current
    val playingSession by mediaPlayerService.state.collectAsState()
    LaunchedEffect(playingSession, localMediaPlaySession, isSingleton) {
        Napier.i(tag = "MediaPlayer") {
            "MediaPlayerInternal $uuid switch uuids: ${playingSession?.uuids ?: "<none>"}, isSingleton: $isSingleton"
        }
        playingSession?.let { session ->
            if (session.id == localMediaPlaySession.id && (session.lastUuid == null || isSingleton)) {
                mediaPlayerService.switch(localMediaPlaySession)
            }
        }
    }
    val context = LocalContext.current.findActivity()
    DisposableEffect(null) {
        onDispose {
            val isPip = context.isInPictureInPictureMode
            Napier.d(tag = "MediaPlayer") {
                "MediaPlayerInternal $uuid dispose isPip: $isPip isSingleton: $isSingleton"
            }
            // 从画中画/全屏退回，不要暂停播放器
            mediaPlayerService.release(localMediaPlaySession, isPip || isSingleton)
        }
    }
    block(playingSession, localMediaPlaySession)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun EmbedMediaPlayerContainer(
    playingSession: MediaPlaySession?,
    localMediaPlaySession: LocalMediaPlaySession,
    block: @Composable (MediaPlaySession?, LocalMediaPlaySession) -> Unit,
) {
    var showSheet by remember {
        mutableStateOf(false)
    }
    MediaObjectBlock {
        Box(modifier = Modifier.weight(1f)) {
            block(playingSession, localMediaPlaySession)
        }
        EmbedMediaPlayerMenus(
            localMediaPlaySession,
            playingSession,
        ) {
            showSheet = true
        }
    }
    val sheetState = rememberModalBottomSheetState()
    val mediaPlayerService = LocalMediaPlayerService.current
    if (playingSession?.lastUuid == localMediaPlaySession.uuid) {
        VideoPlaylistPicker(
            showSheet,
            sheetState,
            {
                showSheet = false
            },
            playingSession.playList,
        ) { _, i ->
            switchPlaylist(i, mediaPlayerService)
        }
    }
}

private fun switchPlaylist(i: Int, mediaPlayerService: MediaPlayerService) {
    val player = mediaPlayerService.controller.value ?: return

    player.seekTo(i, 0)
    player.play()
}

@Composable
fun BoxScope.PlayerOccupy(localMediaPlaySession: LocalMediaPlaySession) {
    val mediaPlayerService = LocalMediaPlayerService.current
    val scope = rememberCoroutineScope()
    IconButton({
        scope.launch {
            mediaPlayerService.switch(localMediaPlaySession)
        }
    }, modifier = Modifier.align(Alignment.Center)) {
        Icon(Icons.Default.TapAndPlay, "return")
    }
}

@Composable
fun BoxScope.PlayerWaiting(localMediaPlaySession: LocalMediaPlaySession, remoteMediaItem: RemoteMediaItem) {
    val playListHandler = LocalMediaPlayListHandlerProvider.current.playListHandler(remoteMediaItem)
    val playList by playListHandler.data.collectAsState()
    val loadingState by playListHandler.state.collectAsState()
    val coverMediaInfo = remoteMediaItem.cover
    if (coverMediaInfo != null) {
        val request = imageRequestInMarkdown(coverMediaInfo)
        AsyncImage(request, contentDescription = "cover", modifier = Modifier.fillMaxSize())
    } else {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).fillMaxSize())
    }
    PlayerWaitingState(loadingState, playListHandler::refresh)
    val mediaPlayerService = LocalMediaPlayerService.current
    val scope = rememberCoroutineScope()
    val availablePlayList = playList.orEmpty()
    if (loadingState !is LoadingState.Error && loadingState != LoadingState.Loading) {
        IconButton({
            scope.launch {
                mediaPlayerService.start(remoteMediaItem, localMediaPlaySession, availablePlayList)
            }
        }, modifier = Modifier.align(Alignment.Center), enabled = availablePlayList.isNotEmpty()) {
            Icon(Icons.Default.PlayArrow, "play")
        }
    }
    Text(
        remoteMediaItem.title ?: remoteMediaItem.name,
        modifier =
        Modifier
            .align(Alignment.BottomStart)
            .padding(10.dp),
        maxLines = 2,
    )
}

@Composable
private fun BoxScope.PlayerWaitingState(loadingState: LoadingState?, refresh: () -> Unit) {
    if (loadingState is LoadingState.Error) {
        Box(
            modifier =
            Modifier
                .align(Alignment.Center)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            ExceptionCell(loadingState.e, refresh)
        }
    } else if (loadingState == LoadingState.Loading) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}

data class MediaPlayerState(
    val currentLoading: Boolean,
    val currentIsPlaying: Boolean,
    val currentPlayingItem: MediaItem?,
)

@OptIn(ExperimentalUuidApi::class)
@Composable
fun rememberPlayerState(
    player: MediaController?,
    localMediaPlaySession: LocalMediaPlaySession,
): State<MediaPlayerState> {
    player ?: return remember {
        mutableStateOf(MediaPlayerState(currentLoading = false, currentIsPlaying = false, currentPlayingItem = null))
    }
    var currentLoading by remember {
        mutableStateOf(player.isLoading)
    }
    var currentIsPlaying by remember {
        mutableStateOf(player.isPlaying)
    }
    var currentPlaying by remember {
        mutableStateOf<MediaItem?>(null)
    }
    val mediaPlayerService = LocalMediaPlayerService.current
    DisposableEffect(localMediaPlaySession, player) {
        val customListener =
            buildListener(
                player,
                object :
                    VideoListener {
                    override fun onPlayStateChange(isPlaying: Boolean) {
                        Napier.d(tag = "MediaPlayer") {
                            "rememberPlayerState ${localMediaPlaySession.uuid} playStateChange $isPlaying"
                        }
                        currentIsPlaying = isPlaying
                    }

                    override fun onUpdateSize(size: CustomVideoSize) {
                        Napier.d(tag = "MediaPlayer") {
                            "rememberPlayerState ${localMediaPlaySession.uuid} updateSize $size"
                        }
                        mediaPlayerService.update(localMediaPlaySession, size)
                    }

                    override fun onUpdateLoading(isLoading: Boolean) {
                        currentLoading = isLoading
                    }

                    override fun onMediaItemChanged(mediaId: String?, currentMediaItemIndex: Int) {
                        currentPlaying =
                            if (currentMediaItemIndex < player.mediaItemCount) {
                                player.getMediaItemAt(currentMediaItemIndex)
                            } else {
                                null
                            }
                    }
                },
            )
        player.addListener(customListener)
        onDispose {
            Napier.d(tag = "MediaPlayer") {
                "rememberPlayerState ${localMediaPlaySession.uuid} release listener"
            }
            player.removeListener(customListener)
        }
    }
    return remember {
        derivedStateOf {
            MediaPlayerState(currentLoading, currentIsPlaying, currentPlaying)
        }
    }
}

fun MediaController.playNewMedia(playList: List<ConstPlayItem>) {
    clearMediaItems()

    addMediaItems(
        playList.map { playItem ->
            val uri = playItem.url
            MediaItem.Builder().setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setArtworkUri(playItem.icon?.toUri())
                        .setTitle(playItem.title)
                        .build(),
                ).build()
        },
    )
    play()
}

private fun buildListener(player: Player, listener: VideoListener): Player.Listener =
    object : Player.Listener {
    override fun onVideoSizeChanged(videoSize: VideoSize) {
        super.onVideoSizeChanged(videoSize)
        listener.onUpdateSize(CustomVideoSize(videoSize.width, videoSize.height))
    }

    override fun onIsLoadingChanged(isLoading: Boolean) {
        super.onIsLoadingChanged(isLoading)
        listener.onUpdateLoading(isLoading)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        listener.onPlayStateChange(isPlaying)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)
        listener.onMediaItemChanged(mediaItem?.mediaId, player.currentMediaItemIndex)
    }
}
