package com.storyteller_f.a.client.room

import androidx.paging.PagingSource
import com.storyteller_f.shared.model.AlternativeAccountInfo
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.storage.AlternativesCollection
import com.storyteller_f.storage.AlternativesStorage
import com.storyteller_f.storage.CommunityCollection
import com.storyteller_f.storage.CommunityStorage
import com.storyteller_f.storage.DownloadCollection
import com.storyteller_f.storage.DownloadInfo
import com.storyteller_f.storage.DownloadStorage
import com.storyteller_f.storage.MediasCollection
import com.storyteller_f.storage.ModelStorage
import com.storyteller_f.storage.OSSStorage
import com.storyteller_f.storage.ReactionCollection
import com.storyteller_f.storage.ReactionStorage
import com.storyteller_f.storage.RemoteKeyStorage
import com.storyteller_f.storage.RemoteKeys
import com.storyteller_f.storage.RoomCollection
import com.storyteller_f.storage.RoomStorage
import com.storyteller_f.storage.TitleCollection
import com.storyteller_f.storage.TitleStorage
import com.storyteller_f.storage.TopicCollection
import com.storyteller_f.storage.TopicStorage
import com.storyteller_f.storage.UserCollection
import com.storyteller_f.storage.UserStorage
import com.storyteller_f.storage.WrappedPagingSource
import com.storyteller_f.storage.getName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class UserRoomStorage(val appDatabase: AppDatabase) : UserStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(
        collection: UserCollection,
        t: UserInfo
    ) {
        val data = Json.encodeToString(t)
        val item = CommonEntity(t.id, UserCollection.Users.getName(), data)
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

    override fun observeDatum(id: PrimaryKey): Flow<UserInfo?> {
        return observeDatum(id.toString())
    }
}

class CommunityRoomStorage(val appDatabase: AppDatabase) : CommunityStorage {
    override suspend fun save(
        collection: CommunityCollection,
        t: CommunityInfo
    ) {
        val data = Json.encodeToString(t)
        val item =
            CommunityEntity(t.id, CommunityCollection.Communities.getName(), data, t.hasPoster)
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
}

class TopicRoomStorage(val appDatabase: AppDatabase) : TopicStorage {
    override suspend fun save(
        collection: TopicCollection,
        t: TopicInfo
    ) {
        val data = Json.encodeToString(t)
        val item = TopicEntity(t.id, TopicCollection.Topics.getName(), data, t.isPin)
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
}

class TitleRoomStorage(val appDatabase: AppDatabase) : TitleStorage {
    override suspend fun save(
        collection: TitleCollection,
        t: TitleInfo
    ) {
        val data = Json.encodeToString(t)
        val item = CommonEntity(t.id, TitleCollection.Titles.getName(), data)
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
            list.mapNotNull {
                it.data?.let { string -> Json.decodeFromString(string) }
            }
        }
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
            list.mapNotNull {
                it.data?.let { string -> Json.decodeFromString(string) }
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

class RoomRoomStorage(val appDatabase: AppDatabase) : RoomStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(
        collection: RoomCollection,
        t: RoomInfo
    ) {
        val data = Json.encodeToString(t)
        val item = CommonEntity(t.id, RoomCollection.Rooms.getName(), data)
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

    override fun observeDatum(id: PrimaryKey): Flow<RoomInfo?> {
        return observeDatum(id.toString())
    }
}

class RemoteKeyRoomStorage(val appDatabase: AppDatabase) : RemoteKeyStorage {
    override suspend fun getPreRemoteKey(collection: String): RemoteKeys? {
        return appDatabase.getCommonDao().get("pre_remote_keys", collection)?.let {
            RemoteKeys(collection, it.data)
        }
    }

    override suspend fun getNextRemoteKey(collection: String): RemoteKeys? {
        return appDatabase.getCommonDao().get("next_remote_keys", collection)?.let {
            RemoteKeys(collection, it.data)
        }
    }

    override suspend fun savePreRemoteKey(remoteKeys: RemoteKeys) {
        appDatabase.getCommonDao()
            .insert(CommonEntity(remoteKeys.collectionName, "pre_remote_keys", remoteKeys.key))
    }

    override suspend fun saveNextRemoteKey(remoteKeys: RemoteKeys) {
        appDatabase.getCommonDao()
            .insert(CommonEntity(remoteKeys.collectionName, "next_remote_keys", remoteKeys.key))
    }

    override suspend fun deletePreRemoteKey(collection: String) {
        appDatabase.getCommonDao().delete("pre_remote_keys", collection)
    }

    override suspend fun deleteNextRemoteKey(collection: String) {
        appDatabase.getCommonDao().delete("next_remote_keys", collection)
    }
}

class ReactionRoomStorage(val appDatabase: AppDatabase) : ReactionStorage {
    override suspend fun save(
        collection: ReactionCollection,
        t: ReactionInfo
    ) {
        val id = "${t.objectId}-${t.emoji}"
        val data = Json.encodeToString(t)
        appDatabase.getReactionDao()
            .insert(ReactionEntity(id, collection.getName(), data, t.count, t.lastReactionId))
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
}

class AlternativesRoomStorage(val appDatabase: AppDatabase) : AlternativesStorage {
    override suspend fun save(collection: AlternativesCollection, t: AlternativeAccountInfo) {
        val data = Json.encodeToString(t)
        appDatabase.getCommonDao().insert(CommonEntity(t.id, collection.name, data))
    }

    override fun observeData(collection: AlternativesCollection): PagingSource<Int, AlternativeAccountInfo> {
        val raw = appDatabase.getReactionDao().getAsSource(collection.name)
        return WrappedPagingSource(raw) { list ->
            list.map {
                Json.decodeFromString(it.data)
            }
        }
    }
}

class OSSRoomStorage(val appDatabase: AppDatabase) : OSSStorage {
    override suspend fun save(
        collection: MediasCollection,
        t: MediaInfo
    ) {
        val data = Json.encodeToString(t)
        appDatabase.getCommonDao().insert(CommonEntity(t.id, collection.getName(), data))
    }

    override fun observeData(collection: MediasCollection): PagingSource<Int, MediaInfo> {
        val raw = appDatabase.getReactionDao().getAsSource(collection.getName())
        return WrappedPagingSource(raw) { list ->
            list.map {
                Json.decodeFromString(it.data)
            }
        }
    }
}

class DownloadRoomStorage(val appDatabase: AppDatabase) : DownloadStorage {
    override suspend fun save(collection: DownloadCollection, t: DownloadInfo) {
        val data = Json.encodeToString(t)
        appDatabase.getCommonDao()
            .insert(CommonEntity(t.mediaInfo.id, collection.name, data))
    }

    override fun observeDatum(id: PrimaryKey): Flow<DownloadInfo?> {
        return appDatabase.getCommonDao()
            .getAsFlow(DownloadCollection.name, id.toString()).map {
                it?.data?.let { string -> Json.decodeFromString(string) }
            }
    }

    override suspend fun getDocument(
        collection: DownloadCollection,
        id: PrimaryKey
    ): DownloadInfo? {
        return appDatabase.getCommonDao().get(collection.name, id.toString())?.let {
            it.data?.let { string -> Json.decodeFromString(string) }
        }
    }
}

class RoomModelStorage(val appDatabase: AppDatabase) : ModelStorage {
    override val userStorage: UserStorage
        get() = UserRoomStorage(appDatabase)
    override val communityStorage: CommunityStorage
        get() = CommunityRoomStorage(appDatabase)
    override val topicStorage: TopicStorage
        get() = TopicRoomStorage(appDatabase)
    override val titleStorage: TitleStorage
        get() = TitleRoomStorage(appDatabase)
    override val roomStorage: RoomStorage
        get() = RoomRoomStorage(appDatabase)
    override val remoteKeyStorage: RemoteKeyStorage
        get() = RemoteKeyRoomStorage(appDatabase)
    override val reactionStorage: ReactionStorage
        get() = ReactionRoomStorage(appDatabase)
    override val alternativesStorage: AlternativesStorage
        get() = AlternativesRoomStorage(appDatabase)
    override val ossStorage: OSSStorage
        get() = OSSRoomStorage(appDatabase)
    override val downloadStorage: DownloadStorage
        get() = DownloadRoomStorage(appDatabase)
}
