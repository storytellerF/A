package com.storyteller_f.storage

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
import kotbase.Expression
import kotbase.From
import kotbase.OrderByRouter
import kotbase.ktx.WhereBuilder
import kotbase.ktx.orderBy
import kotlinx.coroutines.flow.Flow

class UserDocumentStorage(
    val kotbaseDocumentSource: KotbaseDocumentSource
) : UserStorage {
    override fun observeDatum(id: PrimaryKey): Flow<UserInfo?> {
        return kotbaseDocumentSource.getCollection<UserInfo>(UserCollection.Users.getName())
            .observeDatum(id)
    }

    override suspend fun save(collection: UserCollection, t: UserInfo) {
        with(kotbaseDocumentSource.getCollection<UserInfo>(UserCollection.Users.getName())) {
            save(t.id, t)
            save(t.aid, t)
        }
        when (collection) {
            is UserCollection.SearchUser, is UserCollection.Members ->
                kotbaseDocumentSource.getCollection<UserInfo>(collection.getName())
                    .save(t.id, t)

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
}

class CommunityDocumentStorage(
    val kotbaseDocumentSource: KotbaseDocumentSource
) :
    CommunityStorage {
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

    override suspend fun save(collection: CommunityCollection, t: CommunityInfo) {
        with(kotbaseDocumentSource.getCollection<CommunityInfo>(CommunityCollection.Communities.getName())) {
            save(t.id, t)
            save(t.aid, t)
        }
        when (collection) {
            is CommunityCollection.SearchCommunity -> {
                kotbaseDocumentSource.getCollection<CommunityInfo>(collection.getName())
                    .save(t.id, t)
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
}

class TopicDocumentStorage(
    val kotbaseDocumentSource: KotbaseDocumentSource
) : TopicStorage {
    override fun observeDatum(id: PrimaryKey): Flow<TopicInfo?> {
        return kotbaseDocumentSource.getCollection<TopicInfo>(TopicCollection.Topics.getName())
            .observeDatum(id)
    }

    override suspend fun save(collection: TopicCollection, t: TopicInfo) {
        with(kotbaseDocumentSource.getCollection<TopicInfo>(TopicCollection.Topics.getName())) {
            save(t.id, t)
            save(t.aid, t)
        }
        kotbaseDocumentSource.getCollection<TopicInfo>(collection.getName()).save(t.id, t)
        when (collection) {
            is TopicCollection.Recommend, is TopicCollection.TopicList, is TopicCollection.SearchTopic -> {
                kotbaseDocumentSource.getCollection<TopicInfo>(collection.getName())
                    .save(t.id, t)
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
}

class TitleDocumentStorage(
    val kotbaseDocumentSource: KotbaseDocumentSource
) : TitleStorage {
    override fun observeDatum(id: PrimaryKey): Flow<TitleInfo?> {
        return kotbaseDocumentSource.getCollection<TitleInfo>(TitleCollection.Titles.getName())
            .observeDatum(id)
    }

    override suspend fun save(collection: TitleCollection, t: TitleInfo) {
        when (collection) {
            is TitleCollection.Titles -> {
                kotbaseDocumentSource.getCollection<TitleInfo>("titles").save(t.id, t)
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
}

class RoomDocumentStorage(
    val kotbaseDocumentSource: KotbaseDocumentSource
) : RoomStorage {
    override fun observeDatum(id: PrimaryKey): Flow<RoomInfo?> {
        return kotbaseDocumentSource.getCollection<RoomInfo>(RoomCollection.Rooms.getName())
            .observeDatum(id)
    }

    override suspend fun save(collection: RoomCollection, t: RoomInfo) {
        with(kotbaseDocumentSource.getCollection<RoomInfo>(RoomCollection.Rooms.getName())) {
            save(t.id, t)
            save(t.aid, t)
        }
        when (collection) {
            is RoomCollection.SearchRoom -> {
                kotbaseDocumentSource.getCollection<RoomInfo>(collection.getName())
                    .save(t.id, t)
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
}

class RemoteKeyDocumentStorage(
    val kotbaseDocumentSource: KotbaseDocumentSource,
) : RemoteKeyStorage {
    override suspend fun getPreRemoteKey(collection: String): RemoteKeys? {
        return kotbaseDocumentSource.getCollection<RemoteKeys>("pre_remote_keys")
            .getDocument(collection)
    }

    override suspend fun getNextRemoteKey(collection: String): RemoteKeys? {
        return kotbaseDocumentSource.getCollection<RemoteKeys>("next_remote_keys")
            .getDocument(collection)
    }

    override suspend fun savePreRemoteKey(remoteKeys: RemoteKeys) {
        kotbaseDocumentSource.getCollection<RemoteKeys>("pre_remote_keys")
            .saveDocument(remoteKeys.collectionName, remoteKeys)
    }

    override suspend fun saveNextRemoteKey(remoteKeys: RemoteKeys) {
        kotbaseDocumentSource.getCollection<RemoteKeys>("next_remote_keys")
            .saveDocument(remoteKeys.collectionName, remoteKeys)
    }

    override suspend fun deletePreRemoteKey(collection: String) {
        kotbaseDocumentSource.getCollection<RemoteKeys>("pre_remote_keys")
            .deleteDocument(collection)
    }

    override suspend fun deleteNextRemoteKey(collection: String) {
        kotbaseDocumentSource.getCollection<RemoteKeys>("next_remote_keys")
            .deleteDocument(collection)
    }
}

class ReactionDocumentStorage(val kotbaseDocumentSource: KotbaseDocumentSource) : ReactionStorage {

    override suspend fun save(collection: ReactionCollection, t: ReactionInfo) {
        kotbaseDocumentSource.getCollection<ReactionInfo>(collection.getName())
            .save("${t.objectId}-${t.emoji}", t)
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
}

class AlternativesDocumentStorage(val kotbaseDocumentSource: KotbaseDocumentSource) :
    AlternativesStorage {

    override suspend fun save(collection: AlternativesCollection, t: AlternativeAccountInfo) {
        kotbaseDocumentSource.getCollection<AlternativeAccountInfo>(collection.name)
            .save(t.id, t)
    }

    override fun observeData(
        collection: AlternativesCollection,
    ): PagingSource<Int, AlternativeAccountInfo> {
        return kotbaseDocumentSource.getCollection<AlternativeAccountInfo>(collection.name)
            .getSource {
                orderBy {
                    "id".descending()
                }
            }
    }
}

class OSSDocumentStorage(val kotbaseDocumentSource: KotbaseDocumentSource) : OSSStorage {

    override suspend fun save(
        collection: MediasCollection,
        t: MediaInfo
    ) {
        kotbaseDocumentSource.getCollection<MediaInfo>(collection.getName()).save(t.id, t)
    }

    override fun observeData(
        collection: MediasCollection,
    ): PagingSource<Int, MediaInfo> {
        return kotbaseDocumentSource.getCollection<MediaInfo>(collection.getName())
            .getSource {
                orderBy {
                    "id".descending()
                }
            }
    }
}

class DownloadDocumentStorage(val kotbaseDocumentSource: KotbaseDocumentSource) : DownloadStorage {

    override suspend fun save(
        collection: DownloadCollection,
        t: DownloadInfo
    ) {
        kotbaseDocumentSource.getCollection<DownloadInfo>(collection.name)
            .save(t.mediaInfo.id, t)
    }

    override fun observeDatum(
        id: PrimaryKey
    ): Flow<DownloadInfo?> {
        return kotbaseDocumentSource.getCollection<DownloadInfo>(DownloadCollection.name)
            .observeDatum {
                "_id" equalTo id
            }
    }

    override suspend fun getDocument(
        collection: DownloadCollection,
        id: PrimaryKey
    ): DownloadInfo? {
        return kotbaseDocumentSource.getCollection<DownloadInfo>(collection.name)
            .getDocument(id)
    }
}

class DocumentModelStorage(kotbaseDocumentSource: KotbaseDocumentSource) : ModelStorage {
    override val userStorage: UserStorage =
        UserDocumentStorage(kotbaseDocumentSource)
    override val communityStorage: CommunityStorage =
        CommunityDocumentStorage(kotbaseDocumentSource)
    override val topicStorage: TopicStorage =
        TopicDocumentStorage(kotbaseDocumentSource)
    override val titleStorage: TitleStorage =
        TitleDocumentStorage(kotbaseDocumentSource)
    override val roomStorage: RoomStorage =
        RoomDocumentStorage(kotbaseDocumentSource)
    override val remoteKeyStorage: RemoteKeyStorage =
        RemoteKeyDocumentStorage(kotbaseDocumentSource)
    override val reactionStorage: ReactionStorage = ReactionDocumentStorage(kotbaseDocumentSource)
    override val alternativesStorage: AlternativesStorage =
        AlternativesDocumentStorage(kotbaseDocumentSource)
    override val ossStorage: OSSStorage = OSSDocumentStorage(kotbaseDocumentSource)
    override val downloadStorage: DownloadStorage = DownloadDocumentStorage(kotbaseDocumentSource)
}

fun From.whereIf(block: WhereBuilder.() -> Expression?): OrderByRouter {
    val b = WhereBuilder().block() ?: return this
    return where(b)
}
