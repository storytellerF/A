package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.NewTitle
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.Member
import com.storyteller_f.a.backend.core.types.RawTitle
import com.storyteller_f.a.backend.core.types.Title
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.a.backend.core.types.toTitleInfo
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.TitleStatus
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.MemberStatus
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import io.github.aakira.napier.Napier

suspend fun Backend.getUserTitles(
    uid: PrimaryKey,
    searchType: TitleSearchType,
    type: TitleType? = null,
    scopeId: PrimaryKey? = null,
    fetch: PrimaryKeyFetch
) = database.title.getTitlePaginationResult(
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
    list: List<RawTitle>,
    uid: PrimaryKey?
): Result<List<TitleInfo>?> {
    val uidList = list.flatMap {
        val title = it.title
        val listOf = listOf(title.receiver, title.creator)
        if (title.scopeType == ObjectType.USER) {
            listOf + title.scopeId
        } else {
            listOf
        }
    }.distinct()
    val communityIdList = list.mapNotNull {
        val title = it.title
        if (title.scopeType == ObjectType.COMMUNITY) {
            title.scopeId
        } else {
            null
        }
    }.distinct()
    val roomIdList = list.mapNotNull {
        val title = it.title
        if (title.scopeType == ObjectType.ROOM) {
            title.scopeId
        } else {
            null
        }
    }.distinct()
    val topicIdList = list.flatMap {
        val title = it.title
        buildList {
            if (title.scopeType == ObjectType.TOPIC) {
                add(title.scopeId)
            }
            add(title.descriptionTopicId)
        }
    }.distinct()
    return getRelatedObject(
        uidList,
        communityIdList,
        roomIdList
    ).mapResult { (userList, roomList, communityList) ->
        getTopicByIds(topicIdList, uid).mapIfNotNull { topicList ->
            processTitleList(
                userList.orEmpty(),
                communityList.orEmpty(),
                roomList.orEmpty(),
                list,
                topicList
            )
        }
    }
}

private suspend fun Backend.getRelatedObject(
    uidList: List<PrimaryKey>,
    communityIdList: List<PrimaryKey>,
    roomIdList: List<PrimaryKey>
): Result<Triple<List<UserInfo>?, List<RoomInfo>?, List<CommunityInfo>?>> {
    return runCatching {
        val r1 = if (uidList.isNotEmpty()) {
            getUserInfoList(ObjectListFetch.IdListFetch(uidList))
        } else {
            Result.success(emptyList())
        }.getOrThrow()
        val r2 = if (roomIdList.isNotEmpty()) {
            getRoomInfoList(ObjectListFetch.IdListFetch(roomIdList))
        } else {
            Result.success(emptyList())
        }.getOrThrow()
        val r3 = if (communityIdList.isNotEmpty()) {
            database.community.getRawCommunities(
                ObjectListFetch.IdListFetch(communityIdList)
            ).mapResult {
                processRawCommunityToCommunityInfo(it)
            }
        } else {
            Result.success(emptyList())
        }.getOrThrow()
        Triple(r1, r2, r3)
    }
}

private fun processTitleList(
    userList: List<UserInfo>,
    communityList: List<CommunityInfo>,
    roomList: List<RoomInfo>,
    list: List<RawTitle>,
    topicList: List<TopicInfo>,
): List<TitleInfo> {
    val userMap = userList.associateBy { it.id }
    val communityMap = communityList.associateBy { it.id }
    val roomMap = roomList.associateBy { it.id }
    val topicMap = topicList.associateBy { it.id }
    return list.map {
        val title = it.title
        val extension = TitleInfo.Extension(
            userMap[title.creator]!!,
            userMap[title.receiver]!!,
            topicMap[title.descriptionTopicId]!!,
            when (title.scopeType) {
                ObjectType.COMMUNITY -> communityMap[title.scopeId]
                else -> null
            },
            when (title.scopeType) {
                ObjectType.ROOM -> roomMap[title.scopeId]
                else -> null
            },
            when (title.scopeType) {
                ObjectType.USER -> userMap[title.scopeId]
                else -> null
            },
            when (title.scopeType) {
                ObjectType.TOPIC -> topicMap[title.scopeId]
                else -> null
            }
        )
        title.toTitleInfo(extension)
    }
}

suspend fun Backend.createTitle(
    newTitle: NewTitle,
    uid: PrimaryKey
) = checkRootAdminPermission(newTitle.scopeType, newTitle.scopeId, uid).mapResultIfNotNull {
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
        level = 1,
        isPin = false,
        lastModifiedTime = null
    )
    database.topic.createTitle(title, topic).onSuccess {
        // 如果发送的title 类型是JOIN，插入一条Member 记录
        inviteMemberIfNeed(title)
    }.mapResult {
        addUserLog(uid, UserLogType.CREATE, ObjectTuple(title.id, ObjectType.TITLE))
        processTitleList(listOf(RawTitle(title)), uid).mapIfNotNull {
            it.first()
        }
    }
}

private suspend fun Backend.inviteMemberIfNeed(title: Title) {
    if (title.type == TitleType.JOIN) {
        val memberId = SnowflakeFactory.nextId()
        val invitedTime = now()
        database.container.joinContainer(
            Member(
                memberId,
                title.receiver,
                title.scopeId,
                title.scopeType,
                invitedTime,
                MemberStatus.INVITED,
                null,
                invitedTime
            )
        ).onFailure {
            Napier.e(it) {
                "create join title member failed $it"
            }
        }
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

val titleMap = mutableMapOf(
    ObjectType.COMMUNITY to listOf(
        TitleType.REGULAR,
        TitleType.JOIN
    ),
    ObjectType.ROOM to listOf(
        TitleType.REGULAR,
        TitleType.JOIN
    ),
    ObjectType.USER to listOf(
        TitleType.REGULAR
    ),
    ObjectType.TOPIC to listOf(
        TitleType.REGULAR
    )
)

suspend fun createTitle(
    title: NewTitle,
    backend: Backend,
    uid: PrimaryKey
): Result<TitleInfo?> {
    val supportType = titleMap[title.scopeType] ?: return Result.success(null)
    if (!supportType.contains(title.type)) {
        return Result.failure(ForbiddenException("unsupported title type ${title.type} in ${title.scopeType}"))
    }
    return backend.createTitle(title, uid)
}
