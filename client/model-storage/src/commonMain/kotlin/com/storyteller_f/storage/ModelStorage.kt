package com.storyteller_f.storage

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.storyteller_f.shared.model.AlternativeAccountInfo
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.TitleStatus
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

sealed interface UserCollection {
    data object Users : UserCollection
    data class SearchUser(val word: String) : UserCollection
    data class Members(val word: String, val objectId: PrimaryKey) : UserCollection
}

sealed interface TopicCollection {
    data object Topics : TopicCollection
    data class SearchTopic(
        val word: List<String>,
        val parentId: PrimaryKey?,
    ) : TopicCollection

    data object Recommend : TopicCollection
    data class TopicList(val objectId: PrimaryKey) : TopicCollection
}

sealed interface TitleCollection {
    data object Titles : TitleCollection
    data class SearchTitle(
        val uid: PrimaryKey,
        val searchType: TitleSearchType,
        val status: TitleStatus? = null,
        val type: TitleType? = null,
        val scopeId: PrimaryKey? = null,
    ) : TitleCollection
}

sealed interface RoomCollection {
    data object Rooms : RoomCollection
    data class SearchRoom(val word: String, val community: PrimaryKey?) : RoomCollection
}

sealed interface CommunityCollection {
    data object Communities : CommunityCollection

    data class SearchCommunity(
        val joinStatusSearch: JoinStatusSearch,
        val word: String,
        val target: PrimaryKey? = null,
    ) : CommunityCollection
}

sealed interface ReactionCollection {
    data object Reactions : ReactionCollection

    data class ReactionList(val objectId: PrimaryKey) : ReactionCollection
}

data object DownloadCollection {
    const val NAME = "download"
}
data class MediasCollection(val objectId: PrimaryKey) {
    fun getName() = "medias_$objectId"
}
data object AlternativesCollection {
    const val NAME = "alternatives"
}

fun UserCollection.getName(): String {
    return when (this) {
        is UserCollection.SearchUser -> "users_$word"
        UserCollection.Users -> "users"
        is UserCollection.Members -> "members_${objectId}_$word"
    }
}

fun RoomCollection.getName(): String {
    return when (this) {
        RoomCollection.Rooms -> "rooms"
        is RoomCollection.SearchRoom -> "rooms_${word}_$community"
    }
}

fun ReactionCollection.getName(): String {
    return when (this) {
        ReactionCollection.Reactions -> "reactions"
        is ReactionCollection.ReactionList -> "reactions_$objectId"
    }
}

fun TopicCollection.getName(): String {
    return when (this) {
        is TopicCollection.SearchTopic -> "topics_${word}_$parentId"
        is TopicCollection.TopicList -> "topics_$objectId"
        TopicCollection.Topics -> "topics"
        TopicCollection.Recommend -> "topics_recommend"
    }
}

fun CommunityCollection.getName(): String {
    return when (this) {
        CommunityCollection.Communities -> "communities"
        is CommunityCollection.SearchCommunity -> "communities_${word}_${target}_$joinStatusSearch"
    }
}

fun TitleCollection.getName(): String {
    return when (this) {
        is TitleCollection.SearchTitle -> "titles_${uid}_${searchType}_${status}_${type}_$scopeId"
        TitleCollection.Titles -> "title"
    }
}

@Serializable
data class RemoteKeys(val collectionName: String, val key: String?)

interface ModelStorage {
    val userStorage: UserStorage
    val communityStorage: CommunityStorage
    val topicStorage: TopicStorage
    val titleStorage: TitleStorage
    val roomStorage: RoomStorage
    val remoteKeyStorage: RemoteKeyStorage
    val reactionStorage: ReactionStorage
    val alternativesStorage: AlternativesStorage
    val ossStorage: OSSStorage
    val downloadStorage: DownloadStorage
}

interface UserStorage {
    fun observeDatum(id: PrimaryKey): Flow<UserInfo?>
    suspend fun save(collection: UserCollection, t: UserInfo)
    fun observeData(collection: UserCollection): PagingSource<Int, UserInfo>
    fun observeDatum(key: String): Flow<UserInfo?>
    suspend fun clean(collection: UserCollection)
}

interface CommunityStorage {
    fun observeDatum(id: PrimaryKey): Flow<CommunityInfo?>
    fun observeDatum(key: String): Flow<CommunityInfo?>
    suspend fun save(collection: CommunityCollection, t: CommunityInfo)
    fun observeData(collection: CommunityCollection): PagingSource<Int, CommunityInfo>
    suspend fun getDocument(collection: CommunityCollection, id: PrimaryKey): CommunityInfo?
    suspend fun clean(collection: CommunityCollection)
}

interface TopicStorage {
    fun observeDatum(id: PrimaryKey): Flow<TopicInfo?>
    suspend fun save(collection: TopicCollection, t: TopicInfo)
    fun observeData(collection: TopicCollection): PagingSource<Int, TopicInfo>
    fun observeDatum(key: String): Flow<TopicInfo?>
    suspend fun getDocument(collection: TopicCollection, id: PrimaryKey): TopicInfo?
    suspend fun clean(collection: TopicCollection)
}

interface TitleStorage {
    fun observeDatum(id: PrimaryKey): Flow<TitleInfo?>
    suspend fun save(collection: TitleCollection, t: TitleInfo)
    fun observeData(collection: TitleCollection): PagingSource<Int, TitleInfo>
    suspend fun clean(collection: TitleCollection)
}

interface RoomStorage {
    fun observeDatum(id: PrimaryKey): Flow<RoomInfo?>
    suspend fun save(collection: RoomCollection, t: RoomInfo)
    fun observeData(collection: RoomCollection): PagingSource<Int, RoomInfo>
    fun observeDatum(key: String): Flow<RoomInfo?>
    suspend fun clean(collection: RoomCollection)
}

interface ReactionStorage {
    suspend fun save(collection: ReactionCollection, t: ReactionInfo)
    fun observeData(collection: ReactionCollection): PagingSource<Int, ReactionInfo>
    suspend fun clean(collection: ReactionCollection)
}

interface AlternativesStorage {
    suspend fun save(collection: AlternativesCollection, t: AlternativeAccountInfo)
    fun observeData(collection: AlternativesCollection): PagingSource<Int, AlternativeAccountInfo>
    suspend fun clean(collection: AlternativesCollection)
}

interface OSSStorage {
    suspend fun save(collection: MediasCollection, t: MediaInfo)
    fun observeData(collection: MediasCollection): PagingSource<Int, MediaInfo>
    suspend fun clean(collection: MediasCollection)
}

interface DownloadStorage {
    suspend fun save(collection: DownloadCollection, t: DownloadInfo)
    fun observeDatum(id: PrimaryKey): Flow<DownloadInfo?>
    suspend fun getDocument(collection: DownloadCollection, id: PrimaryKey): DownloadInfo?
}

interface RemoteKeyStorage {
    suspend fun getPreRemoteKey(collection: String): RemoteKeys?
    suspend fun getNextRemoteKey(collection: String): RemoteKeys?
    suspend fun savePreRemoteKey(remoteKeys: RemoteKeys)
    suspend fun saveNextRemoteKey(remoteKeys: RemoteKeys)

    suspend fun deletePreRemoteKey(collection: String)
    suspend fun deleteNextRemoteKey(collection: String)
    companion object {
        const val PRE_COLLECTION = "pre_remote_keys"
        const val NEXT_COLLECTION = "next_remote_keys"
    }
}

suspend fun TopicStorage.update(
    collection: TopicCollection,
    id: PrimaryKey,
    block: (TopicInfo) -> TopicInfo
) {
    val document = getDocument(collection, id) ?: return
    val value = block(document)
    save(collection, value)
}

class WrappedPagingSource<K : Any, T : Any, M : Any>(
    val rawSource: PagingSource<K, T>,
    val process: suspend (List<T>) -> List<M>,
) :
    PagingSource<K, M>() {
    init {
        rawSource.registerInvalidatedCallback {
            invalidate()
        }
    }

    override fun getRefreshKey(state: PagingState<K, M>): K? {
        return null
    }

    override suspend fun load(params: LoadParams<K>): LoadResult<K, M> {
        val result = rawSource.load(params)
        return when (result) {
            is LoadResult.Page<K, T> -> LoadResult.Page(
                process(result.data),
                result.prevKey,
                result.nextKey
            )

            is LoadResult.Error<K, T> -> LoadResult.Error(result.throwable)
            is LoadResult.Invalid<K, T> -> LoadResult.Invalid()
        }
    }
}
