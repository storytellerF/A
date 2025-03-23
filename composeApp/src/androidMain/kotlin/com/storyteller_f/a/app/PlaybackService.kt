package com.storyteller_f.a.app

import android.content.Intent
import androidx.media3.common.Player.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.storyteller_f.a.app.compontents.CustomVideoSize
import com.storyteller_f.a.app.compontents.savedSession
import io.github.aakira.napier.Napier
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
object MediaProvider {
    var controller: MediaController? = null

    @Synchronized
    fun get(
        currentSession: MediaPlaySession.VideoOrAudio,
        init: (MediaController, MediaPlaySession.VideoOrAudio) -> Unit
    ) {
        val c = controller ?: return
        val session = savedSession.value
        if (currentSession.uuid == session?.uuid) {
            return
        }
        init(c, currentSession)
        savedSession.value = currentSession
    }

    @Synchronized
    fun release(currentSession: LocalMediaPlaySession) {
        if (currentSession.uuid == savedSession.value?.uuid) {
            controller?.stop()
            savedSession.value = null
        }
    }

    @Synchronized
    fun releaseView(currentSession: LocalMediaPlaySession) {
        val current = savedSession.value
        if (currentSession.uuid == current?.uuid) {
            savedSession.value = current.copy(uuid = null)
        }
    }

    @Synchronized
    fun switch(currentSession: LocalMediaPlaySession) {
        val session = savedSession.value ?: return
        if (session.id == currentSession.id) {
            Napier.d {
                "Video ${currentSession.uuid} switch"
            }
            savedSession.value = session.copy(uuid = currentSession.uuid)
        }
    }

    @Synchronized
    fun update(currentSession: LocalMediaPlaySession, size: CustomVideoSize) {
        val session = savedSession.value ?: return
        if (session.id == currentSession.id && session.uuid == currentSession.uuid) {
            savedSession.value = session.copy(videoSize = size)
        }
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
        if (!isPlaybackOngoing) {
            pauseAllPlayersAndStopSelf()
        }
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
