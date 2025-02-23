package com.storyteller_f.a.server.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.ForbiddenException
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.NewTitle
import com.storyteller_f.shared.obj.TitleSearchType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TitleStatus
import com.storyteller_f.shared.type.TitleType
import com.storyteller_f.shared.utils.mapNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.Title
import com.storyteller_f.tables.Topic
import com.storyteller_f.tables.createTitle
import com.storyteller_f.tables.getCommunityByIds
import com.storyteller_f.tables.getRoomByIds
import com.storyteller_f.tables.getUsersByIds
import com.storyteller_f.tables.processCommunityList
import com.storyteller_f.tables.roomsResponse
import com.storyteller_f.tables.userTitles
import com.storyteller_f.types.PaginationResult

suspend fun getUserTitles(
    backend: Backend,
    uid: PrimaryKey,
    searchType: TitleSearchType,
    type: TitleType? = null,
    scopeId: PrimaryKey? = null,
    next: PrimaryKey? = null,
    limit: Int,
    pre: PrimaryKey?
) = DatabaseFactory.userTitles(uid, searchType, type, scopeId, next, limit, pre).mapResult { (list, count) ->
    processTitleList(list, backend, uid).map {
        PaginationResult(it, count)
    }
}

private suspend fun processTitleList(
    list: List<TitleInfo>,
    backend: Backend,
    uid: PrimaryKey?
): Result<List<TitleInfo>> {
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
    return DatabaseFactory.getUsersByIds(uidList, backend).mapResult { userList ->
        DatabaseFactory.getCommunityByIds(communityIdList).mapResult {
            processCommunityList(backend, it).mapResult { communityList ->
                DatabaseFactory.getRoomByIds(roomIdList).mapResult {
                    roomsResponse(it, backend).mapResult { roomList ->
                        getTopicByIds(topicIdList, uid, false, backend).map { topicList ->
                            processTitleList(userList, communityList, roomList, list, topicList)
                        }
                    }
                }
            }
        }
    }
}

private fun processTitleList(
    userList: List<UserInfo>,
    communityList: List<CommunityInfo>,
    roomList: List<RoomInfo>,
    list: List<TitleInfo>,
    topicList: List<TopicInfo>,
): List<TitleInfo> {
    val userMap = userList.associate {
        it.id to it
    }
    val communityMap = communityList.associate {
        it.id to it
    }
    val roomMap = roomList.associate {
        it.id to it
    }
    val topicMap = topicList.associate {
        it.id to it
    }
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

suspend fun createTitle(newTitle: NewTitle, uid: PrimaryKey, backend: Backend) =
    checkRootAdminPermission(newTitle.scopeType, newTitle.scopeId, uid).mapResultNotNull {
        if (it.hasAdmin) {
            val title = toTitle(newTitle, uid)
            val topic = Topic(
                title.descriptionTopicId,
                now(),
                uid,
                title.id,
                ObjectType.TITLE,
                title.id,
                ObjectType.TITLE,
                false,
                null
            )
            DatabaseFactory.createTitle(title, topic, newTitle.description, backend).mapResult {
                processTitleList(listOf(it), backend, uid).map {
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
