/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.cloud.filesystem

import com.storyteller_f.a.backend.core.service.CopyPack
import com.storyteller_f.a.backend.core.service.FilesystemRpc
import com.storyteller_f.a.backend.core.service.ObjectStorageRecord
import com.storyteller_f.a.backend.core.service.ObjectStorageWriteRecord
import com.storyteller_f.a.backend.core.service.RpcUploadPack
import com.storyteller_f.a.backend.core.service.UploadPack
import com.storyteller_f.a.backend.filesystem.FileSystemObjectStorageService
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json
import java.nio.file.Files
import java.nio.file.Paths

private class FilesystemRpcImpl(private val storage: FileSystemObjectStorageService) : FilesystemRpc {
    override suspend fun health() = "ok"

    override suspend fun upload(bucketName: String, uploadPacks: List<RpcUploadPack>): List<ObjectStorageWriteRecord> {
        val temporaryFiles = uploadPacks.map { Files.createTempFile("a-filesystem-rpc-", ".upload") }
        return try {
            uploadPacks.zip(temporaryFiles).forEach { (pack, path) ->
                Files.newOutputStream(path).buffered().use { it.write(pack.content) }
            }
            storage.upload(
                bucketName,
                uploadPacks.zip(temporaryFiles).map { (pack, path) ->
                    UploadPack(path.toFile(), pack.name, pack.size, pack.fullName, pack.sha256)
                },
            ).getOrThrow()
        } finally {
            temporaryFiles.forEach(Files::deleteIfExists)
        }
    }

    override suspend fun get(bucketName: String, names: List<String>) = storage.get(bucketName, names).getOrThrow()
    override suspend fun cleanObjects(bucketName: String) {
        storage.clean(bucketName).getOrThrow()
    }
    override suspend fun list(bucketName: String, prefix: String) = storage.list(bucketName, prefix).getOrThrow()
    override suspend fun copy(bucketName: String, copyPacks: List<CopyPack>): List<ObjectStorageRecord> =
        storage.copy(bucketName, copyPacks).getOrThrow()
    override suspend fun getBytes(bucketName: String, name: String): ByteArray =
        storage.getInputStream(bucketName, name).getOrThrow().buffered().use { it.readBytes() }
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
    val publicUrl = System.getenv("SERVER_URL") ?: error("SERVER_URL is empty")
    val service = FilesystemRpcImpl(FileSystemObjectStorageService(publicUrl, base))
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
