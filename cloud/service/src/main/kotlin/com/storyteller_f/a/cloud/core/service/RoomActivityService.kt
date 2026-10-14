/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.cloud.core.service

import com.storyteller_f.a.api.MAX_PAGE_SIZE
import com.storyteller_f.a.api.RoomActivityInfo
import com.storyteller_f.a.api.RoomActivityListResponse
import com.storyteller_f.a.api.RoomActivityQuery
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.getRawTopicListByIds
import com.storyteller_f.a.backend.core.processTopicToRawTopic
import com.storyteller_f.a.backend.core.types.RoomActivityRecord
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.cancellableRunCatching
import com.storyteller_f.shared.utils.mapResult
import kotlinx.collections.immutable.toImmutableList

suspend fun Backend.getRoomActivities(uid: PrimaryKey, query: RoomActivityQuery) =
    cancellableRunCatching {
    val ids = parseRoomActivityIds(query.roomIds)
    val records = database.container.getRoomActivityRecords(uid, ids).getOrThrow()
    val activities = processRoomActivities(uid, records).associateBy { it.room.id }
    RoomActivityListResponse(ids.mapNotNull(activities::get).toImmutableList())
}

suspend fun Backend.getUnreadRoomActivities(uid: PrimaryKey, fetch: PrimaryKeyFetch) =
    cancellableRunCatching {
    checkActivityPageSize(fetch)
    val page = database.container.getUnreadRoomActivityRecords(uid, fetch).getOrThrow()
    PaginationResult(processRoomActivities(uid, page.list), page.total)
}

suspend fun Backend.getJoinedPinnedTopics(uid: PrimaryKey, fetch: PrimaryKeyFetch) =
    cancellableRunCatching {
    checkActivityPageSize(fetch)
    val page = database.topic.getJoinedCommunityPinnedTopics(uid, fetch).getOrThrow()
    val rawTopics = database.processTopicToRawTopic(uid, page.list).getOrThrow()
    PaginationResult(processRawTopicToTopicInfo(rawTopics, uid, false).getOrThrow().orEmpty(), page.total)
}

private suspend fun Backend.processRoomActivities(
    uid: PrimaryKey,
    records: List<RoomActivityRecord>,
): List<RoomActivityInfo> {
    if (records.isEmpty()) return emptyList()
    val rooms =
        getRoomInfoList(ObjectListFetch.IdListFetch(records.map { it.roomId }), uid)
            .getOrThrow().associateBy { it.id }
    val messages =
        database.getRawTopicListByIds(uid, records.mapNotNull { it.latestMessageId }).mapResult {
            processRawTopicToTopicInfo(it, uid, false)
        }.getOrThrow().orEmpty().associateBy { it.id }
    return records.mapNotNull { record ->
        rooms[record.roomId]?.let { room ->
            RoomActivityInfo(room, record.latestMessageId, messages[record.latestMessageId], record.unreadMessageCount)
        }
    }
}

internal fun parseRoomActivityIds(value: String): List<PrimaryKey> {
    val values = value.split(',')
    if (values.size !in 1..MAX_PAGE_SIZE) throw CustomBadRequestException("Invalid room activity batch size")
    return values.map { item ->
        item.toLongOrNull()?.takeIf { it > 0 } ?: throw CustomBadRequestException("Invalid room identifier")
    }.distinct()
}

private fun checkActivityPageSize(fetch: PrimaryKeyFetch) {
    if (fetch.size !in 1..MAX_PAGE_SIZE) throw CustomBadRequestException("Invalid activity page size")
}
