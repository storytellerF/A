/*
 * This is a private project. All rights reserved.
 */
package com.storyteller_f.a.backend.lucene

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.OffsetFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.service.CommunityDocument
import com.storyteller_f.a.backend.core.service.CommunityDocumentSearch
import com.storyteller_f.a.backend.core.service.CommunitySearchService
import com.storyteller_f.a.backend.core.service.CommunitySearchServiceFactory
import com.storyteller_f.a.backend.core.service.FileDocument
import com.storyteller_f.a.backend.core.service.FileDocumentSearch
import com.storyteller_f.a.backend.core.service.FileSearchService
import com.storyteller_f.a.backend.core.service.FileSearchServiceFactory
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
import com.storyteller_f.services.lucene.api.LuceneRpc
import com.storyteller_f.services.lucene.api.RpcLuceneQuery
import com.storyteller_f.services.lucene.api.RpcLuceneResult
import com.storyteller_f.services.lucene.api.RpcLuceneSort
import com.storyteller_f.services.lucene.api.RpcLuceneSortType
import com.storyteller_f.services.lucene.api.RpcLuceneStoredDocument
import com.storyteller_f.services.lucene.api.RpcLuceneTextQuery
import com.storyteller_f.shared.type.ObjectType
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
private fun OffsetFetch.query() = RpcLuceneQuery(offset = cursor?.value ?: 0, size = size)
private fun RpcLuceneQuery.withText(word: String, vararg fields: String) =
    copy(
    text = listOf(RpcLuceneTextQuery(word, fields.toList())),
)
private fun <T> RpcLuceneResult.toPage(convert: (RpcLuceneStoredDocument) -> T) =
    PaginationResult(documents.map(convert), total)

class LuceneTopicSearchService(private val rpc: LuceneRpc) : TopicSearchService {
    override suspend fun saveDocument(documents: List<TopicDocument>) =
        rpcResult {
        rpc.save(
            "topic",
            documents.map { LuceneTopicDocument(it).save() },
        )
    }
    override suspend fun getDocuments(idList: List<PrimaryKey>) =
        rpcResult {
        if (idList.isEmpty()) return@rpcResult emptyList<TopicDocument?>()
        val documents =
            rpc.search(
                "topic",
                RpcLuceneQuery(mustLongSet = mapOf(DOCUMENT_ID_FIELD to idList), size = idList.size),
            ).documents.map(LuceneTopicDocument.Companion::restore).associateBy { it.id }
        idList.map(documents::get)
    }
    override suspend fun clean() = rpcResult { rpc.clean("topic") }
    override suspend fun searchDocument(search: TopicDocumentSearch) =
        rpcResult {
        val query =
            when (search) {
                is TopicDocumentSearch.Recommend ->
                    search.fetch.query().copy(
                        mustLongSet = mapOf("parentId" to search.communities),
                        mustNotLong = mapOf("author" to search.uid),
                        sort = listOf(RpcLuceneSort(DOCUMENT_ID_FIELD, RpcLuceneSortType.LONG, descending = true)),
                    )

                is TopicDocumentSearch.RecommendNotLogin ->
                    search.fetch.query().copy(
                        mustKeyword = mapOf("parentType" to ObjectType.COMMUNITY.name),
                        sort = listOf(RpcLuceneSort(DOCUMENT_ID_FIELD, RpcLuceneSortType.LONG, descending = true)),
                    )

                is TopicDocumentSearch.AllCommunityRoot ->
                    search.fetch.query().copy(
                        mustKeyword = mapOf("parentType" to ObjectType.COMMUNITY.name),
                    ).withText(search.word, "content")

                is TopicDocumentSearch.Topics ->
                    search.fetch.query().copy(
                        mustLong = mapOf("parentId" to search.parentId),
                    ).withText(search.word, "content")

                is TopicDocumentSearch.All -> search.fetch.query().withText(search.word, "content")
            }
        if ((
                search is TopicDocumentSearch.AllCommunityRoot || search is TopicDocumentSearch.Topics ||
                    search is TopicDocumentSearch.All
                ) &&
            query.text.single().word.isEmpty()
        ) {
            PaginationResult(
                emptyList(),
                0,
            )
        } else {
            rpc.search("topic", query).toPage(LuceneTopicDocument.Companion::restore)
        }
    }
}

class LuceneUserSearchService(private val rpc: LuceneRpc) : UserSearchService {
    override suspend fun saveDocument(documents: List<UserDocument>) =
        rpcResult {
        rpc.save(
            "user",
            documents.map { LuceneUserDocument(it).save() },
        )
    }
    override suspend fun clean() = rpcResult { rpc.clean("user") }
    override suspend fun searchDocument(search: UserDocumentSearch) =
        rpcResult {
        when (search) {
            is UserDocumentSearch.Keyword ->
                if (search.word.isEmpty()) {
                    PaginationResult(
                        emptyList(),
                        0,
                    )
                } else {
                    rpc.search(
                        "user",
                        search.fetch.query().withText(search.word, "aid", "nickname"),
                    ).toPage(LuceneUserDocument.Companion::restore)
                }
        }
    }
}

class LuceneRoomSearchService(private val rpc: LuceneRpc) : RoomSearchService {
    override suspend fun saveDocument(documents: List<RoomDocument>) =
        rpcResult {
        rpc.save(
            "room",
            documents.map { LuceneRoomDocument(it).save() },
        )
    }
    override suspend fun clean() = rpcResult { rpc.clean("room") }
    override suspend fun searchDocument(search: RoomDocumentSearch) =
        rpcResult {
        when (search) {
            is RoomDocumentSearch.Keyword ->
                if (search.words.isEmpty()) {
                    PaginationResult(
                        emptyList(),
                        0,
                    )
                } else {
                    rpc.search(
                        "room",
                        search.fetch.query().copy(
                            mustLong = search.communityId?.let { mapOf("communityId" to it) }.orEmpty(),
                        ).withText(search.words, "aid", "name"),
                    ).toPage(LuceneRoomDocument.Companion::restore)
                }
        }
    }
}

class LuceneCommunitySearchService(private val rpc: LuceneRpc) : CommunitySearchService {
    override suspend fun saveDocument(documents: List<CommunityDocument>) =
        rpcResult {
        rpc.save(
            "community",
            documents.map { LuceneCommunityDocument(it).save() },
        )
    }
    override suspend fun clean() = rpcResult { rpc.clean("community") }
    override suspend fun searchDocument(search: CommunityDocumentSearch) =
        rpcResult {
        when (search) {
            is CommunityDocumentSearch.Keyword ->
                if (search.keyword.isEmpty()) {
                    PaginationResult(
                        emptyList(),
                        0,
                    )
                } else {
                    rpc.search(
                        "community",
                        search.fetch.query().withText(search.keyword, "aid", "name"),
                    ).toPage(LuceneCommunityDocument.Companion::restore)
                }
        }
    }
}

class LuceneMemberSearchService(private val rpc: LuceneRpc) : MemberSearchService {
    override suspend fun saveDocument(documents: List<MemberDocument>) =
        rpcResult {
        rpc.save(
            "member",
            documents.map { LuceneMemberDocument(it).save() },
        )
    }
    override suspend fun deleteDocument(uid: PrimaryKey, objectId: PrimaryKey) =
        rpcResult {
        rpc.delete(
            "member",
            RpcLuceneQuery(mustLong = mapOf("uid" to uid, "objectId" to objectId)),
        )
    }
    override suspend fun clean() = rpcResult { rpc.clean("member") }
    override suspend fun searchDocument(search: MemberDocumentSearch) =
        rpcResult {
        val query =
            when (search) {
                is MemberDocumentSearch.Keyword ->
                    search.fetch.query().copy(
                        mustLong = search.objectId?.let { mapOf("objectId" to it) }.orEmpty(),
                    ).withText(search.nickname, "nickname")

                is MemberDocumentSearch.CommunityMembers ->
                    search.fetch.query().copy(
                        mustLong = mapOf("uid" to search.uid),
                        mustKeyword = mapOf("objectType" to ObjectType.COMMUNITY.name),
                    ).withText(search.objectName, "objectName")

                is MemberDocumentSearch.RoomMembers ->
                    search.fetch.query().copy(
                        mustLong =
                        buildMap {
                            put("uid", search.uid)
                            search.communityId?.let { put("communityId", it) }
                        },
                        mustKeyword = mapOf("objectType" to ObjectType.ROOM.name),
                    ).withText(search.objectName, "objectName")
            }
        if (query.text.single().word.isBlank()) {
            PaginationResult(
                emptyList(),
                0,
            )
        } else {
            rpc.search("member", query).toPage(LuceneMemberDocument.Companion::restore)
        }
    }
}

class LuceneFileSearchService(private val rpc: LuceneRpc) : FileSearchService {
    override suspend fun saveDocument(documents: List<FileDocument>) =
        rpcResult {
        rpc.save(
            "file",
            documents.map { LuceneFileDocument(it).save() },
        )
    }
    override suspend fun clean() = rpcResult { rpc.clean("file") }
    override suspend fun searchDocument(search: FileDocumentSearch) =
        rpcResult {
        when (search) {
            is FileDocumentSearch.Keyword ->
                if (search.word.isEmpty()) {
                    PaginationResult(
                        emptyList(),
                        0,
                    )
                } else {
                    rpc.search(
                        "file",
                        search.fetch.query().copy(
                            mustLong = search.ownerId?.let { mapOf("ownerId" to it) }.orEmpty(),
                        ).withText(search.word, "name"),
                    ).toPage(LuceneFileDocument.Companion::restore)
                }
        }
    }
}

private class LuceneRpcConnection(rpcUrl: String) {
    private val client =
        HttpClient(CIO) {
            install(WebSockets)
            installKrpc()
        }
    val service: LuceneRpc =
        client.rpc {
            url {
                takeFrom(
                    rpcUrl,
                )
            }
            ; rpcConfig { serialization { json() } }
        }.withService()
}
private object LuceneRpcConnections {
    private val connections = ConcurrentHashMap<String, LuceneRpcConnection>()
    fun service(env: MergedEnv) =
        connections.computeIfAbsent(
        env["LUCENE_RPC_URL"] ?: error("LUCENE_RPC_URL is empty"),
        ::LuceneRpcConnection,
    ).service
}
open class LuceneSearchServiceFactory {
    protected fun service(env: MergedEnv) =
        LuceneRpcConnections.service(
        env,
    )
        ; protected fun matches(env: MergedEnv) = env["SEARCH_SERVICE"] == "lucene"
}
class LuceneTopicSearchServiceFactory :
    LuceneSearchServiceFactory(),
    TopicSearchServiceFactory {
    override fun match(env: MergedEnv) =
        matches(
        env,
    )
        ; override fun build(env: MergedEnv): TopicSearchService = LuceneTopicSearchService(service(env))
}
class LuceneUserSearchServiceFactory :
    LuceneSearchServiceFactory(),
    UserSearchServiceFactory {
    override fun match(env: MergedEnv) =
        matches(
        env,
    )
        ; override fun build(env: MergedEnv): UserSearchService = LuceneUserSearchService(service(env))
}
class LuceneRoomSearchServiceFactory :
    LuceneSearchServiceFactory(),
    RoomSearchServiceFactory {
    override fun match(env: MergedEnv) =
        matches(
        env,
    )
        ; override fun build(env: MergedEnv): RoomSearchService = LuceneRoomSearchService(service(env))
}
class LuceneCommunitySearchServiceFactory :
    LuceneSearchServiceFactory(),
    CommunitySearchServiceFactory {
    override fun match(env: MergedEnv) =
        matches(
        env,
    )
        ; override fun build(env: MergedEnv): CommunitySearchService = LuceneCommunitySearchService(service(env))
}
class LuceneMemberSearchServiceFactory :
    LuceneSearchServiceFactory(),
    MemberSearchServiceFactory {
    override fun match(env: MergedEnv) =
        matches(
        env,
    )
        ; override fun build(env: MergedEnv): MemberSearchService = LuceneMemberSearchService(service(env))
}
class LuceneFileSearchServiceFactory :
    LuceneSearchServiceFactory(),
    FileSearchServiceFactory {
    override fun match(env: MergedEnv) =
        matches(
        env,
    )
        ; override fun build(env: MergedEnv): FileSearchService = LuceneFileSearchService(service(env))
}
