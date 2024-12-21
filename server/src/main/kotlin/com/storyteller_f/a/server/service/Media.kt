package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.ForbiddenException
import com.storyteller_f.a.server.route.RouteMedia
import com.storyteller_f.media.UploadPack
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResultNotNull
import io.ktor.http.content.*
import io.ktor.resources.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
import java.io.File

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
            backend.mediaService.list("amedia", "$uid/").map { names ->
                ServerResponse(names)
            }
        } else {
            Result.failure(ForbiddenException("no permission"))
        }
    }
}

suspend fun RoutingContext.uploadMedia(
    it: RouteMedia.Upload,
    id: PrimaryKey,
    root: File,
    backend: Backend,
) = if (it.parent.objectType == ObjectType.TOPIC) {
    Result.failure(BadRequestException("can't upload to topic"))
} else {
    checkRootWritePermission(it.parent.objectType, it.parent.objectId, id).mapResultNotNull {
        var fileName: String
        val multipartData = call.receiveMultipart()
        val result = mutableListOf<MediaInfo>()

        multipartData.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    fileName = part.originalFileName as String
                    val fileBytes = part.provider().readRemaining().readByteArray()
                    val file = File(root, fileName)
                    file.writeBytes(fileBytes)
                    result.addAll(
                        backend.mediaService.upload(
                            "amedia",
                            listOf(UploadPack("${it.objectId}/$fileName", file))
                        ).getOrThrow().filterNotNull()
                    )
                    file.delete()
                }

                else -> {}
            }
            part.dispose()
        }
        Result.success(ServerResponse(result))
    }
}
