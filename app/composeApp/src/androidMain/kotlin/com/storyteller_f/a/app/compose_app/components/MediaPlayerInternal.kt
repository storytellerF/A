package com.storyteller_f.a.app.compose_app.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TapAndPlay
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
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.storyteller_f.a.app.compose_app.AppGlobalDialogController
import com.storyteller_f.a.app.compose_app.LocalGlobalDialog
import com.storyteller_f.a.app.compose_app.utils.parseM3UPlayList
import com.storyteller_f.a.app.core.components.ConstPlayItem
import com.storyteller_f.a.app.core.components.CustomVideoSize
import com.storyteller_f.a.app.core.components.LocalMediaPlaySession
import com.storyteller_f.a.app.core.components.LocalMediaPlayerService
import com.storyteller_f.a.app.core.components.MediaPlaySession
import com.storyteller_f.a.app.core.components.MediaPlayerService
import com.storyteller_f.a.app.core.components.RemoteMediaItem
import com.storyteller_f.a.app.core.components.Toast
import com.storyteller_f.a.app.core.components.imageRequestInMarkdown
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.utils.UNIT_RESULT
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.ReCaptchaActivity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.StreamingService.LinkType.CHANNEL
import org.schabi.newpipe.extractor.StreamingService.LinkType.NONE
import org.schabi.newpipe.extractor.StreamingService.LinkType.PLAYLIST
import org.schabi.newpipe.extractor.StreamingService.LinkType.STREAM
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Composable
fun MediaPlayerFilled(
    remoteMediaItem: RemoteMediaItem,
    block: @Composable ((MediaPlaySession?, LocalMediaPlaySession) -> Unit)
) {
    MediaPlayerInternal(remoteMediaItem, true, block)
}

@Composable
fun MediaPlayerEmbed(
    remoteMediaItem: RemoteMediaItem,
    block: @Composable ((MediaPlaySession?, LocalMediaPlaySession) -> Unit)
) {
    MediaPlayerInternal(remoteMediaItem, false) { session, localSession ->
        EmbedMediaPlayerContainer(session, localSession, remoteMediaItem.contentType, block)
    }
}

@Composable
fun MediaPlayerFullScreen(
    remoteMediaItem: RemoteMediaItem,
    block: @Composable ((MediaPlaySession?, LocalMediaPlaySession) -> Unit)
) {
    MediaPlayerInternal(remoteMediaItem, true, block)
}

@OptIn(ExperimentalUuidApi::class)
@Composable
fun MediaPlayerInternal(
    remoteMediaItem: RemoteMediaItem,
    isSingleton: Boolean,
    block: @Composable (MediaPlaySession?, LocalMediaPlaySession) -> Unit
) {
    val uuid = rememberSaveable {
        Uuid.random()
    }
    val localMediaPlaySession = remember(remoteMediaItem, uuid) {
        LocalMediaPlaySession(remoteMediaItem.url, uuid)
    }

    Napier.i(tag = "MediaPlayer") {
        "MediaPlayerInternal $uuid recomposing"
    }
    val mediaPlayerService = LocalMediaPlayerService.current
    val playingSession by mediaPlayerService.state.collectAsState()
    LaunchedEffect(playingSession, localMediaPlaySession, isSingleton) {
        Napier.i(tag = "MediaPlayer") {
            "MediaPlayerInternal $uuid switch uuids: ${playingSession?.uuids}, isSingleton: $isSingleton"
        }
        mediaPlayerService.switchSessionIfNeed(playingSession, localMediaPlaySession, isSingleton)
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

@OptIn(ExperimentalUuidApi::class)
private suspend fun MediaPlayerService.switchSessionIfNeed(
    playingSession: MediaPlaySession?,
    localMediaPlaySession: LocalMediaPlaySession,
    isSingleton: Boolean
) {
    if (playingSession == null) return
    if (playingSession.id == localMediaPlaySession.id && (playingSession.lastUuid == null || isSingleton)) {
        switch(localMediaPlaySession)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun EmbedMediaPlayerContainer(
    playingSession: MediaPlaySession?,
    localMediaPlaySession: LocalMediaPlaySession,
    contentType: String,
    block: @Composable (MediaPlaySession?, LocalMediaPlaySession) -> Unit,
) {
    var showSheet by remember {
        mutableStateOf(false)
    }
    ObjectBlock {
        Box(modifier = Modifier.weight(1f)) {
            block(playingSession, localMediaPlaySession)
        }
        EmbedMediaPlayerMenus(localMediaPlaySession, playingSession, contentType) {
            showSheet = true
        }
    }
    val sheetState = rememberModalBottomSheetState()
    val mediaPlayerService = LocalMediaPlayerService.current
    if (playingSession?.lastUuid == localMediaPlaySession.uuid) {
        VideoPlaylistPicker(showSheet, sheetState, {
            showSheet = false
        }, playingSession.playList) { _, i ->
            switchPlaylist(i, mediaPlayerService)
        }
    }
}

private fun switchPlaylist(i: Int, playerComponent1: MediaPlayerService) {
    val player = playerComponent1.controller.value ?: return

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
fun BoxScope.PlayerWaiting(
    localMediaPlaySession: LocalMediaPlaySession,
    remoteMediaItem: RemoteMediaItem
) {
    val coverMediaInfo = remoteMediaItem.cover
    if (coverMediaInfo != null) {
        val request = imageRequestInMarkdown(coverMediaInfo)
        AsyncImage(request, contentDescription = "cover", modifier = Modifier.fillMaxSize())
    } else {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .fillMaxSize()
        )
    }
    val scope = rememberCoroutineScope()
    val client = remember {
        HttpClient()
    }
    val context = LocalContext.current
    val globalDialogController = LocalGlobalDialog.current
    val mediaPlayerService = LocalMediaPlayerService.current
    IconButton({
        scope.launch {
            globalDialogController.startPlay(
                remoteMediaItem,
                client,
                localMediaPlaySession,
                context,
                mediaPlayerService
            )
        }
    }, modifier = Modifier.align(Alignment.Center)) {
        Icon(Icons.Default.PlayArrow, "play")
    }
    if (remoteMediaItem.contentType == FileInfo.M3U8_MIMETYPE ||
        remoteMediaItem.contentType == FileInfo.YOUTUBE_MIMETYPE
    ) {
        Text(
            remoteMediaItem.title ?: remoteMediaItem.url,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
            maxLines = 2
        )
    } else {
        Text(
            remoteMediaItem.title ?: remoteMediaItem.name,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
            maxLines = 2
        )
    }
}

@OptIn(ExperimentalUuidApi::class)
private suspend fun AppGlobalDialogController.startPlay(
    remoteMediaItem: RemoteMediaItem,
    client: HttpClient,
    localMediaPlaySession: LocalMediaPlaySession,
    context: Context,
    playerComponent: MediaPlayerService
) {
    val contentType = remoteMediaItem.contentType
    useResult {
        val playList = when (contentType) {
            FileInfo.M3U8_MIMETYPE -> parseM3UPlayList(remoteMediaItem, client)
            FileInfo.YOUTUBE_MIMETYPE, FileInfo.SOUND_CLOUD_MIME_TYPE -> getPlaylistFromNewPipe(
                remoteMediaItem,
                context
            )

            else -> listOf(ConstPlayItem(remoteMediaItem.url, title = remoteMediaItem.url))
        }
        if (playList.isNotEmpty()) {
            val newSession = MediaPlaySession(
                remoteMediaItem,
                playList,
                listOf(localMediaPlaySession.uuid),
                null
            )
            playerComponent.get(newSession) { player, s ->
                player.playNewMedia(s.playList, contentType)
            }
            UNIT_RESULT
        } else {
            Result.failure(Exception("can't play"))
        }
    }
}

suspend fun getPlaylistFromNewPipe(
    remoteMediaItem: RemoteMediaItem,
    context: Context
): List<ConstPlayItem> {
    val s = NewPipe.getServiceByUrl(remoteMediaItem.url)
    val name = when (remoteMediaItem.contentType) {
        FileInfo.YOUTUBE_MIMETYPE -> "YouTube"
        FileInfo.SOUND_CLOUD_MIME_TYPE -> "SoundCloud"
        else -> null
    } ?: return emptyList()
    if (s.serviceInfo.name != name) {
        return emptyList()
    }
    val supportStreamType = if (remoteMediaItem.contentType.startsWith("video/")) {
        listOf(StreamType.VIDEO_STREAM)
    } else {
        listOf(StreamType.AUDIO_STREAM)
    }
    try {
        return withContext(Dispatchers.IO) {
            val type = s.getLinkTypeByUrl(remoteMediaItem.url)
            when (type) {
                null, NONE, CHANNEL -> emptyList()
                STREAM -> getPlayItemFromStreamInfo(StreamInfo.getInfo(s, remoteMediaItem.url))
                PLAYLIST -> getPlayListInList(s, remoteMediaItem, supportStreamType)
            }
        }
    } catch (e: ReCaptchaException) {
        context.startActivity(Intent(context, ReCaptchaActivity::class.java).apply {
            putExtra(ReCaptchaActivity.RECAPTCHA_URL_EXTRA, e.url)
        })
        return emptyList()
    }
}

private fun getPlayListInList(
    s: StreamingService,
    remoteMediaItem: RemoteMediaItem,
    supportStreamType: List<StreamType>
): List<ConstPlayItem> {
    val playlistInfo = PlaylistInfo.getInfo(s, remoteMediaItem.url)
    return buildList {
        addAll(playlistInfo.relatedItems.take(1).flatMap {
            if (supportStreamType.contains(it.streamType)) {
                getPlayItemFromStreamInfo(StreamInfo.getInfo(s, it.url))
            } else {
                emptyList()
            }
        })
        while (playlistInfo.hasNextPage() && playlistInfo.nextPage.id == playlistInfo.id) {
            val itemsPage = PlaylistInfo.getMoreItems(s, remoteMediaItem.url, playlistInfo.nextPage)
            if (itemsPage.errors.isNotEmpty()) {
                itemsPage.errors.forEach {
                    Napier.e(it) {
                        "parse youtube"
                    }
                }
            }
            addAll(itemsPage.items.take(1).flatMap {
                getPlayItemFromStreamInfo(StreamInfo.getInfo(s, it.url))
            })
            playlistInfo.nextPage = itemsPage.nextPage
        }
    }
}

private fun getPlayItemFromStreamInfo(info: StreamInfo): List<ConstPlayItem> {
    return when (val streamType = info.streamType) {
        StreamType.VIDEO_STREAM -> {
            val firstOrNull = info.videoStreams.firstOrNull()
            firstOrNull?.let { it1 ->
                ConstPlayItem(
                    it1.content,
                    info.thumbnails.firstOrNull()?.url,
                    info.name
                )
            }
                ?.let {
                    listOf(it)
                } ?: emptyList()
        }

        StreamType.AUDIO_STREAM -> {
            val firstOrNull = info.audioStreams.firstOrNull()
            firstOrNull?.let { it1 ->
                ConstPlayItem(
                    it1.content,
                    info.thumbnails.firstOrNull()?.url,
                    info.name
                )
            }
                ?.let {
                    listOf(it)
                } ?: emptyList()
        }

        else -> throw Exception("unsupported type $streamType")
    }
}

data class MediaPlayerState(
    val currentLoading: Boolean,
    val currentIsPlaying: Boolean,
    val currentPlayingItem: MediaItem?
)

@OptIn(ExperimentalUuidApi::class)
@Composable
fun rememberPlayerState(
    player: MediaController?,
    localMediaPlaySession: LocalMediaPlaySession
): State<MediaPlayerState> {
    player ?: return remember {
        mutableStateOf(
            MediaPlayerState(
                currentLoading = false,
                currentIsPlaying = false,
                currentPlayingItem = null
            )
        )
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
            buildListener(player, object : VideoListener {
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
                    currentPlaying = if (currentMediaItemIndex < player.mediaItemCount) {
                        player.getMediaItemAt(currentMediaItemIndex)
                    } else {
                        null
                    }
                }
            })
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

private fun MediaController.playNewMedia(
    playList: List<ConstPlayItem>,
    contentType: String
) {
    clearMediaItems()

    addMediaItems(playList.map { playItem ->
        val uri = playItem.url
        MediaItem.Builder().setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setArtworkUri(playItem.icon?.toUri())
                    .setTitle(playItem.title)
                    .build()
            )
            .apply {
                if (contentType == FileInfo.M3U8_MIMETYPE && !uri.endsWith(".m3u8")) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }.build()
    })
    play()
}

private fun buildListener(
    player: Player,
    listener: VideoListener
): Player.Listener {
    return object : Player.Listener {
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
}

fun buildM3UListener(
    player: Player,
    id: String,
    toasterState: Toast,
    scope: CoroutineScope
): Player.Listener {
    return object : Player.Listener {

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            Napier.i {
                "Video $id error $error ${error.errorCode} ${error.errorCodeName}"
            }
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
            ) {
                toasterState.showMessage("source error, restart after 1 seconds")
                scope.launch {
                    delay(1000)
                    player.play()
                }
            } else if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED) {
                toasterState.showMessage("source error, skip to next after 1 seconds")
                scope.launch {
                    delay(1000)
                    player.seekToNext()
                    player.play()
                }
            }
        }
    }
}
