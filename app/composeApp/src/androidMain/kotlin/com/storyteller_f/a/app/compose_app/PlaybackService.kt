package com.storyteller_f.a.app.compose_app

import android.content.Intent
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.storyteller_f.a.app.compose_app.components.CustomVideoSize
import com.storyteller_f.a.app.compose_app.components.currentPlayerState
import com.storyteller_f.a.app.compose_app.components.setCurrentPlayerState
import io.github.aakira.napier.Napier
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
object MediaProvider {
    var controller: MediaController? = null

    @Synchronized
    fun get(
        currentSession: FileViewInfo.Player,
        init: (MediaController, FileViewInfo.Player) -> Unit
    ) {
        val c = controller ?: return
        val session = currentPlayerState
        if (session?.id == currentSession.id) {
            return
        }
        init(c, currentSession)
        setCurrentPlayerState(currentSession)
    }

    @Synchronized
    fun release(currentSession: LocalMediaPlaySession, keepPlayer: Boolean) {
        Napier.d(tag = "MediaPlayer") {
            "MediaProvider release $currentSession"
        }
        val session = currentPlayerState ?: return
        val lastUuid = session.lastUuid ?: return
        Napier.i(tag = "MediaPlayer") {
            "MediaProvider release $lastUuid ${currentSession.uuid} count: ${session.uuidCount}"
        }
        if (lastUuid != currentSession.uuid) return
        val new = session.uuids.subList(0, session.uuids.size - 1)
        setCurrentPlayerState(session.copy(uuids = new))
        if (new.isNotEmpty()) {
            return
        }
        if (keepPlayer) return
        controller?.stop()
        setCurrentPlayerState(null)
    }

    @Synchronized
    fun switch(currentSession: LocalMediaPlaySession) {
        val session = currentPlayerState ?: return
        val lastUuid = session.lastUuid
        if (session.id != currentSession.id || currentSession.uuid == lastUuid) return
        Napier.d(tag = "MediaPlayer") {
            "Video $lastUuid switch to ${currentSession.uuid}"
        }
        setCurrentPlayerState(session.appendUuid(currentSession.uuid))
    }

    @Synchronized
    fun update(currentSession: LocalMediaPlaySession, size: CustomVideoSize) {
        val session = currentPlayerState ?: return
        val lastUuid = session.lastUuid ?: return
        if (session.id != currentSession.id || currentSession.uuid != lastUuid) return
        setCurrentPlayerState(session.copy(videoSize = size))
    }
}

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        Napier.d {
            "PlaybackService onCreate"
        }
        val player = ExoPlayer.Builder(this).build()
        mediaSession =
            MediaSession.Builder(this, player)
                .setCallback(MyCallback())
                .build()
    }

    override fun onGetSession(p0: MediaSession.ControllerInfo): MediaSession? {
        Napier.i {
            "PlaybackService onGetSession $p0"
        }
        return mediaSession
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Napier.d {
            "PlaybackService onTaskRemoved"
        }
        if (isPlaybackOngoing) return
        pauseAllPlayersAndStopSelf()
    }

    private inner class MyCallback : MediaSession.Callback {
        @androidx.annotation.OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            Napier.d {
                "PlaybackService onConnect"
            }
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(
                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .remove(COMMAND_SEEK_TO_NEXT)
                        .remove(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .remove(COMMAND_SEEK_TO_PREVIOUS)
                        .remove(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .build()
                )
                .build()
        }
    }
}
