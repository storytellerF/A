package com.storyteller_f.a.client.room

import androidx.paging.PagingSource
import com.storyteller_f.shared.commonJson
import com.storyteller_f.shared.model.ChildAccountInfo
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.FileRefInfo
import com.storyteller_f.shared.model.MemberInfo
import com.storyteller_f.shared.model.PanelLogInfo
import com.storyteller_f.shared.model.PanelOverview
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.ReactionRecordInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UploadRecordInfo
import com.storyteller_f.shared.model.UserFavoriteInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserLogInfo
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
import com.storyteller_f.storage.FileRefCollection
import com.storyteller_f.storage.FileRefInfoStorage
import com.storyteller_f.storage.MemberCollection
import com.storyteller_f.storage.MemberInfoStorage
import com.storyteller_f.storage.ModelStorage
import com.storyteller_f.storage.OverviewStorage
import com.storyteller_f.storage.PanelLogCollection
import com.storyteller_f.storage.PanelLogInfoStorage
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
import com.storyteller_f.storage.UploadRecordCollection
import com.storyteller_f.storage.UploadRecordInfoStorage
import com.storyteller_f.storage.UserCollection
import com.storyteller_f.storage.UserFavoriteCollection
import com.storyteller_f.storage.UserFavoriteStorage
import com.storyteller_f.storage.UserInfoStorage
import com.storyteller_f.storage.UserLogCollection
import com.storyteller_f.storage.UserLogInfoStorage
import com.storyteller_f.storage.UserOverviewStorage
import com.storyteller_f.storage.UserReactionRecordCollection
import com.storyteller_f.storage.UserReactionRecordStorage
import com.storyteller_f.storage.UserSubscriptionCollection
import com.storyteller_f.storage.UserSubscriptionStorage
import com.storyteller_f.storage.WrappedPagingSource
import com.storyteller_f.storage.getName
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class RoomUserInfoStorage(appDatabase: AppDatabase) : UserInfoStorage {
    val impl = CommonStorageImpl(appDatabase)

    private suspend fun saveToDefaultCollections(item: UserInfo): CommonEntity {
        val data = commonJson.encodeToString(item)
        val entity = CommonEntity(item.id, UserCollection.Users.getName(), data)
        buildList {
            add(entity)
            item.aid?.let { add(entity.copy(id = it)) }
        }.forEach {
            impl.save(it)
        }
        return entity
    }

    override suspend fun saveToDefault(item: UserInfo) {
        saveToDefaultCollections(item)
    }

    override suspend fun saveLast(
        collection: UserCollection,
        item: UserInfo
    ) {
        val commonEntity = saveToDefaultCollections(item)
        if (collection !is UserCollection.Users) {
            val entity = commonEntity.copy(collection = collection.getName())
            impl.saveLast(entity)
        }
    }

    override suspend fun saveFirst(
        collection: UserCollection,
        item: UserInfo
    ) {
        val commonEntity = saveToDefaultCollections(item)
        if (collection !is UserCollection.Users) {
            val entity = commonEntity.copy(collection = collection.getName())
            impl.saveFirst(entity)
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

    override suspend fun getDocument(
        collection: UserCollection,
        key: String
    ): UserInfo? {
        return impl.getDocument(collection.getName(), key)
    }

    override suspend fun delete(
        collection: UserCollection,
        key: String
    ) {
        impl.delete(collection.getName(), key)
    }

    override suspend fun clean(collection: UserCollection) {
        impl.clean(collection.getName())
    }

    override suspend fun updateDocument(collection: UserCollection, item: UserInfo) {
        val data = commonJson.encodeToString(item)
        impl.update(collection.getName(), item.id.toString(), data)
    }
}

class RoomCommunityInfoStorage(appDatabase: AppDatabase) : CommunityInfoStorage {
    val impl = CommonStorageImpl(appDatabase)

    private suspend fun saveToDefaultCollections(item: CommunityInfo): CommonEntity {
        val data = commonJson.encodeToString(item)
        val entity = CommonEntity(item.id, CommunityCollection.Communities.getName(), data,)
        buildList {
            add(entity)
            add(entity.copy(id = item.aid))
        }.forEach {
            impl.save(it)
        }
        return entity
    }

    override suspend fun saveToDefault(item: CommunityInfo) {
        saveToDefaultCollections(item)
    }

    override suspend fun saveLast(collection: CommunityCollection, item: CommunityInfo) {
        val commonEntity = saveToDefaultCollections(item)
        if (collection !is CommunityCollection.Communities) {
            val entity = commonEntity.copy(collection = collection.getName())
            impl.saveLast(entity)
        }
    }

    override suspend fun saveFirst(
        collection: CommunityCollection,
        item: CommunityInfo
    ) {
        val commonEntity = saveToDefaultCollections(item)
        if (collection !is CommunityCollection.Communities) {
            val entity = commonEntity.copy(collection = collection.getName())
            impl.saveFirst(entity)
        }
    }

    override fun observeData(
        collection: CommunityCollection,
    ): PagingSource<Int, CommunityInfo> {
        return impl.observeData(collection.getName())
    }

    override fun observeDatum(key: String): Flow<CommunityInfo?> {
        val scope = CommunityCollection.Communities.getName()
        return impl.observeDatum(scope, key)
    }

    override suspend fun getDocument(
        collection: CommunityCollection,
        key: String
    ): CommunityInfo? {
        return impl.getDocument(collection.getName(), key)
    }

    override suspend fun delete(
        collection: CommunityCollection,
        key: String
    ) {
        impl.delete(collection.getName(), key)
    }

    override suspend fun clean(collection: CommunityCollection) {
        impl.clean(collection.getName())
    }

    override suspend fun updateDocument(collection: CommunityCollection, item: CommunityInfo) {
        val data = commonJson.encodeToString(item)
        impl.update(collection.getName(), item.id.toString(), data)
    }
}

class RoomTopicInfoStorage(val appDatabase: AppDatabase) : TopicInfoStorage {
    val impl = CommonStorageImpl(appDatabase)
    private suspend fun saveToDefaultCollections(item: TopicInfo): CommonEntity {
        val data = commonJson.encodeToString(item)
        val entity = CommonEntity(item.id, TopicCollection.Topics.getName(), data)
        buildList {
            add(entity)
            item.aid?.let { add(entity.copy(id = it)) }
        }.forEach {
            appDatabase.getCommonDao().insert(it)
        }
        return entity
    }

    override suspend fun saveToDefault(item: TopicInfo) {
        saveToDefaultCollections(item)
    }

    override suspend fun saveLast(
        collection: TopicCollection,
        item: TopicInfo
    ) {
        val commonEntity = saveToDefaultCollections(item)
        if (collection !is TopicCollection.Topics) {
            val entity = commonEntity.copy(collection = collection.getName())
            impl.saveLast(entity)
        }
    }

    override suspend fun saveFirst(
        collection: TopicCollection,
        item: TopicInfo
    ) {
        val commonEntity = saveToDefaultCollections(item)
        if (collection !is TopicCollection.Topics) {
            val entity = commonEntity.copy(collection = collection.getName())
            impl.saveFirst(entity)
        }
    }

    override fun observeData(
        collection: TopicCollection,
    ): PagingSource<Int, TopicInfo> {
        return impl.observeData(collection.getName())
    }

    override fun observeDatum(key: String): Flow<TopicInfo?> {
        return impl.observeDatum(TopicCollection.Topics.getName(), key)
    }

    override suspend fun getDocument(
        collection: TopicCollection,
        key: String
    ): TopicInfo? {
        return impl.getDocument(collection.getName(), key)
    }

    override suspend fun delete(
        collection: TopicCollection,
        key: String
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun clean(collection: TopicCollection) {
        impl.clean(collection.getName())
    }

    override suspend fun updateDocument(collection: TopicCollection, item: TopicInfo) {
        val data = commonJson.encodeToString(item)
        impl.update(collection.getName(), item.id.toString(), data)
    }
}

class RoomTitleInfoStorage(appDatabase: AppDatabase) : TitleInfoStorage {
    val impl = CommonStorageImpl(appDatabase)

    private suspend inline fun saveToCollections(
        item: TitleInfo,
    ): CommonEntity {
        val data = commonJson.encodeToString(item)
        val entity = CommonEntity(item.id, TitleCollection.Titles.getName(), data)
        impl.save(entity)
        return entity
    }

    override suspend fun saveToDefault(item: TitleInfo) {
        saveToCollections(item)
    }

    override suspend fun saveLast(
        collection: TitleCollection,
        item: TitleInfo
    ) {
        val commonEntity = saveToCollections(item)
        if (collection !is TitleCollection.Titles) {
            val entity = commonEntity.copy(collection = collection.getName())
            impl.saveLast(entity)
        }
    }

    override suspend fun saveFirst(
        collection: TitleCollection,
        item: TitleInfo
    ) {
        val commonEntity = saveToCollections(item)
        if (collection !is TitleCollection.Titles) {
            val entity = commonEntity.copy(collection = collection.getName())
            impl.saveFirst(entity)
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

    override fun observeDatum(key: String): Flow<TitleInfo?> {
        return impl.observeDatum(TitleCollection.Titles.getName(), key)
    }

    override suspend fun getDocument(
        collection: TitleCollection,
        key: String
    ): TitleInfo? {
        return impl.getDocument(collection.getName(), key)
    }

    override suspend fun delete(
        collection: TitleCollection,
        key: String
    ) {
        impl.delete(collection.getName(), key)
    }

    override suspend fun updateDocument(collection: TitleCollection, item: TitleInfo) {
        val data = commonJson.encodeToString(item)
        impl.update(collection.getName(), item.id.toString(), data)
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

    suspend inline fun <reified T> getDocument(
        collection: String,
        id: String
    ): T? {
        val entity = appDatabase.getCommonDao().get(collection, id)
        return entity?.data?.let { commonJson.safeDecodeFromStringOrNull(it) }
    }

    suspend fun saveLast(entity: CommonEntity) {
        appDatabase.getCommonDao().saveLast(entity)
    }

    suspend fun saveFirst(entity: CommonEntity) {
        appDatabase.getCommonDao().saveFirst(entity)
    }

    suspend fun delete(collection: String, key: String) {
        appDatabase.getCommonDao().delete(collection, key)
    }

    suspend fun save(entity: CommonEntity) {
        appDatabase.getCommonDao().insert(entity)
    }

    suspend fun update(collection: String, id: String, data: String) {
        appDatabase.getCommonDao().updateData(collection, id, data)
    }
}

class RoomRoomInfoStorage(appDatabase: AppDatabase) : RoomInfoStorage {
    val impl = CommonStorageImpl(appDatabase)

    private suspend fun saveToDefaultCollections(item: RoomInfo): CommonEntity {
        val data = commonJson.encodeToString(item)
        val entity = CommonEntity(item.id, RoomCollection.Rooms.getName(), data)
        listOf(
            entity,
            entity.copy(id = item.aid)
        ).forEach {
            impl.save(it)
        }
        return entity
    }

    override suspend fun saveToDefault(
        item: RoomInfo
    ) {
        saveToDefaultCollections(item)
    }

    override suspend fun saveLast(
        collection: RoomCollection,
        item: RoomInfo
    ) {
        val commonEntity = saveToDefaultCollections(item)
        if (collection !is RoomCollection.Rooms) {
            val entity = commonEntity.copy(collection = collection.getName())
            impl.saveLast(entity)
        }
    }

    override suspend fun saveFirst(
        collection: RoomCollection,
        item: RoomInfo
    ) {
        val commonEntity = saveToDefaultCollections(item)
        if (collection !is RoomCollection.Rooms) {
            val entity = commonEntity.copy(collection = collection.getName())
            impl.saveFirst(entity)
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

    override suspend fun getDocument(
        collection: RoomCollection,
        key: String
    ): RoomInfo? {
        return impl.getDocument(collection.getName(), key)
    }

    override suspend fun delete(
        collection: RoomCollection,
        key: String
    ) {
        impl.delete(collection.getName(), key)
    }

    override suspend fun clean(collection: RoomCollection) {
        impl.clean(RoomCollection.Rooms.getName())
    }

    override suspend fun updateDocument(collection: RoomCollection, item: RoomInfo) {
        val data = commonJson.encodeToString(item)
        impl.update(collection.getName(), item.id.toString(), data)
    }
}

class RoomMemberInfoStorage(appDatabase: AppDatabase) : MemberInfoStorage {
    val impl = CommonStorageImpl(appDatabase)

    override suspend fun saveLast(collection: MemberCollection, item: MemberInfo) {
        val data = commonJson.encodeToString<MemberInfo>(item)
        val entity = CommonEntity(item.id, collection.getName(), data)
        impl.saveLast(entity)
    }

    override suspend fun saveFirst(collection: MemberCollection, item: MemberInfo) {
        val data = commonJson.encodeToString<MemberInfo>(item)
        val entity = CommonEntity(item.id, collection.getName(), data)
        impl.saveFirst(entity)
    }

    override fun observeData(collection: MemberCollection): PagingSource<Int, MemberInfo> {
        return impl.observeData(collection.getName())
    }

    override suspend fun clean(collection: MemberCollection) {
        impl.clean(collection.getName())
    }
    override suspend fun getDocument(
        collection: MemberCollection,
        key: String
    ): MemberInfo? {
        return impl.getDocument(collection.getName(), key)
    }

    override suspend fun delete(
        collection: MemberCollection,
        key: String
    ) {
        impl.delete(collection.getName(), key)
    }

    override suspend fun updateDocument(collection: MemberCollection, item: MemberInfo) {
        val data = commonJson.encodeToString<MemberInfo>(item)
        impl.update(collection.getName(), item.id.toString(), data)
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
            CommonEntity(remoteKeys.collectionName, PRE_COLLECTION, commonJson.encodeToString(remoteKeys))
        )
    }

    override suspend fun saveNextRemoteKey(remoteKeys: RemoteKeys) {
        appDatabase.getCommonDao()
            .insert(CommonEntity(remoteKeys.collectionName, NEXT_COLLECTION, commonJson.encodeToString(remoteKeys)))
    }

    override suspend fun deletePreRemoteKey(collection: String) {
        appDatabase.getCommonDao().delete(PRE_COLLECTION, collection)
    }

    override suspend fun deleteNextRemoteKey(collection: String) {
        appDatabase.getCommonDao().delete(NEXT_COLLECTION, collection)
    }
}

class RoomReactionInfoStorage(appDatabase: AppDatabase) : ReactionInfoStorage {
    val impl = CommonStorageImpl(appDatabase)
    suspend fun saveToDefaultCollections(item: ReactionInfo): CommonEntity {
        val id = "${item.objectId}-${item.emoji}"
        val data = commonJson.encodeToString(item)
        val entity = CommonEntity(id, ReactionCollection.Reactions.getName(), data)
        impl.save(entity)
        return entity
    }

    override suspend fun saveLast(collection: ReactionCollection, item: ReactionInfo) {
        val commonEntity = saveToDefaultCollections(item)
        if (collection == ReactionCollection.Reactions) {
            impl.saveLast(commonEntity.copy(collection = collection.getName()))
        }
    }

    override suspend fun saveFirst(collection: ReactionCollection, item: ReactionInfo) {
        val commonEntity = saveToDefaultCollections(item)
        if (collection == ReactionCollection.Reactions) {
            impl.saveFirst(commonEntity.copy(collection = collection.getName()))
        }
    }

    override fun observeData(
        collection: ReactionCollection,
    ): PagingSource<Int, ReactionInfo> {
        return impl.observeData(collection.getName())
    }

    override suspend fun clean(collection: ReactionCollection) {
        impl.clean(collection.getName())
    }

    override suspend fun getDocument(
        collection: ReactionCollection,
        key: String
    ): ReactionInfo {
        TODO("Not yet implemented")
    }

    override suspend fun delete(
        collection: ReactionCollection,
        key: String
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun updateDocument(collection: ReactionCollection, item: ReactionInfo) {
        val id = "${item.objectId}-${item.emoji}"
        val data = commonJson.encodeToString(item)
        impl.update(collection.getName(), id, data)
    }
}

class RoomChildAccountStorage(appDatabase: AppDatabase) : ChildAccountStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(item: ChildAccountInfo) {
        val data = commonJson.encodeToString(item)
        impl.save(CommonEntity(item.id, ChildAccountStorage.COLLECTION_NAME, data))
    }

    override fun observeData(): PagingSource<Int, ChildAccountInfo> {
        return impl.observeData(ChildAccountStorage.COLLECTION_NAME)
    }

    override suspend fun clean() {
        impl.clean(ChildAccountStorage.COLLECTION_NAME)
    }

    override fun observeDatum(key: String): Flow<ChildAccountInfo?> {
        return impl.observeDatum(ChildAccountStorage.COLLECTION_NAME, key)
    }
}

class RoomFileInfoStorage(appDatabase: AppDatabase) : FileInfoStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun saveToDefault(item: FileInfo) {
        saveToDefaultCollection(item)
    }

    private suspend fun saveToDefaultCollection(item: FileInfo): CommonEntity {
        val data = commonJson.encodeToString(item)
        val entity = CommonEntity(item.id, FileCollection.Files.getName(), data)
        impl.save(entity)
        return entity
    }

    override suspend fun saveFirst(collection: FileCollection, item: FileInfo) {
        val commonEntity = saveToDefaultCollection(item)
        impl.saveFirst(commonEntity.copy(collection = collection.getName()))
    }

    override suspend fun saveLast(collection: FileCollection, item: FileInfo) {
        val commonEntity = saveToDefaultCollection(item)
        impl.saveLast(commonEntity.copy(collection = collection.getName()))
    }

    override fun observeData(collection: FileCollection): PagingSource<Int, FileInfo> {
        return impl.observeData(collection.getName())
    }

    override suspend fun clean(collection: FileCollection) {
        impl.clean(collection.getName())
    }

    override fun observeDatum(key: String): Flow<FileInfo?> {
        return impl.observeDatum(FileCollection.Files.getName(), key)
    }

    override suspend fun getDocument(
        collection: FileCollection,
        key: String
    ): FileInfo? {
        return impl.getDocument(collection.getName(), key)
    }

    override suspend fun delete(
        collection: FileCollection,
        key: String
    ) {
        impl.delete(collection.getName(), key)
    }

    override suspend fun updateDocument(collection: FileCollection, item: FileInfo) {
        val data = commonJson.encodeToString(item)
        impl.update(collection.getName(), item.id.toString(), data)
    }
}

class RoomDownloadInfoStorage(val appDatabase: AppDatabase) : DownloadInfoStorage {
    override suspend fun save(item: DownloadInfo) {
        val data = commonJson.encodeToString(item)
        appDatabase.getDownloadDao().insert(
            DownloadEntity(
                id = item.id.toString(),
                collection = DownloadInfoStorage.COLLECTION_NAME,
                fileId = item.fileInfo.id,
                data = data
            )
        )
    }

    override suspend fun getDocumentByFileId(fileId: PrimaryKey): DownloadInfo? {
        return appDatabase.getDownloadDao().getByFileId(DownloadInfoStorage.COLLECTION_NAME, fileId)
            ?.let {
                commonJson.safeDecodeFromStringOrNull(it.data)
            }
    }

    override fun observeDocumentByFileId(fileId: PrimaryKey): Flow<DownloadInfo?> {
        return appDatabase.getDownloadDao()
            .getByFileIdAsFlow(DownloadInfoStorage.COLLECTION_NAME, fileId).map {
                it?.data?.let { string -> commonJson.safeDecodeFromStringOrNull(string) }
            }
    }

    override fun observeData(): PagingSource<Int, DownloadInfo> {
        return WrappedPagingSource(
            appDatabase.getDownloadDao().getAsSource(DownloadInfoStorage.COLLECTION_NAME)
        ) { entities ->
            entities.mapNotNull {
                commonJson.safeDecodeFromStringOrNull(it.data)
            }
        }
    }

    override suspend fun clean() {
        appDatabase.getDownloadDao().clean(DownloadInfoStorage.COLLECTION_NAME)
    }
}

class RoomUploadInfoStorage(val appDatabase: AppDatabase) : UploadInfoStorage {
    override suspend fun saveFirst(collection: UploadCollection, item: UploadInfo) {
        val data = commonJson.encodeToString(item)
        appDatabase.getUploadDao().insert(
            UploadEntity(item.id.toString(), collection.getName(), data, item.pathHash)
        )
    }

    override suspend fun saveLast(collection: UploadCollection, item: UploadInfo) {
        val data = commonJson.encodeToString(item)
        appDatabase.getUploadDao().insert(
            UploadEntity(item.id.toString(), collection.getName(), data, item.pathHash)
        )
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

    override suspend fun updateDocument(collection: UploadCollection, item: UploadInfo) {
        val data = commonJson.encodeToString(item)
        appDatabase.getUploadDao().updateData(collection.getName(), item.id.toString(), data)
    }

    override fun observeDatumByHash(
        collection: UploadCollection,
        pathHash: String
    ): Flow<UploadInfo?> {
        return appDatabase.getUploadDao().getAsFlow(collection.getName(), pathHash).map {
            it?.data?.let { string -> commonJson.safeDecodeFromStringOrNull(string) }
        }
    }
}

class RoomOverviewStorage(appDatabase: AppDatabase) : OverviewStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(
        item: PanelOverview
    ) {
        val data = commonJson.encodeToString(item)
        impl.save(CommonEntity("overview", OverviewStorage.COLLECTION_NAME, data))
    }

    override fun observeDatum(): Flow<PanelOverview?> {
        return impl.observeDatum(OverviewStorage.COLLECTION_NAME, "overview")
    }
}

class RoomUserOverviewStorage(appDatabase: AppDatabase) : UserOverviewStorage {
    val impl = CommonStorageImpl(appDatabase)
    override suspend fun save(item: UserOverview) {
        val data = commonJson.encodeToString(item)
        impl.save(CommonEntity("overview", UserOverviewStorage.COLLECTION_NAME, data))
    }

    override fun observeDatum(): Flow<UserOverview?> {
        return impl.observeDatum(UserOverviewStorage.COLLECTION_NAME, "overview")
    }
}

class RoomUserFavoriteStorage(appDatabase: AppDatabase) : UserFavoriteStorage {
    val impl = CommonStorageImpl(appDatabase)

    override suspend fun saveToDefault(item: UserFavoriteInfo) {
        val data = commonJson.encodeToString(item)
        impl.save(CommonEntity(item.id, UserFavoriteStorage.COLLECTION_NAME, data))
    }

    override suspend fun saveLast(collection: UserFavoriteCollection, item: UserFavoriteInfo) {
        val data = commonJson.encodeToString(item)
        impl.saveLast(CommonEntity(item.id, collection.getName(), data))
    }

    override suspend fun saveFirst(collection: UserFavoriteCollection, item: UserFavoriteInfo) {
        val data = commonJson.encodeToString(item)
        impl.saveFirst(CommonEntity(item.id, collection.getName(), data))
    }

    override fun observeData(collection: UserFavoriteCollection): PagingSource<Int, UserFavoriteInfo> {
        return impl.observeData(collection.getName())
    }

    override suspend fun clean(collection: UserFavoriteCollection) {
        impl.clean(collection.getName())
    }

    override suspend fun getDocument(
        collection: UserFavoriteCollection,
        key: String
    ): UserFavoriteInfo? {
        return impl.getDocument(collection.getName(), key)
    }

    override fun observeDatum(key: String): Flow<UserFavoriteInfo?> {
        return impl.observeDatum(UserFavoriteStorage.COLLECTION_NAME, key)
    }

    override suspend fun updateDocument(collection: UserFavoriteCollection, item: UserFavoriteInfo) {
        val data = commonJson.encodeToString(item)
        impl.update(collection.getName(), item.id.toString(), data)
    }

    override suspend fun delete(collection: UserFavoriteCollection, key: String) {
        impl.delete(collection.getName(), key)
    }
}

class RoomUserSubscriptionStorage(appDatabase: AppDatabase) : UserSubscriptionStorage {
    val impl = CommonStorageImpl(appDatabase)

    override suspend fun saveToDefault(item: UserSubscriptionInfo) {
        val data = commonJson.encodeToString(item)
        impl.save(CommonEntity(item.id, UserSubscriptionStorage.COLLECTION_NAME, data))
    }

    override suspend fun saveLast(collection: UserSubscriptionCollection, item: UserSubscriptionInfo) {
        val data = commonJson.encodeToString(item)
        impl.saveLast(CommonEntity(item.id, collection.getName(), data))
    }

    override suspend fun saveFirst(collection: UserSubscriptionCollection, item: UserSubscriptionInfo) {
        val data = commonJson.encodeToString(item)
        impl.saveFirst(CommonEntity(item.id, collection.getName(), data))
    }

    override fun observeData(collection: UserSubscriptionCollection): PagingSource<Int, UserSubscriptionInfo> {
        return impl.observeData(collection.getName())
    }

    override suspend fun clean(collection: UserSubscriptionCollection) {
        impl.clean(collection.getName())
    }

    override suspend fun getDocument(
        collection: UserSubscriptionCollection,
        key: String
    ): UserSubscriptionInfo? {
        return impl.getDocument(collection.getName(), key)
    }

    override fun observeDatum(key: String): Flow<UserSubscriptionInfo?> {
        return impl.observeDatum(UserSubscriptionStorage.COLLECTION_NAME, key)
    }

    override suspend fun updateDocument(collection: UserSubscriptionCollection, item: UserSubscriptionInfo) {
        val data = commonJson.encodeToString(item)
        impl.update(collection.getName(), item.id.toString(), data)
    }

    override suspend fun delete(collection: UserSubscriptionCollection, key: String) {
        impl.delete(collection.getName(), key)
    }
}

class RoomUserReactionRecordStorage(appDatabase: AppDatabase) : UserReactionRecordStorage {
    val impl = CommonStorageImpl(appDatabase)

    override suspend fun saveFirst(
        collection: UserReactionRecordCollection,
        item: ReactionRecordInfo
    ) {
        val data = commonJson.encodeToString(item)
        impl.saveFirst(CommonEntity(item.id, collection.getName(), data))
    }

    override suspend fun saveLast(
        collection: UserReactionRecordCollection,
        item: ReactionRecordInfo
    ) {
        val data = commonJson.encodeToString(item)
        impl.saveLast(CommonEntity(item.id, collection.getName(), data))
    }

    override fun observeData(
        collection: UserReactionRecordCollection,
    ): PagingSource<Int, ReactionRecordInfo> {
        return impl.observeData(collection.getName())
    }

    override suspend fun clean(collection: UserReactionRecordCollection) {
        return impl.clean(collection.getName())
    }

    override suspend fun getDocument(
        collection: UserReactionRecordCollection,
        key: String
    ): ReactionRecordInfo {
        TODO("Not yet implemented")
    }

    override suspend fun delete(
        collection: UserReactionRecordCollection,
        key: String
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun updateDocument(collection: UserReactionRecordCollection, item: ReactionRecordInfo) {
        val data = commonJson.encodeToString(item)
        impl.update(collection.getName(), item.id.toString(), data)
    }
}

class RoomUserLogInfoStorage(appDatabase: AppDatabase) : UserLogInfoStorage {
    val impl = CommonStorageImpl(appDatabase)

    override suspend fun saveFirst(collection: UserLogCollection, item: UserLogInfo) {
        impl.saveFirst(CommonEntity(item.id, collection.getName(), commonJson.encodeToString(item)))
    }

    override suspend fun saveLast(collection: UserLogCollection, item: UserLogInfo) {
        impl.saveLast(CommonEntity(item.id, collection.getName(), commonJson.encodeToString(item)))
    }

    override fun observeData(collection: UserLogCollection): PagingSource<Int, UserLogInfo> {
        return impl.observeData(collection.getName())
    }

    override suspend fun clean(collection: UserLogCollection) {
        impl.clean(collection.getName())
    }

    override suspend fun getDocument(
        collection: UserLogCollection,
        key: String
    ): UserLogInfo {
        TODO("Not yet implemented")
    }

    override suspend fun delete(
        collection: UserLogCollection,
        key: String
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun updateDocument(collection: UserLogCollection, item: UserLogInfo) {
        val data = commonJson.encodeToString(item)
        impl.update(collection.getName(), item.id.toString(), data)
    }
}

class RoomUploadRecordInfoStorage(appDatabase: AppDatabase) : UploadRecordInfoStorage {
    val impl = CommonStorageImpl(appDatabase)

    override suspend fun saveFirst(collection: UploadRecordCollection, item: UploadRecordInfo) {
        impl.saveFirst(CommonEntity(item.id, collection.getName(), commonJson.encodeToString(item)))
    }

    override suspend fun saveLast(collection: UploadRecordCollection, item: UploadRecordInfo) {
        impl.saveLast(CommonEntity(item.id, collection.getName(), commonJson.encodeToString(item)))
    }

    override fun observeData(collection: UploadRecordCollection): PagingSource<Int, UploadRecordInfo> {
        return impl.observeData(collection.getName())
    }

    override suspend fun clean(collection: UploadRecordCollection) {
        impl.clean(collection.getName())
    }

    override suspend fun getDocument(
        collection: UploadRecordCollection,
        key: String
    ): UploadRecordInfo {
        TODO("Not yet implemented")
    }

    override suspend fun delete(
        collection: UploadRecordCollection,
        key: String
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun updateDocument(collection: UploadRecordCollection, item: UploadRecordInfo) {
        val data = commonJson.encodeToString(item)
        impl.update(collection.getName(), item.id.toString(), data)
    }
}

class RoomFileRefInfoStorage(appDatabase: AppDatabase) : FileRefInfoStorage {
    private val impl = CommonStorageImpl(appDatabase)

    override suspend fun saveFirst(collection: FileRefCollection, item: FileRefInfo) {
        impl.saveFirst(CommonEntity(item.id, collection.getName(), commonJson.encodeToString(item)))
    }

    override suspend fun saveLast(collection: FileRefCollection, item: FileRefInfo) {
        impl.saveLast(CommonEntity(item.id, collection.getName(), commonJson.encodeToString(item)))
    }

    override fun observeData(collection: FileRefCollection): PagingSource<Int, FileRefInfo> {
        return impl.observeData(collection.getName())
    }

    override suspend fun clean(collection: FileRefCollection) {
        impl.clean(collection.getName())
    }

    override suspend fun getDocument(
        collection: FileRefCollection,
        key: String
    ): FileRefInfo {
        TODO("Not yet implemented")
    }

    override suspend fun delete(
        collection: FileRefCollection,
        key: String
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun updateDocument(collection: FileRefCollection, item: FileRefInfo) {
        val data = commonJson.encodeToString(item)
        impl.update(collection.getName(), item.id.toString(), data)
    }
}

class RoomPanelLogInfoStorage(appDatabase: AppDatabase) : PanelLogInfoStorage {
    val impl = CommonStorageImpl(appDatabase)

    override suspend fun saveFirst(collection: PanelLogCollection, item: PanelLogInfo) {
        impl.saveFirst(CommonEntity(item.id, collection.getName(), commonJson.encodeToString(item)))
    }

    override suspend fun saveLast(collection: PanelLogCollection, item: PanelLogInfo) {
        impl.saveLast(CommonEntity(item.id, collection.getName(), commonJson.encodeToString(item)))
    }

    override fun observeData(collection: PanelLogCollection): PagingSource<Int, PanelLogInfo> {
        return impl.observeData(collection.getName())
    }

    override suspend fun clean(collection: PanelLogCollection) {
        impl.clean(collection.getName())
    }

    override suspend fun getDocument(
        collection: PanelLogCollection,
        key: String
    ): PanelLogInfo {
        TODO("Not yet implemented")
    }

    override suspend fun delete(
        collection: PanelLogCollection,
        key: String
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun updateDocument(collection: PanelLogCollection, item: PanelLogInfo) {
        val data = commonJson.encodeToString(item)
        impl.update(collection.getName(), item.id.toString(), data)
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
    override val userReactionRecord: UserReactionRecordStorage = RoomUserReactionRecordStorage(appDatabase)
    override val userLog: UserLogInfoStorage = RoomUserLogInfoStorage(appDatabase)
    override val panelLog: PanelLogInfoStorage = RoomPanelLogInfoStorage(appDatabase)
    override val uploadRecord: UploadRecordInfoStorage = RoomUploadRecordInfoStorage(appDatabase)
    override val fileRef: FileRefInfoStorage = RoomFileRefInfoStorage(appDatabase)
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
