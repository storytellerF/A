/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.cloud.lucene

import com.storyteller_f.a.backend.core.service.CommunityDocument
import com.storyteller_f.a.backend.core.service.CommunityDocumentSearch
import com.storyteller_f.a.backend.core.service.FileDocument
import com.storyteller_f.a.backend.core.service.FileDocumentSearch
import com.storyteller_f.a.backend.core.service.LuceneRpc
import com.storyteller_f.a.backend.core.service.MemberDocument
import com.storyteller_f.a.backend.core.service.MemberDocumentSearch
import com.storyteller_f.a.backend.core.service.RoomDocument
import com.storyteller_f.a.backend.core.service.RoomDocumentSearch
import com.storyteller_f.a.backend.core.service.TopicDocument
import com.storyteller_f.a.backend.core.service.TopicDocumentSearch
import com.storyteller_f.a.backend.core.service.UserDocument
import com.storyteller_f.a.backend.core.service.UserDocumentSearch
import com.storyteller_f.a.backend.lucene.LuceneCommunitySearchService
import com.storyteller_f.a.backend.lucene.LuceneFileSearchService
import com.storyteller_f.a.backend.lucene.LuceneMemberSearchService
import com.storyteller_f.a.backend.lucene.LuceneRoomSearchService
import com.storyteller_f.a.backend.lucene.LuceneTopicSearchService
import com.storyteller_f.a.backend.lucene.LuceneUserSearchService
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
import java.nio.file.Paths

private class LuceneRpcImpl(basePath: String) : LuceneRpc {
    private val base = Paths.get(basePath)
    private val topics = LuceneTopicSearchService(base.resolve("topic"))
    private val users = LuceneUserSearchService(base.resolve("user"))
    private val rooms = LuceneRoomSearchService(base.resolve("room"))
    private val communities = LuceneCommunitySearchService(base.resolve("community"))
    private val members = LuceneMemberSearchService(base.resolve("member"))
    private val files = LuceneFileSearchService(base.resolve("file"))

    override suspend fun health() = "ok"
    override suspend fun saveTopics(documents: List<TopicDocument>) {
        topics.saveDocument(documents).getOrThrow()
    }
    override suspend fun getTopics(ids: List<Long>) = topics.getDocuments(ids).getOrThrow()
    override suspend fun cleanTopics() {
        topics.clean().getOrThrow()
    }
    override suspend fun searchTopics(search: TopicDocumentSearch) = topics.searchDocument(search).getOrThrow()
    override suspend fun saveUsers(documents: List<UserDocument>) {
        users.saveDocument(documents).getOrThrow()
    }
    override suspend fun cleanUsers() {
        users.clean().getOrThrow()
    }
    override suspend fun searchUsers(search: UserDocumentSearch) = users.searchDocument(search).getOrThrow()
    override suspend fun saveRooms(documents: List<RoomDocument>) {
        rooms.saveDocument(documents).getOrThrow()
    }
    override suspend fun cleanRooms() {
        rooms.clean().getOrThrow()
    }
    override suspend fun searchRooms(search: RoomDocumentSearch) = rooms.searchDocument(search).getOrThrow()
    override suspend fun saveCommunities(documents: List<CommunityDocument>) {
        communities.saveDocument(
            documents,
        ).getOrThrow()
    }
    override suspend fun cleanCommunities() {
        communities.clean().getOrThrow()
    }
    override suspend fun searchCommunities(search: CommunityDocumentSearch) =
        communities.searchDocument(
        search,
    ).getOrThrow()
    override suspend fun saveMembers(documents: List<MemberDocument>) {
        members.saveDocument(documents).getOrThrow()
    }
    override suspend fun deleteMember(uid: Long, objectId: Long) {
        members.deleteDocument(uid, objectId).getOrThrow()
    }
    override suspend fun cleanMembers() {
        members.clean().getOrThrow()
    }
    override suspend fun searchMembers(search: MemberDocumentSearch) = members.searchDocument(search).getOrThrow()
    override suspend fun saveFiles(documents: List<FileDocument>) {
        files.saveDocument(documents).getOrThrow()
    }
    override suspend fun cleanFiles() {
        files.clean().getOrThrow()
    }
    override suspend fun searchFiles(search: FileDocumentSearch) = files.searchDocument(search).getOrThrow()
}

fun main() {
    val port = System.getenv("LUCENE_RPC_PORT")?.toIntOrNull() ?: 8821
    val service = LuceneRpcImpl(System.getenv("LUCENE_BASE_PATH") ?: "/data")
    embeddedServer(CIO, host = "0.0.0.0", port = port) {
        install(WebSockets) { maxFrameSize = Long.MAX_VALUE }
        install(Krpc)
        routing {
            get("/health") { call.respondText("ok") }
            rpc("/rpc") {
                rpcConfig { serialization { json() } }
                registerService<LuceneRpc> { service }
            }
        }
    }.start(wait = true)
}
