package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.ForbiddenException
import com.storyteller_f.a.server.route.RouteMedia
import com.storyteller_f.media.AMEDIA_BUCKET
import com.storyteller_f.media.UploadPack
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
            backend.mediaService.list(AMEDIA_BUCKET, "$uid/").map { names ->
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
                    val file = File(root, Uuid.random().toString() + fileName)
                    // ktor 自带检查，保险起见再次检查
                    if (file.canonicalPath == file.absolutePath) {
                        val fileBytes = part.provider().readRemaining().readByteArray()

                        try {
                            file.writeBytes(fileBytes)
                            val type = checkContentType(part, file, tika)
                            result.addAll(
                                backend.mediaService.upload(
                                    AMEDIA_BUCKET,
                                    listOf(UploadPack("${it.objectId}/$fileName", file, type))
                                ).getOrThrow().filterNotNull()
                            )
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

private fun checkContentType(
    part: PartData,
    file: File,
    tika: Tika
): String? {
    val s = "audio/mp4"
    return if (part.contentType.toString() == s) {
        val mimeType = tika.detect(file)
        if (mimeType == s) {
            s
        } else {
            null
        }
    } else {
        null
    }
}
