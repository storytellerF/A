/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.api

import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.ListResponse
import com.storyteller_f.shared.obj.Pagination
import com.storyteller_f.shared.type.CustomImmutableList
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable

/** Counts existing direct room messages after the read marker; replies are excluded. */
@Serializable
data class RoomActivityInfo(
    val room: RoomInfo,
    val latestMessageId: PrimaryKey?,
    val latestMessage: TopicInfo?,
    val unreadMessageCount: Long,
)

@Serializable
data class RoomActivityListResponse(
    override val data: CustomImmutableList<RoomActivityInfo>,
    override val pagination: Pagination<String>? = null,
) : ListResponse<RoomActivityInfo>

/** Comma-separated room IDs, at most 100. Inaccessible or unjoined rooms are omitted. */
@Serializable
data class RoomActivityQuery(val roomIds: String)
