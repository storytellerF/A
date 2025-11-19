package com.storyteller_f.a.client.room

import androidx.paging.PagingSource
import com.storyteller_f.shared.commonJson
import com.storyteller_f.shared.model.ChildAccountInfo
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.MemberInfo
import com.storyteller_f.shared.model.PanelOverview
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserFavoriteInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserOverview
import com.storyteller_f.shared.model.UserSubscriptionInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.storage.ChildAccountStorage
import com.storyteller_f.storage.CommunityCollection
import com.storyteller_f.storage.CommunityInfoStorage
import com.storyteller_f.storage.DownloadInfo
import com.storyteller_f.storage.DownloadInfoStorage
import com.storyteller_f.storage.FileCollection
import com.storyteller_f.storage.FileInfoStorage
import com.storyteller_f.storage.MemberCollection
import com.storyteller_f.storage.MemberInfoStorage
import com.storyteller_f.storage.ModelStorage
import com.storyteller_f.storage.OverviewStorage
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
import com.storyteller_f.storage.UploadCollection
import com.storyteller_f.storage.UploadInfo
import com.storyteller_f.storage.UploadInfoStorage
import com.storyteller_f.storage.UserCollection
import com.storyteller_f.storage.UserFavoriteStorage
import com.storyteller_f.storage.UserInfoStorage
import com.storyteller_f.storage.UserOverviewStorage
import com.storyteller_f.storage.UserSubscriptionStorage
import com.storyteller_f.storage.WrappedPagingSource
import com.storyteller_f.storage.getName
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class RoomUserInfoStorage(val appDatabase: AppDatabase) : UserInfoStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(
        collection: UserCollection,
        item: UserInfo
    ) {
        val data = commonJson.encodeToString(item)
        val entity = CommonEntity(item.id, UserCollection.Users.getName(), data)
        buildList {
            add(entity)
            item.aid?.let { add(entity.copy(id = it)) }
        }.forEach {
            appDatabase.getCommonDao().insert(it)
        }
        if (collection !is UserCollection.Users) {
            appDatabase.getCommonDao().insert(entity.copy(collection = collection.getName()))
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
        impl.clean(collection.getName())
    }

    override fun observeDatum(id: PrimaryKey): Flow<UserInfo?> {
        return observeDatum(id.toString())
    }
}

class RoomCommunityInfoStorage(val appDatabase: AppDatabase) : CommunityInfoStorage {
    override suspend fun save(
        collection: CommunityCollection,
        item: CommunityInfo
    ) {
        val data = commonJson.encodeToString(item)
        val entity =
            CommunityEntity(
                item.id,
                CommunityCollection.Communities.getName(),
                data,
                item.hasPoster
            )
        listOf(entity, entity.copy(id = item.aid)).forEach {
            appDatabase.getCommunityDao().insert(it)
        }
        if (collection !is CommunityCollection.Communities) {
            appDatabase.getCommunityDao().insert(entity.copy(collection = collection.getName()))
        }
    }

    override fun observeData(
        collection: CommunityCollection,
    ): PagingSource<Int, CommunityInfo> {
        val source = appDatabase.getCommunityDao().getAsSource(collection.getName())
        return WrappedPagingSource(source) { list ->
            list.mapNotNull {
                commonJson.safeDecodeFromStringOrNull(it.data)
            }
        }
    }

    override fun observeDatum(key: String): Flow<CommunityInfo?> {
        val scope = CommunityCollection.Communities.getName()
        return appDatabase.getCommunityDao().getAsFlow(scope, key).map {
            it?.let { it1 -> commonJson.safeDecodeFromStringOrNull(it1.data) }
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
        return entity?.data?.let { commonJson.safeDecodeFromStringOrNull(it) }
    }

    override suspend fun clean(collection: CommunityCollection) {
        appDatabase.getCommunityDao().clean(collection.getName())
    }
}

class RoomTopicInfoStorage(val appDatabase: AppDatabase) : TopicInfoStorage {
    override suspend fun save(
        collection: TopicCollection,
        item: TopicInfo
    ) {
        val data = commonJson.encodeToString(item)
        val entity =
            TopicEntity(item.id, TopicCollection.Topics.getName(), data, item.isPin)
        buildList {
            add(entity)
            item.aid?.let { add(entity.copy(id = it)) }
        }.forEach {
            appDatabase.getTopicDao().insert(it)
        }
        if (collection !is TopicCollection.Topics) {
            appDatabase.getTopicDao().insert(entity.copy(collection = collection.getName()))
        }
    }

    override fun observeData(
        collection: TopicCollection,
    ): PagingSource<Int, TopicInfo> {
        val source = appDatabase.getTopicDao().getAsSource(collection.getName())
        return WrappedPagingSource(source) { list ->
            list.mapNotNull {
                commonJson.safeDecodeFromStringOrNull(it.data)
            }
        }
    }

    override fun observeDatum(key: String): Flow<TopicInfo?> {
        val scope = TopicCollection.Topics.getName()
        return appDatabase.getTopicDao().getAsFlow(scope, key).map {
            it?.data?.let { string -> commonJson.safeDecodeFromStringOrNull(string) }
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
        return entity?.data?.let { commonJson.safeDecodeFromStringOrNull(it) }
    }

    override suspend fun clean(collection: TopicCollection) {
        appDatabase.getTopicDao().clean(collection.getName())
    }
}

class RoomTitleInfoStorage(val appDatabase: AppDatabase) : TitleInfoStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(
        collection: TitleCollection,
        item: TitleInfo
    ) {
        val data = commonJson.encodeToString(item)
        val entity = CommonEntity(item.id, TitleCollection.Titles.getName(), data)
        appDatabase.getCommonDao().insert(entity)
        if (collection !is TitleCollection.Titles) {
            appDatabase.getCommonDao().insert(entity.copy(collection = collection.getName()))
        }
    }

    override fun observeData(
        collection: TitleCollection,
    ): PagingSource<Int, TitleInfo> {
        return impl.observeData(collection.getName())
    }

    override suspend fun clean(collection: TitleCollection) {
        impl.clean(TitleCollection.Titles.getName())
    }

    override fun observeDatum(id: PrimaryKey): Flow<TitleInfo?> {
        return impl.observeDatum(TitleCollection.Titles.getName(), id.toString())
    }
}

class CommonStorageImpl(val appDatabase: AppDatabase) {
    inline fun <reified T : Any> observeData(
        collection: String,
    ): PagingSource<Int, T> {
        val source = appDatabase.getCommonDao().getAsSource(collection)
        return WrappedPagingSource(source) { list ->
            list.mapNotNull {
                commonJson.safeDecodeFromStringOrNull(it.data)
            }
        }
    }

    inline fun <reified T : Any> observeDatum(
        collection: String,
        id: String
    ): Flow<T?> {
        val source = appDatabase.getCommonDao().getAsFlow(collection, id)
        return source.map {
            it?.data?.let { string -> commonJson.safeDecodeFromStringOrNull(string) }
        }
    }

    suspend fun clean(collection: String) {
        appDatabase.getCommonDao().clean(collection)
    }

    suspend inline fun<reified T> getDocument(
        collection: String,
        id: String
    ): T? {
        val entity = appDatabase.getCommonDao().get(collection, id)
        return entity?.data?.let { commonJson.safeDecodeFromStringOrNull(it) }
    }
}

class RoomRoomInfoStorage(val appDatabase: AppDatabase) : RoomInfoStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(
        collection: RoomCollection,
        item: RoomInfo
    ) {
        val data = commonJson.encodeToString(item)
        val entity = CommonEntity(item.id, RoomCollection.Rooms.getName(), data)
        listOf(
            entity,
            entity.copy(id = item.aid)
        ).forEach {
            appDatabase.getCommonDao().insert(it)
        }
        if (collection !is RoomCollection.Rooms) {
            appDatabase.getCommonDao().insert(entity.copy(collection = collection.getName()))
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

    override suspend fun clean(collection: RoomCollection) {
        impl.clean(RoomCollection.Rooms.getName())
    }

    override fun observeDatum(id: PrimaryKey): Flow<RoomInfo?> {
        return observeDatum(id.toString())
    }
}

class RoomMemberInfoStorage(val appDatabase: AppDatabase) : MemberInfoStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(collection: MemberCollection, item: MemberInfo) {
        val data = commonJson.encodeToString(item)
        appDatabase.getCommonDao().insert(
            CommonEntity(item.id, collection.getName(), data)
        )
    }

    override fun observeData(collection: MemberCollection): PagingSource<Int, MemberInfo> {
        return impl.observeData(collection.getName())
    }

    override suspend fun clean(collection: MemberCollection) {
        impl.clean(collection.getName())
    }
}

class RemoteKeyRoomStorage(val appDatabase: AppDatabase) : RemoteKeyStorage {

    override suspend fun getPreRemoteKey(collection: String): RemoteKeys? {
        return appDatabase.getCommonDao().get(PRE_COLLECTION, collection)?.let {
            commonJson.safeDecodeFromStringOrNull(it.data)
        }
    }

    override suspend fun getNextRemoteKey(collection: String): RemoteKeys? {
        return appDatabase.getCommonDao().get(NEXT_COLLECTION, collection)?.let {
            commonJson.safeDecodeFromStringOrNull(it.data)
        }
    }

    override suspend fun savePreRemoteKey(remoteKeys: RemoteKeys) {
        appDatabase.getCommonDao().insert(
            CommonEntity(
                remoteKeys.collectionName,
                PRE_COLLECTION,
                commonJson.encodeToString(remoteKeys)
            )
        )
    }

    override suspend fun saveNextRemoteKey(remoteKeys: RemoteKeys) {
        appDatabase.getCommonDao()
            .insert(
                CommonEntity(
                    remoteKeys.collectionName,
                    NEXT_COLLECTION,
                    commonJson.encodeToString(remoteKeys)
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

class RoomReactionInfoStorage(val appDatabase: AppDatabase) : ReactionInfoStorage {
    override suspend fun save(
        collection: ReactionCollection,
        item: ReactionInfo
    ) {
        val id = "${item.objectId}-${item.emoji}"
        val data = commonJson.encodeToString(item)
        appDatabase.getReactionDao().insert(
            ReactionEntity(
                id,
                collection.getName(),
                data,
                item.count,
                item.lastReactionId
            )
        )
    }

    override fun observeData(
        collection: ReactionCollection,
    ): PagingSource<Int, ReactionInfo> {
        val raw = appDatabase.getReactionDao().getAsSource(collection.getName())
        return WrappedPagingSource(raw) { list ->
            list.mapNotNull {
                commonJson.safeDecodeFromStringOrNull(it.data)
            }
        }
    }

    override suspend fun clean(collection: ReactionCollection) {
        appDatabase.getReactionDao().clean(collection.getName())
    }
}

class RoomChildAccountStorage(val appDatabase: AppDatabase) : ChildAccountStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(item: ChildAccountInfo) {
        val data = commonJson.encodeToString(item)
        appDatabase.getCommonDao()
            .insert(CommonEntity(item.id, ChildAccountStorage.COLLECTION_NAME, data))
    }

    override fun observeData(): PagingSource<Int, ChildAccountInfo> {
        return impl.observeData(ChildAccountStorage.COLLECTION_NAME)
    }

    override suspend fun clean() {
        impl.clean(ChildAccountStorage.COLLECTION_NAME)
    }
}

class RoomFileInfoStorage(val appDatabase: AppDatabase) : FileInfoStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(
        collection: FileCollection,
        item: FileInfo
    ) {
        val data = commonJson.encodeToString(item)
        val entity = CommonEntity(item.id, FileCollection.Files.getName(), data)
        appDatabase.getCommonDao().insert(entity)
        if (collection !is FileCollection.Files) {
            appDatabase.getCommonDao().insert(entity.copy(collection = collection.getName()))
            return
        }
    }

    override fun observeData(collection: FileCollection): PagingSource<Int, FileInfo> {
        return impl.observeData(collection.getName())
    }

    override suspend fun clean(collection: FileCollection) {
        impl.clean(collection.getName())
    }

    override fun observeDatum(id: PrimaryKey): Flow<FileInfo?> {
        return impl.observeDatum(FileCollection.Files.getName(), id.toString())
    }
}

class RoomDownloadInfoStorage(val appDatabase: AppDatabase) : DownloadInfoStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(item: DownloadInfo) {
        val data = commonJson.encodeToString(item)
        appDatabase.getCommonDao().insert(
            CommonEntity(
                item.fileInfo.id,
                DownloadInfoStorage.COLLECTION_NAME,
                data
            )
        )
    }

    override fun observeDatum(id: PrimaryKey): Flow<DownloadInfo?> {
        return impl.observeDatum(DownloadInfoStorage.COLLECTION_NAME, id.toString())
    }

    override suspend fun getDocument(id: PrimaryKey): DownloadInfo? {
        return impl.getDocument(DownloadInfoStorage.COLLECTION_NAME, id.toString())
    }

    override fun observeData(): PagingSource<Int, DownloadInfo> {
        return impl.observeData(DownloadInfoStorage.COLLECTION_NAME)
    }

    override suspend fun clean() {
        impl.clean(DownloadInfoStorage.COLLECTION_NAME)
    }
}

class RoomUploadInfoStorage(val appDatabase: AppDatabase) : UploadInfoStorage {
    override suspend fun save(collection: UploadCollection, item: UploadInfo) {
        val data = commonJson.encodeToString(item)
        appDatabase.getUploadDao().insert(
            UploadEntity(
                item.id.toString(),
                collection.getName(),
                data,
                item.pathHash
            )
        )
    }

    override fun observeDatum(collection: UploadCollection, key: String): Flow<UploadInfo?> {
        return appDatabase.getUploadDao()
            .getAsFlow(collection.getName(), key).map {
                it?.data?.let { string -> commonJson.safeDecodeFromStringOrNull(string) }
            }
    }

    override suspend fun getDocument(collection: UploadCollection, key: String): UploadInfo? {
        return appDatabase.getUploadDao().get(collection.getName(), key)?.let {
            commonJson.safeDecodeFromStringOrNull(it.data)
        }
    }

    override fun observeData(collection: UploadCollection): PagingSource<Int, UploadInfo> {
        return WrappedPagingSource(
            appDatabase.getUploadDao().getAsSource(collection.getName())
        ) { entities ->
            entities.mapNotNull {
                commonJson.safeDecodeFromStringOrNull(it.data)
            }
        }
    }

    override suspend fun delete(collection: UploadCollection, key: String) {
        appDatabase.getUploadDao().delete(collection.getName(), key)
    }

    override suspend fun clean(collection: UploadCollection) {
        appDatabase.getUploadDao().clean(collection.getName())
    }
}

class RoomOverviewStorage(val appDatabase: AppDatabase) : OverviewStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(
        item: PanelOverview
    ) {
        val data = commonJson.encodeToString(item)
        appDatabase.getCommonDao()
            .insert(CommonEntity("overview", OverviewStorage.COLLECTION_NAME, data))
    }

    override fun observeDatum(): Flow<PanelOverview?> {
        return impl.observeDatum(OverviewStorage.COLLECTION_NAME, "overview")
    }
}

class RoomUserOverviewStorage(val appDatabase: AppDatabase) : UserOverviewStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(item: UserOverview) {
        val data = commonJson.encodeToString(item)
        appDatabase.getCommonDao()
            .insert(CommonEntity("overview", UserOverviewStorage.COLLECTION_NAME, data))
    }

    override fun observeDatum(): Flow<UserOverview?> {
        return impl.observeDatum(UserOverviewStorage.COLLECTION_NAME, "overview")
    }
}

class RoomUserFavoriteStorage(val appDatabase: AppDatabase) : UserFavoriteStorage {
    val impl = CommonStorageImpl(appDatabase)

    override suspend fun save(
        item: UserFavoriteInfo
    ) {
        val data = commonJson.encodeToString(item)
        appDatabase.getCommonDao()
            .insert(CommonEntity(item.id, UserFavoriteStorage.COLLECTION_NAME, data))
    }

    override fun observeData(): PagingSource<Int, UserFavoriteInfo> {
        return impl.observeData(UserFavoriteStorage.COLLECTION_NAME)
    }

    override fun observeDatum(key: String): Flow<UserFavoriteInfo?> {
        return impl.observeDatum(UserFavoriteStorage.COLLECTION_NAME, key)
    }

    override suspend fun clean() {
        return impl.clean(UserFavoriteStorage.COLLECTION_NAME)
    }
}

class RoomUserSubscriptionStorage(val appDatabase: AppDatabase) : UserSubscriptionStorage {
    val impl = CommonStorageImpl(appDatabase)

    override suspend fun save(item: UserSubscriptionInfo) {
        val data = commonJson.encodeToString(item)
        appDatabase.getCommonDao().insert(
            CommonEntity(
                item.id,
                UserSubscriptionStorage.COLLECTION_NAME,
                data
            )
        )
    }

    override fun observeData(): PagingSource<Int, UserSubscriptionInfo> {
        return impl.observeData(UserSubscriptionStorage.COLLECTION_NAME)
    }

    override fun observeDatum(key: String): Flow<UserSubscriptionInfo?> {
        return impl.observeDatum(UserSubscriptionStorage.COLLECTION_NAME, key)
    }

    override suspend fun clean() {
        return impl.clean(UserSubscriptionStorage.COLLECTION_NAME)
    }
}

class RoomModelStorage(appDatabase: AppDatabase) : ModelStorage {
    override val user: UserInfoStorage = RoomUserInfoStorage(appDatabase)
    override val community: CommunityInfoStorage = RoomCommunityInfoStorage(appDatabase)
    override val topic: TopicInfoStorage = RoomTopicInfoStorage(appDatabase)
    override val title: TitleInfoStorage = RoomTitleInfoStorage(appDatabase)
    override val room: RoomInfoStorage = RoomRoomInfoStorage(appDatabase)
    override val member: MemberInfoStorage = RoomMemberInfoStorage(appDatabase)
    override val remoteKey: RemoteKeyStorage = RemoteKeyRoomStorage(appDatabase)
    override val reaction: ReactionInfoStorage = RoomReactionInfoStorage(appDatabase)
    override val childAccount: ChildAccountStorage = RoomChildAccountStorage(appDatabase)
    override val fileInfo: FileInfoStorage = RoomFileInfoStorage(appDatabase)
    override val download: DownloadInfoStorage = RoomDownloadInfoStorage(appDatabase)
    override val upload: UploadInfoStorage = RoomUploadInfoStorage(appDatabase)
    override val overview: OverviewStorage = RoomOverviewStorage(appDatabase)
    override val userOverview: UserOverviewStorage = RoomUserOverviewStorage(appDatabase)
    override val favorite: UserFavoriteStorage = RoomUserFavoriteStorage(appDatabase)
    override val subscription: UserSubscriptionStorage = RoomUserSubscriptionStorage(appDatabase)
}

fun getRoomModelStorage(name: String) = RoomModelStorage(getRoomDatabase(name))

inline fun <reified T : Any> Json.safeDecodeFromStringOrNull(
    string: String,
): T? = try {
    decodeFromString(string)
} catch (e: SerializationException) {
    Napier.e(e) {
        "decodeFromString failed, string: $string"
    }
    null
}
