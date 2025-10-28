package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.UserFavoriteInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

class UserFavorite(
    val id: PrimaryKey,
    val uid: PrimaryKey,
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    val createdTime: LocalDateTime,
) {
    companion object
}

fun UserFavorite.toUserFavoriteInfo(): UserFavoriteInfo {
    return UserFavoriteInfo(id, uid, objectId, objectType, createdTime)
}
