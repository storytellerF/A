package com.storyteller_f.a.server.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.ForbiddenException
import com.storyteller_f.a.server.auth.addUserLog
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.NewTitle
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TitleSearchType
import com.storyteller_f.shared.type.TitleStatus
import com.storyteller_f.shared.type.TitleType
import com.storyteller_f.shared.utils.*
import com.storyteller_f.tables.*
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.PagingFetch

suspend fun getUserTitles(
    backend: Backend,
    uid: PrimaryKey,
    searchType: TitleSearchType,
    type: TitleType? = null,
    scopeId: PrimaryKey? = null,
    fetch: PagingFetch
) = DatabaseFactory.userTitles(
    backend,
    fetch,
    uid,
    searchType,
    type,
    scopeId
).mapResultIfNotNull { (list, count) ->
    processTitleList(backend, list, uid).mapIfNotNull {
        PaginationResult(it, count)
    }
}

private suspend fun processTitleList(
    backend: Backend,
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
        backend,
        uidList,
        communityIdList,
        roomIdList
    ).mapResult { (userList, roomList, communityList) ->
        getTopicByIds(backend, topicIdList, uid, false).mapIfNotNull { topicList ->
            processTitleList(userList.orEmpty(), communityList.orEmpty(), roomList.orEmpty(), list, topicList)
        }
    }
}

private suspend fun getRelatedObject(
    backend: Backend,
    uidList: List<PrimaryKey>,
    communityIdList: List<PrimaryKey>,
    roomIdList: List<PrimaryKey>
): Result<Triple<List<UserInfo>?, List<RoomInfo>?, List<CommunityInfo>?>> {
    return merge({
        if (uidList.isNotEmpty()) {
            DatabaseFactory.getUsersByIds(backend, uidList)
        } else {
            Result.success(emptyList())
        }
    }, {
        if (roomIdList.isNotEmpty()) {
            DatabaseFactory.getRoomByIds(backend, roomIdList).mapResult {
                processRoomList(it, backend)
            }
        } else {
            Result.success(emptyList())
        }
    }, {
        if (communityIdList.isNotEmpty()) {
            DatabaseFactory.getCommunityByIds(backend, communityIdList).mapResult {
                processCommunityList(backend, it)
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

suspend fun createTitle(
    backend: Backend,
    newTitle: NewTitle,
    uid: PrimaryKey
) =
    checkRootAdminPermission(
        backend,
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
                false,
                null
            )
            DatabaseFactory.createTitle(
                backend,
                title,
                topic,
                newTitle.description
            ).mapResult { created ->
                addUserLog(backend, uid, UserLogType.CREATE, created.tuple())
                processTitleList(backend, listOf(created), uid).mapIfNotNull {
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
