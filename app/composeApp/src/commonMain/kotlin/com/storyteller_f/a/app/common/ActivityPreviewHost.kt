/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.app.common

import com.storyteller_f.a.api.MAX_PAGE_SIZE
import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.api.RoomActivityInfo
import com.storyteller_f.a.api.RoomActivityListResponse
import com.storyteller_f.a.api.TopicInfoListResponse
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.cancellableRunCatching
import com.storyteller_f.shared.utils.formatTime
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.ExperimentalTime

private const val CONTEXT_PREVIEW_SIZE = 3

sealed interface PreviewState<out T> {
    data object Idle : PreviewState<Nothing>
    data object Loading : PreviewState<Nothing>
    data class Ready<T>(val items: ImmutableList<T>, val total: Long) : PreviewState<T>
    data object Failed : PreviewState<Nothing>
}

sealed interface MessageSummary {
    data class Text(val value: String) : MessageSummary
    data object Unavailable : MessageSummary
    data object Attachment : MessageSummary
    data object Empty : MessageSummary
}

data class MessagePreview(val summary: MessageSummary, val author: String?, val time: String?)
data class RoomActivityPreview(val room: RoomInfo, val unreadCount: Long, val message: MessagePreview?)
data class PinnedTopicPreview(val id: PrimaryKey, val message: MessagePreview)

/** Owns ephemeral previews; no decrypted private body is written to local storage. */
internal class ActivityPreviewHost(
    parentScope: CoroutineScope,
    coordination: CoroutineDispatcher,
    private val worker: CoroutineDispatcher,
    private val fetchRows: suspend (List<PrimaryKey>) -> Result<RoomActivityListResponse>,
    private val fetchUnread: suspend (PaginationQuery) -> Result<RoomActivityListResponse>,
    private val fetchPinned: suspend (PaginationQuery) -> Result<TopicInfoListResponse>,
) {
    private val scope =
        CoroutineScope(
            parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]) + coordination,
        )
    private val mutableRows = MutableStateFlow<PreviewState<RoomActivityPreview>>(PreviewState.Idle)
    private val mutableUnread = MutableStateFlow<PreviewState<RoomActivityPreview>>(PreviewState.Idle)
    private val mutablePinned = MutableStateFlow<PreviewState<PinnedTopicPreview>>(PreviewState.Idle)
    val rows = mutableRows.asStateFlow()
    val unread = mutableUnread.asStateFlow()
    val pinned = mutablePinned.asStateFlow()
    private var rowIds: List<PrimaryKey> = emptyList()
    private var rowJob: Job? = null
    private var unreadJob: Job? = null
    private var pinnedJob: Job? = null

    fun showRooms(ids: List<PrimaryKey>, force: Boolean = false) = scope.launch { loadRows(ids, force) }

    /** Stable loaded batches avoid layout/request feedback when previews change row heights. */
    fun showWindow(anchorId: PrimaryKey?, rooms: List<RoomInfo>) =
        scope.launch {
        val anchor = rooms.indexOfFirst { it.id == anchorId }.coerceAtLeast(0)
        val start = anchor / MAX_PAGE_SIZE * MAX_PAGE_SIZE
        val batch = rooms.subList(start, minOf(start + MAX_PAGE_SIZE, rooms.size)).asSequence()
        loadRows(batch.filter { it.isJoined }.map { it.id }.toList(), false)
    }

    private fun loadRows(ids: List<PrimaryKey>, force: Boolean) {
        val requested = ids.distinct().take(MAX_PAGE_SIZE)
        if (!force && requested == rowIds && mutableRows.value !is PreviewState.Idle) return
        rowIds = requested
        rowJob?.cancel()
        if (requested.isEmpty()) {
            mutableRows.value = PreviewState.Ready(emptyList<RoomActivityPreview>().toImmutableList(), 0)
        } else {
            rowJob = load(mutableRows) { fetchRows(requested).map { response -> response.previewState() } }
        }
    }

    fun refreshRows() = scope.launch { loadRows(rowIds, true) }

    fun refreshUnread() =
        scope.launch {
        unreadJob?.cancel()
        unreadJob =
            load(
                mutableUnread,
            ) { fetchUnread(PaginationQuery(size = CONTEXT_PREVIEW_SIZE)).map { it.previewState() } }
    }

    fun refreshPinned() =
        scope.launch {
        pinnedJob?.cancel()
        pinnedJob =
            load(mutablePinned) {
                fetchPinned(PaginationQuery(size = CONTEXT_PREVIEW_SIZE)).map { response ->
                    PreviewState.Ready(
                        response.data.map { PinnedTopicPreview(it.id, it.messagePreview()) }.toImmutableList(),
                        response.pagination?.total ?: response.data.size.toLong(),
                    )
                }
            }
    }

    fun onEvent(event: Any, context: Boolean) {
        val roomsChanged =
            when (event) {
                is OnTopicCreated -> event.topicInfo.rootType == ObjectType.ROOM

                is OnTopicChanged -> event.topicInfo.rootType == ObjectType.ROOM

                is OnRoomRead, is OnRoomCreated, is OnRoomJoined, is OnRoomExited, is OnRoomUpdated,
                is OnCommunityJoined, is OnCommunityExited,
                -> true

                else -> false
            }
        if (roomsChanged) {
            if (context) refreshUnread() else refreshRows()
        }
        if (context) {
            val pinsChanged =
                when (event) {
                    is OnTopicCreated -> event.topicInfo.rootType == ObjectType.COMMUNITY && event.topicInfo.isPin
                    is OnTopicChanged -> event.topicInfo.rootType == ObjectType.COMMUNITY
                    is OnCommunityJoined, is OnCommunityExited -> true
                    else -> false
                }
            if (pinsChanged) refreshPinned()
        }
    }

    fun pause() =
        scope.launch {
        rowJob?.cancel()
        unreadJob?.cancel()
        pinnedJob?.cancel()
        mutableRows.value = PreviewState.Idle
        mutableUnread.value = PreviewState.Idle
        mutablePinned.value = PreviewState.Idle
    }

    fun close() = scope.cancel()

    private fun <T> load(
        state: MutableStateFlow<PreviewState<T>>,
        source: suspend () -> Result<PreviewState.Ready<T>>,
    ) = scope.launch {
        state.value = PreviewState.Loading
        val result = withContext(worker) { cancellableRunCatching { source().getOrThrow() } }
        ensureActive()
        state.value = result.getOrElse { PreviewState.Failed }
    }
}

private fun RoomActivityListResponse.previewState() =
    PreviewState.Ready(
    data.map { it.toPreview() }.toImmutableList(),
    pagination?.total ?: data.size.toLong(),
)

internal fun RoomActivityInfo.toPreview(): RoomActivityPreview {
    val message =
        latestMessage?.messagePreview() ?: if (latestMessageId != null || unreadMessageCount > 0) {
            MessagePreview(MessageSummary.Unavailable, null, null)
        } else {
            null
        }
    return RoomActivityPreview(room, unreadMessageCount, message)
}

@OptIn(ExperimentalTime::class)
private fun TopicInfo.messagePreview() =
    MessagePreview(
    when (val body = content) {
        is TopicContent.Plain -> summarizeMessage(body.plain, body.fileInfos.isNotEmpty())
        is TopicContent.Extracted -> summarizeMessage(body.plain, body.fileInfos.isNotEmpty())
        else -> MessageSummary.Unavailable
    },
    extension?.authorInfo?.nickname?.takeIf { it.isNotBlank() },
    createdTime.formatTime(),
)

private fun summarizeMessage(text: String, hasFiles: Boolean): MessageSummary {
    val preview = text.take(180).replace('\n', ' ').replace('\r', ' ').trim()
    return when {
        preview.isNotEmpty() -> MessageSummary.Text(preview)
        hasFiles -> MessageSummary.Attachment
        else -> MessageSummary.Empty
    }
}
