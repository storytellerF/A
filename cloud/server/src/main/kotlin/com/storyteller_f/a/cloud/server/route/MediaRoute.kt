package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.UploadPack
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.backend.service.object_storage.uploadFilesAfterDetectContentTypeAndDimension
import com.storyteller_f.a.cloud.core.service.RootWritePermission
import com.storyteller_f.a.cloud.core.service.checkRootWritePermission
import com.storyteller_f.a.cloud.core.service.copyMedia
import com.storyteller_f.a.cloud.core.service.extractAlbum
import com.storyteller_f.a.cloud.core.service.getMediaByName
import com.storyteller_f.a.cloud.core.service.getMediaList
import com.storyteller_f.a.cloud.core.service.newFileName
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.route4k.ktor.server.invoke
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResultIfNotNull
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.routing.*
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import java.io.File
import kotlin.uuid.ExperimentalUuidApi

fun Route.bindProtectedMediaRoute(backend: Backend) {
    CustomApi.Medias.get.invoke(RoutingContext::handleResult) {
        usePrincipal { uid ->
            it.pagination(IdentifiablePagingGenerator) { pagingFetch ->
                backend.getMediaList(uid, it, pagingFetch)
            }
        }
    }

    CustomApi.Medias.getByName.invoke(RoutingContext::handleResult) {
        usePrincipal { uid ->
            backend.getMediaByName(uid, it.objectId ob it.objectType, it.name)
        }
    }

    val userHome = System.getProperty("user.home")
    val root = File(userHome, "atemp")
    if (!root.exists() && !root.mkdir()) {
        error("create atemp failed")
    }

    CustomApi.Medias.Id.extractAlbum.invoke(RoutingContext::handleResult) { p, api ->
        usePrincipal { uid ->
            backend.extractAlbum(p.id, root, uid)
        }
    }

    CustomApi.Medias.upload.invoke(RoutingContext::handleResult) { q, api ->
        usePrincipal { uid ->
            uploadMedia(backend, uid, root, q)
        }
    }

    CustomApi.Medias.Id.copy.invoke(RoutingContext::handleResult) { p, api ->
        usePrincipal { uid ->
            backend.copyMedia(p, uid)
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
suspend fun RoutingContext.uploadMedia(
    backend: Backend,
    id: PrimaryKey,
    root: File,
    objectTuple: ObjectTuple,
) = if (objectTuple.objectType == ObjectType.TOPIC) {
    Result.failure(CustomBadRequestException("can't upload to topic"))
} else {
    val parentType = objectTuple.objectType
    val parentId = objectTuple.objectId
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
                    permission.objectType,
                    length,
                )
            )
        ).getOrThrow()
        result.addAll(mediaInfos.filterNotNull())
    } finally {
        file.delete()
    }
}
