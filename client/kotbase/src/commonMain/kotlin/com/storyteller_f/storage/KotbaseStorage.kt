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
        return kotbaseDocumentSource.getCollection<UserInfo>(ModelCollection.Users.getName())
            .observeDatum(id)
    }

    override suspend fun save(modelCollection: ModelCollection, t: UserInfo) {
        with(kotbaseDocumentSource.getCollection<UserInfo>(ModelCollection.Users.getName())) {
            save(t.id, t)
            save(t.aid, t)
        }
        when (modelCollection) {
            is ModelCollection.SearchUser, is ModelCollection.Members ->
                kotbaseDocumentSource.getCollection<UserInfo>(modelCollection.getName())
                    .save(t.id, t)

            else -> {
            }
        }
    }

    override fun observeData(
        modelCollection: ModelCollection,
    ): PagingSource<Int, UserInfo> {
        return when (modelCollection) {
            is ModelCollection.Members -> {
                kotbaseDocumentSource.getCollection<UserInfo>(modelCollection.getName()).getSource {
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
        return kotbaseDocumentSource.getCollection<UserInfo>(ModelCollection.Users.getName())
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
        return kotbaseDocumentSource.getCollection<CommunityInfo>(ModelCollection.Communities.getName())
            .observeDatum(id)
    }

    override fun observeDatum(
        key: String
    ): Flow<CommunityInfo?> {
        return kotbaseDocumentSource.getCollection<CommunityInfo>(ModelCollection.Communities.getName())
            .observeDatum {
                "aid" equalTo key
            }
    }

    override suspend fun save(modelCollection: ModelCollection, t: CommunityInfo) {
        with(kotbaseDocumentSource.getCollection<CommunityInfo>(ModelCollection.Communities.getName())) {
            save(t.id, t)
            save(t.aid, t)
        }
        when (modelCollection) {
            is ModelCollection.SearchCommunity -> {
                kotbaseDocumentSource.getCollection<CommunityInfo>(modelCollection.getName())
                    .save(t.id, t)
            }

            else -> {}
        }
    }

    override fun observeData(
        modelCollection: ModelCollection,
    ): PagingSource<Int, CommunityInfo> {
        return when (modelCollection) {
            is ModelCollection.SearchCommunity -> {
                kotbaseDocumentSource.getCollection<CommunityInfo>(modelCollection.getName())
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
        modelCollection: ModelCollection,
        id: PrimaryKey
    ): CommunityInfo? {
        return kotbaseDocumentSource.getCollection<CommunityInfo>(modelCollection.getName())
            .getDocument(id)
    }
}

class TopicDocumentStorage(
    val kotbaseDocumentSource: KotbaseDocumentSource
) : TopicStorage {
    override fun observeDatum(id: PrimaryKey): Flow<TopicInfo?> {
        return kotbaseDocumentSource.getCollection<TopicInfo>(ModelCollection.Topics.getName())
            .observeDatum(id)
    }

    override suspend fun save(modelCollection: ModelCollection, t: TopicInfo) {
        with(kotbaseDocumentSource.getCollection<TopicInfo>(ModelCollection.Topics.getName())) {
            save(t.id, t)
            save(t.aid, t)
        }
        kotbaseDocumentSource.getCollection<TopicInfo>(modelCollection.getName()).save(t.id, t)
        when (modelCollection) {
            is ModelCollection.Recommend, is ModelCollection.TopicList, is ModelCollection.SearchTopic -> {
                kotbaseDocumentSource.getCollection<TopicInfo>(modelCollection.getName())
                    .save(t.id, t)
            }

            else -> {}
        }
    }

    override fun observeData(
        modelCollection: ModelCollection,
    ): PagingSource<Int, TopicInfo> {
        return when (modelCollection) {
            is ModelCollection.Recommend -> {
                kotbaseDocumentSource.getCollection<TopicInfo>(modelCollection.getName())
                    .getSource {
                        orderBy {
                            "id".descending()
                        }
                    }
            }

            is ModelCollection.TopicList -> {
                kotbaseDocumentSource.getCollection<TopicInfo>(modelCollection.getName())
                    .getSource {
                        orderBy {
                            "pinned".ascending()
                            "id".descending()
                        }
                    }
            }

            is ModelCollection.SearchTopic -> {
                kotbaseDocumentSource.getCollection<TopicInfo>(modelCollection.getName())
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
        return kotbaseDocumentSource.getCollection<TopicInfo>(ModelCollection.Topics.getName())
            .observeDatum {
                "aid" equalTo key
            }
    }

    override suspend fun getDocument(
        modelCollection: ModelCollection,
        id: PrimaryKey
    ): TopicInfo? {
        return kotbaseDocumentSource.getCollection<TopicInfo>(modelCollection.getName())
            .getDocument(id)
    }
}

class TitleDocumentStorage(
    val kotbaseDocumentSource: KotbaseDocumentSource
) : TitleStorage {
    override fun observeDatum(id: PrimaryKey): Flow<TitleInfo?> {
        return kotbaseDocumentSource.getCollection<TitleInfo>(ModelCollection.Titles.getName())
            .observeDatum(id)
    }

    override suspend fun save(modelCollection: ModelCollection, t: TitleInfo) {
        when (modelCollection) {
            is ModelCollection.Titles -> {
                kotbaseDocumentSource.getCollection<TitleInfo>("titles").save(t.id, t)
            }

            else -> {}
        }
    }

    override fun observeData(
        modelCollection: ModelCollection,
    ): PagingSource<Int, TitleInfo> {
        return when (modelCollection) {
            is ModelCollection.SearchTitle -> {
                kotbaseDocumentSource.getCollection<TitleInfo>(modelCollection.getName())
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
        return kotbaseDocumentSource.getCollection<RoomInfo>(ModelCollection.Rooms.getName())
            .observeDatum(id)
    }

    override suspend fun save(modelCollection: ModelCollection, t: RoomInfo) {
        with(kotbaseDocumentSource.getCollection<RoomInfo>(ModelCollection.Rooms.getName())) {
            save(t.id, t)
            save(t.aid, t)
        }
        when (modelCollection) {
            is ModelCollection.SearchRoom -> {
                kotbaseDocumentSource.getCollection<RoomInfo>(modelCollection.getName())
                    .save(t.id, t)
            }

            else -> {}
        }
    }

    override fun observeData(
        modelCollection: ModelCollection,
    ): PagingSource<Int, RoomInfo> {
        return when (modelCollection) {
            is ModelCollection.SearchRoom -> {
                kotbaseDocumentSource.getCollection<RoomInfo>(
                    modelCollection.getName(),
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
        return kotbaseDocumentSource.getCollection<RoomInfo>(ModelCollection.Rooms.getName())
            .observeDatum {
                "aid" equalTo key
            }
    }
}

class RemoteKeyDocumentStorage(
    val kotbaseDocumentSource: KotbaseDocumentSource,
) : RemoteKeyStorage {
    override suspend fun getPreRemoteKey(modelCollection: ModelCollection): RemoteKeys? {
        return kotbaseDocumentSource.getCollection<RemoteKeys>("pre_remote_keys")
            .getDocument(modelCollection.getName())
    }

    override suspend fun getNextRemoteKey(modelCollection: ModelCollection): RemoteKeys? {
        return kotbaseDocumentSource.getCollection<RemoteKeys>("next_remote_keys")
            .getDocument(modelCollection.getName())
    }

    override suspend fun savePreRemoteKey(remoteKeys: RemoteKeys) {
        kotbaseDocumentSource.getCollection<RemoteKeys>("pre_remote_keys")
            .saveDocument(remoteKeys.collectionName, remoteKeys)
    }

    override suspend fun saveNextRemoteKey(remoteKeys: RemoteKeys) {
        kotbaseDocumentSource.getCollection<RemoteKeys>("next_remote_keys")
            .saveDocument(remoteKeys.collectionName, remoteKeys)
    }

    override suspend fun deletePreRemoteKey(modelCollection: ModelCollection) {
        kotbaseDocumentSource.getCollection<RemoteKeys>("pre_remote_keys")
            .deleteDocument(modelCollection.getName())
    }

    override suspend fun deleteNextRemoteKey(modelCollection: ModelCollection) {
        kotbaseDocumentSource.getCollection<RemoteKeys>("next_remote_keys")
            .deleteDocument(modelCollection.getName())
    }
}

class ReactionDocumentStorage(val kotbaseDocumentSource: KotbaseDocumentSource) : ReactionStorage {

    override suspend fun save(modelCollection: ModelCollection, t: ReactionInfo) {
        kotbaseDocumentSource.getCollection<ReactionInfo>(modelCollection.getName())
            .save("${t.objectId}-${t.emoji}", t)
    }

    override fun observeData(
        modelCollection: ModelCollection,
    ): PagingSource<Int, ReactionInfo> {
        return when (modelCollection) {
            is ModelCollection.ReactionList -> {
                kotbaseDocumentSource.getCollection<ReactionInfo>(modelCollection.getName())
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

    override suspend fun save(
        modelCollection: ModelCollection,
        t: AlternativeAccountInfo
    ) {
        TODO("Not yet implemented")
    }

    override fun observeData(
        modelCollection: ModelCollection,
    ): PagingSource<Int, AlternativeAccountInfo> {
        return when (modelCollection) {
            is ModelCollection.Alternatives -> {
                kotbaseDocumentSource.getCollection<AlternativeAccountInfo>(modelCollection.getName())
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

class OSSDocumentStorage(val kotbaseDocumentSource: KotbaseDocumentSource) : OSSStorage {

    override suspend fun save(
        modelCollection: ModelCollection,
        t: MediaInfo
    ) {
        kotbaseDocumentSource.getCollection<MediaInfo>(modelCollection.getName()).save(t.id, t)
    }

    override fun observeData(
        modelCollection: ModelCollection,
    ): PagingSource<Int, MediaInfo> {
        return when (modelCollection) {
            is ModelCollection.Medias -> {
                kotbaseDocumentSource.getCollection<MediaInfo>(modelCollection.getName())
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

class DownloadDocumentStorage(val kotbaseDocumentSource: KotbaseDocumentSource) : DownloadStorage {

    override suspend fun save(
        modelCollection: ModelCollection,
        t: DownloadInfo
    ) {
        kotbaseDocumentSource.getCollection<DownloadInfo>(modelCollection.getName())
            .save(t.mediaInfo.id, t)
    }

    override fun observeDatum(
        id: PrimaryKey
    ): Flow<DownloadInfo?> {
        return kotbaseDocumentSource.getCollection<DownloadInfo>(ModelCollection.Download.getName())
            .observeDatum {
                "_id" equalTo id
            }
    }

    override suspend fun getDocument(
        modelCollection: ModelCollection,
        id: PrimaryKey
    ): DownloadInfo? {
        return kotbaseDocumentSource.getCollection<DownloadInfo>(modelCollection.getName())
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
