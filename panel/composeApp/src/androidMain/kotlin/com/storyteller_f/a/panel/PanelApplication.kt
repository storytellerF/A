package com.storyteller_f.a.panel

import android.app.Application
import android.content.Intent
import android.os.StrictMode
import com.storyteller_f.a.app.core.components.ConstPlayItem
import com.storyteller_f.a.app.core.components.LocalMediaPlaySession
import com.storyteller_f.a.app.core.components.MediaPlayerService
import com.storyteller_f.a.app.core.components.RemoteMediaItem
import com.storyteller_f.a.app.core.components.mainActivityRef
import com.storyteller_f.a.app.core.startPlayMedia
import com.storyteller_f.shared.appContextRef
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.shared.setupKmpLogger
import java.lang.ref.WeakReference

class PanelApplication : Application() {
    val mediaPlayer = buildPanelMediaPlayer()

    override fun onCreate() {
        super.onCreate()
        setupKmpLogger()
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy())
                .detectLeakedClosableObjects()
                .build()
        )
        appContextRef = WeakReference(this)
        loadCryptoLibIfNeed()
    }
}

private fun buildPanelMediaPlayer(): MediaPlayerService = object : MediaPlayerService() {
    override fun fullscreen(remoteMediaItem: RemoteMediaItem) {
        val context = mainActivityRef?.get() ?: return
        context.startActivity(Intent(context, PanelMediaPlayerActivity::class.java).apply {
//                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            putExtra("id", remoteMediaItem.id)
        })
    }

    override suspend fun start(
        remoteMediaItem: RemoteMediaItem,
        localMediaPlaySession: LocalMediaPlaySession,
        playList: List<ConstPlayItem>
    ) {
        val instance = panelAccountInstance
        instance.controller.startPlayMedia(remoteMediaItem, localMediaPlaySession, this, playList)
    }

    override val enablePip: Boolean
        get() = false
}
