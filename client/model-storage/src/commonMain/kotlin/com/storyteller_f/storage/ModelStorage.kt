package com.storyteller_f.storage

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.storyteller_f.shared.model.ChildAccountInfo
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.MemberInfo
import com.storyteller_f.shared.model.PanelOverview
import com.storyteller_f.shared.model.PosterSearch
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.ReactionRecordInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.TitleStatus
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UploadRecordInfo
import com.storyteller_f.shared.model.UserFavoriteInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserLogInfo
import com.storyteller_f.shared.model.UserOverview
import com.storyteller_f.shared.model.UserSubscriptionInfo
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

sealed interface UserCollection {
    data object Users : UserCollection
    data class SearchUser(val word: String) : UserCollection
    data class Members(val word: String, val objectId: PrimaryKey) : UserCollection
    data object AllUsers : UserCollection
}

interface CollectionListStorage<C, I : Any> {
    suspend fun save(collection: C, item: I)
    fun observeData(collection: C): PagingSource<Int, I>
    suspend fun clean(collection: C)
}

interface CollectionItemStorageByKey<C, I : Any> : CollectionListStorage<C, I> {
    fun observeDatum(collection: C, key: String): Flow<I?>
    suspend fun getDocument(collection: C, key: String): I?
    suspend fun delete(collection: C, key: String)
}

interface CollectionItemStorageById<C, I : Any> : CollectionListStorage<C, I> {
    fun observeDatum(id: PrimaryKey): Flow<I?>
}

interface CollectionItemStorageByIdAndKey<C, I : Any> : CollectionItemStorageById<C, I> {
    fun observeDatum(key: String): Flow<I?>
}

interface CollectionItemStorageByIdAndKeyWithGet<C, I : Any> :
    CollectionItemStorageByIdAndKey<C, I> {
    suspend fun getDocument(collection: C, id: PrimaryKey): I?
}

interface GlobalListStorage<I : Any> {
    suspend fun save(item: I)
    fun observeData(): PagingSource<Int, I>
    suspend fun clean()
}

interface GlobalItemStorageById<I : Any> : GlobalListStorage<I> {
    fun observeDatum(id: PrimaryKey): Flow<I?>
    suspend fun getDocument(id: PrimaryKey): I?
}

interface GlobalListStorageWithKey<I : Any, K> : GlobalListStorage<I> {
    fun observeDatum(key: K): Flow<I?>
}

interface SingletonItemStorage<I : Any> {
    suspend fun save(item: I)
    fun observeDatum(): Flow<I?>
}

sealed interface TopicCollection {
    data object Topics : TopicCollection
    data class SearchTopic(
        val word: List<String>,
        val parentId: PrimaryKey?,
    ) : TopicCollection

    data object Recommend : TopicCollection
    data class TopicList(val objectId: PrimaryKey) : TopicCollection
    data class TopicComments(val objectId: PrimaryKey) : TopicCollection
    data class UserComments(val uid: PrimaryKey) : TopicCollection
    data object AllTopics : TopicCollection
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

    data object AllTitles : TitleCollection
}

sealed interface RoomCollection {
    data object Rooms : RoomCollection
    data class SearchRoom(
        val word: String,
        val communityId: PrimaryKey?,
        val joinStatusSearch: JoinStatusSearch
    ) : RoomCollection

    data class AllRooms(val isPrivate: Boolean) : RoomCollection
}

sealed interface CommunityCollection {
    data object Communities : CommunityCollection

    data class SearchCommunity(
        val joinStatusSearch: JoinStatusSearch,
        val word: String,
        val target: PrimaryKey? = null,
        val hasPointer: PosterSearch? = null,
    ) : CommunityCollection

    data object AllCommunities : CommunityCollection
}

sealed interface ReactionCollection {
    data object Reactions : ReactionCollection

    data class ReactionList(val objectId: PrimaryKey) : ReactionCollection
}

sealed interface UserReactionRecordCollection {
    data class UserReactionRecords(val uid: PrimaryKey) : UserReactionRecordCollection
}

data class UploadCollection(val objectId: PrimaryKey) {
    fun getName() = "upload_$objectId"

    companion object {
        fun fromInfo(uploadInfo: UploadInfo) = UploadCollection(uploadInfo.objectId)
    }
}

sealed interface FileCollection {
    data class FileList(val objectId: PrimaryKey) : FileCollection
    data object Files : FileCollection
}

sealed interface UserLogCollection {
    data class UserLogs(val uid: PrimaryKey) : UserLogCollection
}

sealed interface UploadRecordCollection {
    data class UserUploadRecords(val uid: PrimaryKey) : UploadRecordCollection
}

fun FileCollection.getName(): String {
    return when (this) {
        FileCollection.Files -> "files"
        is FileCollection.FileList -> "files_$objectId"
    }
}

fun UserCollection.getName(): String {
    return when (this) {
        is UserCollection.SearchUser -> "users_$word"
        UserCollection.Users -> "users"
        is UserCollection.Members -> "members_${objectId}_$word"
        UserCollection.AllUsers -> "all_users"
    }
}

fun RoomCollection.getName(): String {
    return when (this) {
        RoomCollection.Rooms -> "rooms"
        is RoomCollection.SearchRoom -> "rooms_${word}_${joinStatusSearch}_$communityId"
        is RoomCollection.AllRooms -> "all_rooms_$isPrivate"
    }
}

fun ReactionCollection.getName(): String {
    return when (this) {
        ReactionCollection.Reactions -> "reactions"
        is ReactionCollection.ReactionList -> "reactions_$objectId"
    }
}

fun UserReactionRecordCollection.getName(): String {
    return when (this) {
        is UserReactionRecordCollection.UserReactionRecords -> "user_reaction_records_$uid"
    }
}

fun TopicCollection.getName(): String {
    return when (this) {
        is TopicCollection.SearchTopic -> "topics_${word}_$parentId"
        is TopicCollection.TopicList -> "topics_$objectId"
        TopicCollection.Topics -> "topics"
        TopicCollection.Recommend -> "topics_recommend"
        TopicCollection.AllTopics -> "all_topics"
        is TopicCollection.TopicComments -> "topic_comments_$objectId"
        is TopicCollection.UserComments -> "user_comments"
    }
}

fun CommunityCollection.getName(): String {
    return when (this) {
        CommunityCollection.Communities -> "communities"
        is CommunityCollection.SearchCommunity -> "communities_${word}_${target}_${joinStatusSearch}_$hasPointer"
        CommunityCollection.AllCommunities -> "all_communities"
    }
}

fun TitleCollection.getName(): String {
    return when (this) {
        is TitleCollection.SearchTitle -> "titles_${uid}_${searchType}_${status}_${type}_$scopeId"
        TitleCollection.Titles -> "title"
        TitleCollection.AllTitles -> "all_titles"
    }
}

fun UserLogCollection.getName(): String {
    return when (this) {
        is UserLogCollection.UserLogs -> "user_logs_$uid"
    }
}

fun UploadRecordCollection.getName(): String {
    return when (this) {
        is UploadRecordCollection.UserUploadRecords -> "upload_records_$uid"
    }
}

@Serializable
data class RemoteKeys(val collectionName: String, val key: String?)

interface ModelStorage {
    val user: UserInfoStorage
    val community: CommunityInfoStorage
    val topic: TopicInfoStorage
    val title: TitleInfoStorage
    val room: RoomInfoStorage
    val member: MemberInfoStorage
    val remoteKey: RemoteKeyStorage
    val reaction: ReactionInfoStorage
    val childAccount: ChildAccountStorage
    val fileInfo: FileInfoStorage
    val download: DownloadInfoStorage
    val upload: UploadInfoStorage
    val overview: OverviewStorage
    val userOverview: UserOverviewStorage
    val favorite: UserFavoriteStorage
    val subscription: UserSubscriptionStorage
    val userReactionRecord: UserReactionRecordStorage
    val userLog: UserLogInfoStorage
    val uploadRecord: UploadRecordInfoStorage
}

interface UserInfoStorage : CollectionItemStorageByIdAndKey<UserCollection, UserInfo>

interface CommunityInfoStorage :
    CollectionItemStorageByIdAndKeyWithGet<CommunityCollection, CommunityInfo>

interface TopicInfoStorage : CollectionItemStorageByIdAndKeyWithGet<TopicCollection, TopicInfo>

interface TitleInfoStorage : CollectionItemStorageById<TitleCollection, TitleInfo>

interface RoomInfoStorage : CollectionItemStorageByIdAndKey<RoomCollection, RoomInfo>

interface ReactionInfoStorage : CollectionListStorage<ReactionCollection, ReactionInfo>

sealed interface MemberCollection {
    data class CommunityMembers(val objectId: PrimaryKey, val word: String? = null) : MemberCollection
    data class RoomMembers(val objectId: PrimaryKey, val word: String? = null) : MemberCollection
}

fun MemberCollection.getName(): String {
    return when (this) {
        is MemberCollection.CommunityMembers -> if (word.isNullOrBlank()) {
            "community_members_$objectId"
        } else {
            "community_members_${objectId}_$word"
        }
        is MemberCollection.RoomMembers -> if (word.isNullOrBlank()) {
            "room_members_$objectId"
        } else {
            "room_members_${objectId}_$word"
        }
    }
}

interface MemberInfoStorage : CollectionListStorage<MemberCollection, MemberInfo>

interface ChildAccountStorage : GlobalListStorage<ChildAccountInfo> {
    companion object {
        const val COLLECTION_NAME = "child-account"
    }
}

interface FileInfoStorage : CollectionItemStorageById<FileCollection, FileInfo>

interface DownloadInfoStorage : GlobalItemStorageById<DownloadInfo> {
    companion object {
        const val COLLECTION_NAME = "download"
    }
}

interface UploadInfoStorage : CollectionItemStorageByKey<UploadCollection, UploadInfo>

interface OverviewStorage : SingletonItemStorage<PanelOverview> {
    companion object {
        const val COLLECTION_NAME = "panel-overview"
    }
}

interface UserOverviewStorage : SingletonItemStorage<UserOverview> {
    companion object {
        const val COLLECTION_NAME = "user-overview"
    }
}

interface UserFavoriteStorage : GlobalListStorageWithKey<UserFavoriteInfo, String> {
    companion object {
        const val COLLECTION_NAME = "user-favorite"
    }
}

interface UserSubscriptionStorage : GlobalListStorageWithKey<UserSubscriptionInfo, String> {
    companion object {
        const val COLLECTION_NAME = "user-subscription"
    }
}

interface UserReactionRecordStorage : CollectionListStorage<UserReactionRecordCollection, ReactionRecordInfo>

interface UserLogInfoStorage : CollectionListStorage<UserLogCollection, UserLogInfo>

interface UploadRecordInfoStorage : CollectionListStorage<UploadRecordCollection, UploadRecordInfo>

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

suspend fun TopicInfoStorage.update(
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
        return when (val result = rawSource.load(params)) {
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
