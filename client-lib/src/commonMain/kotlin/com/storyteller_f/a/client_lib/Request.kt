package com.storyteller_f.a.client_lib

import com.storyteller_f.shared.SignInPack
import com.storyteller_f.shared.SignUpPack
import com.storyteller_f.shared.encrypt
import com.storyteller_f.shared.encryptAesKey
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.*
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*

fun isAlreadyLogin(): Boolean {
    return LoginViewModel.currentIsAlreadySignUp
}

inline fun <R> serviceCatching(block: () -> R): Result<R> {
    return try {
        val value = block()
        Result.success(value)
    } catch (e: Throwable) {
        Napier.e(e) {
            "serviceCatching"
        }
        Result.failure(e)
    }
}

suspend fun HttpClient.requestRoomInfo(id: PrimaryKey, alreadySignUp: Boolean) = serviceCatching {
    get("rooms/$id") {
        url {
            if (alreadySignUp) parameters.append("fillJoinInfo", "true")
        }
    }.body<RoomInfo>()
}

suspend fun HttpClient.requestRoomInfoByAid(aid: String, alreadySignUp: Boolean) = serviceCatching {
    get("rooms") {
        url {
            if (alreadySignUp) parameters.append("fillJoinInfo", "true")
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
) = serviceCatching {
    get("rooms/$roomId/topics") {
        url {
            if (isAlreadyLogin()) {
                parameters.append("fillHasCommented", "true")
            }
            appendPagingQueryParams(size, nextTopicId)
        }
    }.body<ServerResponse<TopicInfo>>()
}

suspend fun HttpClient.getCommunityInfo(id: PrimaryKey, fillJoinInfo: Boolean = false) =
    serviceCatching {
        get("communities/$id") {
            url {
                if (fillJoinInfo) {
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
) = serviceCatching {
    get("communities/search") {
        url {
            word?.let { value -> parameters.append("word", value) }
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

suspend fun HttpClient.getWorldTopics(nextTopicId: PrimaryKey?, size: Int) =
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

suspend fun HttpClient.updateUserInfo(newUserInfo: UserInfo) = serviceCatching {
    post("users/update") {
        contentType(ContentType.Application.Json)
        setBody(newUserInfo)
    }.body<UserInfo>()
}

suspend fun HttpClient.getUserInfoByAid(aid: String) = serviceCatching {
    get("users") {
        url {
            parameters.append("aid", aid)
        }
    }.body<UserInfo>()
}

suspend fun HttpClient.getTopicTopics(topicId: PrimaryKey, nextTopicId: PrimaryKey?, size: Int) =
    serviceCatching {
        get("topics/$topicId/topics") {
            url {
                if (isAlreadyLogin()) {
                    parameters.append("fillHasCommented", "true")
                }
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
        setBody(NewTopic(objectType, objectId, TopicContent.Plain(input)))
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
    nextTopicId: PrimaryKey?,
    size: Int,
    word: List<String>,
    parentId: PrimaryKey?,
    parentType: ObjectType?
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

suspend fun HttpClient.getMediaList(objectId: PrimaryKey, objectType: ObjectType) = serviceCatching {
    get("amedia") {
        url {
            parameters.append("objectId", objectId.toString())
            parameters.append("objectType", objectType.name)
        }
    }.body<ServerResponse<MediaInfo>>()
}

suspend fun HttpClient.upload(
    stream: ByteArray,
    name: String,
    extension: String,
    objectId: PrimaryKey,
    objectType: ObjectType
) = serviceCatching {
    post("amedia/upload") {
        url {
            parameters.append("objectId", objectId.toString())
            parameters.append("objectType", objectType.name)
        }
        setBody(
            MultiPartFormDataContent(
                formData {
                    append("description", "amedia")
                    append("file", stream, Headers.build {
                        append(HttpHeaders.ContentType, ContentType.defaultForFileExtension(extension))
                        append(HttpHeaders.ContentDisposition, "filename=\"$name\"")
                    })
                },
                boundary = "WebAppBoundary"
            )
        )
        onUpload { bytesSentTotal, contentLength ->
            println("Sent $bytesSentTotal bytes from $contentLength")
        }
    }.body<ServerResponse<MediaInfo>>()
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun DefaultClientWebSocketSession.sendMessage(
    roomInfo: RoomInfo,
    input: String,
    keyData: List<Pair<PrimaryKey, String>>,
    topicId: PrimaryKey?,
) {
    val content = if (roomInfo.isPrivate) {
        val (encrypted, aes) = encrypt(input)
        TopicContent.Encrypted(encrypted.toHexString(), keyData.associate {
            it.first to encryptAesKey(it.second, aes).toHexString()
        })
    } else {
        TopicContent.Plain(input)
    }
    val message: RoomFrame = RoomFrame.Message(
        if (topicId != null) {
            NewTopic(
                ObjectType.TOPIC,
                topicId,
                content
            )
        } else {
            NewTopic(
                ObjectType.ROOM,
                roomInfo.id,
                content
            )
        }
    )
    sendSerialized(message)
}
