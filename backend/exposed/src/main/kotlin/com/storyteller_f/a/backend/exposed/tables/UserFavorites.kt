package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.types.UserFavorite
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.a.backend.exposed.objectType
import org.jetbrains.exposed.v1.core.ResultRow

object UserFavorites : BaseTable() {
    val uid = customPrimaryKey("uid")
    val objectId = customPrimaryKey("object_id")
    val objectType = objectType("object_type")

    init {
        uniqueIndex("main", uid, objectId)
    }
}

fun UserFavorite.Companion.wrapRow(resultRow: ResultRow): UserFavorite {
    return UserFavorite(
        resultRow[UserFavorites.id],
        resultRow[UserFavorites.uid],
        resultRow[UserFavorites.objectId],
        resultRow[UserFavorites.objectType],
        resultRow[UserFavorites.createdTime],
    )
}
