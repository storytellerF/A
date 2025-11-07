package com.storyteller_f.a.client.room

import androidx.paging.PagingSource
import com.storyteller_f.shared.commonJson
import com.storyteller_f.shared.model.ChildAccountInfo
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.FileInfo
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
import com.storyteller_f.storage.FileInfoStorage
import com.storyteller_f.storage.MediasCollection
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

class UserRoomInfoStorage(val appDatabase: AppDatabase) : UserInfoStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(
        collection: UserCollection,
        userInfo: UserInfo
    ) {
        val data = commonJson.encodeToString(userInfo)
        val item = CommonEntity(userInfo.id, UserCollection.Users.getName(), data)
        buildList {
            add(item)
            userInfo.aid?.let { add(item.copy(id = it)) }
        }.forEach {
            appDatabase.getCommonDao().insert(it)
        }
        if (collection is UserCollection.SearchUser ||
            collection is UserCollection.AllUsers ||
            collection is UserCollection.Members) {
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
        val data = commonJson.encodeToString(communityInfo)
        val item =
            CommunityEntity(
                communityInfo.id,
                CommunityCollection.Communities.getName(),
                data,
                communityInfo.hasPoster
            )
        listOf(item, item.copy(id = communityInfo.aid)).forEach {
            appDatabase.getCommunityDao().insert(it)
        }
        if (collection is CommunityCollection.SearchCommunity) {
            appDatabase.getCommunityDao().insert(item.copy(collection = collection.getName()))
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

class TopicRoomInfoStorage(val appDatabase: AppDatabase) : TopicInfoStorage {
    override suspend fun save(
        collection: TopicCollection,
        topicInfo: TopicInfo
    ) {
        val data = commonJson.encodeToString(topicInfo)
        val item =
            TopicEntity(topicInfo.id, TopicCollection.Topics.getName(), data, topicInfo.isPin)
        buildList {
            add(item)
            topicInfo.aid?.let { add(item.copy(id = it)) }
        }.forEach {
            appDatabase.getTopicDao().insert(it)
        }
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

class TitleRoomInfoStorage(val appDatabase: AppDatabase) : TitleInfoStorage {
    override suspend fun save(
        collection: TitleCollection,
        titleInfo: TitleInfo
    ) {
        val data = commonJson.encodeToString(titleInfo)
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
            list.mapNotNull {
                commonJson.safeDecodeFromStringOrNull(it.data)
            }
        }
    }

    override suspend fun clean(collection: TitleCollection) {
        appDatabase.getCommonDao().clean(collection.getName())
    }

    override fun observeDatum(id: PrimaryKey): Flow<TitleInfo?> {
        val scope = TitleCollection.Titles.getName()
        return appDatabase.getTopicDao().getAsFlow(scope, id.toString()).map {
            it?.data?.let { string -> commonJson.safeDecodeFromStringOrNull(string) }
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
}

class RoomRoomInfoStorage(val appDatabase: AppDatabase) : RoomInfoStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(
        collection: RoomCollection,
        roomInfo: RoomInfo
    ) {
        val data = commonJson.encodeToString(roomInfo)
        val item = CommonEntity(roomInfo.id, RoomCollection.Rooms.getName(), data)
        listOf(
            item,
            item.copy(id = roomInfo.aid)
        ).forEach {
            appDatabase.getCommonDao().insert(it)
        }
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

    override suspend fun clean(collection: RoomCollection) {
        appDatabase.getCommonDao().clean(collection.getName())
    }

    override fun observeDatum(id: PrimaryKey): Flow<RoomInfo?> {
        return observeDatum(id.toString())
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

class ReactionRoomInfoStorage(val appDatabase: AppDatabase) : ReactionInfoStorage {
    override suspend fun save(
        collection: ReactionCollection,
        reactionInfo: ReactionInfo
    ) {
        val id = "${reactionInfo.objectId}-${reactionInfo.emoji}"
        val data = commonJson.encodeToString(reactionInfo)
        appDatabase.getReactionDao().insert(
            ReactionEntity(
                id,
                collection.getName(),
                data,
                reactionInfo.count,
                reactionInfo.lastReactionId
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

class ChildAccountRoomStorage(val appDatabase: AppDatabase) : ChildAccountStorage {
    override suspend fun save(childAccountInfo: ChildAccountInfo) {
        val data = commonJson.encodeToString(childAccountInfo)
        appDatabase.getCommonDao()
            .insert(CommonEntity(childAccountInfo.id, ChildAccountStorage.COLLECTION_NAME, data))
    }

    override fun observeData(): PagingSource<Int, ChildAccountInfo> {
        val raw = appDatabase.getCommonDao().getAsSource(ChildAccountStorage.COLLECTION_NAME)
        return WrappedPagingSource(raw) { list ->
            list.mapNotNull {
                commonJson.safeDecodeFromStringOrNull(it.data)
            }
        }
    }

    override suspend fun clean() {
        appDatabase.getCommonDao().clean(ChildAccountStorage.COLLECTION_NAME)
    }
}

class FileInfoRoomStorage(val appDatabase: AppDatabase) : FileInfoStorage {
    override suspend fun save(
        collection: MediasCollection,
        fileInfo: FileInfo
    ) {
        val data = commonJson.encodeToString(fileInfo)
        appDatabase.getCommonDao().insert(CommonEntity(fileInfo.id, collection.getName(), data))
    }

    override fun observeData(collection: MediasCollection): PagingSource<Int, FileInfo> {
        val raw = appDatabase.getCommonDao().getAsSource(collection.getName())
        return WrappedPagingSource(raw) { list ->
            list.mapNotNull {
                commonJson.safeDecodeFromStringOrNull(it.data)
            }
        }
    }

    override suspend fun clean(collection: MediasCollection) {
        appDatabase.getCommonDao().clean(collection.getName())
    }
}

class DownloadInfoRoomStorage(val appDatabase: AppDatabase) : DownloadInfoStorage {
    override suspend fun save(downloadInfo: DownloadInfo) {
        val data = commonJson.encodeToString(downloadInfo)
        appDatabase.getCommonDao().insert(
            CommonEntity(
                downloadInfo.fileInfo.id,
                DownloadInfoStorage.COLLECTION_NAME,
                data
            )
        )
    }

    override fun observeDatum(id: PrimaryKey): Flow<DownloadInfo?> {
        return appDatabase.getCommonDao()
            .getAsFlow(DownloadInfoStorage.COLLECTION_NAME, id.toString()).map {
                it?.data?.let { string -> commonJson.safeDecodeFromStringOrNull(string) }
            }
    }

    override suspend fun getDocument(id: PrimaryKey): DownloadInfo? {
        return appDatabase.getCommonDao().get(DownloadInfoStorage.COLLECTION_NAME, id.toString())
            ?.let {
                commonJson.safeDecodeFromStringOrNull(it.data)
            }
    }
}

class UploadInfoRoomStorage(val appDatabase: AppDatabase) : UploadInfoStorage {
    override suspend fun save(collection: UploadCollection, uploadInfo: UploadInfo) {
        val data = commonJson.encodeToString(uploadInfo)
        appDatabase.getUploadDao().insert(
            UploadEntity(
                uploadInfo.id.toString(),
                collection.getName(),
                data,
                uploadInfo.pathHash
            )
        )
    }

    override fun observeDatum(pathHash: String): Flow<UploadInfo?> {
        return appDatabase.getUploadDao()
            .getAsFlow(DownloadInfoStorage.COLLECTION_NAME, pathHash).map {
                it?.data?.let { string -> commonJson.safeDecodeFromStringOrNull(string) }
            }
    }

    override suspend fun getDocument(collection: UploadCollection, pathHash: String): UploadInfo? {
        return appDatabase.getUploadDao().get(collection.getName(), pathHash)?.let {
            commonJson.safeDecodeFromStringOrNull(it.data)
        }
    }

    override fun observeData(collection: UploadCollection): PagingSource<Int, UploadInfo> {
        return WrappedPagingSource(
            appDatabase.getUploadDao().getAsSource(collection.getName())
        ) {
            it.mapNotNull {
                commonJson.safeDecodeFromStringOrNull(it.data)
            }
        }
    }
}

class OverviewRoomStorage(val appDatabase: AppDatabase) : OverviewStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(
        overviewInfo: PanelOverview
    ) {
        val data = commonJson.encodeToString(overviewInfo)
        appDatabase.getCommonDao()
            .insert(CommonEntity("overview", OverviewStorage.COLLECTION_NAME, data))
    }

    override fun observeDatum(): Flow<PanelOverview?> {
        return impl.observeDatum(OverviewStorage.COLLECTION_NAME, "overview")
    }
}

class UserOverviewRoomStorage(val appDatabase: AppDatabase) : UserOverviewStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(overviewInfo: UserOverview) {
        val data = commonJson.encodeToString(overviewInfo)
        appDatabase.getCommonDao()
            .insert(CommonEntity("overview", UserOverviewStorage.COLLECTION_NAME, data))
    }

    override fun observeDatum(): Flow<UserOverview?> {
        return impl.observeDatum(UserOverviewStorage.COLLECTION_NAME, "overview")
    }
}

class UserFavoriteRoomStorage(val appDatabase: AppDatabase) : UserFavoriteStorage {
    val impl = CommonStorageImpl(appDatabase)

    override suspend fun save(
        favoriteInfo: UserFavoriteInfo
    ) {
        val data = commonJson.encodeToString(favoriteInfo)
        appDatabase.getCommonDao()
            .insert(CommonEntity(favoriteInfo.id, UserFavoriteStorage.COLLECTION_NAME, data))
    }

    override fun observeData(): PagingSource<Int, UserFavoriteInfo> {
        return impl.observeData(UserFavoriteStorage.COLLECTION_NAME)
    }

    override fun observeDatum(id: String): Flow<UserFavoriteInfo?> {
        return impl.observeDatum(UserFavoriteStorage.COLLECTION_NAME, id)
    }

    override suspend fun clean() {
        return impl.clean(UserFavoriteStorage.COLLECTION_NAME)
    }
}

class UserSubscriptionRoomStorage(val appDatabase: AppDatabase) : UserSubscriptionStorage {
    val impl = CommonStorageImpl(appDatabase)

    override suspend fun save(subscriptionInfo: UserSubscriptionInfo) {
        val data = commonJson.encodeToString(subscriptionInfo)
        appDatabase.getCommonDao().insert(
            CommonEntity(
                subscriptionInfo.id,
                UserSubscriptionStorage.COLLECTION_NAME,
                data
            )
        )
    }

    override fun observeData(): PagingSource<Int, UserSubscriptionInfo> {
        return impl.observeData(UserSubscriptionStorage.COLLECTION_NAME)
    }

    override fun observeDatum(id: String): Flow<UserSubscriptionInfo?> {
        return impl.observeDatum(UserSubscriptionStorage.COLLECTION_NAME, id)
    }

    override suspend fun clean() {
        return impl.clean(UserSubscriptionStorage.COLLECTION_NAME)
    }
}

class RoomModelStorage(appDatabase: AppDatabase) : ModelStorage {
    override val user: UserInfoStorage = UserRoomInfoStorage(appDatabase)
    override val community: CommunityInfoStorage = CommunityRoomInfoStorage(appDatabase)
    override val topic: TopicInfoStorage = TopicRoomInfoStorage(appDatabase)
    override val title: TitleInfoStorage = TitleRoomInfoStorage(appDatabase)
    override val room: RoomInfoStorage = RoomRoomInfoStorage(appDatabase)
    override val remoteKey: RemoteKeyStorage = RemoteKeyRoomStorage(appDatabase)
    override val reaction: ReactionInfoStorage = ReactionRoomInfoStorage(appDatabase)
    override val childAccount: ChildAccountStorage = ChildAccountRoomStorage(appDatabase)
    override val fileInfo: FileInfoStorage = FileInfoRoomStorage(appDatabase)
    override val download: DownloadInfoStorage = DownloadInfoRoomStorage(appDatabase)
    override val upload: UploadInfoStorage = UploadInfoRoomStorage(appDatabase)
    override val overview: OverviewStorage = OverviewRoomStorage(appDatabase)
    override val userOverview: UserOverviewStorage = UserOverviewRoomStorage(appDatabase)
    override val favorite: UserFavoriteStorage = UserFavoriteRoomStorage(appDatabase)
    override val subscription: UserSubscriptionStorage = UserSubscriptionRoomStorage(appDatabase)
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
