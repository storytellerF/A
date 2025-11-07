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
import com.storyteller_f.a.app.compose_app.FileViewInfo
import com.storyteller_f.a.app.compose_app.LocalGlobalDialog
import com.storyteller_f.a.app.compose_app.LocalMediaPlaySession
import com.storyteller_f.a.app.compose_app.MediaProvider
import com.storyteller_f.a.app.core.components.LocalToaster
import com.storyteller_f.a.app.core.components.Toast
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

@OptIn(ExperimentalUuidApi::class)
@Composable
fun MediaPlayerInternal(
    id: String,
    contentType: String,
    isFilled: Boolean,
    block: @Composable (MediaController, FileViewInfo.Player?, LocalMediaPlaySession) -> Unit
) {
    val uuid = rememberSaveable {
        Uuid.random()
    }
    val currentSession = remember(id, uuid) {
        LocalMediaPlaySession(id, uuid)
    }

    Napier.i(tag = "MediaPlayer") {
        "MediaPlayerInternal $uuid recomposing"
    }
    val player = MediaProvider.controller ?: return
    val playingSession by globalPlayerState
    LaunchedEffect(playingSession, currentSession) {
        Napier.i(tag = "MediaPlayer") {
            "MediaPlayerInternal $uuid switch uuids: ${playingSession?.uuids} current: ${currentSession.uuid}"
        }
        switchSessionIfNeed(playingSession, currentSession, isFilled)
    }
    val context = LocalContext.current.findActivity()
    DisposableEffect(null) {
        onDispose {
            val isPip = context.isInPictureInPictureMode
            Napier.d(tag = "MediaPlayer") {
                "MediaPlayerInternal $uuid dispose isPip: $isPip isEmbed: $isFilled"
            }
            MediaProvider.release(currentSession, isPip || isFilled)
        }
    }
    MediaPlayerContainer(playingSession, player, currentSession, contentType, block, isFilled)
}

@OptIn(ExperimentalUuidApi::class)
private fun switchSessionIfNeed(
    playingSession: FileViewInfo.Player?,
    currentSession: LocalMediaPlaySession,
    isFilled: Boolean
) {
    if (playingSession == null) return
    if (playingSession.id == currentSession.id && (playingSession.lastUuid == null || isFilled)) {
        MediaProvider.switch(currentSession)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun MediaPlayerContainer(
    playingSession: FileViewInfo.Player?,
    player: MediaController,
    currentSession: LocalMediaPlaySession,
    contentType: String,
    block: @Composable (MediaController, FileViewInfo.Player?, LocalMediaPlaySession) -> Unit,
    isFilled: Boolean
) {
    var showSheet by remember {
        mutableStateOf(false)
    }
    if (isFilled) {
        block(player, playingSession, currentSession)
    } else {
        ObjectBlock {
            Box(modifier = Modifier.weight(1f)) {
                block(player, playingSession, currentSession)
            }
            VideoOrAudioOpRow(currentSession, playingSession, contentType) {
                showSheet = true
            }
        }
    }
    val sheetState = rememberModalBottomSheetState()
    if (playingSession?.lastUuid == currentSession.uuid) {
        VideoPlaylistPicker(showSheet, sheetState, {
            showSheet = false
        }, playingSession.playList) { _, i ->
            switchPlaylist(i)
        }
    }
}

private fun switchPlaylist(i: Int) {
    val player = MediaProvider.controller ?: return

    player.seekTo(i, 0)
    player.play()
}

@Composable
fun BoxScope.PlayerOccupy(currentSession: LocalMediaPlaySession) {
    IconButton({
        MediaProvider.switch(currentSession)
    }, modifier = Modifier.Companion.align(Alignment.Center)) {
        Icon(Icons.Default.TapAndPlay, "return")
    }
}

@Composable
fun BoxScope.PlayerWaiting(
    localMediaPlaySession: LocalMediaPlaySession,
    obj: RemoteMediaItem
) {
    val coverMediaInfo = obj.cover
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
    IconButton({
        scope.launch {
            startPlay(obj, client, localMediaPlaySession, context, globalDialogController)
        }
    }, modifier = Modifier.Companion.align(Alignment.Center)) {
        Icon(Icons.Default.PlayArrow, "play")
    }
    if (obj.contentType == FileInfo.M3U8_MIMETYPE || obj.contentType == FileInfo.YOUTUBE_MIMETYPE) {
        Text(
            obj.title ?: obj.url,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
            maxLines = 2
        )
    } else {
        Text(
            obj.title ?: obj.name,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
            maxLines = 2
        )
    }
}

@OptIn(ExperimentalUuidApi::class)
private suspend fun startPlay(
    obj: RemoteMediaItem,
    client: HttpClient,
    localMediaPlaySession: LocalMediaPlaySession,
    context: Context,
    globalDialogController: AppGlobalDialogController
) {
    val contentType = obj.contentType
    globalDialogController.useResult {
        val playList = when (contentType) {
            FileInfo.M3U8_MIMETYPE -> parseM3UPlayList(obj, client)
            FileInfo.YOUTUBE_MIMETYPE, FileInfo.SOUND_CLOUD_MIME_TYPE -> getPlaylistFromNewPipe(
                obj,
                context
            )

            else -> listOf(ConstPlayItem(obj.url, title = obj.url))
        }
        if (playList.isNotEmpty()) {
            val newSession = FileViewInfo.Player(
                obj,
                contentType,
                playList,
                listOf(localMediaPlaySession.uuid),
                null
            )
            MediaProvider.get(newSession) { player, s ->
                player.playNewMedia(s.playList, contentType)
            }
            UNIT_RESULT
        } else {
            Result.failure(Exception("can't play"))
        }
    }
}

suspend fun getPlaylistFromNewPipe(obj: RemoteMediaItem, context: Context): List<ConstPlayItem> {
    val s = NewPipe.getServiceByUrl(obj.url)
    val name = when (obj.contentType) {
        FileInfo.YOUTUBE_MIMETYPE -> "YouTube"
        FileInfo.SOUND_CLOUD_MIME_TYPE -> "SoundCloud"
        else -> null
    } ?: return emptyList()
    if (s.serviceInfo.name != name) {
        return emptyList()
    }
    val supportStreamType = if (obj.contentType.startsWith("video/")) {
        listOf(StreamType.VIDEO_STREAM)
    } else {
        listOf(StreamType.AUDIO_STREAM)
    }
    try {
        return withContext(Dispatchers.IO) {
            val type = s.getLinkTypeByUrl(obj.url)
            when (type) {
                null, NONE, CHANNEL -> emptyList()
                STREAM -> getPlayItemFromStreamInfo(StreamInfo.getInfo(s, obj.url))
                PLAYLIST -> getPlayListInList(s, obj, supportStreamType)
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
    obj: RemoteMediaItem,
    supportStreamType: List<StreamType>
): List<ConstPlayItem> {
    val playlistInfo = PlaylistInfo.getInfo(s, obj.url)
    return buildList {
        addAll(playlistInfo.relatedItems.take(1).flatMap {
            if (supportStreamType.contains(it.streamType)) {
                getPlayItemFromStreamInfo(StreamInfo.getInfo(s, it.url))
            } else {
                emptyList()
            }
        })
        while (playlistInfo.hasNextPage() && playlistInfo.nextPage.id == playlistInfo.id) {
            val itemsPage = PlaylistInfo.getMoreItems(s, obj.url, playlistInfo.nextPage)
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
    player: MediaController,
    currentSession: LocalMediaPlaySession
): MediaPlayerState {
    var currentLoading by remember {
        mutableStateOf(player.isLoading)
    }
    var currentIsPlaying by remember {
        mutableStateOf(player.isPlaying)
    }
    var currentPlaying by remember {
        mutableStateOf<MediaItem?>(null)
    }
    val toasterState = LocalToaster.current
    val scope = rememberCoroutineScope()
    DisposableEffect(currentSession, player) {
        val customListener =
            buildListener(player, currentSession.id, toasterState, scope, object : VideoListener {
                override fun onPlayStateChange(isPlaying: Boolean) {
                    currentIsPlaying = isPlaying
                }

                override fun onUpdateSize(size: CustomVideoSize) {
                    Napier.d {
                        "Video ${currentSession.uuid} updateSize $size"
                    }
                    MediaProvider.update(currentSession, size)
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
            Napier.d {
                "Video ${currentSession.uuid} release listener"
            }
            player.removeListener(customListener)
        }
    }
    val newState by remember {
        derivedStateOf {
            MediaPlayerState(currentLoading, currentIsPlaying, currentPlaying)
        }
    }
    return newState
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
    id: String,
    toasterState: Toast,
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
