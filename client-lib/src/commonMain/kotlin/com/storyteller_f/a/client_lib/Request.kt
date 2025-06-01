package com.storyteller_f.a.client_lib

import com.storyteller_f.shared.SignInPack
import com.storyteller_f.shared.SignUpPack
import com.storyteller_f.shared.eciesEncrypt
import com.storyteller_f.shared.encryptData
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.*
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.ObjectType.*
import com.storyteller_f.shared.type.PosterSearch
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TitleSearchType
import com.storyteller_f.shared.type.TitleStatus
import com.storyteller_f.shared.type.TitleType
import com.storyteller_f.shared.type.TopicPinSearch
import io.github.aakira.napier.Napier
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.core.*

inline fun <R> serviceCatching(block: () -> R): Result<R> {
    return try {
        val value = block()
        Result.success(value)
    } catch (e: Throwable) {
        if (e is ServerErrorException && e.status == HttpStatusCode.Unauthorized) return Result.failure(e)
        val point = Exception(e)
        Napier.e(point) {
            "serviceCatching"
        }
        Result.failure(point)
    }
}

private fun URLBuilder.appendPagingQueryParams(size: Int, nextId: PrimaryKey?) {
    parameters.append("size", size.toString())
    if (nextId != null) {
        parameters.append("nextPageToken", nextId.toString())
    }
}

private fun URLBuilder.appendPagingQueryParams(size: Int, nextCursor: String?) {
    parameters.append("size", size.toString())
    if (nextCursor != null) {
        parameters.append("nextPageToken", nextCursor)
    }
}

private fun HttpRequestBuilder.appendObjectTuple(objectTuple: ObjectTuple) {
    url {
        parameters.append("objectId", objectTuple.objectId.toString())
        parameters.append("objectType", objectTuple.objectType.name)
    }
}

suspend fun SessionManager.getRoomInfo(id: PrimaryKey) = with(client) {
    serviceCatching {
        get("rooms/$id") {
            url {
                if (currentIsAlreadySignUp) parameters.append("fillJoinInfo", "true")
            }
        }.body<RoomInfo>()
    }
}

suspend fun SessionManager.getRoomInfoByAid(aid: String) = serviceCatching {
    client.get("rooms/aid") {
        url {
            if (currentIsAlreadySignUp) parameters.append("fillJoinInfo", "true")
            parameters.append("aid", aid)
        }
    }.body<RoomInfo>()
}

suspend fun SessionManager.requestRoomKeys(id: PrimaryKey, nextId: PrimaryKey?, size: Int) =
    serviceCatching {
        client.get("rooms/$id/pub-keys") {
            url {
                appendPagingQueryParams(size, nextId)
            }
        }.body<ServerResponse<UserPubKeyInfo>>()
    }

suspend fun SessionManager.joinRoom(id: PrimaryKey) = serviceCatching {
    client.post("rooms/$id/join").body<RoomInfo>()
}

suspend fun SessionManager.joinCommunity(id: PrimaryKey) = serviceCatching {
    client.post("communities/$id/join").body<CommunityInfo>()
}

suspend fun SessionManager.getRoomTopics(
    roomId: PrimaryKey,
    nextTopicId: PrimaryKey?,
    size: Int,
    pinType: TopicPinSearch = TopicPinSearch.UNSPECIFIED
) = serviceCatching {
    client.get("rooms/$roomId/topics") {
        url {
            if (currentIsAlreadySignUp) {
                parameters.append("fillHasCommented", "true")
            }
            parameters.append("pinType", pinType.name)
            appendPagingQueryParams(size, nextTopicId)
        }
    }.body<ServerResponse<TopicInfo>>()
}

suspend fun SessionManager.getCommunityTopics(
    communityId: PrimaryKey,
    nextCommunityId: PrimaryKey?,
    size: Int,
    pinType: TopicPinSearch = TopicPinSearch.UNSPECIFIED
) = serviceCatching {
    client.get("communities/$communityId/topics") {
        url {
            if (currentIsAlreadySignUp) {
                parameters.append("fillHasCommented", "true")
            }
            parameters.append("pinType", pinType.name)
            appendPagingQueryParams(size, nextCommunityId)
        }
    }.body<ServerResponse<TopicInfo>>()
}

suspend fun SessionManager.getUserTopics(
    userId: PrimaryKey,
    nextTopicId: PrimaryKey?,
    size: Int,
    pinType: TopicPinSearch = TopicPinSearch.UNSPECIFIED
) = serviceCatching {
    client.get("users/$userId/topics") {
        url {
            if (currentIsAlreadySignUp) {
                parameters.append("fillHasCommented", "true")
            }
            parameters.append("pinType", pinType.name)
            appendPagingQueryParams(size, nextTopicId)
        }
    }.body<ServerResponse<TopicInfo>>()
}

suspend fun SessionManager.getCommunityInfo(id: PrimaryKey) =
    serviceCatching {
        client.get("communities/$id") {
            url {
                if (currentIsAlreadySignUp) {
                    parameters.append("fillJoinInfo", "true")
                }
            }
        }.body<CommunityInfo>()
    }

suspend fun SessionManager.getCommunityInfoByAid(aid: String, fillJoinInfo: Boolean = false) = serviceCatching {
    client.get("communities/aid") {
        url {
            if (fillJoinInfo) {
                parameters.append("fillJoinInfo", "true")
            }
            parameters.append("aid", aid)
        }
    }.body<CommunityInfo>()
}

suspend fun SessionManager.searchCommunity(
    size: Int,
    joinStatusSearch: JoinStatusSearch,
    word: String? = null,
    target: PrimaryKey? = null,
    nextCommunityId: PrimaryKey? = null,
    hasPosterSearch: PosterSearch? = null,
) = serviceCatching {
    client.get("communities/search") {
        url {
            word?.let { value -> parameters.append("word", value) }
            target?.let { value -> parameters.append("target", value.toString()) }
            hasPosterSearch?.let { value -> parameters.append("hasPoster", value.name) }
            parameters.append("joinStatus", joinStatusSearch.name)
            appendPagingQueryParams(size, nextCommunityId)
        }
    }.body<ServerResponse<CommunityInfo>>()
}

suspend fun SessionManager.searchCommunityMembers(
    communityId: PrimaryKey,
    nextCommunityId: PrimaryKey?,
    size: Int,
    word: String?
) = serviceCatching {
    client.get("communities/$communityId/members") {
        url {
            word?.let { value -> parameters.append("word", value) }
            appendPagingQueryParams(size, nextCommunityId)
        }
    }.body<ServerResponse<UserInfo>>()
}

suspend fun SessionManager.searchAllMembers(
    nextUserId: PrimaryKey?,
    size: Int,
    word: String?
) = serviceCatching {
    client.get("users/search") {
        url {
            word?.let { value -> parameters.append("word", value) }
            appendPagingQueryParams(size, nextUserId)
        }
    }.body<ServerResponse<UserInfo>>()
}

suspend fun SessionManager.searchRoomMembers(
    roomId: PrimaryKey,
    nextCommunityId: PrimaryKey?,
    size: Int,
    word: String?
) = serviceCatching {
    client.get("rooms/$roomId/members") {
        url {
            word?.let { value -> parameters.append("word", value) }
            appendPagingQueryParams(size, nextCommunityId)
        }
    }.body<ServerResponse<UserInfo>>()
}

suspend fun SessionManager.getRecommendTopics(nextTopicId: PrimaryKey?, size: Int) =
    serviceCatching {
        client.get(
            "topics/recommend"
        ) {
            url {
                if (currentIsAlreadySignUp) {
                    parameters.append("fillHasCommented", "true")
                }
                appendPagingQueryParams(size, nextTopicId)
            }
        }.body<ServerResponse<TopicInfo>>()
    }

suspend fun SessionManager.getUserInfo(id: PrimaryKey) = serviceCatching {
    client.get("users/$id").body<UserInfo>()
}

suspend fun SessionManager.updateUserInfo(newInfo: UpdateUserBody) = serviceCatching {
    client.post("users/update") {
        contentType(ContentType.Application.Json)
        setBody(newInfo)
    }.body<UserInfo>()
}

suspend fun SessionManager.getUserInfoByAid(aid: String) = serviceCatching {
    client.get("users/aid") {
        url {
            parameters.append("aid", aid)
        }
    }.body<UserInfo>()
}

suspend fun SessionManager.getTopicTopics(
    topicId: PrimaryKey,
    nextTopicId: PrimaryKey?,
    size: Int,
    pinType: TopicPinSearch
) =
    serviceCatching {
        client.get("topics/$topicId/topics") {
            url {
                if (currentIsAlreadySignUp) {
                    parameters.append("fillHasCommented", "true")
                }
                parameters.append("pinType", pinType.name)
                appendPagingQueryParams(size, nextTopicId)
            }
        }.body<ServerResponse<TopicInfo>>()
    }

suspend fun SessionManager.getTopicInfo(id: PrimaryKey) = serviceCatching {
    client.get("topics/$id").body<TopicInfo>()
}

suspend fun SessionManager.getTopicInfoByAid(aid: String) = serviceCatching {
    client.get("topics/aid") {
        url {
            parameters.append("aid", aid)
        }
    }.body<TopicInfo>()
}

suspend fun SessionManager.searchRooms(
    size: Int,
    nextRoomId: PrimaryKey?,
    joinStatusSearch: JoinStatusSearch,
    word: String?,
    communityId: PrimaryKey?
) = serviceCatching {
    client.get("rooms/search") {
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

suspend fun SessionManager.createNewTopic(
    objectType: ObjectType,
    objectId: PrimaryKey,
    input: String
) = serviceCatching {
    client.post("topics") {
        contentType(ContentType.Application.Json)
        setBody(NewTopic(objectType, objectId, input))
    }.body<TopicInfo>()
}

suspend fun SessionManager.signUp(
    publicKey: String,
    signature: String,
) = serviceCatching {
    client.post("accounts/sign_up") {
        contentType(ContentType.Application.Json)
        setBody(SignUpPack(publicKey, signature))
    }.body<UserInfo>()
}

suspend fun SessionManager.signIn(
    address: String,
    signature: String
) = serviceCatching {
    client.post("accounts/sign_in") {
        contentType(ContentType.Application.Json)
        setBody(SignInPack(address, signature))
    }.body<UserInfo>()
}

suspend fun SessionManager.getData() = serviceCatching {
    client.get("accounts/get_data").bodyAsText()
}

suspend fun SessionManager.getTopicSnapshot(topicId: PrimaryKey) = serviceCatching {
    client.get("topics/$topicId/snapshot").body<MediaInfo>()
}

suspend fun SessionManager.searchTopics(
    size: Int,
    word: List<String>,
    parentId: PrimaryKey? = null,
    parentType: ObjectType? = null,
    nextTopicId: PrimaryKey? = null
) = serviceCatching {
    client.get("topics/search") {
        url {
            parentId?.let {
                parameters.append("parentId", it.toString())
            }
            parentType?.let {
                parameters.append("parentType", it.name)
            }
            if (currentIsAlreadySignUp) {
                parameters.append("fillHasCommented", "true")
            }
            parameters.appendAll("word", word)
            appendPagingQueryParams(size, nextTopicId)
        }
    }.body<ServerResponse<TopicInfo>>()
}

suspend fun SessionManager.exitRoom(roomId: PrimaryKey) = serviceCatching {
    client.post("rooms/$roomId/exit").body<RoomInfo>()
}

suspend fun SessionManager.exitCommunity(communityId: PrimaryKey) = serviceCatching {
    client.post(
        "communities/$communityId/exit"
    ).body<CommunityInfo>()
}

suspend fun SessionManager.addReaction(topicId: PrimaryKey, emoji: String) = serviceCatching {
    client.post("topics/$topicId/reactions") {
        contentType(ContentType.Application.Json)
        setBody(NewReaction(emoji))
    }.body<ReactionInfo>()
}

suspend fun SessionManager.deleteReaction(emoji: String, objectId: PrimaryKey) = serviceCatching {
    client.post("reactions/delete") {
        contentType(ContentType.Application.Json)
        setBody(DeleteReaction(emoji, objectId))
    }.body<Boolean>()
}

suspend fun SessionManager.getReactions(topicId: PrimaryKey, size: Int, nextCursor: String? = null) =
    serviceCatching {
        client.get("topics/$topicId/reactions") {
            url {
                if (currentIsAlreadySignUp) {
                    parameters.append("fillHasReacted", "true")
                }
                appendPagingQueryParams(size, nextCursor)
            }
        }.body<ServerResponse<ReactionInfo>>()
    }

suspend fun SessionManager.signOut() = serviceCatching {
    client.post("accounts/sign_out")
}

suspend fun SessionManager.getMediaList(objectId: PrimaryKey, objectType: ObjectType, nextId: PrimaryKey?, size: Int) =
    serviceCatching {
        client.get("amedia") {
            url {
                parameters.append("objectId", objectId.toString())
                parameters.append("objectType", objectType.name)

                appendPagingQueryParams(size, nextId)
            }
        }.body<ServerResponse<MediaInfo>>()
    }

suspend fun SessionManager.getAllMediaList(objectId: PrimaryKey, objectType: ObjectType) =
    serviceCatching {
        client.get("amedia/all") {
            url {
                parameters.append("objectId", objectId.toString())
                parameters.append("objectType", objectType.name)
            }
        }.body<ServerResponse<MediaInfo>>()
    }

data class UploadData(val size: Long, val name: String, val contentType: ContentType)

suspend fun SessionManager.upload(
    objectTuple: ObjectTuple,
    data: UploadData,
    block: () -> Input
) = serviceCatching {
    client.post("amedia/upload") {
        appendObjectTuple(objectTuple)
        setBody(
            MultiPartFormDataContent(
                formData {
                    append("description", "amedia")
                    appendInput("file", Headers.build {
                        append(HttpHeaders.ContentType, data.contentType)
                        append(HttpHeaders.ContentDisposition, "filename=\"${data.name}\"")
                        append(HttpHeaders.ContentLength, data.size)
                    }, data.size, block)
                },
                boundary = "WebAppBoundary"
            )
        )
        onUpload { bytesSentTotal, contentLength ->
            println("Sent $bytesSentTotal bytes from $contentLength")
        }
    }.body<ServerResponse<MediaInfo>>()
}

suspend fun SessionManager.copy(objectTuple: ObjectTuple, noPrefixName: String) =
    serviceCatching {
        client.post("amedia/copy") {
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
    keyData: List<UserPubKeyInfo>,
) {
    val content = if (isPrivate) {
        val (encrypted, aes) = encryptData(input).getOrThrow()
        TopicContent.Encrypted(encrypted.toHexString(), keyData.associate {
            it.id to eciesEncrypt(it.pubKey, aes).getOrThrow().toHexString()
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

suspend fun SessionManager.userTitles(
    uid: PrimaryKey,
    nextId: PrimaryKey?,
    size: Int,
    searchType: TitleSearchType,
    status: TitleStatus? = null,
    type: TitleType? = null,
    scopeId: PrimaryKey? = null
) = serviceCatching {
    client.get("users/$uid/titles") {
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

suspend fun SessionManager.createTitle(newTitle: NewTitle) = serviceCatching {
    client.post("titles") {
        contentType(ContentType.Application.Json)
        setBody(newTitle)
    }.body<TitleInfo>()
}

suspend fun SessionManager.createCommunity(newCommunity: NewCommunity) = serviceCatching {
    client.post("communities") {
        contentType(ContentType.Application.Json)
        setBody(newCommunity)
    }.body<CommunityInfo>()
}

suspend fun SessionManager.createRoom(newRoom: NewRoom) = serviceCatching {
    client.post("rooms") {
        contentType(ContentType.Application.Json)
        setBody(newRoom)
    }.body<RoomInfo>()
}

suspend fun SessionManager.pinTopic(topicId: PrimaryKey) = serviceCatching {
    client.post("topics/$topicId/pin") {
    }.body<TopicInfo>()
}

suspend fun SessionManager.unpinTopic(topicId: PrimaryKey) = serviceCatching {
    client.post("topics/$topicId/unpin") {
    }.body<TopicInfo>()
}

suspend fun SessionManager.updateCommunityInfo(id: PrimaryKey, newInfo: UpdateCommunityBody) = serviceCatching {
    client.post("communities/$id") {
        contentType(ContentType.Application.Json)
        setBody(newInfo)
    }.body<CommunityInfo>()
}

suspend fun SessionManager.updateRoomInfo(id: PrimaryKey, newInfo: UpdateRoomBody) = serviceCatching {
    client.post("rooms/$id") {
        contentType(ContentType.Application.Json)
        setBody(newInfo)
    }.body<RoomInfo>()
}

suspend fun SessionManager.getTopicList(
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

suspend fun SessionManager.addReadLog(info: UpdateUserRead): Result<HttpResponse> {
    return runCatching {
        client.post("users/read") {
            contentType(ContentType.Application.Json)
            setBody(info)
        }
    }
}

suspend fun SessionManager.addDevice(endpointUrl: String) = serviceCatching {
    client.post("users/devices") {
        contentType(ContentType.Application.Json)
        setBody(NewDevice(endpointUrl))
    }
}

suspend fun SessionManager.extractAlbum(mediaId: PrimaryKey) = serviceCatching {
    client.post("amedia/$mediaId/extract-album") {
    }.body<ServerResponse<MediaInfo>>()
}
