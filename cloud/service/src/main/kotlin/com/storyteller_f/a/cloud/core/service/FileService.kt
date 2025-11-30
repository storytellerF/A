package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.CommonPath
import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.getImageDimension
import com.storyteller_f.a.backend.core.mapPagingNotNull
import com.storyteller_f.a.backend.core.mapPagingResultNotNull
import com.storyteller_f.a.backend.core.service.CopyPack
import com.storyteller_f.a.backend.core.service.FileDocument
import com.storyteller_f.a.backend.core.service.FileDocumentSearch
import com.storyteller_f.a.backend.core.service.ProcessedUploadPack
import com.storyteller_f.a.backend.core.service.UploadPack
import com.storyteller_f.a.backend.core.types.FileRecord
import com.storyteller_f.a.backend.core.types.UploadRecord
import com.storyteller_f.a.backend.core.types.toFileInfo
import com.storyteller_f.a.backend.core.types.toFileRefInfo
import com.storyteller_f.a.backend.core.types.toUploadRecordInfo
import com.storyteller_f.a.backend.filesystem.FileSystemObjectStorageService
import com.storyteller_f.a.cloud.core.utils.cleanImageMeta
import com.storyteller_f.a.cloud.core.utils.readFlacAlbumFromAudioStream
import com.storyteller_f.a.cloud.core.utils.readMp3AlbumFromAudioStream
import com.storyteller_f.shared.model.A_FILE_DEFAULT_BUCKET
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.QuotaType
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.UploadRecordStatus
import com.storyteller_f.shared.utils.firstOrNull
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.tika.mime.MimeTypes
import java.io.BufferedInputStream
import java.io.File
import kotlin.io.path.exists
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class FileResponse(val file: File)

class PathResponse(val file: java.nio.file.Path)

suspend fun Backend.getFileList(
    uid: PrimaryKey,
    fileQuery: CustomApi.Files.FileQuery,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<FileInfo>?> {
    if (fileQuery.objectType == ObjectType.TOPIC) {
        return Result.failure(CustomBadRequestException("can't get topic file record"))
    }
    val parentType = fileQuery.objectType
    val parentId = fileQuery.objectId
    if (parentId == null || parentType == null) error("invalid query")
    return checkRootWritePermission(
        parentType,
        parentId,
        uid
    ).mapResultIfNotNull {
        getFileInfoPaginationResult(parentId, primaryKeyFetch)
    }
}

suspend fun Backend.getFileInfoByName(
    uid: PrimaryKey,
    objectTuple: ObjectTuple,
    word: String,
): Result<FileInfo?> {
    if (objectTuple.objectType == ObjectType.TOPIC) {
        return Result.failure(CustomBadRequestException("can't get topic file record"))
    }
    val parentType = objectTuple.objectType
    val parentId = objectTuple.objectId
    return checkRootWritePermission(
        parentType,
        parentId,
        uid
    ).mapResultIfNotNull { (_, rootId) ->
        database.file.getFileRecord(rootId, word)
    }.mapResultIfNotNull { fileRecord ->
        processFileRecordToFileInfo(listOf(fileRecord))
    }.mapIfNotNull {
        it.first()
    }
}

suspend fun Backend.searchFiles(
    uid: PrimaryKey,
    query: CustomApi.Files.FileSearchQuery,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<FileInfo>?> {
    val word = query.word
    val objectId = query.objectId
    val objectType = query.objectType

    // 如果指定了 objectId 和 objectType，需要检查权限
    if (objectId != null && objectType != null) {
        return checkRootWritePermission(objectType, objectId, uid).mapResultIfNotNull {
            searchFilesByWord(word, objectId, primaryKeyFetch)
        }
    }

    // 否则只搜索用户自己的文件
    return searchFilesByWord(word, uid, primaryKeyFetch)
}

suspend fun Backend.uncheckedSearchFiles(
    query: com.storyteller_f.a.api.AdminApi.Files.FileSearchQuery,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<FileInfo>?> {
    val word = query.word
    return if (word.isNullOrBlank()) {
        // 如果没有关键词，返回所有文件
        getAllFileInfos(primaryKeyFetch)
    } else {
        // 使用 fileSearchService 搜索
        searchFilesByWord(word, null, primaryKeyFetch)
    }
}

private suspend fun Backend.searchFilesByWord(
    word: String?,
    ownerId: PrimaryKey?,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<FileInfo>?> {
    return if (word.isNullOrBlank()) {
        // 如果没有关键词，从数据库直接查询
        if (ownerId != null) {
            getFileInfoPaginationResult(ownerId, primaryKeyFetch)
        } else {
            getAllFileInfos(primaryKeyFetch)
        }
    } else {
        // 使用 fileSearchService 搜索
        val searchQuery = if (ownerId != null) {
            FileDocumentSearch.Keyword(listOf(word), ownerId)
        } else {
            FileDocumentSearch.Keyword(listOf(word))
        }
        fileSearchService.searchDocument(searchQuery, primaryKeyFetch).mapResultIfNotNull { (documents, total) ->
            val fileIds = documents.map { it.id }
            if (fileIds.isEmpty()) {
                Result.success(PaginationResult(emptyList(), total))
            } else {
                database.file.getFileRecordByIds(fileIds).mapResultIfNotNull { fileRecords ->
                    processFileRecordToFileInfo(fileRecords)
                }.mapIfNotNull { fileInfos ->
                    PaginationResult(fileInfos, total)
                }
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
suspend fun Backend.extractAlbum(fileRecordId: PrimaryKey, root: File, uid: PrimaryKey) =
    database.file.getFileRecordByIds(listOf(fileRecordId))
        .mapResultIfNotNull { fileRecords ->
            val fileRecord = fileRecords.first()
            if (fileRecord.owner != uid) {
                throw ForbiddenException("no permission")
            }
            if (fileRecord.contentType.startsWith("audio")) {
                Result.success(fileRecord)
            } else {
                Result.failure(CustomBadRequestException("not an audio file"))
            }
        }.mapResultIfNotNull { fileRecord ->
            objectStorageService.getInputStream(A_FILE_DEFAULT_BUCKET, fileRecord.fullName)
                .mapResult { input ->
                    extractAlbumFromStream(input.buffered(), root, fileRecord)
                }.mapResultIfNotNull { (file, contentType) ->
                    val name = newCoverFileName(fileRecord.name, contentType)
                    tryUploadFiles(
                        fileRecord.owner,
                        fileRecord.ownerType,
                        listOf(
                            UploadPack(
                                file,
                                name,
                                fileRecord.size,
                                "${fileRecord.owner}/$name"
                            )
                        )
                    )
                }
        }.mapIfNotNull {
            ServerResponse(it)
        }

@OptIn(ExperimentalUuidApi::class)
private suspend fun extractAlbumFromStream(
    input: BufferedInputStream,
    root: File,
    fileRecord: FileRecord
) = runCatching {
    withContext(Dispatchers.IO) {
        input.use {
            val saveAlbum = { image: ByteArray, mimeType: String ->
                val ext = getCoverExtensionFromMimeType(mimeType)
                val file = File(root, "${Uuid.random()}$ext")
                file.writeBytes(image)
                file
            }
            when (fileRecord.contentType) {
                "audio/mp3" -> it.readMp3AlbumFromAudioStream(saveAlbum)
                "audio/flac", "audio/x-flac" -> it.readFlacAlbumFromAudioStream(saveAlbum)
                else -> throw CustomBadRequestException("unsupported audio type: ${fileRecord.contentType}")
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
fun newFileName(fileName: String): String {
    val extension = fileName.substringAfterLast(".").take(10)
    val originName = fileName.substringBeforeLast(".")
    // length 32
    val uuid = Uuid.random().toHexString()
    val newSavedName = if (fileName.length > 60) {
        // 60 - 32 - 1 = 27
        "${originName.take(27 - extension.length)}$uuid.$extension"
    } else {
        fileName
    }
    return newSavedName
}

@OptIn(ExperimentalUuidApi::class)
fun newCopiedFileName(fileName: String): String {
    val extension = fileName.substringAfterLast(".").take(10)
    val originName = fileName.substringBeforeLast(".")
    // length 32
    val uuid = Uuid.random().toHexString()
    return "${originName.take(27 - extension.length)}$uuid.$extension"
}

@OptIn(ExperimentalUuidApi::class)
private fun newCoverFileName(fileName: String, contentType: String): String {
    val extension = getCoverExtensionFromMimeType(contentType)
    val originName = fileName.substringBeforeLast(".") + "_cover"
    // length 32
    val uuid = Uuid.random().toHexString()
    return "${originName.take(27 - extension.length)}$uuid$extension"
}

fun getCoverExtensionFromMimeType(mimeType: String): String {
    return MimeTypes.getDefaultMimeTypes().forName(mimeType)?.extension
        ?: error("Unsupported mime type: $mimeType")
}

suspend fun getFileSystemDownloadUrl(
    backend: Backend,
    paths: List<String>
): Result<PathResponse?> {
    val service = backend.objectStorageService
    return if (service is FileSystemObjectStorageService) {
        val path = service.getPathResponse(paths)
        if (path?.exists() == true) {
            val value = PathResponse(path)
            Result.success(value)
        } else {
            Result.success(null)
        }
    } else {
        Result.failure(CustomBadRequestException("can't find file"))
    }
}

suspend fun Backend.getFileInfoPaginationResult(
    uid: PrimaryKey,
    primaryKeyFetch: PrimaryKeyFetch,
): Result<PaginationResult<FileInfo>> = database.file.getFileRecordPaginationList(uid, primaryKeyFetch)
    .mapPagingResultNotNull { list ->
        processFileRecordToFileInfo(list)
    }

suspend fun Backend.getAllFileInfos(primaryKeyFetch: PrimaryKeyFetch): Result<PaginationResult<FileInfo>> =
    database.file.getAllFileRecordPaginationList(primaryKeyFetch).mapPagingResultNotNull { list ->
        processFileRecordToFileInfo(list)
    }

suspend fun Backend.getUserUploadRecords(
    uid: PrimaryKey,
    primaryKeyFetch: PrimaryKeyFetch
) = database.file.getUploadRecordPaginationList(uid, primaryKeyFetch).mapPagingNotNull { list ->
    list.map { it.toUploadRecordInfo() }
}

suspend fun Backend.getFileInfoById(id: PrimaryKey): Result<FileInfo?> =
    database.file.getFileRecordByIds(listOf(id)).mapResultIfNotNull { list ->
        processFileRecordToFileInfo(list)
    }.mapIfNotNull { it.firstOrNull() }

suspend fun Backend.tryCopyFile(p: CommonPath, uid: PrimaryKey): Result<ServerResponse<FileInfo>?> =
    database.file.getFileRecordByIds(listOf(p.id)).firstOrNull().mapResultIfNotNull { fileRecord ->
        checkRootReadPermission(
            fileRecord.ownerType,
            fileRecord.owner,
            uid
        ).mapResultIfNotNull { permission ->
            if (permission.hasRead) {
                // 检查重复媒体
                Result.success(fileRecord)
            } else {
                Result.failure(ForbiddenException())
            }
        }
    }.mapResultIfNotNull { fileRecord ->
        lockQuotaInfo(
            ObjectTuple(uid, ObjectType.USER),
            QuotaType.FILE,
            fileRecord.size,
            fileRecord.name
        ) {
            copyFile(fileRecord, uid)
        }
    }.mapResultIfNotNull {
        processFileRecordToFileInfo(it)
    }.mapIfNotNull {
        ServerResponse(it)
    }

private suspend fun Backend.copyFile(
    fileRecord: FileRecord,
    newOwner: PrimaryKey
) = database.file.getFileRecord(newOwner, fileRecord.name).map {
    if (it == null) {
        fileRecord.name
    } else {
        newCopiedFileName(fileRecord.name)
    }
}.mapResult { newName ->
    val id = SnowflakeFactory.nextId()
    val newFileRecord = fileRecord.copy(id = id, owner = newOwner, name = newName, fullName = "$newOwner/$newName")
    objectStorageService.copy(
        A_FILE_DEFAULT_BUCKET,
        listOf(CopyPack(fileRecord.fullName, newFileRecord.fullName))
    ).map {
        listOf(newFileRecord)
    }
}

@OptIn(ExperimentalUuidApi::class)
suspend fun Backend.tryUploadFiles(
    ownerId: PrimaryKey,
    ownerType: ObjectType,
    files: List<UploadPack>
) = lockQuotaInfo(ownerId ob ownerType, QuotaType.FILE, files.sumOf {
    it.size
}, Uuid.random().toHexString()) {
    val f = mutableListOf<File>()
    try {
        val uploadPacks = processContentTypeAndDimension(files)
        val packs = removeExifIfImage(uploadPacks, f)
        uploadFilesToStorage(ownerId, ownerType, packs)
    } finally {
        f.forEach {
            it.delete()
        }
    }
}.mapResult {
    processFileRecordToFileInfo(it)
}

private suspend fun processContentTypeAndDimension(files: List<UploadPack>): List<ProcessedUploadPack> =
    files.map {
        val detectedType = Backend.tika.detect(it.file) ?: error("delete content type failed")
        val dimension = if (detectedType.startsWith("image")) {
            getImageDimension(it.file.absolutePath, detectedType) {
                it.file.inputStream().buffered()
            }
        } else {
            null
        }
        ProcessedUploadPack(it, detectedType, dimension)
    }

@OptIn(ExperimentalUuidApi::class)
private fun removeExifIfImage(
    uploadPacks: List<ProcessedUploadPack>,
    f: MutableList<File>
): List<ProcessedUploadPack> = uploadPacks.map {
    if (it.contentType.startsWith("image")) {
        val target = File(System.getProperty("java.io.tmpdir"), Uuid.random().toHexString() + it.pack.name)
        cleanImageMeta(it.pack.file, target.outputStream().buffered(), it.contentType)
        f.add(target)
        it.copy(pack = it.pack.copy(file = target))
    } else {
        it
    }
}

private suspend fun Backend.uploadFilesToStorage(
    ownerId: PrimaryKey,
    ownerType: ObjectType,
    uploadPacks: List<ProcessedUploadPack>
): Result<List<FileRecord>> {
    val data = uploadPacks.map { p ->
        val uploadPack = p.pack
        val nextId = SnowflakeFactory.nextId()
        val dimension = p.dimension
        FileRecord(
            nextId,
            now(),
            uploadPack.name,
            0,
            dimension?.width ?: 0,
            dimension?.height ?: 0,
            ownerId,
            ownerType,
            p.contentType,
            uploadPack.size,
            uploadPack.fullName
        )
    }
    return objectStorageService.upload(A_FILE_DEFAULT_BUCKET, uploadPacks.map {
        it.pack
    }).map {
        data
    }
}

suspend fun Backend.getFileInfoList(names: List<String>): Result<List<FileInfo?>?> {
    return database.file.getFileRecordByNames(names).mapResult { fileRecords ->
        processFileRecordToFileInfo(fileRecords)
    }
}

suspend fun Backend.processFileRecordToFileInfo(
    fileRecords: List<FileRecord>,
): Result<List<FileInfo>> {
    return objectStorageService.get(A_FILE_DEFAULT_BUCKET, fileRecords.map {
        it.fullName
    }).map { mediaList ->
        val mediaRecordMap = mediaList.associateBy { it.fullName }
        fileRecords.map { media ->
            mediaRecordMap[media.fullName]!!.let {
                media.toFileInfo(it.url, it.lastModified)
            }
        }
    }
}

suspend fun initChunkUpload(
    backend: Backend,
    id: PrimaryKey,
    body: CustomApi.Files.Chunks.InitBody
) = backend.checkRootWritePermission(body.objectType, body.objectId, id)
    .mapResultIfNotNull {
        backend.checkQuotaStatus(body.objectId ob body.objectType, body.size, QuotaType.FILE)
    }.mapResultIfNotNull {
        backend.database.file.insertUploadRecord(
            UploadRecord(
                SnowflakeFactory.nextId(),
                now(),
                body.objectId,
                body.objectType,
                UploadRecordStatus.PENDING,
                body.size,
                0,
                body.name,
                body.chunkSize
            )
        )
    }.mapIfNotNull {
        CustomApi.Files.Chunks.InitResponse(it.id, body.chunkSize)
    }

@Suppress("LongMethod")
suspend fun completeChunkUpload(
    backend: Backend,
    id: PrimaryKey,
    path: CommonPath
): Result<FileInfo?> {
    val recordId = path.id
    // 读取上传记录以获取所有者信息
    val record = backend.database.file.getUploadRecord(recordId).getOrThrow()
        ?: throw CustomBadRequestException("upload record not found")
    val tuple = ObjectTuple(record.objectId, record.objectType)
    return backend.checkRootWritePermission(tuple.objectType, tuple.objectId, id)
        .mapResultIfNotNull {
            runCatching {
                val name = record.name
                val totalSize = record.total
                val newSavedName = newFileName(name)
                // 使用对象存储 compose 在默认桶直接合并到最终路径
                val chunkRecords = backend.objectStorageService.list(
                    A_FILE_DEFAULT_BUCKET,
                    "chunks/$recordId/"
                ).getOrThrow()
                val sortedSources = chunkRecords.sortedBy { r ->
                    r.fullName.substringAfter("chunk_").toIntOrNull() ?: 0
                }.map { it.fullName }
                val targetFullName = "${tuple.objectId}/$newSavedName"
                // 合并到最终对象
                backend.objectStorageService.compose(A_FILE_DEFAULT_BUCKET, targetFullName, sortedSources).getOrThrow()
                val fileRecord = backend.buildFileRecordFromComposedObject(
                    targetFullName,
                    newSavedName,
                    tuple,
                    totalSize
                )
                // 完成后释放上传锁并更新配额使用量
                val quotaInfo = backend.getQuotaInfo(QuotaType.FILE, tuple).getOrThrow()
                backend.database.file.updateUploadRecordStatus(
                    quotaInfo,
                    record.copy(status = UploadRecordStatus.SUCCESS),
                    listOf(fileRecord),
                ).onSuccess {
                    // 保存文件文档到搜索服务
                    backend.fileSearchService.saveDocument(
                        listOf(FileDocument.fromFileRecord(fileRecord))
                    ).onFailure { error ->
                        Napier.e(error) {
                            "save file document failed"
                        }
                    }
                }.getOrThrow()
                // 清理分片与元数据
                cleanChunk(backend, sortedSources, recordId)
                listOf(fileRecord)
            }
        }.mapResultIfNotNull {
            backend.processFileRecordToFileInfo(it)
        }.firstOrNull()
}

private suspend fun Backend.buildFileRecordFromComposedObject(
    targetFullName: String,
    newSavedName: String,
    tuple: ObjectTuple,
    totalSize: Long
): FileRecord {
    // 内容类型与尺寸检测
    val contentType = objectStorageService.getInputStream(
        A_FILE_DEFAULT_BUCKET,
        targetFullName
    ).getOrThrow().buffered().use {
        Backend.tika.detect(it)
    }
    val dimension = if (contentType.startsWith("image")) {
        getImageDimension(targetFullName, contentType) {
            objectStorageService.getInputStream(A_FILE_DEFAULT_BUCKET, targetFullName).getOrThrow().buffered()
        }
    } else {
        null
    }

    // 写入数据库记录
    return FileRecord(
        id = SnowflakeFactory.nextId(),
        createdTime = now(),
        name = newSavedName,
        0,
        width = dimension?.width ?: 0,
        height = dimension?.height ?: 0,
        owner = tuple.objectId,
        ownerType = tuple.objectType,
        contentType = contentType,
        size = totalSize,
        fullName = targetFullName,
    )
}

private suspend fun cleanChunk(
    backend: Backend,
    sortedSources: List<String>,
    recordId: PrimaryKey
) {
    runCatching {
        backend.objectStorageService.delete(A_FILE_DEFAULT_BUCKET, sortedSources).getOrThrow()
        // 删除整个分片目录下可能残留的对象
        val sessionObjects = backend.objectStorageService.list(
            A_FILE_DEFAULT_BUCKET,
            "chunks/$recordId/"
        ).getOrThrow().map { it.fullName }
        if (sessionObjects.isNotEmpty()) {
            backend.objectStorageService.delete(A_FILE_DEFAULT_BUCKET, sessionObjects)
                .getOrThrow()
        }
    }.onFailure {
        Napier.e(it) { "clean chunk failed $recordId $sortedSources" }
    }
}

suspend fun abortChunkUpload(
    backend: Backend,
    path: CommonPath
) = backend.database.file.getUploadRecord(path.id).mapResultIfNotNull { uploadRecord ->
    runCatching {
        // 删除默认桶下对应会话的所有对象，并释放上传锁
        val recordId = path.id
        val tuple = ObjectTuple(uploadRecord.objectId, uploadRecord.objectType)
        val quotaInfo = backend.getQuotaInfo(QuotaType.FILE, tuple).getOrThrow()
        backend.database.file.updateUploadRecordStatus(
            quotaInfo,
            uploadRecord.copy(status = UploadRecordStatus.ABORTED),
            emptyList()
        ).getOrThrow()
        val records = backend.objectStorageService.list(A_FILE_DEFAULT_BUCKET, "chunks/$recordId/").getOrThrow()
        val names = records.map { it.fullName }
        backend.objectStorageService.delete(A_FILE_DEFAULT_BUCKET, names).getOrThrow()
    }
}

suspend fun getChunkStatus(
    backend: Backend,
    path: CommonPath
) = backend.database.file.getUploadRecord(path.id).mapResultIfNotNull { record ->
    runCatching {
        // 从对象存储临时桶读取已上传分片索引
        val records = backend.objectStorageService.list(
            A_FILE_DEFAULT_BUCKET,
            "chunks/${path.id}/"
        ).getOrThrow()
        val uploadedIndices = records.mapNotNull { r ->
            r.fullName.substringAfter("chunk_").toIntOrNull()
        }.sorted()
        // 从数据库中的 UploadRecord 获取 chunkSize/size
        CustomApi.Files.Chunks.StatusResponse(uploadedIndices, record.chunkSize, record.total, record.id, record.status)
    }
}

suspend fun Backend.uncheckedGetFileRefsByFileId(
    fileId: PrimaryKey,
    primaryKeyFetch: PrimaryKeyFetch
) = database.file.getFileRefsByFileId(fileId, primaryKeyFetch).mapPagingNotNull { list ->
    list.map { ref ->
        ref.toFileRefInfo()
    }
}

suspend fun Backend.getFileRefsByFileId(
    uid: PrimaryKey,
    fileId: PrimaryKey,
    primaryKeyFetch: PrimaryKeyFetch
) = checkRootReadPermission(ObjectType.FILE, fileId, uid).mapResultIfNotNull {
    database.file.getFileRefsByFileId(fileId, primaryKeyFetch).mapPagingNotNull { list ->
        list.map { ref ->
            ref.toFileRefInfo()
        }
    }
}
