package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.service.UploadPack
import com.storyteller_f.a.cloud.core.service.RootWritePermission
import com.storyteller_f.a.cloud.core.service.abortChunkUpload
import com.storyteller_f.a.cloud.core.service.checkRootWritePermission
import com.storyteller_f.a.cloud.core.service.completeChunkUpload
import com.storyteller_f.a.cloud.core.service.extractAlbum
import com.storyteller_f.a.cloud.core.service.getChunkStatus
import com.storyteller_f.a.cloud.core.service.getFileInfoByName
import com.storyteller_f.a.cloud.core.service.getFileList
import com.storyteller_f.a.cloud.core.service.getQuotaInfo
import com.storyteller_f.a.cloud.core.service.initChunkUpload
import com.storyteller_f.a.cloud.core.service.newFileName
import com.storyteller_f.a.cloud.core.service.searchFiles
import com.storyteller_f.a.cloud.core.service.tryCopyFile
import com.storyteller_f.a.cloud.core.service.tryUploadFiles
import com.storyteller_f.a.cloud.core.service.uncheckedGetFileRefsByFileId
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.a.cloud.server.common.GeneralOffsetPagingGenerator
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.endpoint4k.ktor.server.invoke
import com.storyteller_f.endpoint4k.ktor.server.receiveBody
import com.storyteller_f.shared.model.A_FILE_DEFAULT_BUCKET
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.sha256
import com.storyteller_f.shared.utils.unit
import io.github.aakira.napier.Napier
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveMultipart
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.util.cio.readChannel
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyAndClose
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.coroutineScope
import java.io.File
import kotlin.uuid.ExperimentalUuidApi

fun Route.bindProtectedFileRoute(backend: Backend) {
    bindUserFileRoute(backend)
    bindUserRoomRoute(backend)
    bindUserCommunityRoute(backend)

    CustomApi.Files.getByName(handleResult()) {
        usePrincipal { uid ->
            backend.getFileInfoByName(uid, it.objectId ob it.objectType, it.name)
        }
    }

    CustomApi.Files.quota(handleResult()) {
        usePrincipal { _ ->
            backend.getQuotaInfo(it.quotaType, ObjectTuple(it.objectId, it.objectType))
        }
    }

    val userHome = System.getProperty("user.home")
    val root = File(userHome, "a-temp")
    if (!root.exists() && !root.mkdir()) {
        error("create a-temp failed")
    }

    CustomApi.Files.Id.extractAlbum(handleResult()) { p, _ ->
        usePrincipal { uid ->
            backend.extractAlbum(p.id, root, uid)
        }
    }

    CustomApi.Files.upload(handleResult()) { q, _ ->
        usePrincipal { uid ->
            uploadMedia(backend, uid, root, q)
        }
    }

    // Chunked upload endpoints
    bindChunkRoute(backend, root)

    CustomApi.Files.Id.copy(handleResult()) { p, _ ->
        usePrincipal { uid ->
            backend.tryCopyFile(p, uid)
        }
    }

    CustomApi.Files.Id.Refs.get(handleResult()) { q, p ->
        usePrincipal { _ ->
            q.pagination(IdentifiablePagingGenerator) { f ->
                backend.uncheckedGetFileRefsByFileId(p.id, f)
            }
        }
    }
}

private fun Route.bindUserCommunityRoute(backend: Backend) {
    CustomApi.Communities.Id.Files.get(handleResult()) { query, path ->
        usePrincipal { uid ->
            query.pagination(IdentifiablePagingGenerator) { pagingFetch ->
                backend.getFileList(uid, path.id ob ObjectType.COMMUNITY, pagingFetch)
            }
        }
    }

    CustomApi.Communities.Id.Files.search(handleResult()) { query, path ->
        usePrincipal { uid ->
            query.pagination(GeneralOffsetPagingGenerator) { pagingFetch ->
                backend.searchFiles(uid, query, path.id ob ObjectType.COMMUNITY, pagingFetch)
            }
        }
    }
}

private fun Route.bindUserRoomRoute(backend: Backend) {
    CustomApi.Rooms.Id.Files.get(handleResult()) { query, path ->
        usePrincipal { uid ->
            query.pagination(IdentifiablePagingGenerator) { pagingFetch ->
                backend.getFileList(uid, path.id ob ObjectType.ROOM, pagingFetch)
            }
        }
    }

    CustomApi.Rooms.Id.Files.search(handleResult()) { query, path ->
        usePrincipal { uid ->
            query.pagination(GeneralOffsetPagingGenerator) { pagingFetch ->
                backend.searchFiles(uid, query, path.id ob ObjectType.ROOM, pagingFetch)
            }
        }
    }
}

private fun Route.bindUserFileRoute(backend: Backend) {
    CustomApi.Users.Id.Files.get(handleResult()) { query, path ->
        usePrincipal { uid ->
            query.pagination(IdentifiablePagingGenerator) { pagingFetch ->
                backend.getFileList(uid, path.id ob ObjectType.USER, pagingFetch)
            }
        }
    }

    CustomApi.Users.Id.Files.search(handleResult()) { query, path ->
        usePrincipal { uid ->
            query.pagination(GeneralOffsetPagingGenerator) { pagingFetch ->
                backend.searchFiles(uid, query, path.id ob ObjectType.USER, pagingFetch)
            }
        }
    }
}

private fun Route.bindChunkRoute(backend: Backend, root: File) {
    CustomApi.Files.Chunks.init(handleResult()) { api ->
        usePrincipal { uid ->
            initChunkUpload(backend, uid, api.receiveBody())
        }
    }

    CustomApi.Files.Chunks.upload(handleResult()) { q, p, _ ->
        usePrincipal { _ ->
            uploadChunkFromChannel(backend, root, p, q) { call.receiveChannel() }
        }
    }

    CustomApi.Files.Chunks.complete(handleResult()) { path, _ ->
        usePrincipal { uid ->
            completeChunkUpload(backend, uid, path)
        }
    }

    CustomApi.Files.Chunks.abort(handleResult()) { path, _ ->
        usePrincipal { _ ->
            abortChunkUpload(backend, path)
        }
    }

    CustomApi.Files.Chunks.status(handleResult()) { path ->
        usePrincipal { _ ->
            getChunkStatus(backend, path)
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
        processFormData { part ->
            val fileInfos = when (part) {
                is PartData.FileItem -> {
                    val fileName = part.originalFileName as String
                    backend.uploadFilesFromChannel(root, it, fileName) {
                        part.provider()
                    }
                }

                else -> {
                    emptyList()
                }
            }
            part.dispose()
            fileInfos
        }
    }
}

private suspend fun RoutingContext.processFormData(
    block: suspend (PartData) -> List<FileInfo>
): Result<ServerResponse<FileInfo>?> = try {
    val result = mutableListOf<FileInfo>()
    coroutineScope {
        call.receiveMultipart(1024 * 1024 * 100).forEachPart { part ->
            result.addAll(block(part))
        }
    }
    Result.success(ServerResponse(result))
} catch (e: Exception) {
    Result.failure(e)
}

private suspend fun Backend.uploadFilesFromChannel(
    root: File,
    permission: RootWritePermission,
    fileName: String,
    provider: () -> ByteReadChannel
): List<FileInfo> {
    val newSavedName = newFileName(fileName)
    val file = File(root, "$newSavedName.tmp")
    provider().copyAndClose(file.writeChannel())
    return try {
        tryUploadFiles(
            permission.rootId,
            permission.rootType,
            listOf(
                UploadPack(
                    file,
                    newSavedName,
                    file.length(),
                    "${permission.rootId}/$newSavedName"
                ),
            )
        ).getOrThrow()
    } finally {
        file.delete()
    }
}

suspend fun uploadChunkFromChannel(
    backend: Backend,
    root: File,
    path: CustomApi.Files.Chunks.UploadPath,
    query: CustomApi.Files.Chunks.UploadQuery,
    provider: suspend () -> ByteReadChannel
) = runCatching {
    // 写入本地临时文件以校验哈希并上传到对象存储临时桶
    val chunkTmp = File(root, "chunk_${path.id}_${path.index}.tmp")
    provider().copyAndClose(chunkTmp.writeChannel())
    try {
        val actual = sha256(chunkTmp.readChannel().readBuffer())
        val expected = query.hash
        if (actual != expected) {
            Napier.i(tag = "file") {
                "chunk hash mismatch, expected: $expected, actual: $actual"
            }
            throw CustomBadRequestException("chunk hash mismatch")
        } else {
            backend.objectStorageService.upload(
                A_FILE_DEFAULT_BUCKET,
                listOf(
                    UploadPack(
                        chunkTmp,
                        "chunk_${path.index}",
                        chunkTmp.length(),
                        "chunks/${path.id}/chunk_${path.index}"
                    )
                )
            ).getOrThrow()
        }
    } finally {
        chunkTmp.delete()
    }
}.unit()
