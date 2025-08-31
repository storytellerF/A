package com.storyteller_f.a.client.core

import com.storyteller_f.a.api.core.CommonPath
import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.api.core.PaginationQuery
import com.storyteller_f.a.api.core.TopicQuery
import com.storyteller_f.route4k.ktor.client.invoke
import com.storyteller_f.shared.SignInPack
import com.storyteller_f.shared.SignUpPack
import com.storyteller_f.shared.eciesEncrypt
import com.storyteller_f.shared.encryptData
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.*
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.ObjectType.*
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.core.*

suspend fun <R> SessionManager.serviceCatching(block: suspend HttpClient.() -> R): Result<R> {
    val point = Exception()
    return try {
        val value = client.block()
        Result.success(value)
    } catch (e: Throwable) {
        point.initCause(e)
        Napier.e(point) {
            "serviceCatching"
        }
        Result.failure(e)
    }
}

suspend fun SessionManager.getRoomInfo(id: PrimaryKey) = serviceCatching {
    CustomApi.Rooms.Id.get.invoke(CustomApi.Rooms.Id.RoomIdQuery(currentIsAlreadySignUp), CommonPath(id))
}

suspend fun SessionManager.getRoomInfoByAid(aid: String) = serviceCatching {
    CustomApi.Rooms.Aid.get.invoke(CustomApi.Rooms.Aid.RoomAidQuery(aid, currentIsAlreadySignUp))
}

suspend fun SessionManager.getRoomMembersPublicKeys(
    id: PrimaryKey,
    paginationQuery: PaginationQuery
) =
    serviceCatching {
        CustomApi.Rooms.Id.Members.publicKeys.invoke(paginationQuery, CommonPath(id))
    }

suspend fun SessionManager.joinRoom(id: PrimaryKey) = serviceCatching {
    CustomApi.Rooms.Id.Members.join.invoke(CommonPath(id), Unit) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun SessionManager.joinCommunity(id: PrimaryKey) = serviceCatching {
    CustomApi.Communities.Id.Members.join.invoke(CommonPath(id), Unit) {}
}

suspend fun SessionManager.getRoomTopics(
    roomId: PrimaryKey,
    pinType: TopicPinSearch? = null,
    paginationQuery: PaginationQuery,
) = serviceCatching {
    CustomApi.Rooms.Id.Topics.get.invoke(
        TopicQuery(
            pinType,
            currentIsAlreadySignUp,
            paginationQuery
        ),
        CommonPath(roomId)
    )
}

suspend fun SessionManager.getCommunityTopics(
    communityId: PrimaryKey,
    pinType: TopicPinSearch? = null,
    paginationQuery: PaginationQuery,
) = serviceCatching {
    CustomApi.Communities.Id.Topics.get.invoke(
        TopicQuery(
            pinType,
            currentIsAlreadySignUp,
            paginationQuery
        ),
        CommonPath(communityId)
    )
}

suspend fun SessionManager.getUserTopics(
    userId: PrimaryKey,
    pinType: TopicPinSearch? = null,
    paginationQuery: PaginationQuery,
) = serviceCatching {
    CustomApi.Users.Id.Topics.get.invoke(
        TopicQuery(
            pinType,
            currentIsAlreadySignUp,
            paginationQuery
        ),
        CommonPath(userId)
    )
}

suspend fun SessionManager.getCommunityInfo(id: PrimaryKey) =
    serviceCatching {
        CustomApi.Communities.Id.get(
            CustomApi.Communities.Id.CommunityIdQuery(
                currentIsAlreadySignUp
            ),
            CommonPath(id)
        )
    }

suspend fun SessionManager.getCommunityInfoByAid(aid: String) = serviceCatching {
    CustomApi.Communities.Aid.get(
        CustomApi.Communities.Aid.CommunityAidQuery(
            aid,
            currentIsAlreadySignUp
        )
    )
}

suspend fun SessionManager.searchCommunity(
    size: Int,
    joinStatusSearch: JoinStatusSearch,
    word: String? = null,
    target: PrimaryKey? = null,
    nextCommunityId: String? = null,
    hasPosterSearch: PosterSearch? = null,
) = serviceCatching {
    CustomApi.Communities.search(
        CustomApi.Communities.CommunitySearchQuery(
            joinStatusSearch,
            word,
            target,
            hasPosterSearch,
            nextCommunityId,
            size
        )
    )
}

suspend fun SessionManager.searchCommunityMembers(
    communityId: PrimaryKey,
    nextCommunityId: String?,
    size: Int,
    word: String?,
) = serviceCatching {
    CustomApi.Communities.Id.Members.get(
        CustomApi.Communities.Id.Members.CommunityMemberQuery(word, nextCommunityId, size),
        CommonPath(communityId)
    )
}

suspend fun SessionManager.searchAllMembers(
    nextUserId: String?,
    size: Int,
    word: String?,
) = serviceCatching {
    CustomApi.Users.search(CustomApi.Users.UserSearchQuery(word, nextUserId, size))
}

suspend fun SessionManager.searchRoomMembers(
    roomId: PrimaryKey,
    nextCommunityId: String?,
    size: Int,
    word: String?,
) = serviceCatching {
    CustomApi.Rooms.Id.Members.get(
        CustomApi.Rooms.Id.Members.MemberQuery(
            word,
            nextCommunityId,
            size
        ),
        CommonPath(roomId)
    )
}

suspend fun SessionManager.getRecommendTopics(
    paginationQuery: PaginationQuery
) =
    serviceCatching {
        CustomApi.Topics.recommend.invoke(
            CustomApi.Topics.RecommendQuery(
                currentIsAlreadySignUp,
                paginationQuery
            )
        )
    }

suspend fun SessionManager.getUserInfo(id: PrimaryKey) = serviceCatching {
    CustomApi.Users.Id.get.invoke(CommonPath(id))
}

suspend fun SessionManager.updateUserInfo(newInfo: UpdateUserBody) = serviceCatching {
    CustomApi.Users.update.invoke(newInfo) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun SessionManager.getUserInfoByAid(aid: String) = serviceCatching {
    CustomApi.Users.Aid.get.invoke(CustomApi.Users.Aid.UserAidQuery(aid))
}

suspend fun SessionManager.getTopicTopics(
    topicId: PrimaryKey,
    pinType: TopicPinSearch,
    paginationQuery: PaginationQuery,
) =
    serviceCatching {
        CustomApi.Topics.Id.Topics.get.invoke(
            TopicQuery(
                pinType,
                currentIsAlreadySignUp,
                paginationQuery
            ),
            CommonPath(topicId)
        )
    }

suspend fun SessionManager.getTopicInfo(id: PrimaryKey) = serviceCatching {
    CustomApi.Topics.Id.get.invoke(
        CustomApi.Topics.Id.TopicIdQuery(currentIsAlreadySignUp),
        CommonPath(id)
    )
}

suspend fun SessionManager.getTopicInfoByAid(aid: String) = serviceCatching {
    CustomApi.Topics.Aid.get.invoke(CustomApi.Topics.Aid.TopicAidQuery(aid, currentIsAlreadySignUp))
}

suspend fun SessionManager.searchRooms(
    size: Int,
    nextRoomId: String?,
    joinStatusSearch: JoinStatusSearch,
    word: String?,
    communityId: PrimaryKey?,
) = serviceCatching {
    CustomApi.Rooms.search.invoke(
        CustomApi.Rooms.RoomSearchQuery(
            joinStatusSearch,
            word,
            communityId,
            nextRoomId,
            size
        )
    )
}

suspend fun SessionManager.createNewTopic(
    objectType: ObjectType,
    objectId: PrimaryKey,
    input: String,
) = serviceCatching {
    CustomApi.Topics.add.invoke(NewTopic(objectType, objectId, input)) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun SessionManager.signUp(
    publicKey: String,
    signature: String,
) = serviceCatching {
    CustomApi.Accounts.signUp.invoke(SignUpPack(publicKey, signature)) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun SessionManager.signIn(
    address: String,
    signature: String,
) = serviceCatching {
    CustomApi.Accounts.signIn.invoke(SignInPack(address, signature)) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun SessionManager.getData() = serviceCatching {
    client.get("accounts/get_data").bodyAsText()
}

suspend fun SessionManager.getTopicSnapshot(topicId: PrimaryKey) = serviceCatching {
    CustomApi.Topics.Id.createSnapshot.invoke(CommonPath(topicId), Unit) {
    }
}

suspend fun SessionManager.searchTopics(
    size: Int,
    word: List<String>,
    parentId: PrimaryKey? = null,
    parentType: ObjectType? = null,
    nextTopicId: String? = null,
) = serviceCatching {
    CustomApi.Topics.search.invoke(
        CustomApi.Topics.TopicSearchQuery(
            word,
            parentId,
            parentType,
            nextTopicId,
            size,
            currentIsAlreadySignUp
        )
    )
}

suspend fun SessionManager.exitRoom(roomId: PrimaryKey) = serviceCatching {
    CustomApi.Rooms.Id.Members.leave.invoke(CommonPath(roomId), Unit) {}
}

suspend fun SessionManager.exitCommunity(communityId: PrimaryKey) = serviceCatching {
    CustomApi.Communities.Id.Members.leave.invoke(CommonPath(communityId), Unit) {}
}

suspend fun SessionManager.addReaction(topicId: PrimaryKey, emoji: String) = serviceCatching {
    CustomApi.Topics.Id.Reactions.add.invoke(CommonPath(topicId), NewReaction(emoji)) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun SessionManager.deleteReaction(emoji: String, objectId: PrimaryKey) = serviceCatching {
    CustomApi.Topics.Id.Reactions.delete.invoke(CommonPath(objectId), DeleteReaction(emoji)) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun SessionManager.getReactions(
    topicId: PrimaryKey,
    size: Int,
    nextCursor: String? = null
) =
    serviceCatching {
        CustomApi.Topics.Id.Reactions.get.invoke(
            CustomApi.Topics.Id.Reactions.ReactionQuery(
                currentIsAlreadySignUp,
                nextCursor,
                size = size
            ),
            CommonPath(topicId),
        )
    }

suspend fun SessionManager.signOut() = serviceCatching {
    CustomApi.Accounts.signOut.invoke(Unit) {}
}

suspend fun SessionManager.getMediaList(
    objectId: PrimaryKey,
    objectType: ObjectType,
    nextId: String?,
    size: Int
) =
    serviceCatching {
        CustomApi.Files.get.invoke(
            CustomApi.Files.FileQuery(
                objectId,
                objectType,
                nextId,
                size = size
            )
        )
    }

suspend fun SessionManager.getMediaByName(
    word: String,
    objectId: PrimaryKey,
    objectType: ObjectType
) =
    serviceCatching {
        CustomApi.Files.getByName.invoke(
            CustomApi.Files.MediaSearchQuery(
                word,
                objectId,
                objectType
            )
        )
    }

class UploadData(val size: Long, val name: String, val contentType: ContentType, val block: () -> Input)

suspend fun SessionManager.upload(
    objectTuple: ObjectTuple,
    data: UploadData,
    onUpload: suspend (Long, Long?) -> Unit = { _, _ -> },
) = serviceCatching {
    CustomApi.Files.upload.invoke(objectTuple, Unit) {
        setBody(
            MultiPartFormDataContent(
                formData {
                    append("description", "a_file")
                    appendInput("file", Headers.build {
                        append(HttpHeaders.ContentType, data.contentType)
                        append(HttpHeaders.ContentDisposition, "filename=\"${data.name}\"")
                        append(HttpHeaders.ContentLength, data.size)
                    }, data.size, data.block)
                },
                boundary = "WebAppBoundary"
            )
        )
        onUpload { bytesSentTotal, contentLength ->
            onUpload(bytesSentTotal, contentLength)
            println("Sent $bytesSentTotal bytes from $contentLength")
        }
    }
}

suspend fun SessionManager.copy(mediaId: PrimaryKey) =
    serviceCatching {
        CustomApi.Files.Id.copy.invoke(CommonPath(mediaId), Unit) {
        }
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
    nextId: String?,
    size: Int,
    searchType: TitleSearchType,
    status: TitleStatus? = null,
    type: TitleType? = null,
    scopeId: PrimaryKey? = null,
) = serviceCatching {
    CustomApi.Users.Id.Titles.get.invoke(
        CustomApi.Users.Id.Titles.TitleQuery(
            searchType,
            type,
            scopeId,
            status,
            nextId,
            size
        ),
        CommonPath(uid)
    )
}

suspend fun SessionManager.createTitle(newTitle: NewTitle) = serviceCatching {
    CustomApi.Titles.add.invoke(newTitle) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun SessionManager.createCommunity(newCommunity: NewCommunity) = serviceCatching {
    CustomApi.Communities.add.invoke(newCommunity) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun SessionManager.createRoom(newRoom: NewRoom) = serviceCatching {
    CustomApi.Rooms.add.invoke(newRoom) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun SessionManager.pinTopic(topicId: PrimaryKey) = serviceCatching {
    CustomApi.Topics.Id.pin.invoke(CommonPath(topicId), Unit) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun SessionManager.unpinTopic(topicId: PrimaryKey) = serviceCatching {
    CustomApi.Topics.Id.unpin.invoke(CommonPath(topicId), Unit) {
    }
}

suspend fun SessionManager.updateCommunityInfo(id: PrimaryKey, newInfo: UpdateCommunityBody) =
    serviceCatching {
        CustomApi.Communities.Id.update.invoke(CommonPath(id), newInfo) {
            contentType(ContentType.Application.Json)
        }
    }

suspend fun SessionManager.updateRoomInfo(id: PrimaryKey, newInfo: UpdateRoomBody) =
    serviceCatching {
        CustomApi.Rooms.Id.update.invoke(CommonPath(id), newInfo) {
            contentType(ContentType.Application.Json)
        }
    }

suspend fun SessionManager.getTopicList(
    type: ObjectType,
    id: PrimaryKey,
    pinSearch: TopicPinSearch,
    paginationQuery: PaginationQuery,
) = when (type) {
    ROOM -> getRoomTopics(id, pinSearch, paginationQuery)
    COMMUNITY -> getCommunityTopics(id, pinSearch, paginationQuery)
    USER -> getUserTopics(id, pinSearch, paginationQuery)
    TOPIC -> getTopicTopics(id, pinSearch, paginationQuery)
    else -> Result.failure(IllegalArgumentException("unrecognized $type"))
}

suspend fun SessionManager.addReadLog(info: UpdateUserRead): Result<Unit> {
    return serviceCatching {
        CustomApi.Users.Read.add.invoke(info) {
            contentType(ContentType.Application.Json)
        }
    }
}

suspend fun SessionManager.addDevice(endpointUrl: String) = serviceCatching {
    CustomApi.Users.Devices.add.invoke(NewDevice(endpointUrl)) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun SessionManager.extractAlbum(mediaId: PrimaryKey) = serviceCatching {
    CustomApi.Files.Id.extractAlbum.invoke(CommonPath(mediaId), Unit) {}
}

suspend fun SessionManager.addAlternativeAccount() = serviceCatching {
    CustomApi.Accounts.ChildAccounts.add.invoke(Unit) {}
}

suspend fun SessionManager.getAlternativeAccounts(nextId: String?, size: Int) = serviceCatching {
    CustomApi.Accounts.ChildAccounts.get.invoke(
        CustomApi.Accounts.ChildAccounts.ChildAccountQuery(
            nextId,
            size
        )
    )
}
