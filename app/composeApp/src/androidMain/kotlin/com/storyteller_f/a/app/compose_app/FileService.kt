package com.storyteller_f.a.app.compose_app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.storyteller_f.a.app.compose_app.common.Downloader
import com.storyteller_f.a.app.compose_app.common.DownloaderImpl
import com.storyteller_f.a.app.compose_app.common.Uploader
import com.storyteller_f.a.app.compose_app.common.UploaderImpl
import kotlinx.collections.immutable.ImmutableList
import java.lang.ref.WeakReference

class FileService : LifecycleService() {
    override fun onCreate() {
        super.onCreate()
        val channel = "Upload"
        getOrCreateNotificationManager(this, channel)
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(com.storyteller_f.a.app.R.drawable.baseline_upload_file_24)
            .setContentTitle("Uploading")
            .setOngoing(true)
        startForeground(2, notification.build())
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        val uiViewModel = (application as AApplication).uiViewModel
        return FileBinder(
            DownloaderImpl(lifecycleScope, uiViewModel),
            UploaderImpl(lifecycleScope, uiViewModel)
        )
    }
}

class FileBinder(
    val downloader: Downloader,
    val uploader: Uploader
) : Binder(), Downloader by downloader, Uploader by uploader

interface ClientFileServiceReceiver {
    var binder: FileBinder?
}

class FileConnection(
    val context: WeakReference<ClientFileServiceReceiver>,
    val clipData: ImmutableList<ClipFile>
) : ServiceConnection {
    override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
        val binder = p1 as FileBinder
        binder.upload(clipData)
        context.get()?.binder = binder
    }

    override fun onServiceDisconnected(p0: ComponentName?) {
        context.get()?.binder = null
    }
}

fun <T> T.bindFileService(clipData: ImmutableList<ClipFile>) where T : Context, T : ClientFileServiceReceiver {
    val serviceIntent = Intent(this, FileService::class.java)
    val connection = FileConnection(WeakReference(this), clipData)
    bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
}

class CustomClientFileProvider(val service: ClientFileServiceReceiver) : ClientFileProvider {
    override fun getDownloader(): Downloader? {
        return service.binder?.downloader
    }

    override fun getUploader(): Uploader? {
        return service.binder?.uploader
    }
}
