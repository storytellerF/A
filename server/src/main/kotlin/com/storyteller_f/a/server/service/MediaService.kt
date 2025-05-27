package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.ForbiddenException
import com.storyteller_f.a.server.route.RouteMedia
import com.storyteller_f.media.UploadPack
import com.storyteller_f.media.uploadFiles
import com.storyteller_f.shared.model.AMEDIA_DEFAULT_BUCKET
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.tables.copyMedia
import com.storyteller_f.tables.getMediaInfoList
import com.storyteller_f.tables.getPagingMedias
import com.storyteller_f.tables.getRawMedia
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.PagingFetch
import io.ktor.http.HttpHeaders
import io.ktor.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import org.apache.tika.Tika
import java.io.File
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
    val (newSavedName, newSavedFileName) = newFileName(fileName)
    val file = File(root, newSavedFileName)
    val length = part.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (length == null) {
        throw BadRequestException("content length not exists")
    }
    // ktor 自带检查，保险起见再次检查
    if (file.canonicalPath != file.absolutePath) throw BadRequestException("invalid path")
    part.provider().copyWithLimitAndClose(file.writeChannel(), length)
    try {
        val info = uploadFiles(
            tika,
            backend,
            listOf(
                UploadPack(
                    file,
                    "${permission.objectId}/$newSavedName",
                    newSavedName,
                    permission.objectId,
                    length,
                    "",
                    part.contentType.toString(),
                    null
                )
            )
        ).getOrThrow()
        result.addAll(info.filterNotNull())
    } finally {
        file.delete()
    }
}

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
private fun newFileName(fileName: String): Pair<String, String> {
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
    return Pair(newSavedName, "${originName.take(60 - extension.length)}$uuid.$extension")
}

@OptIn(ExperimentalUuidApi::class)
private fun newCopiedFileName(fileName: String): String {
    val extension = fileName.substringAfterLast(".").take(10)
    val originName = fileName.substringBeforeLast(".")
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
