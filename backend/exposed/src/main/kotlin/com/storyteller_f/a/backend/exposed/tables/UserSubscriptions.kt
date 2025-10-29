package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.types.UserSubscription
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.a.backend.exposed.objectType
import org.jetbrains.exposed.v1.core.ResultRow

object UserSubscriptions : BaseTable() {
    val uid = customPrimaryKey("uid")
    val objectId = customPrimaryKey("object_id")
    val objectType = objectType("object_type")

    init {
        uniqueIndex("subscription-main", uid, objectId)
    }
}

fun UserSubscription.Companion.wrapRow(resultRow: ResultRow): UserSubscription {
    return UserSubscription(
        resultRow[UserSubscriptions.id],
        resultRow[UserSubscriptions.uid],
        resultRow[UserSubscriptions.objectId],
        resultRow[UserSubscriptions.objectType],
        resultRow[UserSubscriptions.createdTime],
    )
}
