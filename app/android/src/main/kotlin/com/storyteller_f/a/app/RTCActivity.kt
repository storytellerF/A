package com.storyteller_f.a.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.storyteller_f.a.app.core.commonForActivity
import com.storyteller_f.a.app.ui.theme.AppTheme
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.lang.ref.WeakReference

class RTCActivity : ComponentActivity(), RTCContainer {
    override var binder = MutableStateFlow<RTCHandle?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val streamFlow = binder.filterNotNull().flatMapLatest {
        it.stream
    }.stateIn(lifecycleScope, SharingStarted.Lazily, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val callingRoomFlow: StateFlow<Long?> = binder.filterNotNull().flatMapLatest {
        it.callingRoom
    }.stateIn(lifecycleScope, SharingStarted.Lazily, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val remotePeers: StateFlow<List<RemotePeerState>> = binder.filterNotNull().flatMapLatest {
        it.remotePeers
    }.map {
        it.values.sortedBy(RemotePeerState::uid)
    }.stateIn(lifecycleScope, SharingStarted.Lazily, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    override val localAudioMutedFlow: StateFlow<Boolean> = binder.filterNotNull().flatMapLatest {
        it.localAudioMuted
    }.stateIn(lifecycleScope, SharingStarted.Lazily, false)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val localVideoMutedFlow: StateFlow<Boolean> = binder.filterNotNull().flatMapLatest {
        it.localVideoMuted
    }.stateIn(lifecycleScope, SharingStarted.Lazily, false)

    val roomId = MutableStateFlow<PrimaryKey?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        commonForActivity()
        val roomId = intent.getLongExtra("roomId", 0)
        this.roomId.value = roomId
        val serviceIntent = Intent(this, RTCService::class.java)
        val connection = RTCServiceConnection(WeakReference(this))
        bindService(serviceIntent, connection, BIND_AUTO_CREATE)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                super.onDestroy(owner)
                unbindService(connection)
            }
        })
        setContent {
            AppTheme(dynamicColor = true) {
                val currentRoomId by this.roomId.collectAsState()
                currentRoomId?.let { WebRTCPage(this, it) }
            }
        }
    }
}
