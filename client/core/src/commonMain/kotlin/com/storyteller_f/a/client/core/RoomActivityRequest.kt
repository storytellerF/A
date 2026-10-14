/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.client.core

import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.api.MAX_PAGE_SIZE
import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.api.RoomActivityListResponse
import com.storyteller_f.a.api.RoomActivityQuery
import com.storyteller_f.endpoint4k.ktor.client.invoke
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.collections.immutable.toImmutableList

/** Fetches one bounded batch; private message bodies use the standard client decryption boundary. */
suspend fun UserSessionManager.getRoomActivities(roomIds: List<PrimaryKey>): Result<RoomActivityListResponse> =
    serviceCatching {
        require(roomIds.size in 1..MAX_PAGE_SIZE && roomIds.all { it > 0 }) { "Invalid room activity batch" }
        this@getRoomActivities.decryptRoomActivities(
            CustomApi.Users.RoomActivities.get(RoomActivityQuery(roomIds.distinct().joinToString(","))),
        )
    }

suspend fun UserSessionManager.getUnreadRoomActivities(query: PaginationQuery): Result<RoomActivityListResponse> =
    serviceCatching { this@getUnreadRoomActivities.decryptRoomActivities(CustomApi.Users.Unread.activities(query)) }

suspend fun UserSessionManager.getJoinedPinnedTopics(query: PaginationQuery) =
    serviceCatching { CustomApi.Users.PinnedTopics.get(query) }

private suspend fun UserSessionManager.decryptRoomActivities(
    response: RoomActivityListResponse,
): RoomActivityListResponse {
    val messages = processEncryptedTopic(response.data.mapNotNull { it.latestMessage }, this).associateBy { it.id }
    return response.copy(
        data =
        response.data.map { activity ->
            activity.copy(latestMessage = activity.latestMessage?.let { messages[it.id] })
        }.toImmutableList(),
    )
}
