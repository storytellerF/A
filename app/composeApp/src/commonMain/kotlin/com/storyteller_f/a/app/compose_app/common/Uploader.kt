package com.storyteller_f.a.app.compose_app.common

import com.storyteller_f.a.app.compose_app.CustomSessionManager
import com.storyteller_f.a.app.compose_app.UIViewModel
import com.storyteller_f.a.app.compose_app.pages.ClientFile
import com.storyteller_f.a.app.compose_app.utils.getClientFile
import com.storyteller_f.a.client.core.UploadData
import com.storyteller_f.a.client.core.upload
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.md5
import com.storyteller_f.shared.utils.now
import com.storyteller_f.storage.ModelStorage
import com.storyteller_f.storage.UploadCollection
import com.storyteller_f.storage.UploadInfo
import com.storyteller_f.storage.UploadStatus
import io.github.aakira.napier.Napier
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.ExperimentalTime

interface Uploader {
    fun upload(clipData: ImmutableList<ClientFile>)

    fun resume(pathHash: String)
}

class UploaderImpl(val lifecycleScope: CoroutineScope, val uiViewModel: UIViewModel) : Uploader {
    val mutex = Mutex()
    val runningSet = mutableSetOf<String>()
    override fun upload(clipData: ImmutableList<ClientFile>) {
        Napier.i {
            "upload ${
                clipData.joinToString {
                    it.name
                }
            }"
        }
        val instance = uiViewModel.instance.value
        val myUid = instance.manager.sessionModel.uid ?: return
        val modelStorage = instance.database.value
        val userSession = instance.manager
        lifecycleScope.launch {
            clipData.forEach {
                uploadIfNeed(userSession, myUid, it, modelStorage, UploadCollection(myUid))
            }
        }
    }

    override fun resume(pathHash: String) {
        Napier.i {
            "resume upload $pathHash"
        }
        val instance = uiViewModel.instance.value
        val myUid = instance.manager.sessionModel.uid ?: return
        val modelStorage = instance.database.value
        val userSession = instance.manager
        lifecycleScope.launch {
            resumeIfNeed(modelStorage, myUid, pathHash, userSession, UploadCollection(myUid))
        }
    }

    private suspend fun resumeIfNeed(
        modelStorage: ModelStorage,
        myUid: PrimaryKey,
        pathHash: String,
        userSession: CustomSessionManager,
        collection: UploadCollection
    ) {
        val uploadInfo =
            modelStorage.uploadInfoStorage.getDocument(collection, pathHash)
        if (uploadInfo != null) {
            val clientFile = getClientFile(uploadInfo.path) ?: return
            upload(userSession, myUid, clientFile, modelStorage, collection, pathHash)
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun uploadIfNeed(
        userSession: CustomSessionManager,
        myUid: PrimaryKey,
        clipFile: ClientFile,
        modelStorage: ModelStorage,
        collection: UploadCollection
    ) {
        val pathHash = md5(clipFile.path)
        mutex.withLock {
            val existing = modelStorage.uploadInfoStorage.getDocument(collection, pathHash)
            if (existing != null) {
                return
            }
            val id = now().toInstant(
                TimeZone.UTC
            ).toEpochMilliseconds()
            val uploadInfo =
                UploadInfo(
                    id,
                    pathHash,
                    clipFile.path,
                    0,
                    clipFile.size,
                    UploadStatus.UPLOADING,
                    "",
                    clipFile.name, clipFile.contentType.contentType
                )
            modelStorage.uploadInfoStorage.save(collection, uploadInfo)
        }
        upload(userSession, myUid, clipFile, modelStorage, collection, pathHash)
    }

    private suspend fun upload(
        userSession: CustomSessionManager,
        myUid: PrimaryKey,
        clipFile: ClientFile,
        modelStorage: ModelStorage,
        collection: UploadCollection,
        pathHash: String
    ) {
        mutex.withLock {
            if (runningSet.contains(pathHash)) {
                return
            }
            runningSet.add(pathHash)
        }
        try {
            userSession.upload(
                myUid ob ObjectType.USER,
                UploadData(
                    clipFile.size,
                    clipFile.name,
                    clipFile.contentType
                ) {
                    clipFile.source()
                }
            ) { p, t ->
                updateUploadInfo(modelStorage, collection, pathHash) {
                    it.copy(progress = p)
                }
            }.onSuccess {
                updateUploadInfo(modelStorage, collection, pathHash) {
                    it.copy(status = UploadStatus.SUCCESS)
                }
            }.onFailure {
                updateUploadInfo(modelStorage, collection, pathHash) {
                    it.copy(status = UploadStatus.FAILED, message = it.message)
                }
            }
        } finally {
            mutex.withLock {
                runningSet.remove(pathHash)
            }
        }
    }

    suspend fun updateUploadInfo(
        modelStorage: ModelStorage,
        collection: UploadCollection,
        pathHash: String,
        block: (UploadInfo) -> UploadInfo
    ) {
        val uploadInfo = modelStorage.uploadInfoStorage.getDocument(collection, pathHash) ?: return
        modelStorage.uploadInfoStorage.save(collection, block(uploadInfo))
    }
}
