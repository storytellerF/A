package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.api.core.CommonPath
import com.storyteller_f.a.backend.core.CopyPack
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.ProcessedUploadPack
import com.storyteller_f.a.backend.core.UploadPack
import com.storyteller_f.a.backend.core.types.FileRecord
import com.storyteller_f.a.backend.core.types.toFileInfo
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.backend.service.lockQuotaInfo
import com.storyteller_f.a.backend.service.object_storage.FileSystemObjectStorageService
import com.storyteller_f.a.backend.service.object_storage.getImageDimension
import com.storyteller_f.a.backend.service.processFileRecordToFileInfo
import com.storyteller_f.a.cloud.core.utils.readFlacAlbumFromAudioStream
import com.storyteller_f.a.cloud.core.utils.readMp3AlbumFromAudioStream
import com.storyteller_f.shared.model.AMEDIA_DEFAULT_BUCKET
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.QuotaType
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.merge
import com.storyteller_f.shared.utils.now
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.io.path.exists
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class FileResponse(val file: File)

class PathResponse(val file: java.nio.file.Path)

suspend fun Backend.getMediaList(
    uid: PrimaryKey,
    mediaQuery: CustomApi.Medias.MediaQuery,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<FileInfo>?> {
    if (mediaQuery.objectType == ObjectType.TOPIC) {
        return Result.failure(CustomBadRequestException("can't get topic media"))
    }
    val parentType = mediaQuery.objectType
    val parentId = mediaQuery.objectId
    if (parentId == null || parentType == null) error("invalid query")
    return checkRootWritePermission(
        parentType,
        parentId,
        uid
    ).mapResultIfNotNull { (_, _, hasWrite) ->
        if (hasWrite) {
            getFileInfoPaginationResult(parentId, primaryKeyFetch)
        } else {
            Result.failure(ForbiddenException("no permission"))
        }
    }
}

suspend fun Backend.getMediaByName(
    uid: PrimaryKey,
    objectTuple: ObjectTuple,
    word: String,
): Result<FileInfo?> {
    if (objectTuple.objectType == ObjectType.TOPIC) {
        return Result.failure(CustomBadRequestException("can't get topic media"))
    }
    val parentType = objectTuple.objectType
    val parentId = objectTuple.objectId
    return checkRootWritePermission(
        parentType,
        parentId,
        uid
    ).mapResultIfNotNull { (_, objectId, hasWrite) ->
        if (hasWrite) {
            combinedDatabase.fileDatabase.getFileRecord(objectId, word)
                .mapResultIfNotNull { media ->
                    processFileRecordToFileInfo(listOf(media)).map {
                        it.first()
                    }
                }
        } else {
            Result.failure(ForbiddenException("no permission"))
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
suspend fun Backend.extractAlbum(mediaId: PrimaryKey, root: File, uid: PrimaryKey) =
    combinedDatabase.fileDatabase.getFileRecordByIds(listOf(mediaId))
        .mapResultIfNotNull { mediaList ->
            val media = mediaList.first()
            if (media.owner != uid) {
                throw ForbiddenException("no permission")
            }
            if (media.contentType.startsWith("audio")) {
                objectStorageService.getInputStream(AMEDIA_DEFAULT_BUCKET, media.fullName)
                    .map { input ->
                        extractAlbumFromStream(input, root, media)
                    }.mapResultIfNotNull { (file, contentType) ->
                        if (file != null) {
                            val name = newCoverFileName(media.name, contentType)
                            tryUploadFiles(
                                media.owner, media.ownerType, listOf(
                                    UploadPack(
                                        file,
                                        name,
                                        media.size,
                                        "${media.owner}/$name"
                                    )
                                )
                            ).map {
                                ServerResponse(it.filterNotNull())
                            }
                        } else {
                            Result.success(null)
                        }
                    }
            } else {
                Result.failure(CustomBadRequestException("not an audio file"))
            }
        }

@OptIn(ExperimentalUuidApi::class)
private suspend fun extractAlbumFromStream(
    input: InputStream,
    root: File,
    media: FileRecord
): Pair<File?, String> = withContext(Dispatchers.IO) {
    input.use {
        val saveAlbum = { image: ByteArray, mimeType: String ->
            val file = File(
                root,
                "${Uuid.Companion.random()}.${
                    getExtensionFromMimeType(
                        mimeType
                    )
                }"
            )
            file.writeBytes(image)
            file
        }
        when (media.contentType) {
            "audio/mp3" -> it.readMp3AlbumFromAudioStream(saveAlbum)
            "audio/flac", "audio/x-flac" -> it.readFlacAlbumFromAudioStream(
                saveAlbum
            )

            else -> throw CustomBadRequestException("unsupported audio type: ${media.contentType}")
        } to media.contentType
    }
}

@OptIn(ExperimentalUuidApi::class)
fun newFileName(fileName: String): String {
    val extension = fileName.substringAfterLast(".").take(10)
    val originName = fileName.substringBeforeLast(".")
    // length 32
    val uuid = Uuid.Companion.random().toHexString()
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
    val uuid = Uuid.Companion.random().toHexString()
    return "${originName.take(27 - extension.length)}$uuid.$extension"
}

@OptIn(ExperimentalUuidApi::class)
private fun newCoverFileName(fileName: String, contentType: String): String {
    val extension = getExtensionFromMimeType(contentType)
    val originName = fileName.substringBeforeLast(".") + "_cover"
    // length 32
    val uuid = Uuid.Companion.random().toHexString()
    return "${originName.take(27 - extension.length)}$uuid.$extension"
}


fun getExtensionFromMimeType(mimeType: String): String {
    return when (mimeType) {
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "image/gif" -> "gif"
        "image/bmp" -> "bmp"
        "image/webp" -> "webp"
        "audio/mpeg" -> "mp3"
        "audio/ogg" -> "ogg"
        "audio/wav" -> "wav"
        "audio/flac", "audio/x-flac" -> "flac"
        else -> error("Unsupported mime type: $mimeType")
    }
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


suspend fun Backend.getFileInfoList(names: List<String>): Result<List<FileInfo?>?> {
    return combinedDatabase.fileDatabase.getFileRecordByNames(names).mapResult { medias ->
        processFileRecordToFileInfo(medias)
    }
}

suspend fun Backend.getFileInfoPaginationResult(
    uid: PrimaryKey,
    primaryKeyFetch: PrimaryKeyFetch,
): Result<PaginationResult<FileInfo>> =
    combinedDatabase.fileDatabase.getFileRecordPaginationList(uid, primaryKeyFetch)
        .mapResult { (list, count) ->
            processFileRecordToFileInfo(list).map {
                PaginationResult(it, count)
            }
        }

@OptIn(ExperimentalUuidApi::class)
suspend fun Backend.tryUploadFiles(
    ownerId: PrimaryKey,
    ownerType: ObjectType,
    files: List<UploadPack>
): Result<List<FileInfo?>> {
    val totalLength = files.sumOf {
        it.size
    }
    return lockQuotaInfo(
        ownerId,
        QuotaType.FILE,
        ownerType,
        totalLength,
        Uuid.random().toHexString()
    ) {
        uploadFiles(ownerId, ownerType, files.map {
            val detectedType = tika.detect(it.path)
            val dimension = if (detectedType.startsWith("image")) {
                getImageDimension(it.path.absolutePath, detectedType) {
                    it.path.inputStream()
                }
            } else {
                null
            }
            ProcessedUploadPack(it, detectedType, dimension)
        })
    }
}

private suspend fun Backend.uploadFiles(
    ownerId: PrimaryKey,
    ownerType: ObjectType,
    uploadPacks: List<ProcessedUploadPack>
): Result<List<FileInfo?>> {
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
    return merge({
        objectStorageService.upload(AMEDIA_DEFAULT_BUCKET, uploadPacks.map {
            it.pack
        })
    }, {
        combinedDatabase.fileDatabase.insertFileRecord(data, ownerId, ownerType)
    }).map { (mediaRecords) ->
        mediaRecords.mapIndexed { i, e ->
            data[i].toFileInfo(e.url, e.lastModified)
        }
    }
}


suspend fun Backend.tryCopyMedia(
    p: CommonPath,
    uid: PrimaryKey
): Result<ServerResponse<FileInfo>?> =
    combinedDatabase.fileDatabase.getFileRecordByIds(listOf(p.id)).mapResultIfNotNull { mediaList ->
        val media = mediaList.firstOrNull()
        if (media != null) {
            checkRootReadPermission(
                media.ownerType,
                media.owner,
                uid
            ).mapResultIfNotNull { permission ->
                if (permission.hasRead) {
                    // 检查重复媒体
                    combinedDatabase.fileDatabase.getFileRecord(uid, media.name).map {
                        if (it == null) {
                            "$uid/${media.name}"
                        } else {
                            "$uid/${newCopiedFileName(media.name)}"
                        }
                    }.mapResultIfNotNull {
                        copyMedia(media, uid, it)
                    }
                } else {
                    Result.failure(ForbiddenException())
                }
            }
        } else {
            Result.success(null)
        }
    }

private suspend fun Backend.copyMedia(
    fileRecord: FileRecord,
    newOwner: PrimaryKey,
    newName: String,
): Result<ServerResponse<FileInfo>> {
    val id = SnowflakeFactory.nextId()
    return merge({
        objectStorageService.copy(
            AMEDIA_DEFAULT_BUCKET,
            listOf(CopyPack("${fileRecord.owner}/${fileRecord.name}", newName))
        )
    }, {
        combinedDatabase.fileDatabase.insertFileRecord(
            listOf(
                fileRecord.copy(
                    id = id,
                    owner = newOwner
                )
            ),
            newOwner,
            ObjectType.USER
        )
    }).map { (list) ->
        ServerResponse(list.map {
            fileRecord.copy(owner = newOwner, id = id).toFileInfo(it.url, it.lastModified)
        }, null)
    }
}