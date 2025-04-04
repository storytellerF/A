package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.ForbiddenException
import com.storyteller_f.a.server.route.RouteMedia
import com.storyteller_f.media.uploadFiles
import com.storyteller_f.shared.model.AMEDIA_DEFAULT_BUCKET
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResultNotNull
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
    uid: PrimaryKey,
    backend: Backend,
    routeMedia: RouteMedia
): Result<ServerResponse<MediaInfo?>?> {
    if (routeMedia.objectType == ObjectType.TOPIC) {
        return Result.failure(BadRequestException("can't get topic media"))
    }
    return checkRootWritePermission(
        routeMedia.objectType,
        routeMedia.objectId,
        uid
    ).mapResultNotNull { (_, _, hasWrite) ->
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
    it: RouteMedia.Upload,
    id: PrimaryKey,
    root: File,
    backend: Backend,
    tika: Tika,
) = if (it.parent.objectType == ObjectType.TOPIC) {
    Result.failure(BadRequestException("can't upload to topic"))
} else {
    checkRootWritePermission(it.parent.objectType, it.parent.objectId, id).mapResultNotNull {
        val multipartData = call.receiveMultipart()
        val result = mutableListOf<MediaInfo>()

        multipartData.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    val fileName = part.originalFileName as String
                    // total 93
                    val extension = fileName.substringAfterLast(".").take(10)
                    val originName = fileName.substringBeforeLast(".")
                    // length 32
                    val uuid = Uuid.random().toHexString()
                    val name = originName.take(60 - extension.length) + uuid
                    val newSavedFileName = if (fileName.length > 60) {
                        // 60 - 32 - 1 = 27
                        "${originName.take(27 - extension.length)}$uuid.$extension"
                    } else {
                        fileName
                    }
                    val file = File(root, "$name.$extension")
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
                                        Triple(file, "${it.objectId}/$newSavedFileName", part.contentType.toString())
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
