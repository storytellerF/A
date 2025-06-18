package com.storyteller_f.a.server.service

import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.UploadPack
import com.storyteller_f.a.exposed.query.PaginationResult
import com.storyteller_f.a.server.route.RouteMedia
import com.storyteller_f.backend.service.Backend
import com.storyteller_f.backend.service.copyMedia
import com.storyteller_f.backend.service.getMediaInfoList
import com.storyteller_f.backend.service.getMediaPaginationResult
import com.storyteller_f.backend.service.media.uploadFilesAfterDetectContentTypeAndDimension
import com.storyteller_f.shared.model.AMEDIA_DEFAULT_BUCKET
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import io.ktor.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

suspend fun Backend.getMediaList(
    uid: PrimaryKey,
    routeMedia: RouteMedia,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<MediaInfo>?> {
    if (routeMedia.objectType == ObjectType.TOPIC) {
        return Result.failure(BadRequestException("can't get topic media"))
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

suspend fun Backend.getAllMediaList(
    uid: PrimaryKey,
    routeMedia: RouteMedia,
): Result<ServerResponse<MediaInfo>?> {
    if (routeMedia.objectType == ObjectType.TOPIC) {
        return Result.failure(BadRequestException("can't get topic media"))
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
            getMediaInfoList(uid).mapIfNotNull {
                ServerResponse(it.filterNotNull(), null)
            }
        } else {
            Result.failure(ForbiddenException("no permission"))
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
suspend fun Backend.extractAlbum(mediaId: PrimaryKey, root: File, uid: PrimaryKey) =
    exposedDatabase.userDatabase.getMediaByIds(listOf(mediaId)).mapResultIfNotNull {
        val media = it.first()
        if (media.owner != uid) {
            throw ForbiddenException("no permission")
        }
        if (media.contentType.startsWith("audio")) {
            mediaService.getInputStream(AMEDIA_DEFAULT_BUCKET, media.fullName).map { input ->
                withContext(Dispatchers.IO) {
                    input.use {
                        val saveAlbum = { image: ByteArray, mimeType: String ->
                            val file = File(root, "${Uuid.random()}.${getExtensionFromMimeType(mimeType)}")
                            file.writeBytes(image)
                            file
                        }
                        when (media.contentType) {
                            "audio/mp3" -> it.readMp3AlbumFromAudioStream(saveAlbum)
                            "audio/flac", "audio/x-flac" -> it.readFlacAlbumFromAudioStream(saveAlbum)
                            else -> throw CustomBadRequestException("unsupported audio type: ${media.contentType}")
                        } to media.contentType
                    }
                }
            }.mapResultIfNotNull { (file, contentType) ->
                if (file != null) {
                    val name = newCoverFileName(media.name, contentType)
                    uploadFilesAfterDetectContentTypeAndDimension(
                        listOf(UploadPack(file, name, media.owner, media.size))
                    ).map {
                        ServerResponse(it)
                    }
                } else {
                    Result.success(null)
                }
            }
        } else {
            Result.failure(BadRequestException("not an audio file"))
        }
    }

@OptIn(ExperimentalUuidApi::class)
suspend fun RoutingContext.uploadMedia(
    backend: Backend,
    it: RouteMedia.Upload,
    id: PrimaryKey,
    root: File,
) = if (it.parent.objectType == ObjectType.TOPIC) {
    Result.failure(BadRequestException("can't upload to topic"))
} else {
    val parentType = it.parent.objectType
    val parentId = it.parent.objectId
    if (parentId == null || parentType == null) error("invalid query")
    backend.checkRootWritePermission(parentType, parentId, id).mapResultIfNotNull {
        val result = mutableListOf<MediaInfo>()

        call.receiveMultipart().forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    backend.processFilePart(part, root, it, result)
                }

                else -> {}
            }
            part.dispose()
        }
        Result.success(ServerResponse(result))
    }
}

private suspend fun Backend.processFilePart(
    part: PartData.FileItem,
    root: File,
    permission: RootWritePermission,
    result: MutableList<MediaInfo>
) {
//    val length = part.headers[HttpHeaders.ContentLength]?.toLongOrNull()
//    if (length == null) error("content length not exists")
//    if (length > 1024 * 1024 * 10) error("file too large")
    val fileName = part.originalFileName as String
    val newSavedName = newFileName(fileName)
    val file = File(root, "$newSavedName.tmp")
    val length = part.provider().copyAndClose(file.writeChannel())
    try {
        val mediaInfos = uploadFilesAfterDetectContentTypeAndDimension(
            listOf(
                UploadPack(
                    file,
                    newSavedName,
                    permission.objectId,
                    length,
                )
            )
        ).getOrThrow()
        result.addAll(mediaInfos.filterNotNull())
    } finally {
        file.delete()
    }
}

@Suppress("ThrowsCount")
@OptIn(InternalAPI::class)
suspend fun ByteReadChannel.copyWithLimitAndClose(channel: ByteWriteChannel, limit: Long): Long {
    var result = 0L
    try {
        while (!isClosedForRead) {
            result += readBuffer.transferTo(channel.writeBuffer)
            if (result > limit) {
                throw CustomBadRequestException("exceed content length")
            }
            channel.flush()
            awaitContent()
        }

        closedCause?.let { throw it }
    } catch (cause: Throwable) {
        cancel(cause)
        channel.close(cause)
        throw cause
    } finally {
        channel.flushAndClose()
    }

    return result
}

@OptIn(ExperimentalUuidApi::class)
private fun newFileName(fileName: String): String {
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
private fun newCopiedFileName(fileName: String): String {
    val extension = fileName.substringAfterLast(".").take(10)
    val originName = fileName.substringBeforeLast(".")
    // length 32
    val uuid = Uuid.random().toHexString()
    return "${originName.take(27 - extension.length)}$uuid.$extension"
}

@OptIn(ExperimentalUuidApi::class)
private fun newCoverFileName(fileName: String, contentType: String): String {
    val extension = getExtensionFromMimeType(contentType)
    val originName = fileName.substringBeforeLast(".") + "_cover"
    // length 32
    val uuid = Uuid.random().toHexString()
    return "${originName.take(27 - extension.length)}$uuid.$extension"
}

suspend fun Backend.copyMedia(
    name: String,
    uid: PrimaryKey,
    objectTuple: ObjectTuple
) = checkRootReadPermission(
    objectTuple.objectType,
    objectTuple.objectId,
    uid
).mapResultIfNotNull { permission ->
    if (permission.hasRead) {
        mediaService.get(AMEDIA_DEFAULT_BUCKET, listOf("$uid/$name")).map {
            if (it.firstOrNull() == null) {
                "$uid/$name"
            } else {
                "$uid/${newCopiedFileName(name)}"
            }
        }.mapResult {
            exposedDatabase.userDatabase.getMedia(objectTuple.objectId, name).mapResultIfNotNull { media ->
                copyMedia(media, uid, it)
            }
        }
    } else {
        Result.failure(ForbiddenException("Permission denied"))
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
