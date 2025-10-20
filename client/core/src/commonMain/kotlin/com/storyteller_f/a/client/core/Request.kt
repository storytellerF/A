package com.storyteller_f.a.client.core

import com.storyteller_f.a.api.core.CommonPath
import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.api.core.PaginationQuery
import com.storyteller_f.a.api.core.TopicQuery
import com.storyteller_f.route4k.ktor.client.invoke
import com.storyteller_f.shared.SignInPack
import com.storyteller_f.shared.SignUpPack
import com.storyteller_f.shared.encryptDataByAES
import com.storyteller_f.shared.getAlgo
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
import io.ktor.http.*
import io.ktor.utils.io.core.*

suspend fun <R, U> SessionManager<U>.serviceCatching(block: suspend HttpClient.() -> R): Result<R> {
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

suspend fun UserSessionManager.getRoomInfo(id: PrimaryKey) = serviceCatching {
    CustomApi.Rooms.Id.get(CustomApi.Rooms.Id.RoomIdQuery(currentIsAlreadySignUp), CommonPath(id))
}

suspend fun UserSessionManager.getRoomInfoByAid(aid: String) = serviceCatching {
    CustomApi.Rooms.Aid.get(CustomApi.Rooms.Aid.RoomAidQuery(aid, currentIsAlreadySignUp))
}

suspend fun UserSessionManager.getRoomMembersPublicKeys(
    id: PrimaryKey,
    paginationQuery: PaginationQuery
) =
    serviceCatching {
        CustomApi.Rooms.Id.Members.publicKeys(paginationQuery, CommonPath(id))
    }

suspend fun UserSessionManager.joinRoom(id: PrimaryKey) = serviceCatching {
    CustomApi.Rooms.Id.Members.join(CommonPath(id), Unit) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun UserSessionManager.joinCommunity(id: PrimaryKey) = serviceCatching {
    CustomApi.Communities.Id.Members.join(CommonPath(id), Unit) {}
}

suspend fun UserSessionManager.getRoomTopics(
    roomId: PrimaryKey,
    pinType: TopicPinSearch? = null,
    paginationQuery: PaginationQuery,
) = serviceCatching {
    CustomApi.Rooms.Id.Topics.get(
        TopicQuery(
            pinType,
            currentIsAlreadySignUp,
            paginationQuery
        ),
        CommonPath(roomId)
    )
}

suspend fun UserSessionManager.getCommunityTopics(
    communityId: PrimaryKey,
    pinType: TopicPinSearch? = null,
    paginationQuery: PaginationQuery,
) = serviceCatching {
    CustomApi.Communities.Id.Topics.get(
        TopicQuery(
            pinType,
            currentIsAlreadySignUp,
            paginationQuery
        ),
        CommonPath(communityId)
    )
}

suspend fun UserSessionManager.getUserTopics(
    userId: PrimaryKey,
    pinType: TopicPinSearch? = null,
    paginationQuery: PaginationQuery,
) = serviceCatching {
    CustomApi.Users.Id.Topics.get(
        TopicQuery(
            pinType,
            currentIsAlreadySignUp,
            paginationQuery
        ),
        CommonPath(userId)
    )
}

suspend fun UserSessionManager.getCommunityInfo(id: PrimaryKey) =
    serviceCatching {
        CustomApi.Communities.Id.get(
            CustomApi.Communities.Id.CommunityIdQuery(
                currentIsAlreadySignUp
            ),
            CommonPath(id)
        )
    }

suspend fun UserSessionManager.getCommunityInfoByAid(aid: String) = serviceCatching {
    CustomApi.Communities.Aid.get(
        CustomApi.Communities.Aid.CommunityAidQuery(
            aid,
            currentIsAlreadySignUp
        )
    )
}

suspend fun UserSessionManager.searchCommunity(
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
            size,
        )
    )
}

suspend fun UserSessionManager.searchCommunityMembers(
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

suspend fun UserSessionManager.searchAllMembers(
    nextUserId: String?,
    size: Int,
    word: String?,
) = serviceCatching {
    CustomApi.Users.search(CustomApi.Users.UserSearchQuery(word, nextUserId, size))
}

suspend fun UserSessionManager.searchRoomMembers(
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

suspend fun UserSessionManager.getRecommendTopics(
    paginationQuery: PaginationQuery
) =
    serviceCatching {
        CustomApi.Topics.recommend(
            CustomApi.Topics.RecommendQuery(
                currentIsAlreadySignUp,
                paginationQuery.nextPageToken,
                paginationQuery.size,
                paginationQuery.prePageToken
            )
        )
    }

suspend fun UserSessionManager.getUserInfo(id: PrimaryKey) = serviceCatching {
    CustomApi.Users.Id.get(CommonPath(id))
}

suspend fun UserSessionManager.updateUserInfo(newInfo: UpdateUserBody) = serviceCatching {
    CustomApi.Users.update(newInfo) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun UserSessionManager.getUserInfoByAid(aid: String) = serviceCatching {
    CustomApi.Users.Aid.get(CustomApi.Users.Aid.UserAidQuery(aid))
}

suspend fun UserSessionManager.getTopicTopics(
    topicId: PrimaryKey,
    pinType: TopicPinSearch,
    paginationQuery: PaginationQuery,
) =
    serviceCatching {
        CustomApi.Topics.Id.Topics.get(
            TopicQuery(
                pinType,
                currentIsAlreadySignUp,
                paginationQuery
            ),
            CommonPath(topicId)
        )
    }

suspend fun UserSessionManager.getTopicInfo(id: PrimaryKey) = serviceCatching {
    CustomApi.Topics.Id.get(
        CustomApi.Topics.Id.TopicIdQuery(currentIsAlreadySignUp),
        CommonPath(id)
    )
}

suspend fun UserSessionManager.getTopicInfoByAid(aid: String) = serviceCatching {
    CustomApi.Topics.Aid.get(CustomApi.Topics.Aid.TopicAidQuery(aid, currentIsAlreadySignUp))
}

suspend fun UserSessionManager.searchRooms(
    size: Int,
    nextRoomId: String?,
    joinStatusSearch: JoinStatusSearch,
    word: String?,
    communityId: PrimaryKey?,
) = serviceCatching {
    CustomApi.Rooms.search(
        CustomApi.Rooms.RoomSearchQuery(
            joinStatusSearch,
            word,
            communityId,
            nextRoomId,
            size
        )
    )
}

suspend fun UserSessionManager.createTopic(
    objectType: ObjectType,
    objectId: PrimaryKey,
    input: String,
) = serviceCatching {
    CustomApi.Topics.add(NewTopic(objectType, objectId, input)) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun UserSessionManager.signUp(
    pack: SignUpPack,
) = serviceCatching {
    CustomApi.Accounts.signUp(pack) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun UserSessionManager.signIn(
    pack: SignInPack,
) = serviceCatching {
    CustomApi.Accounts.signIn(pack) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun UserSessionManager.getData() = serviceCatching {
    CustomApi.Accounts.getData.invoke()
}

suspend fun UserSessionManager.getTopicSnapshot(topicId: PrimaryKey) = serviceCatching {
    CustomApi.Topics.Id.createSnapshot(CommonPath(topicId), Unit) {
    }
}

suspend fun UserSessionManager.searchTopics(
    size: Int,
    word: List<String>? = null,
    parentId: PrimaryKey? = null,
    parentType: ObjectType? = null,
    nextTopicId: String? = null,
) = serviceCatching {
    CustomApi.Topics.search(
        CustomApi.Topics.TopicSearchQuery(
            word,
            parentId,
            parentType,
            nextTopicId,
            null,
            size,
            currentIsAlreadySignUp
        )
    )
}

suspend fun UserSessionManager.exitRoom(roomId: PrimaryKey) = serviceCatching {
    CustomApi.Rooms.Id.Members.leave(CommonPath(roomId), Unit) {}
}

suspend fun UserSessionManager.exitCommunity(communityId: PrimaryKey) = serviceCatching {
    CustomApi.Communities.Id.Members.leave(CommonPath(communityId), Unit) {}
}

suspend fun UserSessionManager.addReaction(topicId: PrimaryKey, emoji: String) = serviceCatching {
    CustomApi.Topics.Id.Reactions.add(CommonPath(topicId), NewReaction(emoji)) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun UserSessionManager.deleteReaction(emoji: String, objectId: PrimaryKey) = serviceCatching {
    CustomApi.Topics.Id.Reactions.delete(CommonPath(objectId), DeleteReaction(emoji)) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun UserSessionManager.getReactions(
    topicId: PrimaryKey,
    size: Int,
    nextCursor: String? = null
) =
    serviceCatching {
        CustomApi.Topics.Id.Reactions.get(
            CustomApi.Topics.Id.Reactions.ReactionQuery(
                currentIsAlreadySignUp,
                nextCursor,
                size = size
            ),
            CommonPath(topicId),
        )
    }

suspend fun UserSessionManager.signOut() = serviceCatching {
    CustomApi.Accounts.signOut(Unit) {}
}

suspend fun UserSessionManager.getMediaList(
    objectId: PrimaryKey,
    objectType: ObjectType,
    nextId: String?,
    size: Int
) =
    serviceCatching {
        CustomApi.Files.get(
            CustomApi.Files.FileQuery(
                objectId,
                objectType,
                nextId,
                size = size
            )
        )
    }

suspend fun UserSessionManager.getMediaByName(
    word: String,
    objectId: PrimaryKey,
    objectType: ObjectType
) =
    serviceCatching {
        CustomApi.Files.getByName(
            CustomApi.Files.MediaSearchQuery(
                word,
                objectId,
                objectType
            )
        )
    }

class UploadData(val size: Long, val name: String, val contentType: ContentType, val block: () -> Input)

suspend fun UserSessionManager.upload(
    objectTuple: ObjectTuple,
    data: UploadData,
    onUpload: suspend (Long, Long?) -> Unit = { _, _ -> },
) = serviceCatching {
    CustomApi.Files.upload(objectTuple, Unit) {
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

suspend fun UserSessionManager.copy(mediaId: PrimaryKey) =
    serviceCatching {
        CustomApi.Files.Id.copy(CommonPath(mediaId), Unit) {
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
        val (encrypted, aes) = encryptDataByAES(input).getOrThrow()
        TopicContent.Encrypted(encrypted.toHexString(), keyData.associate {
            it.id to getAlgo().eciesEncrypt(it.pubKey, aes).getOrThrow().toHexString()
        })
    } else {
        TopicContent.Plain(input)
    }
    val message = RoomFrame.Message(
        NewRoomTopic(
            parentTarget.objectType,
            parentTarget.objectId,
            content
        )
    )
    sendFrame(message)
}

suspend fun UserSessionManager.userTitles(
    uid: PrimaryKey,
    nextId: String?,
    size: Int,
    searchType: TitleSearchType,
    status: TitleStatus? = null,
    type: TitleType? = null,
    scopeId: PrimaryKey? = null,
) = serviceCatching {
    CustomApi.Users.Id.Titles.get(
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

suspend fun UserSessionManager.createTitle(newTitle: NewTitle) = serviceCatching {
    CustomApi.Titles.add(newTitle) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun UserSessionManager.createCommunity(newCommunity: NewCommunity) = serviceCatching {
    CustomApi.Communities.add(newCommunity) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun UserSessionManager.createRoom(newRoom: NewRoom) = serviceCatching {
    CustomApi.Rooms.add(newRoom) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun UserSessionManager.pinTopic(topicId: PrimaryKey) = serviceCatching {
    CustomApi.Topics.Id.pin(CommonPath(topicId), Unit) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun UserSessionManager.unpinTopic(topicId: PrimaryKey) = serviceCatching {
    CustomApi.Topics.Id.unpin.invoke(CommonPath(topicId), Unit) {
    }
}

suspend fun UserSessionManager.updateCommunityInfo(id: PrimaryKey, newInfo: UpdateCommunityBody) =
    serviceCatching {
        CustomApi.Communities.Id.update.invoke(CommonPath(id), newInfo) {
            contentType(ContentType.Application.Json)
        }
    }

suspend fun UserSessionManager.updateRoomInfo(id: PrimaryKey, newInfo: UpdateRoomBody) =
    serviceCatching {
        CustomApi.Rooms.Id.update.invoke(CommonPath(id), newInfo) {
            contentType(ContentType.Application.Json)
        }
    }

suspend fun UserSessionManager.getTopicList(
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

suspend fun UserSessionManager.addReadLog(info: UpdateUserRead): Result<Unit> {
    return serviceCatching {
        CustomApi.Users.Read.add.invoke(info) {
            contentType(ContentType.Application.Json)
        }
    }
}

suspend fun UserSessionManager.addDevice(endpointUrl: String) = serviceCatching {
    CustomApi.Users.Devices.add.invoke(NewDevice(endpointUrl)) {
        contentType(ContentType.Application.Json)
    }
}

suspend fun UserSessionManager.extractAlbum(mediaId: PrimaryKey) = serviceCatching {
    CustomApi.Files.Id.extractAlbum.invoke(CommonPath(mediaId), Unit) {}
}

suspend fun UserSessionManager.addChildAccount() = serviceCatching {
    CustomApi.Accounts.ChildAccounts.add.invoke(Unit) {}
}

suspend fun UserSessionManager.getChildAccounts(nextId: String?, size: Int) = serviceCatching {
    CustomApi.Accounts.ChildAccounts.get.invoke(
        CustomApi.Accounts.ChildAccounts.ChildAccountQuery(
            nextId,
            size
        )
    )
}
