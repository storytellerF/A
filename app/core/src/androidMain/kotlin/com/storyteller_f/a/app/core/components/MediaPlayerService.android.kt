package com.storyteller_f.a.app.core.components

import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaController
import com.storyteller_f.a.app.core.utils.parseM3UPlayList
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.utils.UNIT_RESULT
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.schabi.newpipe.ReCaptchaActivity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService.LinkType.CHANNEL
import org.schabi.newpipe.extractor.StreamingService.LinkType.NONE
import org.schabi.newpipe.extractor.StreamingService.LinkType.PLAYLIST
import org.schabi.newpipe.extractor.StreamingService.LinkType.STREAM
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
actual abstract class MediaPlayerService {
    val mutex = Mutex()
    actual val state: MutableStateFlow<MediaPlaySession?> = MutableStateFlow(null)

    val currentPlayerState: MediaPlaySession?
        get() = state.value

    fun setCurrentPlayerState(player: MediaPlaySession?) {
        state.value = player
    }

    val controller: MutableStateFlow<MediaController?> = MutableStateFlow(null)

    suspend fun get(
        currentSession: MediaPlaySession,
        init: (MediaController, MediaPlaySession) -> Unit
    ) {
        val c = controller.value ?: return
        mutex.withLock {
            val session = currentPlayerState
            if (session?.id == currentSession.id) {
                return
            }
            init(c, currentSession)
            setCurrentPlayerState(currentSession)
        }
    }

    fun release(localMediaPlaySession: LocalMediaPlaySession, keepPlayer: Boolean) {
        Napier.d(tag = "MediaPlayer") {
            "MediaPlayerService release $localMediaPlaySession keepPlayer: $keepPlayer"
        }
        val session = currentPlayerState ?: return
        val lastUuid = session.lastUuid ?: return
        Napier.i(tag = "MediaPlayer") {
            "MediaPlayerService release $lastUuid ${localMediaPlaySession.uuid} count: ${session.uuidCount}"
        }
        if (lastUuid != localMediaPlaySession.uuid) return
        val new = session.uuids.subList(0, session.uuids.size - 1)
        setCurrentPlayerState(session.copy(uuids = new))
        if (keepPlayer) return
        controller.value?.stop()
        setCurrentPlayerState(null)
    }

    suspend fun switch(localMediaPlaySession: LocalMediaPlaySession) {
        val session = currentPlayerState ?: return
        val lastUuid = session.lastUuid
        if (session.id != localMediaPlaySession.id || localMediaPlaySession.uuid == lastUuid) return
        Napier.d(tag = "MediaPlayer") {
            "MediaPlayerService $lastUuid switch to ${localMediaPlaySession.uuid}"
        }
        mutex.withLock {
            setCurrentPlayerState(session.appendUuid(localMediaPlaySession.uuid))
        }
    }

    fun update(localMediaPlaySession: LocalMediaPlaySession, size: CustomVideoSize) {
        val session = currentPlayerState ?: return
        val lastUuid = session.lastUuid ?: return
        if (session.id != localMediaPlaySession.id || localMediaPlaySession.uuid != lastUuid) return
        setCurrentPlayerState(session.copy(videoSize = size))
    }

    actual abstract fun fullscreen(remoteMediaItem: RemoteMediaItem)
    actual abstract suspend fun start(
        remoteMediaItem: RemoteMediaItem,
        localMediaPlaySession: LocalMediaPlaySession
    )

    actual abstract val enablePip: Boolean
}

@OptIn(ExperimentalUuidApi::class)
suspend fun MediaPlayerService.startPlay(
    contentType: String,
    remoteMediaItem: RemoteMediaItem,
    context: Context,
    localMediaPlaySession: LocalMediaPlaySession
): Result<Unit> {
    val playList = when (contentType) {
        FileInfo.M3U8_MIMETYPE -> parseM3UPlayList(remoteMediaItem, HttpClient { })
        FileInfo.YOUTUBE_MIMETYPE, FileInfo.SOUND_CLOUD_MIME_TYPE -> getPlaylistFromNewPipe(
            remoteMediaItem,
            context
        )

        else -> listOf(ConstPlayItem(remoteMediaItem.url, title = remoteMediaItem.url))
    }
    return if (playList.isNotEmpty()) {
        val newSession = MediaPlaySession(
            remoteMediaItem,
            playList,
            listOf(localMediaPlaySession.uuid),
            null
        )
        get(newSession) { player, s ->
            player.playNewMedia(s.playList, contentType)
        }
        UNIT_RESULT
    } else {
        Result.failure(Exception("can't play"))
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
