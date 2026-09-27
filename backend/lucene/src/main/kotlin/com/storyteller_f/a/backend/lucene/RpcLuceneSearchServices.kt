/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.backend.lucene

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.service.CommunityDocument
import com.storyteller_f.a.backend.core.service.CommunityDocumentSearch
import com.storyteller_f.a.backend.core.service.CommunitySearchService
import com.storyteller_f.a.backend.core.service.CommunitySearchServiceFactory
import com.storyteller_f.a.backend.core.service.FileDocument
import com.storyteller_f.a.backend.core.service.FileDocumentSearch
import com.storyteller_f.a.backend.core.service.FileSearchService
import com.storyteller_f.a.backend.core.service.FileSearchServiceFactory
import com.storyteller_f.a.backend.core.service.LuceneRpc
import com.storyteller_f.a.backend.core.service.MemberDocument
import com.storyteller_f.a.backend.core.service.MemberDocumentSearch
import com.storyteller_f.a.backend.core.service.MemberSearchService
import com.storyteller_f.a.backend.core.service.MemberSearchServiceFactory
import com.storyteller_f.a.backend.core.service.RoomDocument
import com.storyteller_f.a.backend.core.service.RoomDocumentSearch
import com.storyteller_f.a.backend.core.service.RoomSearchService
import com.storyteller_f.a.backend.core.service.RoomSearchServiceFactory
import com.storyteller_f.a.backend.core.service.TopicDocument
import com.storyteller_f.a.backend.core.service.TopicDocumentSearch
import com.storyteller_f.a.backend.core.service.TopicSearchService
import com.storyteller_f.a.backend.core.service.TopicSearchServiceFactory
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
import java.util.concurrent.ConcurrentHashMap

private inline fun <T> rpcResult(block: () -> T): Result<T> = cancellableRunCatching(block)

class LuceneTopicSearchService(private val rpc: LuceneRpc) : TopicSearchService {
    override suspend fun saveDocument(documents: List<TopicDocument>) = rpcResult { rpc.saveTopics(documents) }
    override suspend fun getDocuments(idList: List<PrimaryKey>) = rpcResult { rpc.getTopics(idList) }
    override suspend fun clean() = rpcResult { rpc.cleanTopics() }
    override suspend fun searchDocument(topicDocumentSearch: TopicDocumentSearch) =
        rpcResult { rpc.searchTopics(topicDocumentSearch) }
}

class LuceneUserSearchService(private val rpc: LuceneRpc) : UserSearchService {
    override suspend fun saveDocument(documents: List<UserDocument>) = rpcResult { rpc.saveUsers(documents) }
    override suspend fun clean() = rpcResult { rpc.cleanUsers() }
    override suspend fun searchDocument(userDocumentSearch: UserDocumentSearch) =
        rpcResult { rpc.searchUsers(userDocumentSearch) }
}

class LuceneRoomSearchService(private val rpc: LuceneRpc) : RoomSearchService {
    override suspend fun saveDocument(documents: List<RoomDocument>) = rpcResult { rpc.saveRooms(documents) }
    override suspend fun clean() = rpcResult { rpc.cleanRooms() }
    override suspend fun searchDocument(roomDocumentSearch: RoomDocumentSearch) =
        rpcResult { rpc.searchRooms(roomDocumentSearch) }
}

class LuceneCommunitySearchService(private val rpc: LuceneRpc) : CommunitySearchService {
    override suspend fun saveDocument(documents: List<CommunityDocument>) = rpcResult { rpc.saveCommunities(documents) }
    override suspend fun clean() = rpcResult { rpc.cleanCommunities() }
    override suspend fun searchDocument(communityDocumentSearch: CommunityDocumentSearch) =
        rpcResult { rpc.searchCommunities(communityDocumentSearch) }
}

class LuceneMemberSearchService(private val rpc: LuceneRpc) : MemberSearchService {
    override suspend fun saveDocument(documents: List<MemberDocument>) = rpcResult { rpc.saveMembers(documents) }
    override suspend fun deleteDocument(uid: PrimaryKey, objectId: PrimaryKey) =
        rpcResult { rpc.deleteMember(uid, objectId) }
    override suspend fun clean() = rpcResult { rpc.cleanMembers() }
    override suspend fun searchDocument(memberDocumentSearch: MemberDocumentSearch) =
        rpcResult { rpc.searchMembers(memberDocumentSearch) }
}

class LuceneFileSearchService(private val rpc: LuceneRpc) : FileSearchService {
    override suspend fun saveDocument(documents: List<FileDocument>) = rpcResult { rpc.saveFiles(documents) }
    override suspend fun clean() = rpcResult { rpc.cleanFiles() }
    override suspend fun searchDocument(fileDocumentSearch: FileDocumentSearch) =
        rpcResult { rpc.searchFiles(fileDocumentSearch) }
}

private class LuceneRpcConnection(rpcUrl: String) {
    private val client =
        HttpClient(CIO) {
            install(WebSockets)
            installKrpc()
        }
    val service: LuceneRpc =
        client.rpc {
            url { takeFrom(rpcUrl) }
            rpcConfig { serialization { json() } }
        }.withService()
}

private object LuceneRpcConnections {
    private val connections = ConcurrentHashMap<String, LuceneRpcConnection>()

    fun service(env: MergedEnv): LuceneRpc {
        val rpcUrl = env["LUCENE_RPC_URL"] ?: error("LUCENE_RPC_URL is empty")
        return connections.computeIfAbsent(rpcUrl, ::LuceneRpcConnection).service
    }
}

open class LuceneSearchServiceFactory {
    protected fun service(env: MergedEnv) = LuceneRpcConnections.service(env)
    protected fun matches(env: MergedEnv) = env["SEARCH_SERVICE"] == "lucene"
}

class LuceneTopicSearchServiceFactory :
    LuceneSearchServiceFactory(),
    TopicSearchServiceFactory {
    override fun match(env: MergedEnv) = matches(env)
    override fun build(env: MergedEnv): TopicSearchService = LuceneTopicSearchService(service(env))
}

class LuceneUserSearchServiceFactory :
    LuceneSearchServiceFactory(),
    UserSearchServiceFactory {
    override fun match(env: MergedEnv) = matches(env)
    override fun build(env: MergedEnv): UserSearchService = LuceneUserSearchService(service(env))
}

class LuceneRoomSearchServiceFactory :
    LuceneSearchServiceFactory(),
    RoomSearchServiceFactory {
    override fun match(env: MergedEnv) = matches(env)
    override fun build(env: MergedEnv): RoomSearchService = LuceneRoomSearchService(service(env))
}

class LuceneCommunitySearchServiceFactory :
    LuceneSearchServiceFactory(),
    CommunitySearchServiceFactory {
    override fun match(env: MergedEnv) = matches(env)
    override fun build(env: MergedEnv): CommunitySearchService = LuceneCommunitySearchService(service(env))
}

class LuceneMemberSearchServiceFactory :
    LuceneSearchServiceFactory(),
    MemberSearchServiceFactory {
    override fun match(env: MergedEnv) = matches(env)
    override fun build(env: MergedEnv): MemberSearchService = LuceneMemberSearchService(service(env))
}

class LuceneFileSearchServiceFactory :
    LuceneSearchServiceFactory(),
    FileSearchServiceFactory {
    override fun match(env: MergedEnv) = matches(env)
    override fun build(env: MergedEnv): FileSearchService = LuceneFileSearchService(service(env))
}
