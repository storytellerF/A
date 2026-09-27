/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.backend.rpc

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.service.CommunityDocument
import com.storyteller_f.a.backend.core.service.CommunityDocumentSearch
import com.storyteller_f.a.backend.core.service.CommunitySearchService
import com.storyteller_f.a.backend.core.service.CommunitySearchServiceFactory
import com.storyteller_f.a.backend.core.service.CopyPack
import com.storyteller_f.a.backend.core.service.FileDocument
import com.storyteller_f.a.backend.core.service.FileDocumentSearch
import com.storyteller_f.a.backend.core.service.FileSearchService
import com.storyteller_f.a.backend.core.service.FileSearchServiceFactory
import com.storyteller_f.a.backend.core.service.FilesystemRpc
import com.storyteller_f.a.backend.core.service.LuceneRpc
import com.storyteller_f.a.backend.core.service.MemberDocument
import com.storyteller_f.a.backend.core.service.MemberDocumentSearch
import com.storyteller_f.a.backend.core.service.MemberSearchService
import com.storyteller_f.a.backend.core.service.MemberSearchServiceFactory
import com.storyteller_f.a.backend.core.service.ObjectStorageService
import com.storyteller_f.a.backend.core.service.ObjectStorageServiceFactory
import com.storyteller_f.a.backend.core.service.RoomDocument
import com.storyteller_f.a.backend.core.service.RoomDocumentSearch
import com.storyteller_f.a.backend.core.service.RoomSearchService
import com.storyteller_f.a.backend.core.service.RoomSearchServiceFactory
import com.storyteller_f.a.backend.core.service.RpcUploadPack
import com.storyteller_f.a.backend.core.service.TopicDocument
import com.storyteller_f.a.backend.core.service.TopicDocumentSearch
import com.storyteller_f.a.backend.core.service.TopicSearchService
import com.storyteller_f.a.backend.core.service.TopicSearchServiceFactory
import com.storyteller_f.a.backend.core.service.UploadPack
import com.storyteller_f.a.backend.core.service.UserDocument
import com.storyteller_f.a.backend.core.service.UserDocumentSearch
import com.storyteller_f.a.backend.core.service.UserSearchService
import com.storyteller_f.a.backend.core.service.UserSearchServiceFactory
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.cancellableRunCatching
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.takeFrom
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.ktor.client.rpcConfig
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.withService
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

private fun rpcClient() =
    HttpClient(CIO) {
    install(WebSockets)
    installKrpc()
}

private class FilesystemRpcConnection(url: String) {
    private val client = rpcClient()
    val service: FilesystemRpc =
        client.rpc {
            url { takeFrom(url) }
            rpcConfig { serialization { json() } }
        }.withService()
}

private class LuceneRpcConnection(url: String) {
    private val client = rpcClient()
    val service: LuceneRpc =
        client.rpc {
            url { takeFrom(url) }
            rpcConfig { serialization { json() } }
        }.withService()
}

private object FilesystemRpcConnections {
    private val connections = ConcurrentHashMap<String, FilesystemRpcConnection>()

    fun service(env: MergedEnv): FilesystemRpc {
        val url = env["FILESYSTEM_RPC_URL"] ?: error("FILESYSTEM_RPC_URL is empty")
        return connections.computeIfAbsent(url, ::FilesystemRpcConnection).service
    }
}

private object LuceneRpcConnections {
    private val connections = ConcurrentHashMap<String, LuceneRpcConnection>()

    fun service(env: MergedEnv): LuceneRpc {
        val url = env["LUCENE_RPC_URL"] ?: error("LUCENE_RPC_URL is empty")
        return connections.computeIfAbsent(url, ::LuceneRpcConnection).service
    }
}

private inline fun <T> rpcResult(block: () -> T): Result<T> = cancellableRunCatching(block)

class RpcObjectStorageService(private val rpc: FilesystemRpc) : ObjectStorageService {
    override suspend fun upload(bucketName: String, uploadPacks: List<UploadPack>) =
        rpcResult {
        rpc.upload(
            bucketName,
            uploadPacks.map {
                RpcUploadPack(
                    it.file.inputStream().buffered().use { input -> input.readBytes() },
                    it.name,
                    it.size,
                    it.fullName,
                    it.sha256,
                )
            },
        )
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
    override suspend fun getInputStream(bucketName: String, name: String) =
        rpcResult {
        ByteArrayInputStream(rpc.getBytes(bucketName, name)).buffered()
    }
    override suspend fun compose(bucketName: String, targetFullName: String, sourceFullNames: List<String>) =
        rpcResult {
            rpc.compose(bucketName, targetFullName, sourceFullNames)
        }
    override suspend fun delete(bucketName: String, names: List<String>) = rpcResult { rpc.delete(bucketName, names) }
}

class RpcObjectStorageServiceFactory : ObjectStorageServiceFactory {
    override fun match(env: MergedEnv) = env["MEDIA_SERVICE"] == "rpc"
    override fun build(env: MergedEnv): ObjectStorageService =
        RpcObjectStorageService(
        FilesystemRpcConnections.service(env),
    )
}

class RpcTopicSearchService(private val rpc: LuceneRpc) : TopicSearchService {
    override suspend fun saveDocument(documents: List<TopicDocument>) = rpcResult { rpc.saveTopics(documents) }
    override suspend fun getDocuments(idList: List<PrimaryKey>) = rpcResult { rpc.getTopics(idList) }
    override suspend fun clean() = rpcResult { rpc.cleanTopics() }
    override suspend fun searchDocument(topicDocumentSearch: TopicDocumentSearch) =
        rpcResult {
        rpc.searchTopics(
            topicDocumentSearch,
        )
    }
}

class RpcUserSearchService(private val rpc: LuceneRpc) : UserSearchService {
    override suspend fun saveDocument(documents: List<UserDocument>) = rpcResult { rpc.saveUsers(documents) }
    override suspend fun clean() = rpcResult { rpc.cleanUsers() }
    override suspend fun searchDocument(userDocumentSearch: UserDocumentSearch) =
        rpcResult {
        rpc.searchUsers(
            userDocumentSearch,
        )
    }
}

class RpcRoomSearchService(private val rpc: LuceneRpc) : RoomSearchService {
    override suspend fun saveDocument(documents: List<RoomDocument>) = rpcResult { rpc.saveRooms(documents) }
    override suspend fun clean() = rpcResult { rpc.cleanRooms() }
    override suspend fun searchDocument(roomDocumentSearch: RoomDocumentSearch) =
        rpcResult {
        rpc.searchRooms(
            roomDocumentSearch,
        )
    }
}

class RpcCommunitySearchService(private val rpc: LuceneRpc) : CommunitySearchService {
    override suspend fun saveDocument(documents: List<CommunityDocument>) = rpcResult { rpc.saveCommunities(documents) }
    override suspend fun clean() = rpcResult { rpc.cleanCommunities() }
    override suspend fun searchDocument(communityDocumentSearch: CommunityDocumentSearch) =
        rpcResult {
        rpc.searchCommunities(
            communityDocumentSearch,
        )
    }
}

class RpcMemberSearchService(private val rpc: LuceneRpc) : MemberSearchService {
    override suspend fun saveDocument(documents: List<MemberDocument>) = rpcResult { rpc.saveMembers(documents) }
    override suspend fun deleteDocument(uid: PrimaryKey, objectId: PrimaryKey) =
        rpcResult {
        rpc.deleteMember(
            uid,
            objectId,
        )
    }
    override suspend fun clean() = rpcResult { rpc.cleanMembers() }
    override suspend fun searchDocument(memberDocumentSearch: MemberDocumentSearch) =
        rpcResult {
        rpc.searchMembers(
            memberDocumentSearch,
        )
    }
}

class RpcFileSearchService(private val rpc: LuceneRpc) : FileSearchService {
    override suspend fun saveDocument(documents: List<FileDocument>) = rpcResult { rpc.saveFiles(documents) }
    override suspend fun clean() = rpcResult { rpc.cleanFiles() }
    override suspend fun searchDocument(fileDocumentSearch: FileDocumentSearch) =
        rpcResult {
        rpc.searchFiles(
            fileDocumentSearch,
        )
    }
}

open class RpcSearchFactory {
    protected fun rpc(env: MergedEnv) = LuceneRpcConnections.service(env)
    protected fun matches(env: MergedEnv) = env["SEARCH_SERVICE"] == "rpc"
}

class RpcTopicSearchServiceFactory :
    RpcSearchFactory(),
    TopicSearchServiceFactory {
    override fun match(env: MergedEnv) = matches(env)
    override fun build(env: MergedEnv): TopicSearchService = RpcTopicSearchService(rpc(env))
}
class RpcUserSearchServiceFactory :
    RpcSearchFactory(),
    UserSearchServiceFactory {
    override fun match(env: MergedEnv) = matches(env)
    override fun build(env: MergedEnv): UserSearchService = RpcUserSearchService(rpc(env))
}
class RpcRoomSearchServiceFactory :
    RpcSearchFactory(),
    RoomSearchServiceFactory {
    override fun match(env: MergedEnv) = matches(env)
    override fun build(env: MergedEnv): RoomSearchService = RpcRoomSearchService(rpc(env))
}
class RpcCommunitySearchServiceFactory :
    RpcSearchFactory(),
    CommunitySearchServiceFactory {
    override fun match(env: MergedEnv) = matches(env)
    override fun build(env: MergedEnv): CommunitySearchService = RpcCommunitySearchService(rpc(env))
}
class RpcMemberSearchServiceFactory :
    RpcSearchFactory(),
    MemberSearchServiceFactory {
    override fun match(env: MergedEnv) = matches(env)
    override fun build(env: MergedEnv): MemberSearchService = RpcMemberSearchService(rpc(env))
}
class RpcFileSearchServiceFactory :
    RpcSearchFactory(),
    FileSearchServiceFactory {
    override fun match(env: MergedEnv) = matches(env)
    override fun build(env: MergedEnv): FileSearchService = RpcFileSearchService(rpc(env))
}
