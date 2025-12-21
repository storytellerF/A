package com.storyteller_f.storage

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
import com.storyteller_f.storage.RemoteKeyStorage.Companion.NEXT_COLLECTION
import com.storyteller_f.storage.RemoteKeyStorage.Companion.PRE_COLLECTION
import kotbase.Expression
import kotbase.From
import kotbase.OrderByRouter
import kotbase.ktx.WhereBuilder
import kotbase.ktx.orderBy
import kotlinx.coroutines.flow.Flow

class UserDocumentInfoStorage(
    val kotbaseDocumentSource: KotbaseDocumentSource
) : UserInfoStorage {
    override fun observeDatum(id: PrimaryKey): Flow<UserInfo?> {
        return kotbaseDocumentSource.getCollection<UserInfo>(UserCollection.Users.getName())
            .observeDatum(id)
    }

    override suspend fun saveLast(collection: UserCollection, item: UserInfo) {
        saveFirst(collection, item)
    }

    override suspend fun saveFirst(collection: UserCollection, item: UserInfo) {
        saveToDefault(item)
        if (collection != UserCollection.Users) {
            kotbaseDocumentSource.getCollection<UserInfo>(collection.getName())
                .save(item.id, item)
        }
    }

    override fun observeData(
        collection: UserCollection,
    ): PagingSource<Int, UserInfo> {
        return when (collection) {
            is UserCollection.Members -> {
                kotbaseDocumentSource.getCollection<UserInfo>(collection.getName()).getSource {
                    orderBy {
                        "id".descending()
                    }
                }
            }

            else -> throw Exception("unsupported")
        }
    }

    override fun observeDatum(
        key: String
    ): Flow<UserInfo?> {
        return kotbaseDocumentSource.getCollection<UserInfo>(UserCollection.Users.getName())
            .observeDatum {
                "aid" equalTo key
            }
    }

    override suspend fun clean(collection: UserCollection) = Unit

    override suspend fun getDocument(collection: UserCollection, key: String): UserInfo? {
        return kotbaseDocumentSource.getCollection<UserInfo>(collection.getName()).getDocument(key)
    }

    override suspend fun updateDocument(collection: UserCollection, item: UserInfo) {
        saveToDefault(item)
        kotbaseDocumentSource.getCollection<UserInfo>(collection.getName()).save(item.id, item)
    }

    override suspend fun delete(collection: UserCollection, key: String) {
        kotbaseDocumentSource.getCollection<UserInfo>(collection.getName()).delete(key)
    }

    override suspend fun saveToDefault(item: UserInfo) {
        with(kotbaseDocumentSource.getCollection<UserInfo>(UserCollection.Users.getName())) {
            save(item.id.toString(), item)
            save(item.aid, item)
        }
    }
}

class CommunityDocumentInfoStorage(
    val kotbaseDocumentSource: KotbaseDocumentSource
) :
    CommunityInfoStorage {
    override fun observeDatum(id: PrimaryKey): Flow<CommunityInfo?> {
        return kotbaseDocumentSource.getCollection<CommunityInfo>(CommunityCollection.Communities.getName())
            .observeDatum(id)
    }

    override fun observeDatum(key: String): Flow<CommunityInfo?> {
        return kotbaseDocumentSource.getCollection<CommunityInfo>(CommunityCollection.Communities.getName())
            .observeDatum {
                "aid" equalTo key
            }
    }

    override suspend fun saveLast(collection: CommunityCollection, item: CommunityInfo) {
        saveFirst(collection, item)
    }

    override suspend fun saveFirst(collection: CommunityCollection, item: CommunityInfo) {
        saveToDefault(item)
        if (collection != CommunityCollection.Communities) {
            kotbaseDocumentSource.getCollection<CommunityInfo>(collection.getName())
                .save(item.id, item)
        }
    }

    override fun observeData(
        collection: CommunityCollection,
    ): PagingSource<Int, CommunityInfo> {
        return when (collection) {
            is CommunityCollection.SearchCommunity -> {
                kotbaseDocumentSource.getCollection<CommunityInfo>(collection.getName())
                    .getSource {
                        orderBy {
                            "hasPoster".descending()
                            "id".descending()
                        }
                    }
            }

            else -> throw Exception("unsupported")
        }
    }

    override suspend fun getDocument(
        collection: CommunityCollection,
        key: String
    ): CommunityInfo? {
        return kotbaseDocumentSource.getCollection<CommunityInfo>(collection.getName())
            .getDocument(key)
    }

    override suspend fun clean(collection: CommunityCollection) = Unit

    override suspend fun updateDocument(collection: CommunityCollection, item: CommunityInfo) {
        saveToDefault(item)
        kotbaseDocumentSource.getCollection<CommunityInfo>(collection.getName()).save(item.id.toString(), item)
    }

    override suspend fun delete(collection: CommunityCollection, key: String) {
        kotbaseDocumentSource.getCollection<CommunityInfo>(collection.getName()).delete(key)
    }

    override suspend fun saveToDefault(item: CommunityInfo) {
        with(kotbaseDocumentSource.getCollection<CommunityInfo>(CommunityCollection.Communities.getName())) {
            save(item.id.toString(), item)
            save(item.aid, item)
        }
    }
}

class TopicDocumentInfoStorage(
    val kotbaseDocumentSource: KotbaseDocumentSource
) : TopicInfoStorage {
    override fun observeDatum(id: PrimaryKey): Flow<TopicInfo?> {
        return kotbaseDocumentSource.getCollection<TopicInfo>(TopicCollection.Topics.getName())
            .observeDatum(id)
    }

    override suspend fun saveLast(collection: TopicCollection, item: TopicInfo) {
        saveFirst(collection, item)
    }

    override suspend fun saveFirst(collection: TopicCollection, item: TopicInfo) {
        saveToDefault(item)
        if (collection != TopicCollection.Topics) {
            kotbaseDocumentSource.getCollection<TopicInfo>(collection.getName())
                .save(item.id, item)
        }
    }

    override fun observeData(
        collection: TopicCollection,
    ): PagingSource<Int, TopicInfo> {
        return when (collection) {
            is TopicCollection.Recommend, is TopicCollection.SearchTopic -> {
                kotbaseDocumentSource.getCollection<TopicInfo>(collection.getName())
                    .getSource {
                        orderBy {
                            "id".descending()
                        }
                    }
            }

            else -> throw Exception("unsupported")
        }
    }

    override fun observeDatum(
        key: String
    ): Flow<TopicInfo?> {
        return kotbaseDocumentSource.getCollection<TopicInfo>(TopicCollection.Topics.getName())
            .observeDatum {
                "aid" equalTo key
            }
    }

    override suspend fun getDocument(
        collection: TopicCollection,
        key: String
    ): TopicInfo? {
        return kotbaseDocumentSource.getCollection<TopicInfo>(collection.getName())
            .getDocument(key)
    }

    override suspend fun clean(collection: TopicCollection) = Unit

    override suspend fun updateDocument(collection: TopicCollection, item: TopicInfo) {
        saveToDefault(item)
        kotbaseDocumentSource.getCollection<TopicInfo>(collection.getName()).save(item.id.toString(), item)
    }

    override suspend fun delete(collection: TopicCollection, key: String) {
        kotbaseDocumentSource.getCollection<TopicInfo>(collection.getName()).delete(key)
    }

    override suspend fun saveToDefault(item: TopicInfo) {
        with(kotbaseDocumentSource.getCollection<TopicInfo>(TopicCollection.Topics.getName())) {
            save(item.id.toString(), item)
            save(item.aid, item)
        }
    }
}

class TitleDocumentInfoStorage(
    val kotbaseDocumentSource: KotbaseDocumentSource
) : TitleInfoStorage {
    override fun observeDatum(id: PrimaryKey): Flow<TitleInfo?> {
        return kotbaseDocumentSource.getCollection<TitleInfo>(TitleCollection.Titles.getName())
            .observeDatum(id)
    }

    override suspend fun saveLast(collection: TitleCollection, item: TitleInfo) {
        saveFirst(collection, item)
    }

    override suspend fun saveFirst(collection: TitleCollection, item: TitleInfo) {
        saveToDefault(item)
        if (collection != TitleCollection.Titles) {
            kotbaseDocumentSource.getCollection<TitleInfo>(collection.getName())
                .save(item.id, item)
        }
    }

    override fun observeData(
        collection: TitleCollection,
    ): PagingSource<Int, TitleInfo> {
        return when (collection) {
            is TitleCollection.SearchTitle -> {
                kotbaseDocumentSource.getCollection<TitleInfo>(collection.getName())
                    .getSource {
                        orderBy {
                            "id".descending()
                        }
                    }
            }

            else -> throw Exception("unsupported")
        }
    }

    override suspend fun clean(collection: TitleCollection) = Unit

    override fun observeDatum(key: String): Flow<TitleInfo?> {
        return kotbaseDocumentSource.getCollection<TitleInfo>(TitleCollection.Titles.getName())
            .observeDatum(key)
    }

    override suspend fun getDocument(collection: TitleCollection, key: String): TitleInfo? {
        return kotbaseDocumentSource.getCollection<TitleInfo>(collection.getName()).getDocument(key)
    }

    override suspend fun updateDocument(collection: TitleCollection, item: TitleInfo) {
        saveToDefault(item)
        kotbaseDocumentSource.getCollection<TitleInfo>(collection.getName()).save(item.id.toString(), item)
    }

    override suspend fun delete(collection: TitleCollection, key: String) {
        kotbaseDocumentSource.getCollection<TitleInfo>(collection.getName()).delete(key)
    }

    override suspend fun saveToDefault(item: TitleInfo) {
        kotbaseDocumentSource.getCollection<TitleInfo>(TitleCollection.Titles.getName()).save(item.id.toString(), item)
    }
}

class RoomDocumentInfoStorage(
    val kotbaseDocumentSource: KotbaseDocumentSource
) : RoomInfoStorage {
    override fun observeDatum(id: PrimaryKey): Flow<RoomInfo?> {
        return kotbaseDocumentSource.getCollection<RoomInfo>(RoomCollection.Rooms.getName())
            .observeDatum(id)
    }

    override suspend fun saveLast(collection: RoomCollection, item: RoomInfo) {
        saveFirst(collection, item)
    }

    override suspend fun saveFirst(collection: RoomCollection, item: RoomInfo) {
        saveToDefault(item)
        if (collection != RoomCollection.Rooms) {
            kotbaseDocumentSource.getCollection<RoomInfo>(collection.getName())
                .save(item.id, item)
        }
    }

    override fun observeData(
        collection: RoomCollection,
    ): PagingSource<Int, RoomInfo> {
        return when (collection) {
            is RoomCollection.SearchRoom, is RoomCollection.CommunityRooms, is RoomCollection.CommunityRoomSearch -> {
                kotbaseDocumentSource.getCollection<RoomInfo>(
                    collection.getName(),
                ).getSource {
                    orderBy {
                        "id".descending()
                    }
                }
            }

            else -> throw Exception("unsupported")
        }
    }

    override fun observeDatum(
        key: String
    ): Flow<RoomInfo?> {
        return kotbaseDocumentSource.getCollection<RoomInfo>(RoomCollection.Rooms.getName()).observeDatum(key)
    }

    override suspend fun getDocument(
        collection: RoomCollection,
        key: String
    ): RoomInfo? {
        return kotbaseDocumentSource.getCollection<RoomInfo>(collection.getName())
            .getDocument(key)
    }

    override suspend fun delete(collection: RoomCollection, key: String) {
        kotbaseDocumentSource.getCollection<RoomInfo>(collection.getName()).delete(key)
    }

    override suspend fun clean(collection: RoomCollection) {
        kotbaseDocumentSource.getCollection<RoomInfo>(RoomCollection.Rooms.getName()).clean()
    }

    override suspend fun updateDocument(collection: RoomCollection, item: RoomInfo) {
        saveToDefault(item)
        kotbaseDocumentSource.getCollection<RoomInfo>(collection.getName()).save(item.id.toString(), item)
    }

    override suspend fun saveToDefault(item: RoomInfo) {
        with(kotbaseDocumentSource.getCollection<RoomInfo>(RoomCollection.Rooms.getName())) {
            save(item.id.toString(), item)
            save(item.aid, item)
        }
    }
}

class RemoteKeyDocumentStorage(
    val kotbaseDocumentSource: KotbaseDocumentSource,
) : RemoteKeyStorage {
    override suspend fun getPreRemoteKey(collection: String): RemoteKeys? {
        return kotbaseDocumentSource.getCollection<RemoteKeys>(PRE_COLLECTION)
            .getDocument(collection)
    }

    override suspend fun getNextRemoteKey(collection: String): RemoteKeys? {
        return kotbaseDocumentSource.getCollection<RemoteKeys>(NEXT_COLLECTION)
            .getDocument(collection)
    }

    override suspend fun savePreRemoteKey(remoteKeys: RemoteKeys) {
        kotbaseDocumentSource.getCollection<RemoteKeys>(PRE_COLLECTION)
            .saveDocument(remoteKeys.collectionName, remoteKeys)
    }

    override suspend fun saveNextRemoteKey(remoteKeys: RemoteKeys) {
        kotbaseDocumentSource.getCollection<RemoteKeys>(NEXT_COLLECTION)
            .saveDocument(remoteKeys.collectionName, remoteKeys)
    }

    override suspend fun deletePreRemoteKey(collection: String) {
        kotbaseDocumentSource.getCollection<RemoteKeys>(PRE_COLLECTION)
            .deleteDocument(collection)
    }

    override suspend fun deleteNextRemoteKey(collection: String) {
        kotbaseDocumentSource.getCollection<RemoteKeys>(NEXT_COLLECTION)
            .deleteDocument(collection)
    }
}

class ReactionDocumentInfoStorage(val kotbaseDocumentSource: KotbaseDocumentSource) : ReactionInfoStorage {

    override suspend fun saveLast(collection: ReactionCollection, item: ReactionInfo) {
        saveFirst(collection, item)
    }

    override suspend fun saveFirst(collection: ReactionCollection, item: ReactionInfo) {
        kotbaseDocumentSource.getCollection<ReactionInfo>(collection.getName())
            .save("${item.objectId}-${item.emoji}", item)
    }

    override fun observeData(
        collection: ReactionCollection,
    ): PagingSource<Int, ReactionInfo> {
        return when (collection) {
            is ReactionCollection.ReactionList -> {
                kotbaseDocumentSource.getCollection<ReactionInfo>(collection.getName())
                    .getSource {
                        orderBy {
                            "count".descending()
                            "lastReactionId".ascending()
                        }
                    }
            }

            else -> throw Exception("unsupported")
        }
    }

    override suspend fun clean(collection: ReactionCollection) = Unit

    override suspend fun getDocument(collection: ReactionCollection, key: String): ReactionInfo? {
        return kotbaseDocumentSource.getCollection<ReactionInfo>(collection.getName()).getDocument(key)
    }

    override suspend fun updateDocument(collection: ReactionCollection, item: ReactionInfo) {
        kotbaseDocumentSource.getCollection<ReactionInfo>(collection.getName())
            .save("${item.objectId}-${item.emoji}", item)
    }

    override suspend fun delete(collection: ReactionCollection, key: String) {
        kotbaseDocumentSource.getCollection<ReactionInfo>(collection.getName()).delete(key)
    }
}

class ChildAccountDocumentStorage(val kotbaseDocumentSource: KotbaseDocumentSource) :
    ChildAccountStorage {

    override suspend fun save(item: ChildAccountInfo) {
        kotbaseDocumentSource.getCollection<ChildAccountInfo>(ChildAccountStorage.COLLECTION_NAME)
            .save(item.id, item)
    }

    override fun observeData(): PagingSource<Int, ChildAccountInfo> {
        return kotbaseDocumentSource.getCollection<ChildAccountInfo>(ChildAccountStorage.COLLECTION_NAME)
            .getSource {
                orderBy {
                    "id".descending()
                }
            }
    }

    override suspend fun clean() = Unit

    override fun observeDatum(key: String): Flow<ChildAccountInfo?> {
        return kotbaseDocumentSource.getCollection<ChildAccountInfo>(ChildAccountStorage.COLLECTION_NAME)
            .observeDatum(key)
    }

    override suspend fun getDocument(key: String): ChildAccountInfo? {
        return kotbaseDocumentSource.getCollection<ChildAccountInfo>(ChildAccountStorage.COLLECTION_NAME)
            .getDocument(key)
    }
}

class FileInfoDocumentStorage(val kotbaseDocumentSource: KotbaseDocumentSource) : FileInfoStorage {

    override suspend fun saveToDefault(item: FileInfo) {
        kotbaseDocumentSource.getCollection<FileInfo>(FileCollection.Files.getName()).save(item.id, item)
    }

    override suspend fun saveLast(
        collection: FileCollection,
        item: FileInfo
    ) {
        kotbaseDocumentSource.getCollection<FileInfo>(collection.getName()).save(item.id, item)
    }

    override suspend fun saveFirst(
        collection: FileCollection,
        item: FileInfo
    ) {
        kotbaseDocumentSource.getCollection<FileInfo>(collection.getName()).save(item.id, item)
    }

    override fun observeData(
        collection: FileCollection,
    ): PagingSource<Int, FileInfo> {
        return kotbaseDocumentSource.getCollection<FileInfo>(collection.getName())
            .getSource {
                orderBy {
                    "id".descending()
                }
            }
    }

    override suspend fun clean(collection: FileCollection) = Unit

    override suspend fun getDocument(collection: FileCollection, key: String): FileInfo? {
        return kotbaseDocumentSource.getCollection<FileInfo>(collection.getName()).getDocument(key)
    }

    override suspend fun updateDocument(collection: FileCollection, item: FileInfo) {
        kotbaseDocumentSource.getCollection<FileInfo>(collection.getName()).save(item.id, item)
    }

    override suspend fun delete(collection: FileCollection, key: String) {
        kotbaseDocumentSource.getCollection<FileInfo>(collection.getName()).delete(key)
    }

    override fun observeDatum(key: String): Flow<FileInfo?> {
        return kotbaseDocumentSource.getCollection<FileInfo>(FileCollection.Files.getName()).observeDatum(key)
    }
}

class DownloadInfoDocumentStorage(val kotbaseDocumentSource: KotbaseDocumentSource) : DownloadInfoStorage {

    override suspend fun save(item: DownloadInfo) {
        kotbaseDocumentSource.getCollection<DownloadInfo>(DownloadInfoStorage.COLLECTION_NAME)
            .save(item.fileInfo.id, item)
    }

    override fun observeDatum(key: String): Flow<DownloadInfo?> {
        return kotbaseDocumentSource.getCollection<DownloadInfo>(DownloadInfoStorage.COLLECTION_NAME)
            .observeDatum(key)
    }

    override suspend fun getDocument(key: String): DownloadInfo? {
        return kotbaseDocumentSource.getCollection<DownloadInfo>(DownloadInfoStorage.COLLECTION_NAME)
            .getDocument(key)
    }

    override suspend fun getDocumentByFileId(fileId: PrimaryKey): DownloadInfo? {
        return null
    }

    override fun observeData(): PagingSource<Int, DownloadInfo> {
        return kotbaseDocumentSource.getCollection<DownloadInfo>(DownloadInfoStorage.COLLECTION_NAME)
            .getSource {
                orderBy {
                    "id".descending()
                }
            }
    }

    override suspend fun clean() = Unit
}

class DocumentModelStorage(source: KotbaseDocumentSource) : ModelStorage {
    override val user: UserInfoStorage = UserDocumentInfoStorage(source)
    override val community: CommunityInfoStorage = CommunityDocumentInfoStorage(source)
    override val topic: TopicInfoStorage = TopicDocumentInfoStorage(source)
    override val title: TitleInfoStorage = TitleDocumentInfoStorage(source)
    override val room: RoomInfoStorage = RoomDocumentInfoStorage(source)
    override val member: MemberInfoStorage = TODO("Not yet implemented")
    override val remoteKey: RemoteKeyStorage = RemoteKeyDocumentStorage(source)
    override val reaction: ReactionInfoStorage = ReactionDocumentInfoStorage(source)
    override val childAccount: ChildAccountStorage = ChildAccountDocumentStorage(source)
    override val fileInfo: FileInfoStorage = FileInfoDocumentStorage(source)
    override val download: DownloadInfoStorage = DownloadInfoDocumentStorage(source)
    override val upload: UploadInfoStorage = TODO("Not yet implemented")
    override val overview: OverviewStorage = TODO("Not yet implemented")
    override val userOverview: UserOverviewStorage = TODO("Not yet implemented")
    override val favorite: UserFavoriteStorage = TODO("Not yet implemented")
    override val subscription: UserSubscriptionStorage = TODO("Not yet implemented")
    override val userReactionRecord: UserReactionRecordStorage = TODO("Not yet implemented")
    override val userLog: UserLogInfoStorage = TODO("Not yet implemented")
    override val uploadRecord: UploadRecordInfoStorage = TODO("Not yet implemented")
    override val fileRef: FileRefInfoStorage = TODO("Not yet implemented")
}

fun From.whereIf(block: WhereBuilder.() -> Expression?): OrderByRouter {
    val b = WhereBuilder().block() ?: return this
    return where(b)
}
