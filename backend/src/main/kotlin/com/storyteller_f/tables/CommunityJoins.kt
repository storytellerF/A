package com.storyteller_f.tables

import com.storyteller_f.DatabaseFactory
import com.storyteller_f.customPrimaryKey
import com.storyteller_f.shared.type.PrimaryKey
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.selectAll

object CommunityJoins : Table() {
    val uid = customPrimaryKey("uid").index()
    val communityId = customPrimaryKey("community_id").index()
    val joinTime = datetime("join_time").index()

    init {
        index("community-join-main", true, uid, communityId)
    }
}

suspend fun isCommunityJoined(communityId: PrimaryKey, uid: PrimaryKey?) = if (uid == null) {
    Result.success(false)
} else {
    DatabaseFactory.isEmpty {
        CommunityJoins.selectAll().where {
            CommunityJoins.communityId eq communityId and (CommunityJoins.uid eq uid)
        }
    }.map { value ->
        !value
    }
}
