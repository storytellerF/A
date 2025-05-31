package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.ForbiddenException
import com.storyteller_f.a.server.route.RouteMedia
import com.storyteller_f.media.UploadPack
import com.storyteller_f.media.uploadFilesAfterDetectContentTypeAndDimension
import com.storyteller_f.shared.model.AMEDIA_DEFAULT_BUCKET
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.tables.*
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.PagingFetch
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.tika.Tika
import java.io.File
import java.io.InputStream
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

suspend fun getMediaList(
    backend: Backend,
    uid: PrimaryKey,
    routeMedia: RouteMedia,
    pagingFetch: PagingFetch
): Result<PaginationResult<MediaInfo>?> {
    if (routeMedia.objectType == ObjectType.TOPIC) {
        return Result.failure(BadRequestException("can't get topic media"))
    }
    return checkRootWritePermission(
        backend,
        routeMedia.objectType,
        routeMedia.objectId,
        uid
    ).mapResultIfNotNull { (_, _, hasWrite) ->
        if (hasWrite) {
            DatabaseFactory.getPagingMedias(backend, uid, pagingFetch)
        } else {
            Result.failure(ForbiddenException("no permission"))
        }
    }
}

suspend fun getAllMediaList(
    backend: Backend,
    uid: PrimaryKey,
    routeMedia: RouteMedia,
): Result<ServerResponse<MediaInfo>?> {
    if (routeMedia.objectType == ObjectType.TOPIC) {
        return Result.failure(BadRequestException("can't get topic media"))
    }
    return checkRootWritePermission(
        backend,
        routeMedia.objectType,
        routeMedia.objectId,
        uid
    ).mapResultIfNotNull { (_, _, hasWrite) ->
        if (hasWrite) {
            DatabaseFactory.getMediaInfoList(backend, uid).mapIfNotNull {
                ServerResponse(it.filterNotNull(), null)
            }
        } else {
            Result.failure(ForbiddenException("no permission"))
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
suspend fun extractAlbum(mediaId: PrimaryKey, backend: Backend, root: File, tika: Tika, uid: PrimaryKey) =
    DatabaseFactory.getRawMediaById(backend, mediaId).mapResultIfNotNull { media ->
        if (media.owner != uid) {
            throw ForbiddenException("no permission")
        }
        if (media.contentType.startsWith("audio")) {
            backend.mediaService.getInputStream(AMEDIA_DEFAULT_BUCKET, media.newFullName).map {
                it.use {
                    withContext(Dispatchers.IO) {
                        it.readAlbumFromAudioStream { image, mimeType ->
                            val file = File(root, "${Uuid.random()}.${getExtensionFromMimeType(mimeType)}")
                            file.writeBytes(image)
                            file
                        }
                    }
                }
            }.mapResultIfNotNull { file ->
                val name = newCoverFileName(media.name)
                uploadFilesAfterDetectContentTypeAndDimension(
                    tika,
                    backend,
                    listOf(UploadPack(file, name, media.owner, media.size))
                )
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
    tika: Tika,
) = if (it.parent.objectType == ObjectType.TOPIC) {
    Result.failure(BadRequestException("can't upload to topic"))
} else {
    checkRootWritePermission(backend, it.parent.objectType, it.parent.objectId, id).mapResultIfNotNull {
        val multipartData = call.receiveMultipart()
        val result = mutableListOf<MediaInfo>()

        multipartData.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    processFilePart(part, root, tika, backend, it, result)
                }

                else -> {}
            }
            part.dispose()
        }
        Result.success(ServerResponse(result))
    }
}

private suspend fun processFilePart(
    part: PartData.FileItem,
    root: File,
    tika: Tika,
    backend: Backend,
    permission: RootWritePermission,
    result: MutableList<MediaInfo>
) {
    val fileName = part.originalFileName as String
    val newSavedName = newFileName(fileName)
    val file = File(root, "$newSavedName.tmp")
    val length = part.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (length == null) {
        throw BadRequestException("content length not exists")
    }
    // ktor 自带检查，保险起见再次检查
    if (file.canonicalPath != file.absolutePath) throw BadRequestException("invalid path")
    part.provider().copyWithLimitAndClose(file.writeChannel(), length)
    try {
        val info = uploadFilesAfterDetectContentTypeAndDimension(
            tika,
            backend,
            listOf(
                UploadPack(
                    file,
                    newSavedName,
                    permission.objectId,
                    length,
                    "",
                    null
                )
            )
        ).getOrThrow()
        result.addAll(info.filterNotNull())
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
                throw BadRequestException("exceed content length")
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
private fun newCoverFileName(fileName: String): String {
    val extension = fileName.substringAfterLast(".").take(10)
    val originName = fileName.substringBeforeLast(".") + "_cover"
    // length 32
    val uuid = Uuid.random().toHexString()
    return "${originName.take(27 - extension.length)}$uuid.$extension"
}

suspend fun copyMedia(
    backend: Backend,
    noPrefixName: String,
    uid: PrimaryKey,
    objectTuple: ObjectTuple
): Result<ServerResponse<MediaInfo?>?> {
    return checkRootReadPermission(
        backend,
        objectTuple.objectType,
        objectTuple.objectId,
        uid
    ).mapResultIfNotNull { permission ->
        if (permission.hasRead) {
            backend.mediaService.get(AMEDIA_DEFAULT_BUCKET, listOf("$uid/$noPrefixName")).map {
                if (it.firstOrNull() == null) {
                    "$uid/$noPrefixName"
                } else {
                    "$uid/${newCopiedFileName(noPrefixName)}"
                }
            }.mapResult {
                DatabaseFactory.getRawMedia(backend, objectTuple.objectId, noPrefixName).mapResultIfNotNull { media ->
                    DatabaseFactory.copyMedia(backend, media, uid, it)
                }
            }
        } else {
            Result.failure(ForbiddenException("Permission denied"))
        }
    }
}

fun<T> InputStream.readAlbumFromAudioStream(block: (ByteArray, String) -> T): T? {
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

            return block(realImage, mimeType)
        } else {
            skip(blockLength.toLong())
        }

        if ((header and 0x80) != 0) {
            break
        }
    }
    return null
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
        "audio/flac" -> "flac"
        else -> error("Unsupported mime type: $mimeType")
    }
}
