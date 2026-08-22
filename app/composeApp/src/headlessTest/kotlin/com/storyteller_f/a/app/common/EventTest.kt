/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.app.common

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.storage.ChildAccountStorage
import com.storyteller_f.storage.CollectionListStorageWithDefault
import com.storyteller_f.storage.CommunityInfoStorage
import com.storyteller_f.storage.DownloadInfoStorage
import com.storyteller_f.storage.FileInfoStorage
import com.storyteller_f.storage.FileRefInfoStorage
import com.storyteller_f.storage.MemberInfoStorage
import com.storyteller_f.storage.ModelStorage
import com.storyteller_f.storage.OverviewStorage
import com.storyteller_f.storage.PanelLogInfoStorage
import com.storyteller_f.storage.ReactionInfoStorage
import com.storyteller_f.storage.RemoteKeyStorage
import com.storyteller_f.storage.RoomCollection
import com.storyteller_f.storage.RoomInfoStorage
import com.storyteller_f.storage.TaskRecordInfoStorage
import com.storyteller_f.storage.TitleInfoStorage
import com.storyteller_f.storage.TopicCollection
import com.storyteller_f.storage.TopicInfoStorage
import com.storyteller_f.storage.UploadInfoStorage
import com.storyteller_f.storage.UploadRecordInfoStorage
import com.storyteller_f.storage.UserFavoriteStorage
import com.storyteller_f.storage.UserInfoStorage
import com.storyteller_f.storage.UserLogInfoStorage
import com.storyteller_f.storage.UserOverviewStorage
import com.storyteller_f.storage.UserReactionRecordStorage
import com.storyteller_f.storage.UserSubscriptionStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EventTest {
    @Test
    fun testProcessTopicCreated() =
        runTest {
        val fakeTopicStorage = FakeTopicStorage()
        val fakeStorage = FakeModelStorage(topic = fakeTopicStorage)
        val bus = MutableSharedFlow<Any>()
        val parentId: PrimaryKey = 100L
        val topicInfo = TopicInfo.EMPTY.copy(id = 1L, parentId = parentId)

        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                processEvent(fakeStorage, bus)
            }
        bus.emit(OnTopicCreated(topicInfo))

        val savedFirst = fakeTopicStorage.savedFirst
        assertEquals(1, savedFirst.size)
        assertEquals(TopicCollection.ChildTopicList(parentId), savedFirst[0].first)
        assertEquals(topicInfo, savedFirst[0].second)

        assertEquals(1, fakeTopicStorage.savedDefault.size)
        assertEquals(topicInfo, fakeTopicStorage.savedDefault[0])

        job.cancel()
    }

    @Test
    fun testProcessTopicChanged() =
        runTest {
        val fakeTopicStorage = FakeTopicStorage()
        val fakeStorage = FakeModelStorage(topic = fakeTopicStorage)
        val bus = MutableSharedFlow<Any>()
        val parentId: PrimaryKey = 100L
        val topicId: PrimaryKey = 2L
        val topicInfo =
            TopicInfo.EMPTY.copy(
                id = topicId,
                parentId = parentId,
                content = com.storyteller_f.shared.model.TopicContent.Plain("Updated"),
            )

        // Pre-populate recommend to test updateDocument call
        fakeTopicStorage.documents[TopicCollection.Recommend to topicId.toString()] = topicInfo

        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                processEvent(fakeStorage, bus)
            }
        bus.emit(OnTopicChanged(topicInfo))

        // Check saveToDefault
        assertEquals(1, fakeTopicStorage.savedDefault.size)
        assertEquals(topicInfo, fakeTopicStorage.savedDefault[0])

        // Check updateDocument for Recommend
        val updatedDocs = fakeTopicStorage.updatedDocuments
        val recommendUpdate = updatedDocs.find { it.first == TopicCollection.Recommend }
        assertTrue(recommendUpdate != null, "Should update Recommend collection")
        assertEquals(topicInfo, recommendUpdate.second)

        // Check update on ChildTopicList
        fakeTopicStorage.documents[TopicCollection.ChildTopicList(parentId) to topicId.toString()] = topicInfo

        // Reset and try again to capture update
        fakeTopicStorage.updatedDocuments.clear()

        bus.emit(OnTopicChanged(topicInfo))

        val childListUpdate =
            fakeTopicStorage.updatedDocuments.find {
                it.first == TopicCollection.ChildTopicList(parentId)
            }
        assertTrue(childListUpdate != null, "Should update ChildTopicList collection")
        assertEquals(topicInfo, childListUpdate.second)

        job.cancel()
    }

    @Test
    fun testProcessRoomCreated() =
        runTest {
        val fakeRoomStorage = FakeRoomStorage()
        val fakeStorage = FakeModelStorage(room = fakeRoomStorage)
        val bus = MutableSharedFlow<Any>()
        val communityId: PrimaryKey = 200L
        val roomInfo = RoomInfo.EMPTY.copy(id = 10L, communityId = communityId)

        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                processEvent(fakeStorage, bus)
            }
        bus.emit(OnRoomCreated(roomInfo))

        assertEquals(1, fakeRoomStorage.savedDefault.size)
        assertEquals(roomInfo, fakeRoomStorage.savedDefault[0])

        val savedFirst = fakeRoomStorage.savedFirst
        assertEquals(1, savedFirst.size)
        assertEquals(RoomCollection.CommunityRooms(communityId), savedFirst[0].first)
        assertEquals(roomInfo, savedFirst[0].second)

        job.cancel()
    }

    @Test
    fun testProcessRoomUpdated() =
        runTest {
        val fakeRoomStorage = FakeRoomStorage()
        val fakeStorage = FakeModelStorage(room = fakeRoomStorage)
        val bus = MutableSharedFlow<Any>()
        val communityId: PrimaryKey = 200L
        val roomInfo = RoomInfo.EMPTY.copy(id = 10L, communityId = communityId)

        // Pre-populate CommunityRooms to allow update
        fakeRoomStorage.documents[RoomCollection.CommunityRooms(communityId) to roomInfo.id.toString()] = roomInfo

        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                processEvent(fakeStorage, bus)
            }
        bus.emit(OnRoomUpdated(roomInfo))

        assertEquals(1, fakeRoomStorage.savedDefault.size)
        assertEquals(roomInfo, fakeRoomStorage.savedDefault[0])

        val updatedDocs = fakeRoomStorage.updatedDocuments
        val communityRoomUpdate = updatedDocs.find { it.first == RoomCollection.CommunityRooms(communityId) }
        assertTrue(communityRoomUpdate != null, "Should update CommunityRooms collection")
        assertEquals(roomInfo, communityRoomUpdate.second)

        job.cancel()
    }
}

// Fakes
open class FakeCollectionListStorage<C, I : Any> : CollectionListStorageWithDefault<C, I> {
    val savedDefault = mutableListOf<I>()
    val savedFirst = mutableListOf<Pair<C, I>>()
    val updatedDocuments = mutableListOf<Pair<C, I>>()
    val documents = mutableMapOf<Pair<C, String>, I>()

    override suspend fun saveToDefault(item: I) {
        savedDefault.add(item)
    }

    override suspend fun saveLast(collection: C, item: I) {
        // no-op
    }
    override suspend fun saveFirst(collection: C, item: I) {
        savedFirst.add(collection to item)
    }

    override fun observeData(collection: C): PagingSource<Int, I> = FakePagingSource()
    override suspend fun clean(collection: C) {
        // no-op
    }

    override suspend fun getDocument(collection: C, key: String): I? = documents[collection to key]

    override suspend fun updateDocument(collection: C, item: I) {
        updatedDocuments.add(collection to item)
    }

    override suspend fun delete(collection: C, key: String) {
        // no-op
    }
    override fun observeDatum(key: String): Flow<I?> = emptyFlow()
}

class FakeTopicStorage :
    FakeCollectionListStorage<TopicCollection, TopicInfo>(),
    TopicInfoStorage
class FakeRoomStorage :
    FakeCollectionListStorage<RoomCollection, RoomInfo>(),
    RoomInfoStorage
class FakeUserStorage :
    FakeCollectionListStorage<com.storyteller_f.storage.UserCollection, UserInfo>(),
    UserInfoStorage
class FakeCommunityStorage :
    FakeCollectionListStorage<com.storyteller_f.storage.CommunityCollection, CommunityInfo>(),
    CommunityInfoStorage

class FakePagingSource<I : Any> : PagingSource<Int, I>() {
    override fun getRefreshKey(state: PagingState<Int, I>): Int? = null
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, I> = LoadResult.Page(emptyList(), null, null)
}

private fun unavailableStorage(name: String): Nothing =
    throw UnsupportedOperationException("$name storage is not configured for this focused test")

class FakeModelStorage(
    override val topic: TopicInfoStorage = FakeTopicStorage(),
    override val room: RoomInfoStorage = FakeRoomStorage(),
    override val user: UserInfoStorage = FakeUserStorage(),
    override val community: CommunityInfoStorage = FakeCommunityStorage(),
) : ModelStorage {
    override val title: TitleInfoStorage get() = unavailableStorage("title")
    override val member: MemberInfoStorage get() = unavailableStorage("member")
    override val remoteKey: RemoteKeyStorage get() = unavailableStorage("remote key")
    override val reaction: ReactionInfoStorage get() = unavailableStorage("reaction")
    override val childAccount: ChildAccountStorage get() = unavailableStorage("child account")
    override val fileInfo: FileInfoStorage get() = unavailableStorage("file info")
    override val download: DownloadInfoStorage get() = unavailableStorage("download")
    override val upload: UploadInfoStorage get() = unavailableStorage("upload")
    override val overview: OverviewStorage get() = unavailableStorage("overview")
    override val userOverview: UserOverviewStorage get() = unavailableStorage("user overview")
    override val favorite: UserFavoriteStorage get() = unavailableStorage("favorite")
    override val subscription: UserSubscriptionStorage get() = unavailableStorage("subscription")
    override val userReactionRecord: UserReactionRecordStorage get() = unavailableStorage("reaction record")
    override val userLog: UserLogInfoStorage get() = unavailableStorage("user log")
    override val uploadRecord: UploadRecordInfoStorage get() = unavailableStorage("upload record")
    override val fileRef: FileRefInfoStorage get() = unavailableStorage("file reference")
    override val panelLog: PanelLogInfoStorage get() = unavailableStorage("panel log")
    override val taskRecord: TaskRecordInfoStorage get() = unavailableStorage("task record")
}
