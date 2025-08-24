package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.UploadPack
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.cloud.core.service.RootWritePermission
import com.storyteller_f.a.cloud.core.service.checkRootWritePermission
import com.storyteller_f.a.cloud.core.service.extractAlbum
import com.storyteller_f.a.cloud.core.service.getFileInfoByName
import com.storyteller_f.a.cloud.core.service.getFileList
import com.storyteller_f.a.cloud.core.service.newFileName
import com.storyteller_f.a.cloud.core.service.tryCopyFile
import com.storyteller_f.a.cloud.core.service.tryUploadFiles
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.route4k.ktor.server.invoke
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResultIfNotNull
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.routing.*
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.close
import java.io.File
import kotlin.uuid.ExperimentalUuidApi

fun Route.bindProtectedMediaRoute(backend: Backend) {
    CustomApi.Files.get.invoke(RoutingContext::handleResult) {
        usePrincipal { uid ->
            it.pagination(IdentifiablePagingGenerator) { pagingFetch ->
                backend.getFileList(uid, it, pagingFetch)
            }
        }
    }

    CustomApi.Files.getByName.invoke(RoutingContext::handleResult) {
        usePrincipal { uid ->
            backend.getFileInfoByName(uid, it.objectId ob it.objectType, it.name)
        }
    }

    val userHome = System.getProperty("user.home")
    val root = File(userHome, "a-temp")
    if (!root.exists() && !root.mkdir()) {
        error("create a-temp failed")
    }

    CustomApi.Files.Id.extractAlbum.invoke(RoutingContext::handleResult) { p, api ->
        usePrincipal { uid ->
            backend.extractAlbum(p.id, root, uid)
        }
    }

    CustomApi.Files.upload.invoke(RoutingContext::handleResult) { q, api ->
        usePrincipal { uid ->
            uploadMedia(backend, uid, root, q)
        }
    }

    CustomApi.Files.Id.copy.invoke(RoutingContext::handleResult) { p, api ->
        usePrincipal { uid ->
            backend.tryCopyFile(p, uid)
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
        val result = mutableListOf<FileInfo>()

        call.receiveMultipart().forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    val length = part.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                    if (length == null) error("content length not exists")
                    if (length > 1024 * 1024 * 10) error("file too large")
                    backend.processFilePart(root, it, result, part.originalFileName as String, length) {
                        part.provider()
                    }
                }

                else -> {}
            }
            part.dispose()
        }
        Result.success(ServerResponse(result))
    }
}

private suspend fun Backend.processFilePart(
    root: File,
    permission: RootWritePermission,
    result: MutableList<FileInfo>,
    fileName: String,
    length: Long,
    provider: () -> ByteReadChannel
) {
    val newSavedName = newFileName(fileName)
    val file = File(root, "$newSavedName.tmp")
    provider().copyContentAndClose(file.writeChannel(), length)
    try {
        val mediaInfos = tryUploadFiles(
            permission.objectId,
            permission.objectType,
            listOf(
                UploadPack(
                    file,
                    newSavedName,
                    length,
                    "${permission.objectId}/$newSavedName"
                ),
            )
        ).getOrThrow()
        result.addAll(mediaInfos.filterNotNull())
    } finally {
        file.delete()
    }
}

@OptIn(InternalAPI::class)
suspend fun ByteReadChannel.copyContentAndClose(channel: ByteWriteChannel, length: Long): Long {
    var result = 0L
    try {
        while (!isClosedForRead) {
            result += readBuffer.transferTo(channel.writeBuffer)
            if (result > length) {
                error("File too large")
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
