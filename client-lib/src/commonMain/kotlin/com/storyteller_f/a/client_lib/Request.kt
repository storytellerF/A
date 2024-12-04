package com.storyteller_f.a.client_lib

import com.storyteller_f.shared.SignInPack
import com.storyteller_f.shared.SignUpPack
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

fun isAlreadyLogin(): Boolean {
    return LoginViewModel.isAlreadySignUp
}

suspend fun HttpClient.requestRoomInfo(id: PrimaryKey, alreadySignUp: Boolean) = get("rooms/$id") {
    url {
        if (alreadySignUp) parameters.append("fillJoinInfo", "true")
    }
}.body<RoomInfo>()

suspend fun HttpClient.requestRoomInfoByAid(aid: String, alreadySignUp: Boolean) = get("rooms") {
    url {
        if (alreadySignUp) parameters.append("fillJoinInfo", "true")
        parameters.append("aid", aid)
    }
}.body<RoomInfo>()

suspend fun HttpClient.requestRoomKeys(id: PrimaryKey, nextId: PrimaryKey?, size: Int) =
    get("rooms/$id/pub-keys") {
        url {
            appendPagingQueryParams(size, nextId)
        }
    }.body<ServerResponse<Pair<PrimaryKey, String>>>()

suspend fun HttpClient.joinRoom(id: PrimaryKey) = post("rooms/$id/join").body<RoomInfo>()

suspend fun HttpClient.joinCommunity(id: PrimaryKey) = post("communities/$id/join").body<CommunityInfo>()

suspend fun HttpClient.getRoomTopics(
    roomId: PrimaryKey,
    nextTopicId: PrimaryKey?,
    size: Int,
) = get("rooms/$roomId/topics") {
    url {
        appendPagingQueryParams(size, nextTopicId)
    }
}.body<ServerResponse<TopicInfo>>()

suspend fun HttpClient.getCommunityInfo(id: PrimaryKey, fillJoinInfo: Boolean = false) =
    get("communities/$id") {
        url {
            if (fillJoinInfo) {
                parameters.append("fillJoinInfo", "true")
            }
        }
    }.body<CommunityInfo>()

suspend fun HttpClient.getCommunityInfoByAid(aid: String, fillJoinInfo: Boolean = false) = get("communities") {
    url {
        if (fillJoinInfo) {
            parameters.append("fillJoinInfo", "true")
        }
        parameters.append("aid", aid)
    }
}.body<CommunityInfo>()

suspend fun HttpClient.getCommunityTopics(
    communityId: PrimaryKey,
    nextTopicId: PrimaryKey?,
    size: Int
) = searchTopics(nextTopicId, size, emptyList(), communityId, ObjectType.COMMUNITY)

suspend fun HttpClient.searchCommunity(
    nextCommunityId: PrimaryKey?,
    size: Int,
    joinStatusSearch: JoinStatusSearch,
    word: String?
) = get("communities/search") {
    url {
        word?.let { value -> parameters.append("word", value) }
        parameters.append("joinStatus", joinStatusSearch.name)
        appendPagingQueryParams(size, nextCommunityId)
    }
}.body<ServerResponse<CommunityInfo>>()

suspend fun HttpClient.searchCommunityMembers(
    communityId: PrimaryKey,
    nextCommunityId: PrimaryKey?,
    size: Int,
    word: String?
) = get("communities/$communityId/members") {
    url {
        word?.let { value -> parameters.append("word", value) }
        appendPagingQueryParams(size, nextCommunityId)
    }
}.body<ServerResponse<UserInfo>>()

suspend fun HttpClient.searchAllMembers(
    nextUserId: PrimaryKey?,
    size: Int,
    word: String?
) = get("users/search") {
    url {
        word?.let { value -> parameters.append("word", value) }
        appendPagingQueryParams(size, nextUserId)
    }
}.body<ServerResponse<UserInfo>>()

suspend fun HttpClient.searchRoomMembers(
    roomId: PrimaryKey,
    nextCommunityId: PrimaryKey?,
    size: Int,
    word: String?
) = get("rooms/$roomId/members") {
    url {
        word?.let { value -> parameters.append("word", value) }
        appendPagingQueryParams(size, nextCommunityId)
    }
}.body<ServerResponse<UserInfo>>()

private fun URLBuilder.appendPagingQueryParams(size: Int, nextId: PrimaryKey?) {
    parameters.append("size", size.toString())
    if (nextId != null) {
        parameters.append("nextPageToken", nextId.toString())
    }
}

private fun URLBuilder.appendPagingQueryParams(size: Int, nextId: String?) {
    parameters.append("size", size.toString())
    if (nextId != null) {
        parameters.append("nextPageToken", nextId)
    }
}

suspend fun HttpClient.getWorldTopics(nextTopicId: PrimaryKey?, size: Int, fillHasCommented: Boolean) = get("topics/recommend") {
    url {
        parameters.append("fillHasCommented", fillHasCommented.toString())
        appendPagingQueryParams(size, nextTopicId)
    }
}.body<ServerResponse<TopicInfo>>()

suspend fun HttpClient.getUserInfo(id: PrimaryKey) = get("users/$id").body<UserInfo>()

suspend fun HttpClient.updateUserInfo(newUserInfo: UserInfo) = post("users/update") {
    contentType(ContentType.Application.Json)
    setBody(newUserInfo)
}.body<Int>()

suspend fun HttpClient.getUserInfoByAid(aid: String) = get("users") {
    url {
        parameters.append("aid", aid)
    }
}.body<UserInfo>()

suspend fun HttpClient.getTopicTopics(topicId: PrimaryKey, nextTopicId: PrimaryKey?, size: Int) =
    get("topics/$topicId/topics") {
        url {
            appendPagingQueryParams(size, nextTopicId)
        }
    }.body<ServerResponse<TopicInfo>>()

suspend fun HttpClient.getTopicInfo(id: PrimaryKey) = get("topics/$id").body<TopicInfo>()

suspend fun HttpClient.getTopicInfoByAid(aid: String) = get("topics") {
    url {
        parameters.append("aid", aid)
    }
}.body<TopicInfo>()

suspend fun HttpClient.getJoinedRooms(
    size: Int,
    nextRoomId: PrimaryKey?,
    joinStatusSearch: JoinStatusSearch,
    word: String,
    communityId: PrimaryKey?
) = get("rooms/search") {
    url {
        parameters.append("word", word)
        parameters.append("joinStatus", joinStatusSearch.name)
        communityId?.let {
            parameters.append("community", it.toString())
        }
        appendPagingQueryParams(size, nextRoomId)
    }
}.body<ServerResponse<RoomInfo>>()

suspend fun HttpClient.createNewTopic(
    objectType: ObjectType,
    objectId: PrimaryKey,
    input: String
): HttpResponse {
    return post("topics") {
        contentType(ContentType.Application.Json)
        setBody(NewTopic(objectType, objectId, TopicContent.Plain(input)))
    }
}

suspend fun HttpClient.signUp(
    publicKey: String,
    signature: String,
) = post("sign_up") {
    contentType(ContentType.Application.Json)
    setBody(SignUpPack(publicKey, signature))
}.body<UserInfo>()

suspend fun HttpClient.signIn(
    address: String,
    signature: String
) = post("sign_in") {
    contentType(ContentType.Application.Json)
    setBody(SignInPack(address, signature))
}.body<UserInfo>()

suspend fun HttpClient.getData() = get("get_data").bodyAsText()

suspend fun HttpClient.getTopicSnapshot(topicId: PrimaryKey) = get("topics/$topicId/snapshot")

suspend fun HttpClient.searchTopics(
    nextTopicId: PrimaryKey?,
    size: Int,
    word: List<String>,
    parentId: PrimaryKey?,
    parentType: ObjectType?
) = get("/topics/search") {
    url {
        parentId?.let {
            parameters.append("parentId", it.toString())
        }
        parentType?.let {
            parameters.append("parentType", it.name)
        }
        if (isAlreadyLogin()) {
            parameters.append("fillHasCommented", "true")
        }
        parameters.appendAll("word", word)
        appendPagingQueryParams(size, nextTopicId)
    }
}.body<ServerResponse<TopicInfo>>()

suspend fun HttpClient.exitRoom(roomId: PrimaryKey) = post("rooms/$roomId/exit").body<RoomInfo>()

suspend fun HttpClient.exitCommunity(communityId: PrimaryKey) = post(
    "communities/$communityId/exit"
).body<CommunityInfo>()

suspend fun HttpClient.addReaction(topicId: PrimaryKey, emoji: String) = post("topics/${topicId}/reactions") {
    contentType(ContentType.Text.Plain)
    setBody(emoji)
}.body<ReactionInfo>()

suspend fun HttpClient.deleteReaction(emoji: String) = post("reactions/delete") {
    contentType(ContentType.Text.Plain)
    setBody(emoji)
}.body<Boolean>()

suspend fun HttpClient.getReactions(topicId: PrimaryKey) =
    get("topics/${topicId}/reactions") {
        url {
            if (isAlreadyLogin())
                parameters.append("fillHasReacted", "true")
        }
    }.body<ServerResponse<ReactionInfo>>()