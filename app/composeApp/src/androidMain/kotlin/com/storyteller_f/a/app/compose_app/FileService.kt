package com.storyteller_f.a.app.compose_app

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.storyteller_f.a.client.core.RawUserPass
import com.storyteller_f.a.client.core.UploadData
import com.storyteller_f.a.client.core.buildWebSocketUrl
import com.storyteller_f.a.client.core.upload
import com.storyteller_f.a.client.room.RoomModelStorage
import com.storyteller_f.a.client.room.getRoomDatabase
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.md5
import com.storyteller_f.shared.utils.now
import com.storyteller_f.storage.ModelStorage
import com.storyteller_f.storage.UploadCollection
import com.storyteller_f.storage.UploadInfo
import com.storyteller_f.storage.UploadStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.lang.ref.WeakReference
import kotlin.time.ExperimentalTime

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
        return FileBinder(this)
    }
}

class FileBinder(val service: FileService) : Binder() {
    @OptIn(ExperimentalTime::class)
    fun upload(clipData: ImmutableList<ClipFile>) {
        val userSession = createCustomUserSessionManager(
            "main",
            buildWebSocketUrl(AppConfig.WS_SERVER_URL),
            { model, cookie ->
                buildHttpClient(AppConfig.SERVER_URL, cookie, model)
            },
            { _, _ -> }
        )

        service.lifecycleScope.launch {
            upload(userSession, clipData)
        }
    }

    private suspend fun upload(
        userSession: CustomSessionManager,
        clipData: ImmutableList<ClipFile>
    ) {
        userSession.login()
        val pass = userSession.sessionModel.currentUserPass as? RawUserPass ?: return
        val myUid = userSession.sessionModel.uid ?: return
        val collection = UploadCollection(myUid)
        try {
            val address = pass.address().getOrThrow()
            val modelStorage = RoomModelStorage(getRoomDatabase(address))
            clipData.forEach { e ->
                upload(e, modelStorage, collection, userSession, myUid)
            }
        } catch (_: Exception) {
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun upload(
        e: ClipFile,
        modelStorage: RoomModelStorage,
        collection: UploadCollection,
        userSession: CustomSessionManager,
        myUid: PrimaryKey
    ) {
        val pathHash = md5(e.path)
        val existing = modelStorage.uploadInfoStorage.getDocument(collection, pathHash)
        if (existing != null) return
        val id = now().toInstant(
            TimeZone.UTC
        ).toEpochMilliseconds()
        val uploadInfo =
            UploadInfo(id, pathHash, e.path, 0, e.size, UploadStatus.UPLOADING, "")
        modelStorage.uploadInfoStorage.save(collection, uploadInfo)
        userSession.upload(
            myUid ob ObjectType.USER,
            UploadData(
                e.size,
                e.name,
                e.contentType
            ) {
                e.source()
            }
        ) { p, t ->
            modelStorage.uploadInfoStorage.save(
                collection,
                uploadInfo.copy(progress = p, total = t)
            )
        }.onSuccess {
            update(modelStorage, collection, pathHash) {
                uploadInfo.copy(status = UploadStatus.SUCCESS)
            }
        }.onFailure {
            update(modelStorage, collection, pathHash) {
                uploadInfo.copy(status = UploadStatus.FAILED, message = it.message)
            }
        }
    }

    suspend fun update(
        modelStorage: ModelStorage,
        collection: UploadCollection,
        pathHash: String,
        block: (UploadInfo) -> UploadInfo
    ) {
        val uploadInfo = modelStorage.uploadInfoStorage.getDocument(collection, pathHash) ?: return
        modelStorage.uploadInfoStorage.save(collection, block(uploadInfo))
    }
}

class FileConnection(
    val context: WeakReference<UploadActivity>,
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
