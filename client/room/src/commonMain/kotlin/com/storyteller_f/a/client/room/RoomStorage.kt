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
import com.storyteller_f.storage.AlternativesStorage
import com.storyteller_f.storage.CommunityStorage
import com.storyteller_f.storage.DownloadInfo
import com.storyteller_f.storage.DownloadStorage
import com.storyteller_f.storage.ModelCollection
import com.storyteller_f.storage.ModelStorage
import com.storyteller_f.storage.OSSStorage
import com.storyteller_f.storage.ReactionStorage
import com.storyteller_f.storage.RemoteKeyStorage
import com.storyteller_f.storage.RemoteKeys
import com.storyteller_f.storage.RoomStorage
import com.storyteller_f.storage.TitleStorage
import com.storyteller_f.storage.TopicStorage
import com.storyteller_f.storage.UserStorage
import com.storyteller_f.storage.WrappedPagingSource
import com.storyteller_f.storage.getName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class UserRoomStorage(val appDatabase: AppDatabase) : UserStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(
        modelCollection: ModelCollection,
        t: UserInfo
    ) {
        val data = Json.encodeToString(t)
        val item = CommonEntity(t.id, ModelCollection.Users.getName(), data)
        appDatabase.getCommonDao().insert(item)
        if (modelCollection is ModelCollection.SearchUser) {
            appDatabase.getCommonDao().insert(item.copy(collection = modelCollection.getName()))
        }
    }

    override fun observeData(
        modelCollection: ModelCollection,
    ): PagingSource<Int, UserInfo> {
        return impl.observeData(modelCollection)
    }

    override fun observeDatum(
        key: String
    ): Flow<UserInfo?> {
        return impl.observeDatum(ModelCollection.Users, key)
    }

    override fun observeDatum(id: PrimaryKey): Flow<UserInfo?> {
        return observeDatum(id.toString())
    }
}

class CommunityRoomStorage(val appDatabase: AppDatabase) : CommunityStorage {
    override suspend fun save(
        modelCollection: ModelCollection,
        t: CommunityInfo
    ) {
        val data = Json.encodeToString(t)
        val item = CommunityEntity(t.id, ModelCollection.Communities.getName(), data, t.hasPoster)
        appDatabase.getCommunityDao().insert(item)
        if (modelCollection is ModelCollection.SearchCommunity) {
            appDatabase.getCommunityDao().insert(item.copy(collection = modelCollection.getName()))
        }
    }

    override fun observeData(
        modelCollection: ModelCollection,
    ): PagingSource<Int, CommunityInfo> {
        val source = appDatabase.getCommunityDao().getAsSource(modelCollection.getName())
        return WrappedPagingSource(source) { list ->
            list.map {
                Json.decodeFromString(it.data)
            }
        }
    }

    override fun observeDatum(key: String): Flow<CommunityInfo?> {
        val scope = ModelCollection.Communities.getName()
        return appDatabase.getCommunityDao().getAsFlow(scope, key).map {
            it?.let { it1 -> Json.decodeFromString(it1.data) }
        }
    }

    override fun observeDatum(id: PrimaryKey): Flow<CommunityInfo?> {
        return observeDatum(id.toString())
    }

    override suspend fun getDocument(
        modelCollection: ModelCollection,
        id: PrimaryKey
    ): CommunityInfo? {
        val entity = appDatabase.getCommunityDao().get(modelCollection.getName(), id.toString())
        return entity?.data?.let { Json.decodeFromString(it) }
    }
}

class TopicRoomStorage(val appDatabase: AppDatabase) : TopicStorage {
    override suspend fun save(
        modelCollection: ModelCollection,
        t: TopicInfo
    ) {
        val data = Json.encodeToString(t)
        val item = TopicEntity(t.id, ModelCollection.Topics.getName(), data, t.isPin)
        appDatabase.getTopicDao().insert(item)
        if (modelCollection is ModelCollection.TopicList ||
            modelCollection is ModelCollection.SearchTopic ||
            modelCollection is ModelCollection.Recommend
        ) {
            appDatabase.getTopicDao().insert(item.copy(collection = modelCollection.getName()))
        }
    }

    override fun observeData(
        modelCollection: ModelCollection,
    ): PagingSource<Int, TopicInfo> {
        val source = appDatabase.getTopicDao().getAsSource(modelCollection.getName())
        return WrappedPagingSource(source) { list ->
            list.map {
                Json.decodeFromString(it.data)
            }
        }
    }

    override fun observeDatum(key: String): Flow<TopicInfo?> {
        val scope = ModelCollection.Topics.getName()
        return appDatabase.getTopicDao().getAsFlow(scope, key).map {
            it?.data?.let { string -> Json.decodeFromString(string) }
        }
    }

    override fun observeDatum(id: PrimaryKey): Flow<TopicInfo?> {
        return observeDatum(id.toString())
    }

    override suspend fun getDocument(
        modelCollection: ModelCollection,
        id: PrimaryKey
    ): TopicInfo? {
        val entity = appDatabase.getTopicDao().get(modelCollection.getName(), id.toString())
        return entity?.data?.let { Json.decodeFromString(it) }
    }
}

class TitleRoomStorage(val appDatabase: AppDatabase) : TitleStorage {
    override suspend fun save(
        modelCollection: ModelCollection,
        t: TitleInfo
    ) {
        val data = Json.encodeToString(t)
        val item = CommonEntity(t.id, ModelCollection.Titles.getName(), data)
        appDatabase.getCommonDao().insert(item)
        if (modelCollection is ModelCollection.SearchTitle) {
            appDatabase.getCommonDao().insert(item.copy(collection = modelCollection.getName()))
        }
    }

    override fun observeData(
        modelCollection: ModelCollection,
    ): PagingSource<Int, TitleInfo> {
        val source = appDatabase.getCommonDao().getAsSource(modelCollection.getName())
        return WrappedPagingSource(source) { list ->
            list.mapNotNull {
                it.data?.let { string -> Json.decodeFromString(string) }
            }
        }
    }

    override fun observeDatum(id: PrimaryKey): Flow<TitleInfo?> {
        val scope = ModelCollection.Titles.getName()
        return appDatabase.getTopicDao().getAsFlow(scope, id.toString()).map {
            it?.data?.let { string -> Json.decodeFromString(string) }
        }
    }
}

class CommonStorageImpl(val appDatabase: AppDatabase) {
    inline fun <reified T : Any> observeData(
        modelCollection: ModelCollection,
    ): PagingSource<Int, T> {
        val source = appDatabase.getCommonDao().getAsSource(modelCollection.getName())
        return WrappedPagingSource(source) { list ->
            list.mapNotNull {
                it.data?.let { string -> Json.decodeFromString(string) }
            }
        }
    }

    inline fun <reified T : Any> observeDatum(
        modelCollection: ModelCollection,
        id: String
    ): Flow<T?> {
        val source = appDatabase.getCommonDao().getAsFlow(modelCollection.getName(), id)
        return source.map {
            it?.data?.let { string -> Json.decodeFromString(string) }
        }
    }
}

class RoomRoomStorage(val appDatabase: AppDatabase) : RoomStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(
        modelCollection: ModelCollection,
        t: RoomInfo
    ) {
        val data = Json.encodeToString(t)
        val item = CommonEntity(t.id, ModelCollection.Rooms.getName(), data)
        appDatabase.getCommonDao().insert(item)
        if (modelCollection is ModelCollection.SearchRoom) {
            appDatabase.getCommonDao().insert(item.copy(collection = modelCollection.getName()))
        }
    }

    override fun observeData(
        modelCollection: ModelCollection,
    ): PagingSource<Int, RoomInfo> {
        return impl.observeData(modelCollection)
    }

    override fun observeDatum(key: String): Flow<RoomInfo?> {
        return impl.observeDatum(ModelCollection.Rooms, key)
    }

    override fun observeDatum(id: PrimaryKey): Flow<RoomInfo?> {
        return observeDatum(id.toString())
    }
}

class RemoteKeyRoomStorage(val appDatabase: AppDatabase) : RemoteKeyStorage {
    override suspend fun getPreRemoteKey(modelCollection: ModelCollection): RemoteKeys? {
        return appDatabase.getCommonDao().get(modelCollection.getName(), "pre")?.let {
            RemoteKeys(modelCollection.getName(), it.data)
        }
    }

    override suspend fun getNextRemoteKey(modelCollection: ModelCollection): RemoteKeys? {
        return appDatabase.getCommonDao().get(modelCollection.getName(), "next")?.let {
            RemoteKeys(modelCollection.getName(), it.data)
        }
    }

    override suspend fun savePreRemoteKey(remoteKeys: RemoteKeys) {
        appDatabase.getCommonDao()
            .insert(CommonEntity("pre", remoteKeys.collectionName, remoteKeys.key))
    }

    override suspend fun saveNextRemoteKey(remoteKeys: RemoteKeys) {
        appDatabase.getCommonDao()
            .insert(CommonEntity("next", remoteKeys.collectionName, remoteKeys.key))
    }

    override suspend fun deletePreRemoteKey(modelCollection: ModelCollection) {
        appDatabase.getCommonDao().delete(modelCollection.getName(), "pre")
    }

    override suspend fun deleteNextRemoteKey(modelCollection: ModelCollection) {
        appDatabase.getCommonDao().delete(modelCollection.getName(), "next")
    }
}

class ReactionRoomStorage(val appDatabase: AppDatabase) : ReactionStorage {
    override suspend fun save(
        modelCollection: ModelCollection,
        t: ReactionInfo
    ) {
        val id = "${t.objectId}-${t.emoji}"
        val data = Json.encodeToString(t)
        appDatabase.getReactionDao()
            .insert(ReactionEntity(id, modelCollection.getName(), data, t.count, t.lastReactionId))
    }

    override fun observeData(
        modelCollection: ModelCollection,
    ): PagingSource<Int, ReactionInfo> {
        val raw = appDatabase.getReactionDao().getAsSource(modelCollection.getName())
        return WrappedPagingSource(raw) { list ->
            list.map {
                Json.decodeFromString(it.data)
            }
        }
    }
}

class AlternativesRoomStorage(val appDatabase: AppDatabase) : AlternativesStorage {
    override suspend fun save(
        modelCollection: ModelCollection,
        t: AlternativeAccountInfo
    ) {
        val data = Json.encodeToString(t)
        appDatabase.getCommonDao().insert(CommonEntity(t.id, modelCollection.getName(), data))
    }

    override fun observeData(
        modelCollection: ModelCollection,
    ): PagingSource<Int, AlternativeAccountInfo> {
        val raw = appDatabase.getReactionDao().getAsSource(modelCollection.getName())
        return WrappedPagingSource(raw) { list ->
            list.map {
                Json.decodeFromString(it.data)
            }
        }
    }
}

class OSSRoomStorage(val appDatabase: AppDatabase) : OSSStorage {
    override suspend fun save(
        modelCollection: ModelCollection,
        t: MediaInfo
    ) {
        val data = Json.encodeToString(t)
        appDatabase.getCommonDao().insert(CommonEntity(t.id, modelCollection.getName(), data))
    }

    override fun observeData(
        modelCollection: ModelCollection,
    ): PagingSource<Int, MediaInfo> {
        val raw = appDatabase.getReactionDao().getAsSource(modelCollection.getName())
        return WrappedPagingSource(raw) { list ->
            list.map {
                Json.decodeFromString(it.data)
            }
        }
    }
}

class DownloadRoomStorage(val appDatabase: AppDatabase) : DownloadStorage {
    override suspend fun save(
        modelCollection: ModelCollection,
        t: DownloadInfo
    ) {
        val data = Json.encodeToString(t)
        appDatabase.getCommonDao()
            .insert(CommonEntity(t.mediaInfo.id, modelCollection.getName(), data))
    }

    override fun observeDatum(id: PrimaryKey): Flow<DownloadInfo?> {
        return appDatabase.getCommonDao()
            .getAsFlow(ModelCollection.Download.getName(), id.toString()).map {
                it?.data?.let { string -> Json.decodeFromString(string) }
            }
    }

    override suspend fun getDocument(
        modelCollection: ModelCollection,
        id: PrimaryKey
    ): DownloadInfo? {
        return appDatabase.getCommonDao().get(modelCollection.getName(), id.toString())?.let {
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
