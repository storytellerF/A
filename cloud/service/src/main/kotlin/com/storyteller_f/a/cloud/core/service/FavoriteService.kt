package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.UserFavorite
import com.storyteller_f.a.backend.core.types.toUserFavoriteInfo
import com.storyteller_f.shared.model.UserFavoriteInfo
import com.storyteller_f.shared.obj.NewFavorite
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now

suspend fun Backend.addFavorite(
    uid: PrimaryKey,
    newFavorite: NewFavorite
): Result<UserFavoriteInfo> {
    val id = SnowflakeFactory.nextId()
    val userFavorite = UserFavorite(
        id,
        uid,
        newFavorite.objectId,
        newFavorite.objectType,
        now()
    )
    return combinedDatabase.userDatabase.addFavorite(userFavorite).mapResult {
        processUserFavoriteToUserFavoriteInfo(uid, listOf(userFavorite))
    }.map {
        it.first()
    }
}

suspend fun Backend.deleteFavorite(uid: PrimaryKey, id: PrimaryKey) =
    combinedDatabase.userDatabase.getFavorite(id).mapResultIfNotNull {
        if (it.uid == uid) {
            combinedDatabase.userDatabase.removeFavorite(id)
        } else {
            Result.failure(ForbiddenException())
        }
    }.mapIfNotNull {
    }

suspend fun Backend.getFavorites(uid: PrimaryKey, fetch: PrimaryKeyFetch) =
    combinedDatabase.userDatabase.getUserFavorites(uid, fetch).mapResult {
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
