package com.storyteller_f.a.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.storyteller_f.a.app.android_library.R.drawable.baseline_upload_file_24
import com.storyteller_f.a.app.common.Downloader
import com.storyteller_f.a.app.common.DownloaderImpl
import com.storyteller_f.a.app.common.SimpleTaskRegister
import com.storyteller_f.a.app.common.Uploader
import com.storyteller_f.a.app.common.UploaderImpl
import io.github.aakira.napier.Napier
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class FileService : LifecycleService() {
    val channel = "File Service"
    val taskRegister by lazy { SimpleTaskRegister(lifecycleScope) }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        lifecycleScope.launch {
            taskRegister.runningTaskCount.debounce(1000).collectLatest {
                updateNotification(it)
            }
        }
    }

    private fun updateNotification(count: Int) {
        Napier.i(tag = "File Service") {
            "task count $count"
        }
        getOrCreateNotificationChannel(this, channel)
        val id = 2
        if (count > 0) {
            val notification = NotificationCompat.Builder(this, channel)
                .setSmallIcon(baseline_upload_file_24)
                .setContentTitle("Uploading")
                .setOngoing(true)
            startForeground(id, notification.build())
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        val uiViewModel = uiViewModel
        return FileBinder(DownloaderImpl(uiViewModel, taskRegister), UploaderImpl(uiViewModel, taskRegister))
    }
}

class FileBinder(
    val downloader: Downloader,
    val uploader: Uploader
) : Binder(), Downloader by downloader, Uploader by uploader

interface ClientFileServiceContainer {
    var binder: FileBinder?
    var isConnecting: Boolean
}

class FileConnection(
    val context: WeakReference<ClientFileServiceContainer>,
    val clipData: ImmutableList<ClipFile>
) : ServiceConnection {
    override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
        val binder = p1 as FileBinder
        binder.upload(clipData)
        val container = context.get() ?: return
        container.binder = binder
        container.isConnecting = false
    }

    override fun onServiceDisconnected(p0: ComponentName?) {
        context.get()?.binder = null
    }
}

fun <T> T.bindFileService(clipFiles: ImmutableList<ClipFile>)
    where T : ComponentActivity, T : ClientFileServiceContainer {
    isConnecting = true
    val serviceIntent = Intent(this, FileService::class.java)
    val connection = FileConnection(WeakReference(this), clipFiles)
    bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            super.onDestroy(owner)
            unbindService(connection)
        }
    })
}

class CustomClientFileProvider<T>(val service: T) :
    ClientFileProvider where T : ComponentActivity, T : ClientFileServiceContainer {
    override suspend fun getDownloader(): Downloader? {
        val binder = service.binder ?: awaitBinder()
        return binder?.downloader
    }

    private suspend fun awaitBinder(): FileBinder? {
        if (!service.isConnecting) {
            service.bindFileService(persistentListOf())
        }
        while (service.isConnecting) {
            delay(100)
        }
        val binder = service.binder
        if (binder == null) {
            Napier.w(tag = "file") {
                "FileService binder is null after connect"
            }
        }
        return binder
    }

    override suspend fun getUploader(): Uploader? {
        val binder = service.binder ?: awaitBinder()
        return binder?.uploader
    }
}
