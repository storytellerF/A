/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.backend.filesystem

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.service.CopyPack
import com.storyteller_f.a.backend.core.service.FilesystemRpc
import com.storyteller_f.a.backend.core.service.ObjectStorageService
import com.storyteller_f.a.backend.core.service.ObjectStorageServiceFactory
import com.storyteller_f.a.backend.core.service.RpcUploadPack
import com.storyteller_f.a.backend.core.service.UploadPack
import com.storyteller_f.shared.utils.cancellableRunCatching
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.ktor.client.rpcConfig
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.withService
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

private const val TRANSFER_CHUNK_SIZE = 256 * 1024

class FileSystemObjectStorageService(private val rpc: FilesystemRpc) : ObjectStorageService {
    override suspend fun upload(bucketName: String, uploadPacks: List<UploadPack>) =
        rpcResult {
        uploadPacks.map { pack -> uploadOne(bucketName, pack) }
    }

    private suspend fun uploadOne(
        bucketName: String,
        pack: UploadPack,
    ): com.storyteller_f.a.backend.core.service.ObjectStorageWriteRecord {
        val transferId = UUID.randomUUID().toString()
        rpc.beginUpload(
            bucketName,
            RpcUploadPack(transferId, pack.name, pack.size, pack.fullName, pack.sha256),
        )
        return try {
            pack.file.inputStream().buffered().use { input ->
                val buffer = ByteArray(TRANSFER_CHUNK_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    rpc.uploadChunk(transferId, if (count == buffer.size) buffer else buffer.copyOf(count))
                }
            }
            rpc.finishUpload(transferId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            abortUpload(transferId, exception)
            throw exception
        }
    }

    private suspend fun abortUpload(transferId: String, cause: Throwable) {
        try {
            rpc.abortUpload(transferId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (abortException: Exception) {
            cause.addSuppressed(abortException)
        }
    }

    override suspend fun get(bucketName: String, names: List<String>) = rpcResult { rpc.get(bucketName, names) }
    override suspend fun clean(bucketName: String) = rpcResult { rpc.cleanObjects(bucketName) }
    override suspend fun list(bucketName: String, prefix: String) = rpcResult { rpc.list(bucketName, prefix) }
    override suspend fun copy(bucketName: String, copyPacks: List<CopyPack>) =
        rpcResult {
        rpc.copy(
            bucketName,
            copyPacks,
        )
    }

    override suspend fun getInputStream(bucketName: String, name: String): Result<InputStream> =
        rpcResult {
        val temporary = Files.createTempFile("a-filesystem-rpc-", ".download")
        try {
            Files.newOutputStream(temporary).buffered().use { output ->
                var offset = 0L
                do {
                    val chunk = rpc.getChunk(bucketName, name, offset, TRANSFER_CHUNK_SIZE)
                    output.write(chunk)
                    offset += chunk.size
                } while (chunk.size == TRANSFER_CHUNK_SIZE)
            }
            DeletingInputStream(temporary)
        } catch (throwable: Throwable) {
            Files.deleteIfExists(temporary)
            throw throwable
        }
    }

    override suspend fun compose(bucketName: String, targetFullName: String, sourceFullNames: List<String>) =
        rpcResult { rpc.compose(bucketName, targetFullName, sourceFullNames) }

    override suspend fun delete(bucketName: String, names: List<String>) = rpcResult { rpc.delete(bucketName, names) }
}

private class DeletingInputStream(private val path: Path) :
    FilterInputStream(Files.newInputStream(path).buffered()) {
    override fun close() {
        try {
            super.close()
        } finally {
            Files.deleteIfExists(path)
        }
    }
}

private inline fun <T> rpcResult(block: () -> T): Result<T> = cancellableRunCatching(block)

class FileSystemObjectStorageServiceFactory : ObjectStorageServiceFactory {
    override fun match(env: MergedEnv) = env["MEDIA_SERVICE"] == "filesystem"

    override fun build(env: MergedEnv): ObjectStorageService {
        val url = env["FILESYSTEM_RPC_URL"] ?: error("FILESYSTEM_RPC_URL is empty")
        val client =
            HttpClient(CIO) {
                install(WebSockets)
                installKrpc()
            }
        val service =
            client.rpc {
                url { takeFrom(url) }
                rpcConfig { serialization { json() } }
            }.withService<FilesystemRpc>()
        return FileSystemObjectStorageService(service)
    }
}
