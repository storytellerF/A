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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

interface ModelObservable<T> {
    val deferred: CompletableDeferred<List<T>>

    fun remove()
}

sealed interface ModelCollection {
    data object Users : ModelCollection

    data object Topics : ModelCollection

    data object Titles : ModelCollection

    data object Rooms : ModelCollection

    data object Communities : ModelCollection

    data class SearchUser(val word: String) : ModelCollection

    data class SearchRoom(val word: String, val community: PrimaryKey?) : ModelCollection

    data class SearchTopic(
        val word: List<String>,
        val parentId: PrimaryKey?,
    ) : ModelCollection

    data class SearchCommunity(
        val joinStatusSearch: JoinStatusSearch,
        val word: String,
        val target: PrimaryKey? = null,
    ) : ModelCollection

    data object Recommend : ModelCollection

    data object Reactions : ModelCollection

    data class ReactionList(val objectId: PrimaryKey) : ModelCollection

    data class SearchTitle(
        val uid: PrimaryKey,
        val searchType: TitleSearchType,
        val status: TitleStatus? = null,
        val type: TitleType? = null,
        val scopeId: PrimaryKey? = null,
    ) : ModelCollection

    data object Alternatives : ModelCollection

    data class TopicList(val objectId: PrimaryKey) : ModelCollection

    data class Medias(val objectId: PrimaryKey) : ModelCollection

    data class Members(val word: String, val objectId: PrimaryKey) : ModelCollection

    data object Download : ModelCollection
}

fun ModelCollection.getName(): String {
    return when (this) {
        ModelCollection.Alternatives -> "alternatives"
        ModelCollection.Communities -> "communities"
        ModelCollection.Download -> "downloads"
        is ModelCollection.Medias -> "medias_$objectId"
        is ModelCollection.Members -> "members_${objectId}_$word"
        is ModelCollection.ReactionList -> "reactions_$objectId"
        ModelCollection.Reactions -> "reactions"
        ModelCollection.Recommend -> "topics_recommend"
        ModelCollection.Rooms -> "rooms"
        is ModelCollection.SearchCommunity -> "communities_${word}_${target}_$joinStatusSearch"
        is ModelCollection.SearchRoom -> "rooms_${word}_$community"
        is ModelCollection.SearchTitle -> "titles_${uid}_${searchType}_${status}_${type}_$scopeId"
        is ModelCollection.SearchTopic -> "topics_${word}_$parentId"
        is ModelCollection.SearchUser -> "users_$word"
        ModelCollection.Titles -> "title"
        is ModelCollection.TopicList -> "topics_$objectId"
        ModelCollection.Topics -> "topics"
        ModelCollection.Users -> "users"
    }
}

@Serializable
data class RemoteKeys(val collectionName: String, val key: String?)

interface SaveStorage<T> {
    suspend fun save(modelCollection: ModelCollection, t: T)
}

interface ObservePrimaryKeyDatumStorage<T> {
    fun observeDatum(id: PrimaryKey): Flow<T?>
}

interface ObserveDataStorage<T : Any> {
    fun observeData(
        modelCollection: ModelCollection,
    ): PagingSource<Int, T>
}

interface ObserveDatumStorage<T : Any> {
    fun observeDatum(key: String): Flow<T?>
}

interface GetStorage<T> {
    suspend fun getDocument(modelCollection: ModelCollection, id: PrimaryKey): T?
}

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

interface UserStorage : SaveStorage<UserInfo>, ObserveDataStorage<UserInfo>,
    ObserveDatumStorage<UserInfo>,
    ObservePrimaryKeyDatumStorage<UserInfo>

interface CommunityStorage : SaveStorage<CommunityInfo>, ObserveDataStorage<CommunityInfo>,
    ObserveDatumStorage<CommunityInfo>, ObservePrimaryKeyDatumStorage<CommunityInfo>,
    GetStorage<CommunityInfo>

interface TopicStorage : SaveStorage<TopicInfo>, ObserveDataStorage<TopicInfo>,
    ObserveDatumStorage<TopicInfo>, ObservePrimaryKeyDatumStorage<TopicInfo>, GetStorage<TopicInfo>

interface TitleStorage : SaveStorage<TitleInfo>, ObserveDataStorage<TitleInfo>,
    ObservePrimaryKeyDatumStorage<TitleInfo>

interface RoomStorage : SaveStorage<RoomInfo>, ObserveDataStorage<RoomInfo>,
    ObserveDatumStorage<RoomInfo>,
    ObservePrimaryKeyDatumStorage<RoomInfo>

interface ReactionStorage : SaveStorage<ReactionInfo>, ObserveDataStorage<ReactionInfo>
interface AlternativesStorage : SaveStorage<AlternativeAccountInfo>,
    ObserveDataStorage<AlternativeAccountInfo>

interface OSSStorage : SaveStorage<MediaInfo>, ObserveDataStorage<MediaInfo>

interface DownloadStorage : SaveStorage<DownloadInfo>, ObservePrimaryKeyDatumStorage<DownloadInfo>,
    GetStorage<DownloadInfo>

interface RemoteKeyStorage {
    suspend fun getPreRemoteKey(modelCollection: ModelCollection): RemoteKeys?
    suspend fun getNextRemoteKey(modelCollection: ModelCollection): RemoteKeys?
    suspend fun savePreRemoteKey(remoteKeys: RemoteKeys)
    suspend fun saveNextRemoteKey(remoteKeys: RemoteKeys)

    suspend fun deletePreRemoteKey(modelCollection: ModelCollection)
    suspend fun deleteNextRemoteKey(modelCollection: ModelCollection)
}

suspend fun <T, Storage> Storage.update(
    modelCollection: ModelCollection,
    id: PrimaryKey,
    block: (T) -> T
) where Storage : SaveStorage<T>, Storage : GetStorage<T> {
    val document = getDocument(modelCollection, id) ?: return
    val value = block(document)
    save(modelCollection, value)
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
