package com.storyteller_f.a.cloud.server.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.exposed.query.PaginationResult
import com.storyteller_f.a.backend.exposed.tables.Title
import com.storyteller_f.a.backend.exposed.tables.Topic
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.backend.service.getUserInfoList
import com.storyteller_f.a.backend.service.index.TopicDocument
import com.storyteller_f.a.backend.service.insertTitleAndTopicDescription
import com.storyteller_f.a.backend.service.processCommunityRawResultToCommunityInfo
import com.storyteller_f.a.backend.service.processRoomRawResultToRoomInfo
import com.storyteller_f.a.cloud.server.auth.addUserLog
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.NewTitle
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.*

suspend fun Backend.getUserTitles(
    uid: PrimaryKey,
    searchType: TitleSearchType,
    type: TitleType? = null,
    scopeId: PrimaryKey? = null,
    fetch: PrimaryKeyFetch
) = exposedDatabase.titleDatabase.getTitlePaginationResult(
    fetch,
    uid,
    searchType,
    type,
    scopeId
).mapResultIfNotNull { (list, count) ->
    processTitleList(list, uid).mapIfNotNull {
        PaginationResult(it, count)
    }
}

private suspend fun Backend.processTitleList(
    list: List<TitleInfo>,
    uid: PrimaryKey?
): Result<List<TitleInfo>?> {
    val uidList = list.flatMap {
        val listOf = listOf(it.receiver, it.creator)
        if (it.scopeType == ObjectType.USER) {
            listOf + it.scopeId
        } else {
            listOf
        }
    }.distinct()
    val communityIdList = list.mapNotNull {
        if (it.scopeType == ObjectType.COMMUNITY) {
            it.scopeId
        } else {
            null
        }
    }.distinct()
    val roomIdList = list.mapNotNull {
        if (it.scopeType == ObjectType.ROOM) {
            it.scopeId
        } else {
            null
        }
    }.distinct()
    val topicIdList = list.flatMap {
        buildList {
            if (it.scopeType == ObjectType.TOPIC) {
                add(it.scopeId)
            }
            add(it.descriptionTopicId)
        }
    }.distinct()
    return getRelatedObject(
        uidList,
        communityIdList,
        roomIdList
    ).mapResult { (userList, roomList, communityList) ->
        getTopicByIds(topicIdList, uid).mapIfNotNull { topicList ->
            processTitleList(userList.orEmpty(), communityList.orEmpty(), roomList.orEmpty(), list, topicList)
        }
    }
}

private suspend fun Backend.getRelatedObject(
    uidList: List<PrimaryKey>,
    communityIdList: List<PrimaryKey>,
    roomIdList: List<PrimaryKey>
): Result<Triple<List<UserInfo>?, List<RoomInfo>?, List<CommunityInfo>?>> {
    return merge({
        if (uidList.isNotEmpty()) {
            getUserInfoList(ObjectListFetch.IdListFetch(uidList))
        } else {
            Result.success(emptyList())
        }
    }, {
        if (roomIdList.isNotEmpty()) {
            exposedDatabase.roomData.getRoomRawResultList(ObjectListFetch.IdListFetch(roomIdList)).mapResult {
                processRoomRawResultToRoomInfo(it)
            }
        } else {
            Result.success(emptyList())
        }
    }, {
        if (communityIdList.isNotEmpty()) {
            exposedDatabase.communityDatabase.getCommunityRawResults(
                ObjectListFetch.IdListFetch(communityIdList)
            ).mapResult {
                processCommunityRawResultToCommunityInfo(it)
            }
        } else {
            Result.success(emptyList())
        }
    })
}

private fun processTitleList(
    userList: List<UserInfo>,
    communityList: List<CommunityInfo>,
    roomList: List<RoomInfo>,
    list: List<TitleInfo>,
    topicList: List<TopicInfo>,
): List<TitleInfo> {
    val userMap = userList.associateBy { it.id }
    val communityMap = communityList.associateBy { it.id }
    val roomMap = roomList.associateBy { it.id }
    val topicMap = topicList.associateBy { it.id }
    return list.map {
        val extension = TitleInfo.Extension(
            userMap[it.creator]!!,
            userMap[it.receiver]!!,
            topicMap[it.descriptionTopicId]!!,
            when (it.scopeType) {
                ObjectType.COMMUNITY -> communityMap[it.scopeId]
                else -> null
            },
            when (it.scopeType) {
                ObjectType.ROOM -> roomMap[it.scopeId]
                else -> null
            },
            when (it.scopeType) {
                ObjectType.USER -> userMap[it.scopeId]
                else -> null
            },
            when (it.scopeType) {
                ObjectType.TOPIC -> topicMap[it.scopeId]
                else -> null
            }
        )
        it.copy(extension = extension)
    }
}

suspend fun Backend.createTitle(
    newTitle: NewTitle,
    uid: PrimaryKey
) =
    checkRootAdminPermission(
        newTitle.scopeType,
        newTitle.scopeId,
        uid
    ).mapResultIfNotNull { permission ->
        if (permission.hasAdmin) {
            val title = toTitle(newTitle, uid)
            val topic = Topic(
                title.descriptionTopicId,
                now(),
                uid,
                title.id,
                ObjectType.TITLE,
                title.id,
                ObjectType.TITLE,
                newTitle.description.encodeToByteArray(),
                isEncrypted = false,
                isPin = false,
                lastModifiedTime = null
            )
            insertTitleAndTopicDescription(
                title,
                topic
            ).mapResult { created ->
                topicSearchService.saveDocument(
                    listOf(TopicDocument.fromTopic(topic, TopicContent.Plain(newTitle.description)))
                ).getOrThrow()
                addUserLog(uid, UserLogType.CREATE, created.tuple())
                processTitleList(listOf(created), uid).mapIfNotNull {
                    it.first()
                }
            }
        } else {
            Result.failure(ForbiddenException("Permission denied"))
        }
    }

private suspend fun toTitle(
    newTitle: NewTitle,
    id: PrimaryKey,
): Title {
    val titleId = SnowflakeFactory.nextId()
    val descriptionTopicId = SnowflakeFactory.nextId()
    return Title(
        titleId,
        now(),
        newTitle.name,
        id,
        newTitle.receiver,
        newTitle.type,
        newTitle.scopeId,
        newTitle.scopeType,
        TitleStatus.OK,
        descriptionTopicId
    )
}
