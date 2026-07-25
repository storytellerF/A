package com.storyteller_f.a.client.compose_core.components

import androidx.media3.session.MediaController
import com.storyteller_f.shared.utils.UNIT_RESULT
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        localMediaPlaySession: LocalMediaPlaySession,
        playList: List<ConstPlayItem>
    )

    actual abstract val enablePip: Boolean
}

@OptIn(ExperimentalUuidApi::class)
suspend fun MediaPlayerService.startPlay(
    remoteMediaItem: RemoteMediaItem,
    localMediaPlaySession: LocalMediaPlaySession,
    playList: List<ConstPlayItem>
): Result<Unit> {
    return if (playList.isNotEmpty()) {
        val newSession = MediaPlaySession(remoteMediaItem, playList, listOf(localMediaPlaySession.uuid), null)
        get(newSession) { player, s ->
            player.playNewMedia(s.playList, remoteMediaItem.contentType)
        }
        UNIT_RESULT
    } else {
        Result.failure(Exception("can't play"))
    }
}
