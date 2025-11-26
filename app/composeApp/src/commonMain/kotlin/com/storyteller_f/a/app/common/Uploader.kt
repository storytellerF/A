package com.storyteller_f.a.app.common

import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.app.CustomUserSessionManager
import com.storyteller_f.a.app.UIViewModel
import com.storyteller_f.a.app.pages.file.getUploadDataFromClipFile
import com.storyteller_f.a.app.utils.ClientFile
import com.storyteller_f.a.app.utils.getClientFile
import com.storyteller_f.a.client.core.completeChunkUpload
import com.storyteller_f.a.client.core.getChunkStatus
import com.storyteller_f.a.client.core.initChunkUpload
import com.storyteller_f.a.client.core.upload
import com.storyteller_f.a.client.core.uploadChunk
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.md5
import com.storyteller_f.shared.utils.now
import com.storyteller_f.shared.utils.safeMessage
import com.storyteller_f.shared.utils.sha256
import com.storyteller_f.storage.ModelStorage
import com.storyteller_f.storage.UploadCollection
import com.storyteller_f.storage.UploadInfo
import com.storyteller_f.storage.UploadStatus
import io.github.aakira.napier.Napier
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.io.Buffer
import kotlin.time.ExperimentalTime

interface Uploader {
    fun upload(clipData: ImmutableList<ClientFile>)

    fun resume(pathHash: String)
    fun pause(pathHash: String)
}

class UploaderImpl(
    val uiViewModel: UIViewModel,
    val taskRegister: TaskRegister
) : Uploader {
    override fun upload(clipData: ImmutableList<ClientFile>) {
        if (clipData.isEmpty()) {
            return
        }
        Napier.i {
            "upload ${
                clipData.joinToString {
                    it.name
                }
            }"
        }
        val instance = uiViewModel.instance.value
        val myUid = instance.sessionManager.model.uid ?: return
        val modelStorage = instance.database.value
        val userSession = instance.sessionManager
        clipData.forEach {
            taskRegister.lockTask(md5(it.path)) {
                try {
                    uploadIfNeed(userSession, myUid, it, modelStorage, UploadCollection(myUid))
                } catch (e: Exception) {
                    Napier.e(e) {
                        "upload failed ${it.path}"
                    }
                }
            }
        }
    }

    override fun resume(pathHash: String) {
        Napier.i {
            "resume upload $pathHash"
        }
        val instance = uiViewModel.instance.value
        val myUid = instance.sessionManager.model.uid ?: return
        val modelStorage = instance.database.value
        val userSession = instance.sessionManager
        taskRegister.lockTask(pathHash) {
            try {
                resumeIfNeed(modelStorage, myUid, pathHash, userSession, UploadCollection(myUid))
            } catch (e: Exception) {
                Napier.e(e) {
                    "resume upload $pathHash"
                }
            }
        }
    }

    override fun pause(pathHash: String) {
        Napier.i {
            "pause upload $pathHash"
        }
        val instance = uiViewModel.instance.value
        val myUid = instance.sessionManager.model.uid ?: return
        val modelStorage = instance.database.value
        taskRegister.stop(pathHash) {
            updateUploadInfo(modelStorage, UploadCollection(myUid), pathHash) {
                it.copy(status = UploadStatus.PAUSED)
            }
        }
    }

    private suspend fun resumeIfNeed(
        modelStorage: ModelStorage,
        myUid: PrimaryKey,
        pathHash: String,
        userSession: CustomUserSessionManager,
        collection: UploadCollection
    ) {
        val uploadInfo = modelStorage.upload.getDocument(collection, pathHash)
        if (uploadInfo == null) {
            Napier.i {
                "resume upload $pathHash not found"
            }
            return
        }
        val clientFile = getClientFile(uploadInfo.path) ?: return
        upload(userSession, myUid, clientFile, modelStorage, collection, pathHash, uploadInfo)
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun uploadIfNeed(
        userSession: CustomUserSessionManager,
        myUid: PrimaryKey,
        clipFile: ClientFile,
        modelStorage: ModelStorage,
        collection: UploadCollection
    ) {
        val pathHash = md5(clipFile.path)
        val existing = modelStorage.upload.getDocument(collection, pathHash)
        if (existing != null) {
            Napier.i {
                "upload $pathHash exists"
            }
            return
        }
        val id = now().toInstant(
            TimeZone.UTC
        ).toEpochMilliseconds()
        val uploadInfo = UploadInfo.EMPTY.copy(
            id = id,
            objectId = myUid,
            pathHash = pathHash,
            path = clipFile.path,
            total = clipFile.size,
            status = UploadStatus.UPLOADING,
            name = clipFile.name,
            contentType = clipFile.contentType.contentType,
            chunkSize = 10L * 1024 * 1024 // 10MB
        )
        modelStorage.upload.save(collection, uploadInfo)
        upload(userSession, myUid, clipFile, modelStorage, collection, pathHash, uploadInfo)
    }

    private suspend fun upload(
        userSession: CustomUserSessionManager,
        myUid: PrimaryKey,
        clientFile: ClientFile,
        modelStorage: ModelStorage,
        collection: UploadCollection,
        pathHash: String,
        uploadInfo: UploadInfo,
    ) {
        if (uploadInfo.status != UploadStatus.UPLOADING) {
            updateUploadInfo(modelStorage, collection, pathHash) {
                it.copy(status = UploadStatus.UPLOADING)
            }
        }
        val objectTuple = myUid ob ObjectType.USER
        val chunkThreshold = uploadInfo.chunkSize
        if (clientFile.size > chunkThreshold) {
            uploadChunked(userSession, objectTuple, clientFile, modelStorage, uploadInfo)
            return
        }
        userSession.upload(
            objectTuple,
            clientFile.getUploadDataFromClipFile()
        ) { p, _ ->
            updateUploadInfo(modelStorage, collection, pathHash) {
                it.copy(progress = p)
            }
        }.onSuccess {
            updateUploadInfo(modelStorage, collection, pathHash) {
                it.copy(status = UploadStatus.SUCCESS)
            }
        }.onFailure { throwable ->
            updateUploadInfo(modelStorage, collection, pathHash) {
                it.copy(status = UploadStatus.FAILED, message = throwable.safeMessage())
            }
        }
    }

    private suspend fun uploadChunked(
        userSession: CustomUserSessionManager,
        objectTuple: ObjectTuple,
        clipFile: ClientFile,
        modelStorage: ModelStorage,
        uploadInfo: UploadInfo,
    ) {
        val collection = UploadCollection.fromInfo(uploadInfo)
        val pathHash = uploadInfo.pathHash
        getUploadId(modelStorage, userSession, objectTuple, clipFile, uploadInfo).mapResult {
            userSession.getChunkStatus(it)
        }.mapResult {
            uploadChunkedFiles(
                userSession,
                it.id,
                clipFile,
                modelStorage,
                uploadInfo,
                it
            )
        }.mapResult {
            userSession.completeChunkUpload(it)
        }.onSuccess {
            updateUploadInfo(modelStorage, collection, pathHash) {
                it.copy(status = UploadStatus.SUCCESS)
            }
        }.onFailure { throwable ->
            updateUploadInfo(modelStorage, collection, pathHash) {
                it.copy(status = UploadStatus.FAILED, message = throwable.safeMessage())
            }
        }
    }

    private suspend fun getUploadId(
        modelStorage: ModelStorage,
        userSession: CustomUserSessionManager,
        objectTuple: ObjectTuple,
        clipFile: ClientFile,
        uploadInfo: UploadInfo
    ): Result<PrimaryKey> {
        val collection = UploadCollection.fromInfo(uploadInfo)
        val pathHash = uploadInfo.pathHash
        val existingRecordId = modelStorage.upload.getDocument(collection, pathHash)?.recordId
        return Result.success(existingRecordId).mapResult {
            if (it == null) {
                getUploadRecordId(userSession, objectTuple, clipFile, modelStorage, uploadInfo)
            } else {
                Result.success(it)
            }
        }
    }

    private suspend fun uploadChunkedFiles(
        userSession: CustomUserSessionManager,
        uploadRecordId: PrimaryKey,
        clipFile: ClientFile,
        modelStorage: ModelStorage,
        uploadInfo: UploadInfo,
        status: CustomApi.Files.Chunks.StatusResponse,
    ): Result<PrimaryKey> {
        return runCatching {
            val collection = UploadCollection.fromInfo(uploadInfo)
            val pathHash = uploadInfo.pathHash
            val chunkSize = uploadInfo.chunkSize
            val uploadedIndices = status.uploaded.toSet()
            val totalSent = uploadedIndices.sumOf { idx ->
                val chunkCount = ((clipFile.size + chunkSize - 1) / chunkSize).toInt()
                val isLast = idx == chunkCount - 1
                if (isLast) {
                    clipFile.size - chunkSize * (chunkCount - 1)
                } else {
                    chunkSize
                }
            }
            updateUploadInfo(modelStorage, collection, pathHash) {
                it.copy(progress = totalSent)
            }

            var index = 0
            var currentSent = totalSent
            clipFile.source().use { raw ->
                while (true) {
                    val buf = Buffer()
                    var count = 0L
                    while (count < chunkSize) {
                        val read = raw.readAtMostTo(buf, minOf(1024, chunkSize - count))
                        if (read == -1L) break
                        count += read
                    }
                    Napier.i(tag = "upload") {
                        "upload chunk $index, size $count"
                    }
                    if (count == 0L) break
                    // skip already uploaded chunks by index
                    if (!uploadedIndices.contains(index)) {
                        val hash = sha256(buf.peek())
                        userSession.uploadChunk(uploadRecordId, index, buf, hash) { p, _ ->
                            updateUploadInfo(modelStorage, collection, pathHash) {
                                it.copy(progress = currentSent + p)
                            }
                        }.getOrThrow()
                        currentSent += count
                        updateUploadInfo(modelStorage, collection, pathHash) {
                            it.copy(progress = currentSent)
                        }
                    }
                    index++
                }
            }
            uploadRecordId
        }
    }

    private suspend fun getUploadRecordId(
        userSession: CustomUserSessionManager,
        objectTuple: ObjectTuple,
        clipFile: ClientFile,
        modelStorage: ModelStorage,
        uploadInfo: UploadInfo,
    ): Result<PrimaryKey> {
        val chunkSize = uploadInfo.chunkSize
        val pathHash = uploadInfo.pathHash
        val collection = UploadCollection.fromInfo(uploadInfo)
        return userSession.initChunkUpload(
            objectTuple,
            clipFile.name,
            clipFile.size,
            clipFile.contentType,
            chunkSize
        ).onSuccess { initResponse ->
            updateUploadInfo(modelStorage, collection, pathHash) {
                it.copy(recordId = initResponse.recordId, chunkSize = initResponse.chunkSize)
            }
        }.map {
            it.recordId
        }
    }

    suspend fun updateUploadInfo(
        modelStorage: ModelStorage,
        collection: UploadCollection,
        pathHash: String,
        block: (UploadInfo) -> UploadInfo
    ) {
        val uploadInfo = modelStorage.upload.getDocument(collection, pathHash) ?: return
        modelStorage.upload.save(collection, block(uploadInfo))
    }
}
