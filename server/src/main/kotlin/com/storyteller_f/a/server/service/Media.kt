package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.ForbiddenException
import com.storyteller_f.a.server.route.RouteMedia
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResultNotNull
import io.ktor.resources.*
import io.ktor.server.plugins.*

suspend fun getMediaList(
    uid: PrimaryKey,
    backend: Backend,
    routeMedia: RouteMedia
): Result<ServerResponse<MediaInfo?>?> {
    if (routeMedia.objectType == null || routeMedia.objectId == null) {
        return Result.failure(
            BadRequestException("invalid query")
        )
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
