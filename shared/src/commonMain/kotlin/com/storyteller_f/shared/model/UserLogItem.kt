/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class UserLogItem(
    val logInfo: UserLogInfo,
    val user: UserInfo? = null,
    val topic: TopicDetail? = null,
    val community: CommunityInfo? = null,
    val room: RoomInfo? = null,
) : PrimaryKeyIdentifiable {
    override val id: PrimaryKey get() = logInfo.id
    val uid: PrimaryKey get() = logInfo.uid
    val type: UserLogType get() = logInfo.type
    val objectId: PrimaryKey get() = logInfo.objectId
    val objectType: ObjectType get() = logInfo.objectType
    val createdTime: LocalDate get() = logInfo.createdTime
}
