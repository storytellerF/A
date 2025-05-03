package com.storyteller_f.a.client_lib

import com.storyteller_f.shared.SignInPack
import com.storyteller_f.shared.SignUpPack
import com.storyteller_f.shared.eciesEncrypt
import com.storyteller_f.shared.encryptData
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.*
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.ObjectType.*
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TitleStatus
import com.storyteller_f.shared.type.TitleType
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.internal.*
import io.ktor.utils.io.core.*

fun isAlreadyLogin(): Boolean {
    return SignInViewModel.currentIsAlreadySignUp
}

inline fun <R> serviceCatching(block: () -> R): Result<R> {
    val point = Exception()
    return try {
        val value = block()
        Result.success(value)
    } catch (e: Throwable) {
        point.initCauseBridge(e)
        Napier.e(point) {
            "serviceCatching"
        }
        Result.failure(e)
    }
}

suspend fun HttpClient.getRoomInfo(id: PrimaryKey) = serviceCatching {
    get("rooms/$id") {
        url {
            if (isAlreadyLogin()) parameters.append("fillJoinInfo", "true")
        }
    }.body<RoomInfo>()
}

suspend fun HttpClient.getRoomInfoByAid(aid: String) = serviceCatching {
    get("rooms") {
        url {
            if (isAlreadyLogin()) parameters.append("fillJoinInfo", "true")
            parameters.append("aid", aid)
        }
    }.body<RoomInfo>()
}

suspend fun HttpClient.requestRoomKeys(id: PrimaryKey, nextId: PrimaryKey?, size: Int) =
    serviceCatching {
        get("rooms/$id/pub-keys") {
            url {
                appendPagingQueryParams(size, nextId)
            }
        }.body<ServerResponse<Pair<PrimaryKey, String>>>()
    }

suspend fun HttpClient.joinRoom(id: PrimaryKey) = serviceCatching {
    post("rooms/$id/join").body<RoomInfo>()
}

suspend fun HttpClient.joinCommunity(id: PrimaryKey) = serviceCatching {
    post("communities/$id/join").body<CommunityInfo>()
}

suspend fun HttpClient.getRoomTopics(
    roomId: PrimaryKey,
    nextTopicId: PrimaryKey?,
    size: Int,
    pinType: TopicPinSearch = TopicPinSearch.UNSPECIFIED
) = serviceCatching {
    get("rooms/$roomId/topics") {
        url {
            if (isAlreadyLogin()) {
                parameters.append("fillHasCommented", "true")
            }
            parameters.append("pinType", pinType.name)
            appendPagingQueryParams(size, nextTopicId)
        }
    }.body<ServerResponse<TopicInfo>>()
}

suspend fun HttpClient.getCommunityTopics(
    communityId: PrimaryKey,
    nextCommunityId: PrimaryKey?,
    size: Int,
    pinType: TopicPinSearch = TopicPinSearch.UNSPECIFIED
) = serviceCatching {
    get("communities/$communityId/topics") {
        url {
            if (isAlreadyLogin()) {
                parameters.append("fillHasCommented", "true")
            }
            parameters.append("pinType", pinType.name)
            appendPagingQueryParams(size, nextCommunityId)
        }
    }.body<ServerResponse<TopicInfo>>()
}

suspend fun HttpClient.getUserTopics(
    userId: PrimaryKey,
    nextTopicId: PrimaryKey?,
    size: Int,
    pinType: TopicPinSearch = TopicPinSearch.UNSPECIFIED
) = serviceCatching {
    get("users/$userId/topics") {
        url {
            if (isAlreadyLogin()) {
                parameters.append("fillHasCommented", "true")
            }
            parameters.append("pinType", pinType.name)
            appendPagingQueryParams(size, nextTopicId)
        }
    }.body<ServerResponse<TopicInfo>>()
}

suspend fun HttpClient.getCommunityInfo(id: PrimaryKey) =
    serviceCatching {
        get("communities/$id") {
            url {
                if (isAlreadyLogin()) {
                    parameters.append("fillJoinInfo", "true")
                }
            }
        }.body<CommunityInfo>()
    }

suspend fun HttpClient.getCommunityInfoByAid(aid: String, fillJoinInfo: Boolean = false) = serviceCatching {
    get("communities") {
        url {
            if (fillJoinInfo) {
                parameters.append("fillJoinInfo", "true")
            }
            parameters.append("aid", aid)
        }
    }.body<CommunityInfo>()
}

suspend fun HttpClient.searchCommunity(
    size: Int,
    joinStatusSearch: JoinStatusSearch,
    word: String? = null,
    target: PrimaryKey? = null,
    nextCommunityId: PrimaryKey? = null,
    hasPosterSearch: PosterSearch? = null,
) = serviceCatching {
    get("communities/search") {
        url {
            word?.let { value -> parameters.append("word", value) }
            target?.let { value -> parameters.append("target", value.toString()) }
            hasPosterSearch?.let { value -> parameters.append("hasPoster", value.name) }
            parameters.append("joinStatus", joinStatusSearch.name)
            appendPagingQueryParams(size, nextCommunityId)
        }
    }.body<ServerResponse<CommunityInfo>>()
}

suspend fun HttpClient.searchCommunityMembers(
    communityId: PrimaryKey,
    nextCommunityId: PrimaryKey?,
    size: Int,
    word: String?
) = serviceCatching {
    get("communities/$communityId/members") {
        url {
            word?.let { value -> parameters.append("word", value) }
            appendPagingQueryParams(size, nextCommunityId)
        }
    }.body<ServerResponse<UserInfo>>()
}

suspend fun HttpClient.searchAllMembers(
    nextUserId: PrimaryKey?,
    size: Int,
    word: String?
) = serviceCatching {
    get("users/search") {
        url {
            word?.let { value -> parameters.append("word", value) }
            appendPagingQueryParams(size, nextUserId)
        }
    }.body<ServerResponse<UserInfo>>()
}

suspend fun HttpClient.searchRoomMembers(
    roomId: PrimaryKey,
    nextCommunityId: PrimaryKey?,
    size: Int,
    word: String?
) = serviceCatching {
    get("rooms/$roomId/members") {
        url {
            word?.let { value -> parameters.append("word", value) }
            appendPagingQueryParams(size, nextCommunityId)
        }
    }.body<ServerResponse<UserInfo>>()
}

private fun URLBuilder.appendPagingQueryParams(size: Int, nextId: PrimaryKey?) {
    parameters.append("size", size.toString())
    if (nextId != null) {
        parameters.append("nextPageToken", nextId.toString())
    }
}

suspend fun HttpClient.getRecommendTopics(nextTopicId: PrimaryKey?, size: Int) =
    serviceCatching {
        get(
            "topics/recommend"
        ) {
            url {
                if (isAlreadyLogin()) {
                    parameters.append("fillHasCommented", "true")
                }
                appendPagingQueryParams(size, nextTopicId)
            }
        }.body<ServerResponse<TopicInfo>>()
    }

suspend fun HttpClient.getUserInfo(id: PrimaryKey) = serviceCatching {
    get("users/$id").body<UserInfo>()
}

suspend fun HttpClient.updateUserInfo(newInfo: UpdateUserBody) = serviceCatching {
    post("users/update") {
        contentType(ContentType.Application.Json)
        setBody(newInfo)
    }.body<UserInfo>()
}

suspend fun HttpClient.getUserInfoByAid(aid: String) = serviceCatching {
    get("users") {
        url {
            parameters.append("aid", aid)
        }
    }.body<UserInfo>()
}

suspend fun HttpClient.getTopicTopics(
    topicId: PrimaryKey,
    nextTopicId: PrimaryKey?,
    size: Int,
    pinType: TopicPinSearch
) =
    serviceCatching {
        get("topics/$topicId/topics") {
            url {
                if (isAlreadyLogin()) {
                    parameters.append("fillHasCommented", "true")
                }
                parameters.append("pinType", pinType.name)
                appendPagingQueryParams(size, nextTopicId)
            }
        }.body<ServerResponse<TopicInfo>>()
    }

suspend fun HttpClient.getTopicInfo(id: PrimaryKey) = serviceCatching {
    get("topics/$id").body<TopicInfo>()
}

suspend fun HttpClient.getTopicInfoByAid(aid: String) = serviceCatching {
    get("topics") {
        url {
            parameters.append("aid", aid)
        }
    }.body<TopicInfo>()
}

suspend fun HttpClient.searchRooms(
    size: Int,
    nextRoomId: PrimaryKey?,
    joinStatusSearch: JoinStatusSearch,
    word: String?,
    communityId: PrimaryKey?
) = serviceCatching {
    get("rooms/search") {
        url {
            if (!(word.isNullOrBlank())) {
                parameters.append("word", word)
            }
            parameters.append("joinStatus", joinStatusSearch.name)
            communityId?.let {
                parameters.append("community", it.toString())
            }
            appendPagingQueryParams(size, nextRoomId)
        }
    }.body<ServerResponse<RoomInfo>>()
}

suspend fun HttpClient.createNewTopic(
    objectType: ObjectType,
    objectId: PrimaryKey,
    input: String
) = serviceCatching {
    post("topics") {
        contentType(ContentType.Application.Json)
        setBody(NewTopic(objectType, objectId, input))
    }.body<TopicInfo>()
}

suspend fun HttpClient.signUp(
    publicKey: String,
    signature: String,
) = serviceCatching {
    post("accounts/sign_up") {
        contentType(ContentType.Application.Json)
        setBody(SignUpPack(publicKey, signature))
    }.body<UserInfo>()
}

suspend fun HttpClient.signIn(
    address: String,
    signature: String
) = serviceCatching {
    post("accounts/sign_in") {
        contentType(ContentType.Application.Json)
        setBody(SignInPack(address, signature))
    }.body<UserInfo>()
}

suspend fun HttpClient.getData() = serviceCatching {
    get("accounts/get_data").bodyAsText()
}

suspend fun HttpClient.getTopicSnapshot(topicId: PrimaryKey) = serviceCatching {
    get("topics/$topicId/snapshot").body<MediaInfo>()
}

suspend fun HttpClient.searchTopics(
    size: Int,
    word: List<String>,
    parentId: PrimaryKey? = null,
    parentType: ObjectType? = null,
    nextTopicId: PrimaryKey? = null
) = serviceCatching {
    get("topics/search") {
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
}

suspend fun HttpClient.exitRoom(roomId: PrimaryKey) = serviceCatching {
    post("rooms/$roomId/exit").body<RoomInfo>()
}

suspend fun HttpClient.exitCommunity(communityId: PrimaryKey) = serviceCatching {
    post(
        "communities/$communityId/exit"
    ).body<CommunityInfo>()
}

suspend fun HttpClient.addReaction(topicId: PrimaryKey, emoji: String) = serviceCatching {
    post("topics/$topicId/reactions") {
        contentType(ContentType.Application.Json)
        setBody(NewReaction(emoji))
    }.body<ReactionInfo>()
}

suspend fun HttpClient.deleteReaction(emoji: String, objectId: PrimaryKey) = serviceCatching {
    post("reactions/delete") {
        contentType(ContentType.Application.Json)
        setBody(DeleteReaction(emoji, objectId))
    }.body<Boolean>()
}

suspend fun HttpClient.getReactions(topicId: PrimaryKey) =
    serviceCatching {
        get("topics/$topicId/reactions") {
            url {
                if (isAlreadyLogin()) {
                    parameters.append("fillHasReacted", "true")
                }
            }
        }.body<ServerResponse<ReactionInfo>>()
    }

suspend fun HttpClient.signOut() = serviceCatching {
    post("accounts/sign_out")
}

suspend fun HttpClient.getMediaList(objectId: PrimaryKey, objectType: ObjectType, nextId: PrimaryKey?, size: Int) =
    serviceCatching {
        get("amedia") {
            url {
                parameters.append("objectId", objectId.toString())
                parameters.append("objectType", objectType.name)

                appendPagingQueryParams(size, nextId)
            }
        }.body<ServerResponse<MediaInfo>>()
    }

suspend fun HttpClient.getAllMediaList(objectId: PrimaryKey, objectType: ObjectType) =
    serviceCatching {
        get("amedia/all") {
            url {
                parameters.append("objectId", objectId.toString())
                parameters.append("objectType", objectType.name)
            }
        }.body<ServerResponse<MediaInfo>>()
    }

suspend fun HttpClient.upload(
    objectTuple: ObjectTuple,
    size: Long,
    name: String,
    contentType: ContentType,
    block: () -> Input
) = serviceCatching {
    post("amedia/upload") {
        appendObjectTuple(objectTuple)
        setBody(
            MultiPartFormDataContent(
                formData {
                    append("description", "amedia")
                    appendInput("file", Headers.build {
                        append(HttpHeaders.ContentType, contentType)
                        append(HttpHeaders.ContentDisposition, "filename=\"$name\"")
                    }, size, block)
                },
                boundary = "WebAppBoundary"
            )
        )
        onUpload { bytesSentTotal, contentLength ->
            println("Sent $bytesSentTotal bytes from $contentLength")
        }
    }.body<ServerResponse<MediaInfo>>()
}

suspend fun HttpClient.copy(objectTuple: ObjectTuple, noPrefixName: String) =
    serviceCatching {
        post("amedia/copy") {
            appendObjectTuple(objectTuple)

            contentType(ContentType.Application.Json)
            setBody(NewMedia(noPrefixName))
        }.body<ServerResponse<MediaInfo>>()
    }

@OptIn(ExperimentalStdlibApi::class)
suspend fun DefaultClientWebSocketSession.sendMessage(
    parentTarget: ObjectTuple,
    isPrivate: Boolean,
    input: String,
    keyData: List<Pair<PrimaryKey, String>>,
) {
    val content = if (isPrivate) {
        val (encrypted, aes) = encryptData(input).getOrThrow()
        TopicContent.Encrypted(encrypted.toHexString(), keyData.associate {
            it.first to eciesEncrypt(it.second, aes).getOrThrow().toHexString()
        })
    } else {
        TopicContent.Plain(input)
    }
    val message: RoomFrame = RoomFrame.Message(
        NewRoomTopic(
            parentTarget.objectType,
            parentTarget.objectId,
            content
        )
    )
    sendSerialized(message)
}

suspend fun HttpClient.userTitles(
    uid: PrimaryKey,
    nextId: PrimaryKey?,
    size: Int,
    searchType: TitleSearchType,
    status: TitleStatus? = null,
    type: TitleType? = null,
    scopeId: PrimaryKey? = null
) = serviceCatching {
    get("users/$uid/titles") {
        url {
            parameters.append("searchType", searchType.name)
            if (status != null) {
                parameters.append("status", status.name)
            }
            if (type != null) {
                parameters.append("type", type.name)
            }
            if (scopeId != null) {
                parameters.append("scopeId", scopeId.toString())
            }
            appendPagingQueryParams(size, nextId)
        }
    }.body<ServerResponse<TitleInfo>>()
}

suspend fun HttpClient.createTitle(newTitle: NewTitle) = serviceCatching {
    post("titles") {
        contentType(ContentType.Application.Json)
        setBody(newTitle)
    }.body<TitleInfo>()
}

suspend fun HttpClient.createCommunity(newCommunity: NewCommunity) = serviceCatching {
    post("communities") {
        contentType(ContentType.Application.Json)
        setBody(newCommunity)
    }.body<CommunityInfo>()
}

suspend fun HttpClient.createRoom(newRoom: NewRoom) = serviceCatching {
    post("rooms") {
        contentType(ContentType.Application.Json)
        setBody(newRoom)
    }.body<RoomInfo>()
}

suspend fun HttpClient.pinTopic(topicId: PrimaryKey) = serviceCatching {
    post("topics/$topicId/pin") {
    }.body<TopicInfo>()
}

suspend fun HttpClient.unpinTopic(topicId: PrimaryKey) = serviceCatching {
    post("topics/$topicId/unpin") {
    }.body<TopicInfo>()
}

suspend fun HttpClient.updateCommunityInfo(newInfo: UpdateCommunityBody, id: PrimaryKey) = serviceCatching {
    post("communities/$id") {
        contentType(ContentType.Application.Json)
        setBody(newInfo)
    }.body<CommunityInfo>()
}

suspend fun HttpClient.updateRoomInfo(newInfo: UpdateRoomBody, id: PrimaryKey) = serviceCatching {
    post("rooms/$id") {
        contentType(ContentType.Application.Json)
        setBody(newInfo)
    }.body<RoomInfo>()
}

suspend fun HttpClient.getTopicList(
    type: ObjectType?,
    id: PrimaryKey,
    loadKey: PrimaryKey?,
    size: Int,
    pinSearch: TopicPinSearch
) = when (type) {
    ROOM -> getRoomTopics(id, loadKey, size, pinSearch)
    COMMUNITY -> getCommunityTopics(id, loadKey, size, pinSearch)
    USER -> getUserTopics(id, loadKey, size, pinSearch)
    TOPIC -> getTopicTopics(id, loadKey, size, pinSearch)
    else -> Result.failure(IllegalArgumentException("unrecognized $type"))
}

private fun HttpRequestBuilder.appendObjectTuple(objectTuple: ObjectTuple) {
    url {
        parameters.append("objectId", objectTuple.objectId.toString())
        parameters.append("objectType", objectTuple.objectType.name)
    }
}

suspend fun HttpClient.addReadLog(info: UpdateUserRead): Result<HttpResponse> {
    return runCatching {
        post("users/read") {
            contentType(ContentType.Application.Json)
            setBody(info)
        }
    }
}
