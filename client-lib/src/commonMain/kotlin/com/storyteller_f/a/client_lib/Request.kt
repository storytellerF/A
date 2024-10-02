package com.storyteller_f.a.client_lib

import com.storyteller_f.shared.SignInPack
import com.storyteller_f.shared.SignUpPack
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.obj.TopicSnapshotPack
import com.storyteller_f.shared.type.OKey
import com.storyteller_f.shared.type.ObjectType
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

suspend fun HttpClient.requestRoomInfo(id: OKey) = get("/room/$id").body<RoomInfo>()
suspend fun HttpClient.requestRoomKeys(id: OKey) =
    get("/room/$id/pub-keys").body<ServerResponse<Pair<OKey, String>>>()

suspend fun HttpClient.joinRoom(id: OKey) = post("/room/$id/join")
suspend fun HttpClient.joinCommunity(id: OKey) = post("/community/$id/join")

suspend fun HttpClient.getRoomTopics(
    roomId: OKey,
    loadKey: OKey?
) = get("/room/$roomId/topics?start=${loadKey ?: 0}").body<ServerResponse<TopicInfo>>()

suspend fun HttpClient.getCommunityInfo(id: OKey) = get("/community/$id").body<CommunityInfo>()
suspend fun HttpClient.getCommunityTopics(communityId: OKey, size: Int) =
    get("/community/$communityId/topics?size=$size").body<ServerResponse<TopicInfo>>()

suspend fun HttpClient.getCommunityRooms(communityId: OKey) =
    get("/community/$communityId/rooms").body<ServerResponse<RoomInfo>>()

suspend fun HttpClient.getJoinCommunities() = get("/community/joined").body<ServerResponse<CommunityInfo>>()
suspend fun HttpClient.getWorldTopics() = get("/world").body<ServerResponse<TopicInfo>>()
suspend fun HttpClient.getUserInfo(id: OKey) = get("/user/$id").body<UserInfo>()

suspend fun HttpClient.getTopicTopics(topicId: OKey) =
   get("/topic/$topicId/topics").body<ServerResponse<TopicInfo>>()

suspend fun HttpClient.getTopicInfo(id: OKey) = get("/topic/$id").body<TopicInfo>()
suspend fun HttpClient.getJoinedRooms() = get("/room/joined").body<ServerResponse<RoomInfo>>()

suspend fun HttpClient.createNewTopic(
    objectType: ObjectType,
    objectId: OKey,
    input: String
): HttpResponse {
    return post("/topic") {
        contentType(ContentType.Application.Json)
        setBody(NewTopic(objectType, objectId, TopicContent.Plain(input)))
    }
}

suspend fun HttpClient.sign(
    isSignUp: Boolean,
    publicKey: String,
    signature: String,
    address: String
) = post(if (isSignUp) "/sign_up" else "/sign_in") {
    contentType(ContentType.Application.Json)
    if (isSignUp) {
        setBody(SignUpPack(publicKey, signature))
    } else {
        setBody(SignInPack(address, signature))
    }
}.body<UserInfo>()

suspend fun HttpClient.getData() = get("/get_data").bodyAsText()

suspend fun HttpClient.getTopicSnapshot(topicId: OKey) = get("/topic/${topicId}/snapshot")

suspend fun HttpClient.verifySnapshot(pack: TopicSnapshotPack) = post("/topic/verify-snapshot") {
    contentType(ContentType.Application.Json)
    setBody(pack)
}
