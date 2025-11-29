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

    override suspend fun save(collection: UserCollection, userInfo: UserInfo) {
        with(kotbaseDocumentSource.getCollection<UserInfo>(UserCollection.Users.getName())) {
            save(userInfo.id, userInfo)
            save(userInfo.aid, userInfo)
        }
        when (collection) {
            is UserCollection.SearchUser, is UserCollection.Members ->
                kotbaseDocumentSource.getCollection<UserInfo>(collection.getName())
                    .save(userInfo.id, userInfo)

            else -> {
            }
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
}

class CommunityDocumentInfoStorage(
    val kotbaseDocumentSource: KotbaseDocumentSource
) :
    CommunityInfoStorage {
    override fun observeDatum(
        id: PrimaryKey
    ): Flow<CommunityInfo?> {
        return kotbaseDocumentSource.getCollection<CommunityInfo>(CommunityCollection.Communities.getName())
            .observeDatum(id)
    }

    override fun observeDatum(
        key: String
    ): Flow<CommunityInfo?> {
        return kotbaseDocumentSource.getCollection<CommunityInfo>(CommunityCollection.Communities.getName())
            .observeDatum {
                "aid" equalTo key
            }
    }

    override suspend fun save(collection: CommunityCollection, communityInfo: CommunityInfo) {
        with(kotbaseDocumentSource.getCollection<CommunityInfo>(CommunityCollection.Communities.getName())) {
            save(communityInfo.id, communityInfo)
            save(communityInfo.aid, communityInfo)
        }
        when (collection) {
            is CommunityCollection.SearchCommunity -> {
                kotbaseDocumentSource.getCollection<CommunityInfo>(collection.getName())
                    .save(communityInfo.id, communityInfo)
            }

            else -> {}
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
        id: PrimaryKey
    ): CommunityInfo? {
        return kotbaseDocumentSource.getCollection<CommunityInfo>(collection.getName())
            .getDocument(id)
    }

    override suspend fun clean(collection: CommunityCollection) = Unit
}

class TopicDocumentInfoStorage(
    val kotbaseDocumentSource: KotbaseDocumentSource
) : TopicInfoStorage {
    override fun observeDatum(id: PrimaryKey): Flow<TopicInfo?> {
        return kotbaseDocumentSource.getCollection<TopicInfo>(TopicCollection.Topics.getName())
            .observeDatum(id)
    }

    override suspend fun save(collection: TopicCollection, topicInfo: TopicInfo) {
        with(kotbaseDocumentSource.getCollection<TopicInfo>(TopicCollection.Topics.getName())) {
            save(topicInfo.id, topicInfo)
            save(topicInfo.aid, topicInfo)
        }
        kotbaseDocumentSource.getCollection<TopicInfo>(collection.getName()).save(topicInfo.id, topicInfo)
        when (collection) {
            is TopicCollection.Recommend, is TopicCollection.TopicList, is TopicCollection.SearchTopic -> {
                kotbaseDocumentSource.getCollection<TopicInfo>(collection.getName())
                    .save(topicInfo.id, topicInfo)
            }

            else -> {}
        }
    }

    override fun observeData(
        collection: TopicCollection,
    ): PagingSource<Int, TopicInfo> {
        return when (collection) {
            is TopicCollection.Recommend -> {
                kotbaseDocumentSource.getCollection<TopicInfo>(collection.getName())
                    .getSource {
                        orderBy {
                            "id".descending()
                        }
                    }
            }

            is TopicCollection.TopicList -> {
                kotbaseDocumentSource.getCollection<TopicInfo>(collection.getName())
                    .getSource {
                        orderBy {
                            "pinned".ascending()
                            "id".descending()
                        }
                    }
            }

            is TopicCollection.SearchTopic -> {
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
        id: PrimaryKey
    ): TopicInfo? {
        return kotbaseDocumentSource.getCollection<TopicInfo>(collection.getName())
            .getDocument(id)
    }

    override suspend fun clean(collection: TopicCollection) = Unit
}

class TitleDocumentInfoStorage(
    val kotbaseDocumentSource: KotbaseDocumentSource
) : TitleInfoStorage {
    override fun observeDatum(id: PrimaryKey): Flow<TitleInfo?> {
        return kotbaseDocumentSource.getCollection<TitleInfo>(TitleCollection.Titles.getName())
            .observeDatum(id)
    }

    override suspend fun save(collection: TitleCollection, titleInfo: TitleInfo) {
        when (collection) {
            is TitleCollection.Titles -> {
                kotbaseDocumentSource.getCollection<TitleInfo>("titles").save(titleInfo.id, titleInfo)
            }

            else -> {}
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
}

class RoomDocumentInfoStorage(
    val kotbaseDocumentSource: KotbaseDocumentSource
) : RoomInfoStorage {
    override fun observeDatum(id: PrimaryKey): Flow<RoomInfo?> {
        return kotbaseDocumentSource.getCollection<RoomInfo>(RoomCollection.Rooms.getName())
            .observeDatum(id)
    }

    override suspend fun save(collection: RoomCollection, roomInfo: RoomInfo) {
        with(kotbaseDocumentSource.getCollection<RoomInfo>(RoomCollection.Rooms.getName())) {
            save(roomInfo.id, roomInfo)
            save(roomInfo.aid, roomInfo)
        }
        when (collection) {
            is RoomCollection.SearchRoom -> {
                kotbaseDocumentSource.getCollection<RoomInfo>(collection.getName())
                    .save(roomInfo.id, roomInfo)
            }

            else -> {}
        }
    }

    override fun observeData(
        collection: RoomCollection,
    ): PagingSource<Int, RoomInfo> {
        return when (collection) {
            is RoomCollection.SearchRoom -> {
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
        return kotbaseDocumentSource.getCollection<RoomInfo>(RoomCollection.Rooms.getName())
            .observeDatum {
                "aid" equalTo key
            }
    }

    override suspend fun clean(collection: RoomCollection) = Unit
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

    override suspend fun save(collection: ReactionCollection, reactionInfo: ReactionInfo) {
        kotbaseDocumentSource.getCollection<ReactionInfo>(collection.getName())
            .save("${reactionInfo.objectId}-${reactionInfo.emoji}", reactionInfo)
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
}

class ChildAccountDocumentStorage(val kotbaseDocumentSource: KotbaseDocumentSource) :
    ChildAccountStorage {

    override suspend fun save(collection: ChildAccountCollection, childAccountInfo: ChildAccountInfo) {
        kotbaseDocumentSource.getCollection<ChildAccountInfo>(collection.NAME)
            .save(childAccountInfo.id, childAccountInfo)
    }

    override fun observeData(
        collection: ChildAccountCollection,
    ): PagingSource<Int, ChildAccountInfo> {
        return kotbaseDocumentSource.getCollection<ChildAccountInfo>(collection.NAME)
            .getSource {
                orderBy {
                    "id".descending()
                }
            }
    }

    override suspend fun clean(collection: ChildAccountCollection) = Unit
}

class FileInfoDocumentStorage(val kotbaseDocumentSource: KotbaseDocumentSource) : FileInfoStorage {

    override suspend fun save(
        collection: MediasCollection,
        fileInfo: FileInfo
    ) {
        kotbaseDocumentSource.getCollection<FileInfo>(collection.getName()).save(fileInfo.id, fileInfo)
    }

    override fun observeData(
        collection: MediasCollection,
    ): PagingSource<Int, FileInfo> {
        return kotbaseDocumentSource.getCollection<FileInfo>(collection.getName())
            .getSource {
                orderBy {
                    "id".descending()
                }
            }
    }

    override suspend fun clean(collection: MediasCollection) = Unit
}

class DownloadInfoDocumentStorage(val kotbaseDocumentSource: KotbaseDocumentSource) : DownloadInfoStorage {

    override suspend fun save(
        collection: DownloadCollection,
        downloadInfo: DownloadInfo
    ) {
        kotbaseDocumentSource.getCollection<DownloadInfo>(collection.NAME)
            .save(downloadInfo.fileInfo.id, downloadInfo)
    }

    override fun observeDatum(
        id: PrimaryKey
    ): Flow<DownloadInfo?> {
        return kotbaseDocumentSource.getCollection<DownloadInfo>(DownloadCollection.NAME)
            .observeDatum {
                "_id" equalTo id
            }
    }

    override suspend fun getDocument(
        collection: DownloadCollection,
        id: PrimaryKey
    ): DownloadInfo? {
        return kotbaseDocumentSource.getCollection<DownloadInfo>(collection.NAME)
            .getDocument(id)
    }

    override fun observeData(): PagingSource<Int, DownloadInfo> {
        return kotbaseDocumentSource.getCollection<DownloadInfo>(DownloadCollection.NAME)
            .getSource {
                orderBy {
                    "_id".descending()
                }
            }
    }
}

class DocumentModelStorage(source: KotbaseDocumentSource) : ModelStorage {
    override val userInfoStorage: UserInfoStorage = UserDocumentInfoStorage(source)
    override val communityInfoStorage: CommunityInfoStorage = CommunityDocumentInfoStorage(source)
    override val topicInfoStorage: TopicInfoStorage = TopicDocumentInfoStorage(source)
    override val titleInfoStorage: TitleInfoStorage = TitleDocumentInfoStorage(source)
    override val roomInfoStorage: RoomInfoStorage = RoomDocumentInfoStorage(source)
    override val remoteKeyStorage: RemoteKeyStorage = RemoteKeyDocumentStorage(source)
    override val reactionInfoStorage: ReactionInfoStorage = ReactionDocumentInfoStorage(source)
    override val childAccountStorage: ChildAccountStorage = ChildAccountDocumentStorage(source)
    override val fileInfoStorage: FileInfoStorage = FileInfoDocumentStorage(source)
    override val downloadInfoStorage: DownloadInfoStorage = DownloadInfoDocumentStorage(source)
}

fun From.whereIf(block: WhereBuilder.() -> Expression?): OrderByRouter {
    val b = WhereBuilder().block() ?: return this
    return where(b)
}
