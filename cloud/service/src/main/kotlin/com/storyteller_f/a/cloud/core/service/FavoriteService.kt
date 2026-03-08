package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.NewFavorite
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.addIfNotExists
import com.storyteller_f.a.backend.core.mapPagingResultNotNull
import com.storyteller_f.a.backend.core.types.UserFavorite
import com.storyteller_f.a.backend.core.types.toUserFavoriteInfo
import com.storyteller_f.shared.model.UserFavoriteInfo
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.firstOrNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now

suspend fun Backend.addFavorite(
    uid: PrimaryKey,
    newFavorite: NewFavorite
) = addIfNotExists({
    database.favorite.getFavorite(uid, newFavorite.objectId)
}) {
    val id = SnowflakeFactory.nextId()
    val userFavorite = UserFavorite(id, uid, newFavorite.objectId, newFavorite.objectType, now())
    database.favorite.addFavorite(userFavorite).onSuccess {
        addUserLog(uid, UserLogType.ADD_FAVORITE, newFavorite.tuple())
    }
}.mapResultIfNotNull {
    processUserFavoriteToUserFavoriteInfo(uid, listOf(it))
}.firstOrNull()

suspend fun Backend.deleteFavorite(uid: PrimaryKey, id: PrimaryKey) =
    database.favorite.getFavorite(id).mapResultIfNotNull { userFavorite ->
        if (userFavorite.uid == uid) {
            database.favorite.removeFavorite(id).onSuccess {
                addUserLog(uid, UserLogType.REMOVE_FAVORITE, userFavorite.objectTuple())
            }
        } else {
            Result.failure(ForbiddenException())
        }
    }

suspend fun Backend.deleteFavoriteByObject(uid: PrimaryKey, objectId: PrimaryKey) =
    database.favorite.getFavorite(uid, objectId).mapResultIfNotNull { userFavorite ->
        database.favorite.removeFavorite(userFavorite.id).onSuccess {
            addUserLog(uid, UserLogType.REMOVE_FAVORITE, userFavorite.objectTuple())
        }
    }

suspend fun Backend.getFavorites(uid: PrimaryKey, fetch: PrimaryKeyFetch) =
    database.favorite.getUserFavorites(uid, fetch).mapPagingResultNotNull {
        processUserFavoriteToUserFavoriteInfo(uid, it)
    }

suspend fun Backend.processUserFavoriteToUserFavoriteInfo(
    uid: PrimaryKey,
    userFavorite: List<UserFavorite>
) = runCatching {
    val topicIds = mutableListOf<PrimaryKey>()
    val communityIds = mutableListOf<PrimaryKey>()
    val roomIds = mutableListOf<PrimaryKey>()
    val userIds = mutableListOf<PrimaryKey>()
    val titleIds = mutableListOf<PrimaryKey>()
    val fileIds = mutableListOf<PrimaryKey>()

    userFavorite.forEach {
        when (it.objectType) {
            ObjectType.TOPIC -> topicIds.add(it.objectId)
            ObjectType.COMMUNITY -> communityIds.add(it.objectId)
            ObjectType.ROOM -> roomIds.add(it.objectId)
            ObjectType.USER -> userIds.add(it.objectId)
            ObjectType.TITLE -> titleIds.add(it.objectId)
            ObjectType.FILE -> fileIds.add(it.objectId)
            else -> {}
        }
    }

    val topics = getTopicByIds(topicIds, uid).getOrNull()?.associateBy { it.id } ?: emptyMap()
    val communities = database.community.getRawCommunities(ObjectListFetch.IdListFetch(communityIds))
        .mapResult { processRawCommunityToCommunityInfo(it) }
        .getOrNull()?.associateBy { it.id } ?: emptyMap()
    val rooms = getRoomInfoList(ObjectListFetch.IdListFetch(roomIds), uid)
        .getOrNull()?.associateBy { it.id } ?: emptyMap()
    val users = getUserInfoList(ObjectListFetch.IdListFetch(userIds))
        .getOrNull()?.associateBy { it.id } ?: emptyMap()
    val titles = titleIds.mapNotNull { getTitleInfo(it).getOrNull() }.associateBy { it.id }
    val files = database.file.getFileRecordByIds(fileIds)
        .mapResult { processFileRecordToFileInfo(it) }
        .getOrNull()?.associateBy { it.id } ?: emptyMap()

    userFavorite.map {
        val favoriteInfo = it.toUserFavoriteInfo()
        val extensions = when (it.objectType) {
            ObjectType.TOPIC -> UserFavoriteInfo.Extensions(topicInfo = topics[it.objectId])
            ObjectType.COMMUNITY -> UserFavoriteInfo.Extensions(communityInfo = communities[it.objectId])
            ObjectType.ROOM -> UserFavoriteInfo.Extensions(roomInfo = rooms[it.objectId])
            ObjectType.USER -> UserFavoriteInfo.Extensions(userInfo = users[it.objectId])
            ObjectType.TITLE -> UserFavoriteInfo.Extensions(titleInfo = titles[it.objectId])
            ObjectType.FILE -> UserFavoriteInfo.Extensions(fileInfo = files[it.objectId])
            else -> null
        }
        favoriteInfo.copy(extensions = extensions)
    }
}
