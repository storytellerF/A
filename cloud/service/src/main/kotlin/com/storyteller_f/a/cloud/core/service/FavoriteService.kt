package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.core.NewFavorite
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.addIfNotExists
import com.storyteller_f.a.backend.core.types.UserFavorite
import com.storyteller_f.a.backend.core.types.toUserFavoriteInfo
import com.storyteller_f.shared.model.UserFavoriteInfo
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now

suspend fun Backend.addFavorite(
    uid: PrimaryKey,
    newFavorite: NewFavorite
) = addIfNotExists({
    database.user.getFavorite(uid, newFavorite.objectId)
}) {
    val id = SnowflakeFactory.nextId()
    val userFavorite = UserFavorite(
        id,
        uid,
        newFavorite.objectId,
        newFavorite.objectType,
        now()
    )
    database.user.addFavorite(userFavorite).onSuccess<UserFavorite> {
        addUserLog(uid, UserLogType.ADD_FAVORITE, newFavorite.tuple())
    }
}.mapResultIfNotNull {
    processUserFavoriteToUserFavoriteInfo(uid, listOf(it))
}.mapIfNotNull {
    it.firstOrNull()
}

suspend fun Backend.deleteFavorite(uid: PrimaryKey, id: PrimaryKey) =
    database.user.getFavorite(id).mapResultIfNotNull { userFavorite ->
        if (userFavorite.uid == uid) {
            database.user.removeFavorite(id).onSuccess {
                addUserLog(uid, UserLogType.REMOVE_FAVORITE, userFavorite.objectTuple())
            }
        } else {
            Result.failure(ForbiddenException())
        }
    }

suspend fun Backend.getFavorites(uid: PrimaryKey, fetch: PrimaryKeyFetch) =
    database.user.getUserFavorites(uid, fetch).mapResult {
        processUserFavoriteToUserFavoriteInfo(uid, it.list).map { list ->
            PaginationResult(list, it.total)
        }
    }

suspend fun Backend.processUserFavoriteToUserFavoriteInfo(
    uid: PrimaryKey,
    userFavorite: List<UserFavorite>
) = runCatching {
    userFavorite.map {
        val favoriteInfo = it.toUserFavoriteInfo()
        when (it.objectType) {
            ObjectType.TOPIC -> {
                val topicInfo = getTopic(it.objectId, uid, true).getOrThrow()
                favoriteInfo.copy(extensions = UserFavoriteInfo.Extensions(topicInfo))
            }

            else -> favoriteInfo
        }
    }
}
