package com.storyteller_f.a.cloud.core.service

import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.api.core.Path
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.UploadPack
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.backend.service.copyMedia
import com.storyteller_f.a.backend.service.getMediaPaginationResult
import com.storyteller_f.a.backend.service.object_storage.FileSystemObjectStorageService
import com.storyteller_f.a.backend.service.object_storage.uploadFilesAfterDetectContentTypeAndDimension
import com.storyteller_f.a.backend.service.processMediaToMediaInfo
import com.storyteller_f.shared.model.AMEDIA_DEFAULT_BUCKET
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResultIfNotNull
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
    routeMedia: CustomApi.Medias.MediaQuery,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<FileInfo>?> {
    if (routeMedia.objectType == ObjectType.TOPIC) {
        return Result.failure(CustomBadRequestException("can't get topic media"))
    }
    val parentType = routeMedia.objectType
    val parentId = routeMedia.objectId
    if (parentId == null || parentType == null) error("invalid query")
    return checkRootWritePermission(
        parentType,
        parentId,
        uid
    ).mapResultIfNotNull { (_, _, hasWrite) ->
        if (hasWrite) {
            getMediaPaginationResult(uid, primaryKeyFetch)
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
            exposedDatabase.fileDatabase.getMedia(objectId, word).mapResultIfNotNull { media ->
                processMediaToMediaInfo(listOf(media)).map {
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
    exposedDatabase.fileDatabase.getMediaByIds(listOf(mediaId)).mapResultIfNotNull { mediaList ->
        val media = mediaList.first()
        if (media.owner != uid) {
            throw ForbiddenException("no permission")
        }
        if (media.contentType.startsWith("audio")) {
            objectStorageService.getInputStream(AMEDIA_DEFAULT_BUCKET, media.fullName).map { input ->
                withContext(Dispatchers.IO) {
                    input.use {
                        val saveAlbum = { image: ByteArray, mimeType: String ->
                            val file = File(
                                root,
                                "${Uuid.Companion.random()}.${getExtensionFromMimeType(mimeType)}"
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
            }.mapResultIfNotNull { (file, contentType) ->
                if (file != null) {
                    val name = newCoverFileName(media.name, contentType)
                    uploadFilesAfterDetectContentTypeAndDimension(
                        listOf(UploadPack(file, name, media.owner, media.ownerType, media.size))
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

suspend fun Backend.copyMedia(
    p: Path,
    uid: PrimaryKey
): Result<ServerResponse<FileInfo>?> =
    exposedDatabase.fileDatabase.getMediaByIds(listOf(p.id)).mapResultIfNotNull { mediaList ->
        val media = mediaList.firstOrNull()
        if (media != null) {
            checkRootReadPermission(media.ownerType, media.owner, uid).mapResultIfNotNull { permission ->
                if (permission.hasRead) {
                    // 检查重复媒体
                    exposedDatabase.fileDatabase.getMedia(uid, media.name).map {
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

fun <T> InputStream.readFlacAlbumFromAudioStream(saveAlbum: (ByteArray, String) -> T): T? {
    val signature = ByteArray(4)
    read(signature)

    if (String(signature) != "fLaC") {
        error("Not a valid FLAC file")
    }

    while (true) {
        val header = read()
        val blockType = header and 0x7F
        val lengthBytes = ByteArray(3)
        read(lengthBytes)
        val blockLength = ((lengthBytes[0].toInt() and 0xFF) shl 16) or
            ((lengthBytes[1].toInt() and 0xFF) shl 8) or
            (lengthBytes[2].toInt() and 0xFF)

        if (blockType == 6) {
            val pictureData = ByteArray(blockLength)
            read(pictureData)

            val dataInput = pictureData.inputStream().buffered()

            fun readInt(): Int =
                (dataInput.read() shl 24) or (dataInput.read() shl 16) or
                    (dataInput.read() shl 8) or dataInput.read()

            readInt() // picture type
            val mimeLength = readInt()
            val mimeBytes = ByteArray(mimeLength)
            dataInput.read(mimeBytes)
            val mimeType = String(mimeBytes)

            val descLength = readInt()
            dataInput.skip(descLength.toLong())

            readInt() // width
            readInt() // height
            readInt() // color depth
            readInt() // indexed colors

            val picDataLength = readInt()
            val realImage = ByteArray(picDataLength)
            dataInput.read(realImage)

            return saveAlbum(realImage, mimeType)
        } else {
            skip(blockLength.toLong())
        }

        if ((header and 0x80) != 0) {
            break
        }
    }
    return null
}

@Suppress("CyclomaticComplexMethod")
fun <T> InputStream.readMp3AlbumFromAudioStream(
    saveAlbum: (ByteArray, String) -> T
): T? {
    // 读取 ID3 header（10 bytes）
    val header = ByteArray(10)
    if (read(header) != 10 ||
        header[0] != 'I'.code.toByte() ||
        header[1] != 'D'.code.toByte() ||
        header[2] != '3'.code.toByte()
    ) {
        error("不是有效的 ID3v2 标签")
    }

    // 计算标签总长度（同步安全整数：4 x 7bits）
    val tagSize = syncSafeInt(header.copyOfRange(6, 10))
    var totalRead = 0

    while (totalRead < tagSize) {
        val frameHeader = ByteArray(10)
        val read = read(frameHeader)
        if (read < 10) break

        val frameId = String(frameHeader, 0, 4)
        val frameSize = ((frameHeader[4].toInt() and 0xFF) shl 24) or
            ((frameHeader[5].toInt() and 0xFF) shl 16) or
            ((frameHeader[6].toInt() and 0xFF) shl 8) or
            (frameHeader[7].toInt() and 0xFF)

        totalRead += 10 + frameSize
        if (frameId != "APIC") {
            skip(frameSize.toLong())
            continue
        }

        val frameData = ByteArray(frameSize)
        if (read(frameData) != frameSize) return null

        var idx = 1 // skip text encoding byte

        // 读取 MIME 类型字符串
        val mimeStart = idx
        while (idx < frameData.size && frameData[idx] != 0.toByte()) idx++
        val mimeType = String(frameData, mimeStart, idx - mimeStart)
        idx++ // skip null byte

        // 跳过图片类型（1 byte）
        idx++

        // 跳过描述字符串
        while (idx < frameData.size && frameData[idx] != 0.toByte()) idx++
        idx++ // skip null byte

        if (idx >= frameData.size) return null

        val imageData = frameData.copyOfRange(idx, frameData.size)
        return saveAlbum(imageData, mimeType)
    }
    return null
}

private fun syncSafeInt(bytes: ByteArray): Int {
    return ((bytes[0].toInt() and 0x7F) shl 21) or
        ((bytes[1].toInt() and 0x7F) shl 14) or
        ((bytes[2].toInt() and 0x7F) shl 7) or
        (bytes[3].toInt() and 0x7F)
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
