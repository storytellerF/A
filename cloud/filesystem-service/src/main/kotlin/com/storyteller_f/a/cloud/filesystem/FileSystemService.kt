/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.cloud.filesystem

import com.storyteller_f.services.filesystem.api.CopyPack
import com.storyteller_f.services.filesystem.api.FilesystemRpc
import com.storyteller_f.services.filesystem.api.ObjectStorageRecord
import com.storyteller_f.services.filesystem.api.ObjectStorageWriteRecord
import com.storyteller_f.services.filesystem.api.RpcUploadPack
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.response.header
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap

private const val MAX_CHUNK_SIZE = 256 * 1024

private data class PendingUpload(val bucketName: String, val pack: RpcUploadPack, val path: Path)

private class FilesystemRpcImpl(private val storage: LocalFileSystemObjectStorageService) : FilesystemRpc {
    private val uploads = ConcurrentHashMap<String, PendingUpload>()

    override suspend fun health() = "ok"

    override suspend fun beginUpload(bucketName: String, uploadPack: RpcUploadPack) {
        require(uploadPack.transferId.isNotBlank()) { "transfer id is empty" }
        val pending = PendingUpload(bucketName, uploadPack, Files.createTempFile("a-filesystem-rpc-", ".upload"))
        check(uploads.putIfAbsent(uploadPack.transferId, pending) == null) { "transfer already exists" }
    }

    override suspend fun uploadChunk(transferId: String, content: ByteArray) {
        require(content.size <= MAX_CHUNK_SIZE) { "upload chunk is too large" }
        val pending = uploads[transferId] ?: error("unknown transfer")
        Files.newOutputStream(pending.path, StandardOpenOption.APPEND).buffered().use { it.write(content) }
    }

    override suspend fun finishUpload(transferId: String): ObjectStorageWriteRecord {
        val pending = uploads.remove(transferId) ?: error("unknown transfer")
        return try {
            check(Files.size(pending.path) == pending.pack.size) { "uploaded size does not match metadata" }
            storage.upload(
                pending.bucketName,
                listOf(
                    UploadPack(
                        pending.path.toFile(),
                        pending.pack.fullName,
                    ),
                ),
            ).getOrThrow().single()
        } finally {
            Files.deleteIfExists(pending.path)
        }
    }

    override suspend fun abortUpload(transferId: String) {
        uploads.remove(transferId)?.let { Files.deleteIfExists(it.path) }
    }

    override suspend fun get(bucketName: String, names: List<String>) = storage.get(bucketName, names).getOrThrow()
    override suspend fun cleanObjects(bucketName: String) {
        storage.clean(bucketName).getOrThrow()
    }
    override suspend fun list(bucketName: String, prefix: String) = storage.list(bucketName, prefix).getOrThrow()
    override suspend fun copy(bucketName: String, copyPacks: List<CopyPack>): List<ObjectStorageRecord> =
        storage.copy(bucketName, copyPacks).getOrThrow()
    override suspend fun getChunk(bucketName: String, name: String, offset: Long, size: Int): ByteArray {
        require(offset >= 0) { "offset must not be negative" }
        require(size in 1..MAX_CHUNK_SIZE) { "invalid chunk size" }
        return storage.getInputStream(bucketName, name).getOrThrow().buffered().use { input ->
            var remaining = offset
            while (remaining > 0) {
                val skipped = input.skip(remaining)
                check(skipped > 0) { "offset exceeds object size" }
                remaining -= skipped
            }
            input.readNBytes(size)
        }
    }
    override suspend fun compose(bucketName: String, targetFullName: String, sourceFullNames: List<String>) =
        storage.compose(bucketName, targetFullName, sourceFullNames).getOrThrow()
    override suspend fun delete(bucketName: String, names: List<String>) {
        storage.delete(
            bucketName,
            names,
        ).getOrThrow()
    }
}

fun main() {
    val port = System.getenv("FILESYSTEM_RPC_PORT")?.toIntOrNull() ?: 8820
    val base = Paths.get(System.getenv("FILE_SYSTEM_MEDIA_PATH") ?: "/data")
    val publicUrl = System.getenv("FILESYSTEM_PUBLIC_URL") ?: error("FILESYSTEM_PUBLIC_URL is empty")
    val httpPort = System.getenv("FILESYSTEM_HTTP_PORT")?.toIntOrNull() ?: 8822
    val storage = LocalFileSystemObjectStorageService(publicUrl, base)
    val service = FilesystemRpcImpl(storage)
    embeddedServer(CIO, host = "0.0.0.0", port = httpPort) {
        configureObjectDownloads(storage)
    }.start(wait = false)
    embeddedServer(CIO, host = "0.0.0.0", port = port) {
        install(WebSockets) { maxFrameSize = Long.MAX_VALUE }
        install(Krpc)
        routing {
            get("/health") { call.respondText("ok") }
            rpc("/rpc") {
                rpcConfig { serialization { json() } }
                registerService<FilesystemRpc> { service }
            }
        }
    }.start(wait = true)
}

internal fun Application.configureObjectDownloads(storage: LocalFileSystemObjectStorageService) {
    install(PartialContent)
    install(AutoHeadResponse)
    routing {
        get("/health") { call.respondText("ok") }
        get("/objects/{path...}") {
            val path = call.parameters.getAll("path").orEmpty()
            val file = storage.getPathResponse(path)
            if (file == null) {
                call.respondText("not found", status = io.ktor.http.HttpStatusCode.NotFound)
            } else {
                call.response.header("Access-Control-Allow-Origin", "*")
                call.response.header("Access-Control-Expose-Headers", "Content-Length,Content-Range,Accept-Ranges")
                call.respondFile(file.toFile())
            }
        }
    }
}
