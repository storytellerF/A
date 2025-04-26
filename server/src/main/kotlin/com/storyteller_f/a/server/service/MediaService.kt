package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.ForbiddenException
import com.storyteller_f.a.server.route.RouteMedia
import com.storyteller_f.media.CopyPack
import com.storyteller_f.media.uploadFiles
import com.storyteller_f.shared.model.AMEDIA_DEFAULT_BUCKET
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import io.ktor.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import org.apache.tika.Tika
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

suspend fun getMediaList(
    backend: Backend,
    uid: PrimaryKey,
    routeMedia: RouteMedia
): Result<ServerResponse<MediaInfo?>?> {
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
            backend.mediaService.list(AMEDIA_DEFAULT_BUCKET, "$uid/").map { names ->
                ServerResponse(names.sortedByDescending { info ->
                    info.item.lastModified
                })
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
                    val fileName = part.originalFileName as String
                    val (newSavedName, newSavedFileName) = newFileName(fileName)
                    val file = File(root, newSavedFileName)
                    // ktor 自带检查，保险起见再次检查
                    if (file.canonicalPath == file.absolutePath) {
                        val fileBytes = part.provider().readRemaining().readByteArray()
                        try {
                            withContext(Dispatchers.IO) {
                                file.writeBytes(fileBytes)
                                val info = uploadFiles(
                                    tika,
                                    backend,
                                    listOf(
                                        Triple(file, "${it.objectId}/$newSavedName", part.contentType.toString())
                                    )
                                ).getOrThrow()
                                result.addAll(info.filterNotNull())
                            }
                        } finally {
                            file.delete()
                        }
                    }
                }

                else -> {}
            }
            part.dispose()
        }
        Result.success(ServerResponse(result))
    }
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
    return checkRootReadPermission(backend, objectTuple.objectType, objectTuple.objectId, uid).mapResultIfNotNull {
        if (it.hasRead) {
            backend.mediaService.get(AMEDIA_DEFAULT_BUCKET, listOf("$uid/$noPrefixName")).map {
                if (it.firstOrNull() == null) {
                    "$uid/$noPrefixName"
                } else {
                    "$uid/${newCopiedFileName(noPrefixName)}"
                }
            }.mapResult {
                backend.mediaService.copy(
                    AMEDIA_DEFAULT_BUCKET,
                    listOf(CopyPack("${objectTuple.objectId}/$noPrefixName", it))
                ).map { list ->
                    ServerResponse(list, null)
                }
            }
        } else {
            Result.failure(ForbiddenException("Permission denied"))
        }
    }
}