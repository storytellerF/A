package com.storyteller_f.a.client_lib

import com.storyteller_f.shared.SignInPack
import com.storyteller_f.shared.SignUpPack
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.obj.TopicSnapshotPack
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

suspend fun HttpClient.requestRoomInfo(id: PrimaryKey) = get("room/$id").body<RoomInfo>()

suspend fun HttpClient.requestRoomInfoByAid(aid: String) = get("room") {
    url {
        parameters.append("aid", aid)
    }
}.body<RoomInfo>()

suspend fun HttpClient.requestRoomKeys(id: PrimaryKey, nextId: PrimaryKey?, size: Int) =
    get("room/$id/pub-keys") {
        url {
            appendPagingQueryParams(size, nextId)
        }
    }.body<ServerResponse<Pair<PrimaryKey, String>>>()

suspend fun HttpClient.joinRoom(id: PrimaryKey) = post("room/$id/join")

suspend fun HttpClient.joinCommunity(id: PrimaryKey) = post("community/$id/join")

suspend fun HttpClient.getRoomTopics(
    roomId: PrimaryKey,
    nextTopicId: PrimaryKey?,
    size: Int,
) = get("room/$roomId/topics?start=${nextTopicId ?: 0}") {
    url {
        appendPagingQueryParams(size, nextTopicId)
    }
}.body<ServerResponse<TopicInfo>>()

suspend fun HttpClient.getCommunityInfo(id: PrimaryKey) = get("community/$id").body<CommunityInfo>()

suspend fun HttpClient.getCommunityInfoByAid(aid: String) = get("community", {
    url {
        parameters.append("aid", aid)
    }
}).body<CommunityInfo>()

suspend fun HttpClient.getCommunityTopics(communityId: PrimaryKey, nextTopicId: PrimaryKey?, size: Int) =
    get("community/$communityId/topics") {
        url {
            appendPagingQueryParams(size, nextTopicId)
        }
    }.body<ServerResponse<TopicInfo>>()

suspend fun HttpClient.getCommunityRooms(communityId: PrimaryKey, nextRoomId: PrimaryKey?, size: Int) =
    get("community/$communityId/rooms") {
        url {
            appendPagingQueryParams(size, nextRoomId)
        }
    }.body<ServerResponse<RoomInfo>>()

suspend fun HttpClient.getJoinCommunities(nextCommunityId: PrimaryKey?, size: Int) = get("community/joined") {
    url {
        appendPagingQueryParams(size, nextCommunityId)
    }
}.body<ServerResponse<CommunityInfo>>()

private fun URLBuilder.appendPagingQueryParams(size: Int, nextId: PrimaryKey?) {
    parameters.append("size", size.toString())
    if (nextId != null) {
        parameters.append("nextPageToken", nextId.toString())
    }
}

suspend fun HttpClient.getWorldTopics(nextTopicId: PrimaryKey?, size: Int) = get("world") {
    url {
        parameters.append("size", size.toString())
        if (nextTopicId != null) {
            parameters.append("nextPageToken", nextTopicId.toString())
        }
    }
}.body<ServerResponse<TopicInfo>>()

suspend fun HttpClient.getUserInfo(id: PrimaryKey) = get("user/$id").body<UserInfo>()

suspend fun HttpClient.updateUserInfo(newUserInfo: UserInfo) = post("user/update") {
    contentType(ContentType.Application.Json)
    setBody(newUserInfo)
}.body<Int>()

suspend fun HttpClient.getUserInfoByAid(aid: String) = get("user", {
    url {
        parameters.append("aid", aid)
    }
}).body<UserInfo>()

suspend fun HttpClient.getTopicTopics(topicId: PrimaryKey, nextTopicId: PrimaryKey?, size: Int) =
    get("topic/$topicId/topics") {
        url {
            appendPagingQueryParams(size, nextTopicId)
        }
    }.body<ServerResponse<TopicInfo>>()

suspend fun HttpClient.getTopicInfo(id: PrimaryKey) = get("topic/$id").body<TopicInfo>()

suspend fun HttpClient.getTopicInfoByAid(aid: String) = get("topic") {
    url {
        parameters.append("aid", aid)
    }
}.body<TopicInfo>()

suspend fun HttpClient.getJoinedRooms(size: Int, nextRoomId: PrimaryKey?) = get("room/joined") {
    url {
        appendPagingQueryParams(size, nextRoomId)
    }
}.body<ServerResponse<RoomInfo>>()

suspend fun HttpClient.createNewTopic(
    objectType: ObjectType,
    objectId: PrimaryKey,
    input: String
): HttpResponse {
    return post("topic") {
        contentType(ContentType.Application.Json)
        setBody(NewTopic(objectType, objectId, TopicContent.Plain(input)))
    }
}

suspend fun HttpClient.sign(
    isSignUp: Boolean,
    publicKey: String,
    signature: String,
    address: String
) = post(if (isSignUp) "sign_up" else "sign_in") {
    contentType(ContentType.Application.Json)
    if (isSignUp) {
        setBody(SignUpPack(publicKey, signature))
    } else {
        setBody(SignInPack(address, signature))
    }
}.body<UserInfo>()

suspend fun HttpClient.getData() = get("get_data").bodyAsText()

suspend fun HttpClient.getTopicSnapshot(topicId: PrimaryKey) = get("topic/$topicId/snapshot")

suspend fun HttpClient.verifySnapshot(pack: TopicSnapshotPack) = post("topic/verify-snapshot") {
    contentType(ContentType.Application.Json)
    setBody(pack)
}

suspend fun HttpClient.searchTopics(nextTopicId: PrimaryKey?, size: Int, word: List<String>) = get("/topic/search", {
    url {
        parameters.appendAll("word", word)
        appendPagingQueryParams(size, nextTopicId)
    }
}).body<ServerResponse<TopicInfo>>()
