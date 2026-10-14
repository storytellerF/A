/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.app.common

import com.storyteller_f.a.api.RoomActivityInfo
import com.storyteller_f.a.api.RoomActivityListResponse
import com.storyteller_f.a.api.TopicInfoListResponse
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.Pagination
import com.storyteller_f.shared.type.ObjectType
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityPreviewHostTest {
    @Test
    fun rowsAreBatchedAndRefreshedOnWorker() =
        runTest {
        val coordination = StandardTestDispatcher(testScheduler, "coordination")
        val worker = StandardTestDispatcher(testScheduler, "worker")
        val requests = mutableListOf<List<Long>>()
        val host =
            ActivityPreviewHost(this, coordination, worker, { ids ->
                assertEquals(worker, currentCoroutineContext()[ContinuationInterceptor])
                requests.add(ids)
                Result.success(RoomActivityListResponse(persistentListOf(activity(ids.first()))))
            }, { Result.success(RoomActivityListResponse(persistentListOf())) }, {
                Result.success(TopicInfoListResponse(persistentListOf()))
            })
        host.showRooms((1L..120L).toList() + 1L)
        runCurrent()
        assertEquals(100, requests.single().size)
        host.showRooms((1L..120L).toList())
        runCurrent()
        assertEquals(1, requests.size)
        host.refreshRows()
        runCurrent()
        assertEquals(2, requests.size)
        host.showRooms(emptyList())
        runCurrent()
        assertTrue(assertIs<PreviewState.Ready<RoomActivityPreview>>(host.rows.value).items.isEmpty())
        assertEquals(2, requests.size)
        host.close()
    }

    @Test
    fun visibleAnchorUsesStableJoinedRoomBatches() =
        runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val batches = mutableListOf<List<Long>>()
        val host =
            ActivityPreviewHost(this, dispatcher, dispatcher, { ids ->
                batches.add(ids)
                Result.success(RoomActivityListResponse(persistentListOf()))
            }, { Result.success(RoomActivityListResponse(persistentListOf())) }, {
                Result.success(TopicInfoListResponse(persistentListOf()))
            })
        val rooms = (1L..120L).map { RoomInfo.EMPTY.copy(id = it, joinedTime = RoomInfo.EMPTY.createdTime) }
        host.showWindow(1, rooms)
        runCurrent()
        host.showWindow(20, rooms)
        runCurrent()
        assertEquals(1, batches.size)
        assertEquals((1L..100L).toList(), batches.first())
        host.showWindow(110, rooms)
        runCurrent()
        assertEquals((101L..120L).toList(), batches.last())
        host.showWindow(110, rooms.map { it.copy(joinedTime = null) })
        runCurrent()
        assertEquals(2, batches.size)
        assertTrue(assertIs<PreviewState.Ready<RoomActivityPreview>>(host.rows.value).items.isEmpty())
        host.close()
    }

    @Test
    fun supersededRequestCannotPublishPrivatePreview() =
        runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val late = CompletableDeferred<RoomActivityListResponse>()
        val host =
            ActivityPreviewHost(this, dispatcher, dispatcher, { ids ->
                if (ids.single() == 1L) {
                    Result.success(withContext(NonCancellable) { late.await() })
                } else {
                    Result.success(RoomActivityListResponse(persistentListOf(activity(2))))
                }
            }, { Result.success(RoomActivityListResponse(persistentListOf())) }, {
                Result.success(TopicInfoListResponse(persistentListOf()))
            })
        host.showRooms(listOf(1))
        runCurrent()
        host.showRooms(listOf(2))
        runCurrent()
        late.complete(RoomActivityListResponse(persistentListOf(activity(1))))
        runCurrent()
        assertEquals(2L, assertIs<PreviewState.Ready<RoomActivityPreview>>(host.rows.value).items.single().room.id)
        host.close()
    }

    @Test
    fun contextStatesFailAndRetryIndependently() =
        runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var failUnread = true
        val host =
            ActivityPreviewHost(this, dispatcher, dispatcher, {
                Result.success(RoomActivityListResponse(persistentListOf()))
            }, {
                if (failUnread) error("Synthetic failure")
                Result.success(RoomActivityListResponse(persistentListOf(activity(1)), Pagination(null, null, 9)))
            }, {
                Result.success(
                    TopicInfoListResponse(persistentListOf(TopicInfo.EMPTY.copy(content = TopicContent.Plain("Pin")))),
                )
            })
        host.refreshUnread()
        host.refreshPinned()
        runCurrent()
        assertIs<PreviewState.Failed>(host.unread.value)
        assertEquals(1, assertIs<PreviewState.Ready<PinnedTopicPreview>>(host.pinned.value).items.size)
        failUnread = false
        host.refreshUnread()
        runCurrent()
        assertEquals(9L, assertIs<PreviewState.Ready<RoomActivityPreview>>(host.unread.value).total)
        host.pause()
        runCurrent()
        assertIs<PreviewState.Idle>(host.unread.value)
        assertIs<PreviewState.Idle>(host.pinned.value)
        host.close()
    }

    @Test
    fun pauseCancelsLoadingWithoutPublishingFailure() =
        runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val pending = CompletableDeferred<RoomActivityListResponse>()
        val host =
            ActivityPreviewHost(this, dispatcher, dispatcher, {
                Result.success(pending.await())
            }, { Result.success(RoomActivityListResponse(persistentListOf())) }, {
                Result.success(TopicInfoListResponse(persistentListOf()))
            })
        host.showRooms(listOf(1))
        runCurrent()
        assertIs<PreviewState.Loading>(host.rows.value)
        host.pause()
        runCurrent()
        pending.complete(RoomActivityListResponse(persistentListOf(activity(1))))
        runCurrent()
        assertIs<PreviewState.Idle>(host.rows.value)
        host.close()
    }

    @Test
    fun roomEventsDoNotReloadPinnedTopics() =
        runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var unreadLoads = 0
        var pinLoads = 0
        val host =
            ActivityPreviewHost(this, dispatcher, dispatcher, {
                Result.success(RoomActivityListResponse(persistentListOf()))
            }, {
                unreadLoads++
                Result.success(RoomActivityListResponse(persistentListOf()))
            }, {
                pinLoads++
                Result.success(TopicInfoListResponse(persistentListOf()))
            })
        host.onEvent(OnRoomRead(1), true)
        runCurrent()
        assertEquals(1, unreadLoads)
        assertEquals(0, pinLoads)
        host.onEvent(OnTopicChanged(TopicInfo.EMPTY.copy(rootType = ObjectType.COMMUNITY)), true)
        runCurrent()
        assertEquals(1, unreadLoads)
        assertEquals(1, pinLoads)
        host.close()
    }

    @Test
    fun unavailableAndEmptyMessagesAreDistinct() {
        val encrypted =
            activity(
                1,
            ).copy(latestMessage = TopicInfo.EMPTY.copy(content = TopicContent.Encrypted("secret", emptyMap())))
        assertIs<MessageSummary.Unavailable>(encrypted.toPreview().message?.summary)
        val failed =
            encrypted.copy(
                latestMessage = TopicInfo.EMPTY.copy(content = TopicContent.DecryptFailed("secret")),
            )
        assertIs<MessageSummary.Unavailable>(failed.toPreview().message?.summary)
        val missing = activity(1).copy(latestMessageId = 12)
        assertIs<MessageSummary.Unavailable>(missing.toPreview().message?.summary)
        assertNull(activity(1).toPreview().message)
        assertIs<MessageSummary.Unavailable>(activity(1).copy(unreadMessageCount = 2).toPreview().message?.summary)
        val plain = activity(1).copy(latestMessage = TopicInfo.EMPTY.copy(content = TopicContent.Plain("hello\nworld")))
        assertEquals("hello world", assertIs<MessageSummary.Text>(plain.toPreview().message?.summary).value)
    }
}

private fun activity(id: Long) = RoomActivityInfo(RoomInfo.EMPTY.copy(id = id), null, null, 0)
