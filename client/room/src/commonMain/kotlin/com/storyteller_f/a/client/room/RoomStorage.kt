package com.storyteller_f.a.client.room

import androidx.paging.PagingSource
import com.storyteller_f.shared.model.ChildAccountInfo
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.storage.ChildAccountCollection
import com.storyteller_f.storage.ChildAccountStorage
import com.storyteller_f.storage.CommunityCollection
import com.storyteller_f.storage.CommunityInfoStorage
import com.storyteller_f.storage.DownloadCollection
import com.storyteller_f.storage.DownloadInfo
import com.storyteller_f.storage.DownloadInfoStorage
import com.storyteller_f.storage.FileInfoStorage
import com.storyteller_f.storage.MediasCollection
import com.storyteller_f.storage.ModelStorage
import com.storyteller_f.storage.ReactionCollection
import com.storyteller_f.storage.ReactionInfoStorage
import com.storyteller_f.storage.RemoteKeyStorage
import com.storyteller_f.storage.RemoteKeyStorage.Companion.NEXT_COLLECTION
import com.storyteller_f.storage.RemoteKeyStorage.Companion.PRE_COLLECTION
import com.storyteller_f.storage.RemoteKeys
import com.storyteller_f.storage.RoomCollection
import com.storyteller_f.storage.RoomInfoStorage
import com.storyteller_f.storage.TitleCollection
import com.storyteller_f.storage.TitleInfoStorage
import com.storyteller_f.storage.TopicCollection
import com.storyteller_f.storage.TopicInfoStorage
import com.storyteller_f.storage.UserCollection
import com.storyteller_f.storage.UserInfoStorage
import com.storyteller_f.storage.WrappedPagingSource
import com.storyteller_f.storage.getName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class UserRoomInfoStorage(val appDatabase: AppDatabase) : UserInfoStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(
        collection: UserCollection,
        userInfo: UserInfo
    ) {
        val data = Json.encodeToString(userInfo)
        val item = CommonEntity(userInfo.id, UserCollection.Users.getName(), data)
        appDatabase.getCommonDao().insert(item)
        if (collection is UserCollection.SearchUser) {
            appDatabase.getCommonDao().insert(item.copy(collection = collection.getName()))
        }
    }

    override fun observeData(
        collection: UserCollection,
    ): PagingSource<Int, UserInfo> {
        return impl.observeData(collection.getName())
    }

    override fun observeDatum(
        key: String
    ): Flow<UserInfo?> {
        return impl.observeDatum(UserCollection.Users.getName(), key)
    }

    override suspend fun clean(collection: UserCollection) {
        appDatabase.getCommonDao().clean(collection.getName())
    }

    override fun observeDatum(id: PrimaryKey): Flow<UserInfo?> {
        return observeDatum(id.toString())
    }
}

class CommunityRoomInfoStorage(val appDatabase: AppDatabase) : CommunityInfoStorage {
    override suspend fun save(
        collection: CommunityCollection,
        communityInfo: CommunityInfo
    ) {
        val data = Json.encodeToString(communityInfo)
        val item =
            CommunityEntity(communityInfo.id, CommunityCollection.Communities.getName(), data, communityInfo.hasPoster)
        appDatabase.getCommunityDao().insert(item)
        if (collection is CommunityCollection.SearchCommunity) {
            appDatabase.getCommunityDao().insert(item.copy(collection = collection.getName()))
        }
    }

    override fun observeData(
        collection: CommunityCollection,
    ): PagingSource<Int, CommunityInfo> {
        val source = appDatabase.getCommunityDao().getAsSource(collection.getName())
        return WrappedPagingSource(source) { list ->
            list.map {
                Json.decodeFromString(it.data)
            }
        }
    }

    override fun observeDatum(key: String): Flow<CommunityInfo?> {
        val scope = CommunityCollection.Communities.getName()
        return appDatabase.getCommunityDao().getAsFlow(scope, key).map {
            it?.let { it1 -> Json.decodeFromString(it1.data) }
        }
    }

    override fun observeDatum(id: PrimaryKey): Flow<CommunityInfo?> {
        return observeDatum(id.toString())
    }

    override suspend fun getDocument(
        collection: CommunityCollection,
        id: PrimaryKey
    ): CommunityInfo? {
        val entity = appDatabase.getCommunityDao().get(collection.getName(), id.toString())
        return entity?.data?.let { Json.decodeFromString(it) }
    }

    override suspend fun clean(collection: CommunityCollection) {
        appDatabase.getCommunityDao().clean(collection.getName())
    }
}

class TopicRoomInfoStorage(val appDatabase: AppDatabase) : TopicInfoStorage {
    override suspend fun save(
        collection: TopicCollection,
        topicInfo: TopicInfo
    ) {
        val data = Json.encodeToString(topicInfo)
        val item = TopicEntity(topicInfo.id, TopicCollection.Topics.getName(), data, topicInfo.isPin)
        appDatabase.getTopicDao().insert(item)
        if (collection is TopicCollection.TopicList ||
            collection is TopicCollection.SearchTopic ||
            collection is TopicCollection.Recommend
        ) {
            appDatabase.getTopicDao().insert(item.copy(collection = collection.getName()))
        }
    }

    override fun observeData(
        collection: TopicCollection,
    ): PagingSource<Int, TopicInfo> {
        val source = appDatabase.getTopicDao().getAsSource(collection.getName())
        return WrappedPagingSource(source) { list ->
            list.map {
                Json.decodeFromString(it.data)
            }
        }
    }

    override fun observeDatum(key: String): Flow<TopicInfo?> {
        val scope = TopicCollection.Topics.getName()
        return appDatabase.getTopicDao().getAsFlow(scope, key).map {
            it?.data?.let { string -> Json.decodeFromString(string) }
        }
    }

    override fun observeDatum(id: PrimaryKey): Flow<TopicInfo?> {
        return observeDatum(id.toString())
    }

    override suspend fun getDocument(
        collection: TopicCollection,
        id: PrimaryKey
    ): TopicInfo? {
        val entity = appDatabase.getTopicDao().get(collection.getName(), id.toString())
        return entity?.data?.let { Json.decodeFromString(it) }
    }

    override suspend fun clean(collection: TopicCollection) {
        appDatabase.getTopicDao().clean(collection.getName())
    }
}

class TitleRoomInfoStorage(val appDatabase: AppDatabase) : TitleInfoStorage {
    override suspend fun save(
        collection: TitleCollection,
        titleInfo: TitleInfo
    ) {
        val data = Json.encodeToString(titleInfo)
        val item = CommonEntity(titleInfo.id, TitleCollection.Titles.getName(), data)
        appDatabase.getCommonDao().insert(item)
        if (collection is TitleCollection.SearchTitle) {
            appDatabase.getCommonDao().insert(item.copy(collection = collection.getName()))
        }
    }

    override fun observeData(
        collection: TitleCollection,
    ): PagingSource<Int, TitleInfo> {
        val source = appDatabase.getCommonDao().getAsSource(collection.getName())
        return WrappedPagingSource(source) { list ->
            list.map {
                Json.decodeFromString(it.data)
            }
        }
    }

    override suspend fun clean(collection: TitleCollection) {
        appDatabase.getCommonDao().clean(collection.getName())
    }

    override fun observeDatum(id: PrimaryKey): Flow<TitleInfo?> {
        val scope = TitleCollection.Titles.getName()
        return appDatabase.getTopicDao().getAsFlow(scope, id.toString()).map {
            it?.data?.let { string -> Json.decodeFromString(string) }
        }
    }
}

class CommonStorageImpl(val appDatabase: AppDatabase) {
    inline fun <reified T : Any> observeData(
        collection: String,
    ): PagingSource<Int, T> {
        val source = appDatabase.getCommonDao().getAsSource(collection)
        return WrappedPagingSource(source) { list ->
            list.map {
                Json.decodeFromString(it.data)
            }
        }
    }

    inline fun <reified T : Any> observeDatum(
        collection: String,
        id: String
    ): Flow<T?> {
        val source = appDatabase.getCommonDao().getAsFlow(collection, id)
        return source.map {
            it?.data?.let { string -> Json.decodeFromString(string) }
        }
    }
}

class RoomRoomInfoStorage(val appDatabase: AppDatabase) : RoomInfoStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(
        collection: RoomCollection,
        roomInfo: RoomInfo
    ) {
        val data = Json.encodeToString(roomInfo)
        val item = CommonEntity(roomInfo.id, RoomCollection.Rooms.getName(), data)
        appDatabase.getCommonDao().insert(item)
        if (collection is RoomCollection.SearchRoom) {
            appDatabase.getCommonDao().insert(item.copy(collection = collection.getName()))
        }
    }

    override fun observeData(
        collection: RoomCollection,
    ): PagingSource<Int, RoomInfo> {
        return impl.observeData(collection.getName())
    }

    override fun observeDatum(key: String): Flow<RoomInfo?> {
        return impl.observeDatum(RoomCollection.Rooms.getName(), key)
    }

    override suspend fun clean(collection: RoomCollection) = Unit

    override fun observeDatum(id: PrimaryKey): Flow<RoomInfo?> {
        return observeDatum(id.toString())
    }
}

class RemoteKeyRoomStorage(val appDatabase: AppDatabase) : RemoteKeyStorage {

    override suspend fun getPreRemoteKey(collection: String): RemoteKeys? {
        return appDatabase.getCommonDao().get(PRE_COLLECTION, collection)?.let {
            Json.decodeFromString(it.data)
        }
    }

    override suspend fun getNextRemoteKey(collection: String): RemoteKeys? {
        return appDatabase.getCommonDao().get(NEXT_COLLECTION, collection)?.let {
            Json.decodeFromString(it.data)
        }
    }

    override suspend fun savePreRemoteKey(remoteKeys: RemoteKeys) {
        appDatabase.getCommonDao()
            .insert(
                CommonEntity(
                    remoteKeys.collectionName,
                    PRE_COLLECTION,
                    Json.encodeToString(remoteKeys)
                )
            )
    }

    override suspend fun saveNextRemoteKey(remoteKeys: RemoteKeys) {
        appDatabase.getCommonDao()
            .insert(
                CommonEntity(
                    remoteKeys.collectionName,
                    NEXT_COLLECTION,
                    Json.encodeToString(remoteKeys)
                )
            )
    }

    override suspend fun deletePreRemoteKey(collection: String) {
        appDatabase.getCommonDao().delete(PRE_COLLECTION, collection)
    }

    override suspend fun deleteNextRemoteKey(collection: String) {
        appDatabase.getCommonDao().delete(NEXT_COLLECTION, collection)
    }
}

class ReactionRoomInfoStorage(val appDatabase: AppDatabase) : ReactionInfoStorage {
    override suspend fun save(
        collection: ReactionCollection,
        reactionInfo: ReactionInfo
    ) {
        val id = "${reactionInfo.objectId}-${reactionInfo.emoji}"
        val data = Json.encodeToString(reactionInfo)
        appDatabase.getReactionDao()
            .insert(ReactionEntity(id, collection.getName(), data, reactionInfo.count, reactionInfo.lastReactionId))
    }

    override fun observeData(
        collection: ReactionCollection,
    ): PagingSource<Int, ReactionInfo> {
        val raw = appDatabase.getReactionDao().getAsSource(collection.getName())
        return WrappedPagingSource(raw) { list ->
            list.map {
                Json.decodeFromString(it.data)
            }
        }
    }

    override suspend fun clean(collection: ReactionCollection) {
        appDatabase.getReactionDao().clean(collection.getName())
    }
}

class ChildAccountRoomStorage(val appDatabase: AppDatabase) : ChildAccountStorage {
    override suspend fun save(collection: ChildAccountCollection, childAccountInfo: ChildAccountInfo) {
        val data = Json.encodeToString(childAccountInfo)
        appDatabase.getCommonDao().insert(CommonEntity(childAccountInfo.id, collection.NAME, data))
    }

    override fun observeData(collection: ChildAccountCollection): PagingSource<Int, ChildAccountInfo> {
        val raw = appDatabase.getCommonDao().getAsSource(collection.NAME)
        return WrappedPagingSource(raw) { list ->
            list.map {
                Json.decodeFromString(it.data)
            }
        }
    }

    override suspend fun clean(collection: ChildAccountCollection) {
        appDatabase.getCommonDao().clean(collection.NAME)
    }
}

class FileInfoRoomStorage(val appDatabase: AppDatabase) : FileInfoStorage {
    override suspend fun save(
        collection: MediasCollection,
        fileInfo: FileInfo
    ) {
        val data = Json.encodeToString(fileInfo)
        appDatabase.getCommonDao().insert(CommonEntity(fileInfo.id, collection.getName(), data))
    }

    override fun observeData(collection: MediasCollection): PagingSource<Int, FileInfo> {
        val raw = appDatabase.getCommonDao().getAsSource(collection.getName())
        return WrappedPagingSource(raw) { list ->
            list.map {
                Json.decodeFromString(it.data)
            }
        }
    }

    override suspend fun clean(collection: MediasCollection) {
        appDatabase.getCommonDao().clean(collection.getName())
    }
}

class DownloadInfoRoomStorage(val appDatabase: AppDatabase) : DownloadInfoStorage {
    override suspend fun save(collection: DownloadCollection, downloadInfo: DownloadInfo) {
        val data = Json.encodeToString(downloadInfo)
        appDatabase.getCommonDao()
            .insert(CommonEntity(downloadInfo.fileInfo.id, collection.NAME, data))
    }

    override fun observeDatum(id: PrimaryKey): Flow<DownloadInfo?> {
        return appDatabase.getCommonDao()
            .getAsFlow(DownloadCollection.NAME, id.toString()).map {
                it?.data?.let { string -> Json.decodeFromString(string) }
            }
    }

    override suspend fun getDocument(
        collection: DownloadCollection,
        id: PrimaryKey
    ): DownloadInfo? {
        return appDatabase.getCommonDao().get(collection.NAME, id.toString())?.let {
            Json.decodeFromString(it.data)
        }
    }
}

class RoomModelStorage(appDatabase: AppDatabase) : ModelStorage {
    override val userInfoStorage: UserInfoStorage = UserRoomInfoStorage(appDatabase)
    override val communityInfoStorage: CommunityInfoStorage = CommunityRoomInfoStorage(appDatabase)
    override val topicInfoStorage: TopicInfoStorage = TopicRoomInfoStorage(appDatabase)
    override val titleInfoStorage: TitleInfoStorage = TitleRoomInfoStorage(appDatabase)
    override val roomInfoStorage: RoomInfoStorage = RoomRoomInfoStorage(appDatabase)
    override val remoteKeyStorage: RemoteKeyStorage = RemoteKeyRoomStorage(appDatabase)
    override val reactionInfoStorage: ReactionInfoStorage = ReactionRoomInfoStorage(appDatabase)
    override val childAccountStorage: ChildAccountStorage = ChildAccountRoomStorage(appDatabase)
    override val fileInfoStorage: FileInfoStorage = FileInfoRoomStorage(appDatabase)
    override val downloadInfoStorage: DownloadInfoStorage = DownloadInfoRoomStorage(appDatabase)
}
