package com.storyteller_f.a.backend.core

import com.storyteller_f.a.backend.core.types.AssetTransaction
import com.storyteller_f.a.backend.core.types.ChildAccount
import com.storyteller_f.a.backend.core.types.Community
import com.storyteller_f.a.backend.core.types.FileRecord
import com.storyteller_f.a.backend.core.types.FileRef
import com.storyteller_f.a.backend.core.types.Member
import com.storyteller_f.a.backend.core.types.PanelAccount
import com.storyteller_f.a.backend.core.types.Quota
import com.storyteller_f.a.backend.core.types.RawChildAccount
import com.storyteller_f.a.backend.core.types.RawCommunity
import com.storyteller_f.a.backend.core.types.RawPanelAccount
import com.storyteller_f.a.backend.core.types.RawRoom
import com.storyteller_f.a.backend.core.types.RawTitle
import com.storyteller_f.a.backend.core.types.RawTopic
import com.storyteller_f.a.backend.core.types.RawUser
import com.storyteller_f.a.backend.core.types.RawUserOverview
import com.storyteller_f.a.backend.core.types.ReactionRecord
import com.storyteller_f.a.backend.core.types.Room
import com.storyteller_f.a.backend.core.types.SubscriptionSentLog
import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.a.backend.core.types.Title
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.a.backend.core.types.UploadRecord
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.core.types.UserDevice
import com.storyteller_f.a.backend.core.types.UserFavorite
import com.storyteller_f.a.backend.core.types.UserLog
import com.storyteller_f.a.backend.core.types.UserSubscription
import com.storyteller_f.a.backend.core.types.UserTopicRead
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.NestedMemberInfo
import com.storyteller_f.shared.model.PosterSearch
import com.storyteller_f.shared.model.QuotaInfo
import com.storyteller_f.shared.model.QuotaType
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.ReactionRecordInfo
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicPinSearch
import com.storyteller_f.shared.model.UserPubKeyInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.PresetCommunity
import com.storyteller_f.shared.obj.PresetRoom
import com.storyteller_f.shared.obj.PresetTopic
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.associateByPair
import com.storyteller_f.shared.utils.firstOrNull
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import kotlinx.datetime.LocalDateTime
import kotlin.collections.emptyList

data class PaginationResult<T>(val list: List<T>, val total: Long)

suspend fun <T> Result<List<T>?>.paging(total: Long) = mapIfNotNull {
    PaginationResult(it, total)
}

fun <T> Result<List<T>>.pagingNotNull(total: Long) = map {
    PaginationResult(it, total)
}

suspend fun <T, R> Result<PaginationResult<T>>.mapPagingResultNotNull(
    block: suspend (List<T>) -> Result<List<R>>
) = mapResult { paging ->
    block(paging.list).map {
        PaginationResult(it, paging.total)
    }
}

suspend fun <T, R> Result<PaginationResult<T>>.mapPagingNotNull(
    block: suspend (List<T>) -> List<R>
) = map { paging ->
    PaginationResult(block(paging.list), paging.total)
}

suspend fun <T, R> Result<PaginationResult<T>?>.mapPagingIfNotNull(
    block: suspend (List<T>) -> Result<List<R>>
) = mapResultIfNotNull { paging ->
    block(paging.list).map {
        PaginationResult(it, paging.total)
    }
}

suspend fun <T, R> Result<PaginationResult<T>?>.mapPagingResultIfNotNullNullable(
    block: suspend (List<T>) -> Result<List<R>?>
) = mapResultIfNotNull { paging ->
    block(paging.list).mapIfNotNull {
        PaginationResult(it, paging.total)
    }
}

suspend fun <T, R> Result<PaginationResult<T>>.mapPagingResultNullable(
    block: suspend (List<T>) -> Result<List<R>?>
) = mapResult { paging ->
    block(paging.list).mapIfNotNull {
        PaginationResult(it, paging.total)
    }
}

suspend fun <T> paginationFromResults(
    listResult: Result<List<T>>,
    totalResult: Result<Long>
): Result<PaginationResult<T>> = listResult.mapResult { list ->
    totalResult.map { total -> PaginationResult(list, total) }
}

suspend fun <T> paginationNullableFromResults(
    listResult: Result<List<T>?>,
    totalResult: Result<Long>
): Result<PaginationResult<T>?> = listResult.mapResultIfNotNull { list ->
    totalResult.map { total -> PaginationResult(list, total) }
}

data class ContainerInfo(
    val member: Member?,
    val userTopicRead: UserTopicRead?,
    val memberCount: Long?,
    val latestTopicId: PrimaryKey?,
)

data class UserAuthData(val publicKey: String, val userId: PrimaryKey, val algoType: AlgoType)

sealed interface JoinSearch {
    data class Joined(val uid: PrimaryKey) : JoinSearch
    data class Unspecified(val uid: PrimaryKey?) : JoinSearch
}

class InsertTopicTuple(
    val topic: PresetTopic,
    val originalIndex: Int,
    val level: Int,
    val id: PrimaryKey,
    val content: ByteArray,
    val isEncrypted: Boolean,
    val rootId: PrimaryKey
)

class InsertCommunityTuple(
    val community: PresetCommunity,
    val icon: PrimaryKey?,
    val id: PrimaryKey,
    val font: PrimaryKey?,
    val createdTime: LocalDateTime
)

class InsertRoomTuple(
    val room: PresetRoom,
    val icon: PrimaryKey?,
    val id: PrimaryKey,
    val createdTime: LocalDateTime,
)

interface CombinedDatabase {
    val user: UserDatabase
    val topic: TopicDatabase
    val title: TitleDatabase
    val community: CommunityDatabase
    val room: RoomDatabase
    val file: FileDatabase
    val container: ContainerDatabase
    val admin: AdminDatabase
    val panelAccount: PanelAccountDatabase
    val reaction: ReactionDatabase
    val favorite: FavoriteDatabase
    val subscription: SubscriptionDatabase

    suspend fun init()
    suspend fun clean()
    suspend fun migration()

    fun isDup(throwable: Throwable): Boolean

    suspend fun getUserOverview(uid: PrimaryKey): Result<RawUserOverview> = runCatching {
        val subscriptionCount = subscription.getUserSubscriptionCount(uid).getOrThrow()
        val favoriteCount = favorite.getUserFavoriteCount().getOrThrow()
        val childAccountCount = user.getChildAccountCount(uid).getOrThrow()
        val reactionRecordCount = reaction.getUserReactionRecordCount(uid).getOrThrow()
        val commentCount = topic.getUserCommentCount(uid).getOrThrow()
        val rawUser = user.getRawUser(ObjectFetch.IdFetch(uid)).getOrThrow() ?: error("user not found")
        RawUserOverview(
            subscriptionCount,
            favoriteCount,
            rawUser.user.acgAmount,
            childAccountCount,
            reactionRecordCount,
            commentCount,
            rawUser
        )
    }

    suspend fun processTopicToRawTopic(
        uid: PrimaryKey?,
        topics: List<Topic>
    ): Result<List<RawTopic>> = runCatching {
        val topicIds = topics.map { it.id }
        if (topicIds.isEmpty()) return@runCatching emptyList()

        val commentedSet = if (uid != null) {
            topic.isUserCommented(uid, topicIds).map { it.toSet() }.getOrThrow()
        } else {
            emptySet()
        }

        val commentCountMap = topic.getTopicCommentCount(topicIds).map { it.associateByPair() }.getOrThrow()
        val reactionCountMap = reaction.getReactionCount(topicIds).map { it.associateByPair() }.getOrThrow()

        val lastReadMap = if (uid != null) {
            container.getTopicReadList(topicIds, uid).map {
                it.associateBy { userTopicRead -> userTopicRead.objectId }
            }.getOrThrow()
        } else {
            emptyMap()
        }

        val contentMap = topic.getTopicContentFromByteArray(topics, uid).getOrThrow()

        val favoriteMap = if (uid != null) {
            favorite.getHasFavorite(ObjectListFetch.IdListFetch(topicIds), uid)
                .getOrThrow().associateBy { it.objectId }
        } else {
            emptyMap()
        }

        val subscriptionMap = if (uid != null) {
            subscription.getHasSubscription(ObjectListFetch.IdListFetch(topicIds), uid)
                .getOrThrow().associateBy { it.objectId }
        } else {
            emptyMap()
        }

        topics.map { topic ->
            val id = topic.id
            RawTopic(
                topic,
                contentMap[id] ?: TopicContent.Nil,
                commentCountMap[id] ?: 0,
                commentedSet.contains(id),
                reactionCountMap[id] ?: 0,
                lastReadMap[id]?.topicId,
                favoriteId = favoriteMap[id]?.id,
                subscriptionId = subscriptionMap[id]?.id,
            )
        }
    }

    suspend fun getRawTopic(fetch: ObjectFetch, uid: PrimaryKey?) =
        topic.getTopic(fetch).mapResultIfNotNull { topic ->
            processTopicToRawTopic(uid, listOf(topic))
        }.firstOrNull()

    suspend fun getAllRawTopics(primaryKeyFetch: PrimaryKeyFetch): Result<PaginationResult<RawTopic>> {
        return topic.getAllTopicPagination(primaryKeyFetch).mapResult {
            processTopicToRawTopic(null, it.list).pagingNotNull(it.total)
        }
    }

    suspend fun getRawTopicListByIds(
        uid: PrimaryKey?,
        ids: List<PrimaryKey>
    ) = topic.getTopicListByIds(ids).mapResult {
        processTopicToRawTopic(uid, it)
    }

    suspend fun getRawTopicByParentId(
        uid: PrimaryKey?,
        primaryKeyFetch: PrimaryKeyFetch,
        parentId: PrimaryKey,
        pinType: TopicPinSearch?
    ) = topic.getTopicByParentId(uid, primaryKeyFetch, parentId, pinType).mapResult {
        processTopicToRawTopic(uid, it.list).pagingNotNull(it.total)
    }

    suspend fun getLatestRawTopic(
        uid: PrimaryKey?,
        parentId: PrimaryKey
    ) = topic.getLatestTopic(parentId).mapResult {
        processTopicToRawTopic(uid, it)
    }
}

interface UserDatabase {
    suspend fun getUserAid(id: PrimaryKey): Result<String?>
    suspend fun getRawUser(objectFetch: ObjectFetch): Result<RawUser?>
    suspend fun getRawUserAndPublicKeyByAddress(ad: String): Result<Pair<RawUser, String>?>
    suspend fun createUser(user: User): Result<User>
    suspend fun isUserNotExistsByPublicKey(pk: String): Result<Boolean>

    /**
     * nickname 为null 或者空字符串时不更新，avatar 为null 时不更新,aid 为null 或者空字符串时不更新
     */
    suspend fun updateUserInfo(id: PrimaryKey, newUser: UpdateUserBody): Result<Boolean>
    suspend fun getUserAuthDataById(id: PrimaryKey): Result<UserAuthData?>
    suspend fun getUserAuthDataByAid(aid: String): Result<UserAuthData?>
    suspend fun getUserAuthDataByAddress(address: String): Result<UserAuthData?>
    suspend fun getRawUsers(objectListFetch: ObjectListFetch): Result<List<RawUser>>
    suspend fun getUserAcgByIds(objectListFetch: ObjectListFetch): Result<List<Pair<Long, Long>>>
    suspend fun addReadLog(userTopicRead: UserTopicRead): Result<Unit>
    suspend fun insertUserLog(log: UserLog): Result<Unit>
    suspend fun addDevice(uid: PrimaryKey, endpointUrl: String): Result<Unit>
    suspend fun removeDevice(uid: PrimaryKey, endpointUrl: String): Result<Int>
    suspend fun getUserDevices(uid: List<PrimaryKey>): Result<List<UserDevice>>
    suspend fun addAcgForUser(record: TaskRecord, assetTransactions: List<AssetTransaction>): Result<Unit>

    suspend fun getLatestTaskRecord(type: TaskRecordType): Result<TaskRecord?>
    suspend fun getRawChildAccountPaginationListByHost(
        hostId: PrimaryKey,
        fetch: PrimaryKeyFetch,
    ): Result<PaginationResult<RawChildAccount>>

    suspend fun getRawChildAccount(uid: PrimaryKey): Result<ChildAccount?>

    suspend fun createChildAccount(hostId: PrimaryKey, privateKey: String, user: User): Result<Unit>

    suspend fun getAllUsers(primaryKeyFetch: PrimaryKeyFetch): Result<PaginationResult<RawUser>>
    suspend fun getUserCount(): Result<Long>

    suspend fun getUserLogs(uid: PrimaryKey, fetch: PrimaryKeyFetch): Result<PaginationResult<UserLog>>

    suspend fun getChildAccountCount(hostId: PrimaryKey): Result<Long>
}

interface TopicDatabase {
    suspend fun getTopicRootTuple(parentId: PrimaryKey): Result<ObjectTuple?>

    suspend fun saveEncryptedTopic(topic: Topic, content: TopicContent.Encrypted): Result<Unit>
    suspend fun savePlainTopic(topic: Topic, content: TopicContent.Plain, fileRefs: List<FileRef>): Result<Unit>

    suspend fun updateTopicStatus(topicId: PrimaryKey, newValue: Boolean): Result<Boolean>
    suspend fun getTopicList(primaryKeyFetch: PrimaryKeyFetch): Result<List<Topic>>
    suspend fun getTopicCommentCount(topicIdList: List<PrimaryKey>,): Result<List<Pair<Long, Long>>>

    suspend fun isUserCommented(uid: PrimaryKey, topicId: List<PrimaryKey>): Result<List<Long>>

    suspend fun getUserCommentedTopicsPaginationResult(
        uid: PrimaryKey,
        primaryKeyFetch: PrimaryKeyFetch
    ): Result<PaginationResult<Topic>>

    suspend fun getUserCommentCount(uid: PrimaryKey): Result<Long>

    suspend fun getTopicContentFromByteArray(
        topics: List<Topic>,
        uid: PrimaryKey?,
    ): Result<Map<PrimaryKey, TopicContent>>

    suspend fun createTitle(title: Title, topic: Topic): Result<Unit>
    suspend fun getTopicCount(): Result<Long>
    suspend fun getTopic(fetch: ObjectFetch): Result<Topic?>
    suspend fun getAllTopicPagination(primaryKeyFetch: PrimaryKeyFetch): Result<PaginationResult<Topic>>
    suspend fun getTopicListByIds(ids: List<PrimaryKey>): Result<List<Topic>>
    suspend fun getTopicByParentId(
        uid: PrimaryKey?,
        primaryKeyFetch: PrimaryKeyFetch,
        parentId: PrimaryKey,
        pinType: TopicPinSearch?
    ): Result<PaginationResult<Topic>>

    suspend fun getLatestTopic(parentId: PrimaryKey): Result<List<Topic>>
}

interface FavoriteDatabase {
    suspend fun getUserFavorites(uid: PrimaryKey, fetch: PrimaryKeyFetch): Result<PaginationResult<UserFavorite>>

    suspend fun addFavorite(userFavorite: UserFavorite): Result<UserFavorite>
    suspend fun removeFavorite(id: PrimaryKey): Result<Unit>
    suspend fun getFavorite(id: PrimaryKey): Result<UserFavorite?>
    suspend fun getFavorite(uid: PrimaryKey, objectId: PrimaryKey): Result<UserFavorite?>
    suspend fun getHasFavorite(idList: ObjectListFetch.IdListFetch, uid: PrimaryKey): Result<List<UserFavorite>>

    suspend fun getUserFavoriteCount(): Result<Long>
}

interface SubscriptionDatabase {
    suspend fun getUserSubscriptions(
        uid: PrimaryKey,
        fetch: PrimaryKeyFetch
    ): Result<PaginationResult<UserSubscription>>

    suspend fun addSubscription(userSubscription: UserSubscription): Result<UserSubscription>
    suspend fun removeSubscription(id: PrimaryKey): Result<Unit>
    suspend fun getSubscription(id: PrimaryKey): Result<UserSubscription?>
    suspend fun getSubscription(uid: PrimaryKey, objectId: PrimaryKey): Result<UserSubscription?>
    suspend fun getSubscriptionsByObjectId(
        objectId: PrimaryKey,
        primaryKeyFetch: PrimaryKeyFetch
    ): Result<List<UserSubscription>>

    suspend fun insertSubscriptionSentLog(log: SubscriptionSentLog): Result<SubscriptionSentLog>
    suspend fun getLatestSubscriptionSentLog(objectId: PrimaryKey): Result<SubscriptionSentLog?>
    suspend fun getHasSubscription(idList: ObjectListFetch.IdListFetch, uid: PrimaryKey): Result<List<UserSubscription>>

    suspend fun getUserSubscriptionCount(uid: PrimaryKey): Result<Long>
}

interface ReactionDatabase {
    suspend fun statsReactionRecord(objectId: PrimaryKey, emoji: String, objectType: ObjectType): Result<Unit>

    suspend fun getReactionInfoPaginationResult(
        objectId: List<PrimaryKey>,
        uid: PrimaryKey?,
        reactionFetch: ReactionFetch,
    ): Result<PaginationResult<ReactionInfo>>

    suspend fun hasReactedEmoji(objectIdList: List<PrimaryKey>, uid: PrimaryKey): Result<List<Pair<Long, String>>>

    suspend fun getReactionInfo(uid: PrimaryKey, objectId: PrimaryKey, emojiText: String): Result<ReactionInfo?>

    suspend fun hasReactedForEmoji(objectId: PrimaryKey, uid: PrimaryKey, emoji: String): Result<Boolean>

    suspend fun deleteReaction(uid: PrimaryKey, emoji: String, objectId: PrimaryKey): Result<Boolean>

    suspend fun getReactionRecordInfo(uid: PrimaryKey, emoji: String, objectId: PrimaryKey): Result<ReactionRecordInfo?>

    suspend fun deleteReaction(reactionId: PrimaryKey): Result<Boolean>
    suspend fun insertReaction(reactionRecord: ReactionRecord): Result<Unit>
    suspend fun getReactionCount(objectIdList: List<PrimaryKey>): Result<List<Pair<Long, Long>>>
    suspend fun getReactionCountForEmoji(
        objectId: List<PrimaryKey>,
        emoji: String
    ): Result<List<Triple<Long, Long, PrimaryKey?>>>

    suspend fun getUserReactionRecordsPaginationResult(
        uid: PrimaryKey,
        primaryKeyFetch: PrimaryKeyFetch
    ): Result<PaginationResult<ReactionRecord>>

    suspend fun getUserReactionRecordCount(uid: PrimaryKey): Result<Long>
}

interface TitleDatabase {
    suspend fun getTitlePaginationResult(
        primaryKeyFetch: PrimaryKeyFetch,
        uid: PrimaryKey,
        searchType: TitleSearchType,
        type: TitleType? = null,
        scopeId: PrimaryKey? = null,
    ): Result<PaginationResult<RawTitle>>

    suspend fun getAllRawTitles(primaryKeyFetch: PrimaryKeyFetch): Result<PaginationResult<RawTitle>>
    suspend fun getTitleCount(): Result<Long>
    suspend fun getTitle(id: PrimaryKey): Result<RawTitle?>
}

interface CommunityDatabase {
    suspend fun getRawCommunity(
        objectFetch: ObjectFetch,
        fillJoinInfo: Boolean? = null,
        uid: PrimaryKey? = null
    ): Result<RawCommunity?>

    suspend fun getJoinedCommunityIds(uid: PrimaryKey): Result<List<Long>>
    suspend fun getCommunityPaginationResult(
        hasPosterSearch: PosterSearch?,
        primaryKeyFetch: PrimaryKeyFetch,
        joinSearch: JoinSearch,
    ): Result<PaginationResult<RawCommunity>?>

    suspend fun createCommunity(community: Community, memberId: PrimaryKey): Result<Pair<Community, Member>>

    suspend fun getRawCommunities(objectListFetch: ObjectListFetch): Result<List<RawCommunity>>

    /**
     * name 为null 或者空字符串时不更新，icon，poster 为null时不更新
     */
    suspend fun updateCommunity(id: PrimaryKey, body: UpdateCommunityBody): Result<Boolean>
    suspend fun getCommunityCount(): Result<Long>
}

interface RoomDatabase {
    suspend fun checkRoomIsPrivate(roomId: PrimaryKey): Result<Boolean?>
    suspend fun getRoomPaginationResult(
        uid: PrimaryKey?,
        community: PrimaryKey?,
        primaryKeyFetch: PrimaryKeyFetch,
        joinSearch: JoinSearch,
    ): Result<PaginationResult<RawRoom>>

    suspend fun getRoomCommunityId(parentId: PrimaryKey): Result<PrimaryKey?>
    suspend fun getRoomPubKeyPaginationResult(
        roomId: PrimaryKey,
        primaryKeyFetch: PrimaryKeyFetch,
    ): Result<PaginationResult<UserPubKeyInfo>>

    suspend fun getRawRoom(
        objectFetch: ObjectFetch,
        fillJoinInfo: Boolean? = null,
        uid: PrimaryKey? = null,
    ): Result<RawRoom?>

    suspend fun createRoom(room: Room, members: List<Member>): Result<Room>
    suspend fun getRawRooms(objectListFetch: ObjectListFetch, uid: PrimaryKey?): Result<List<RawRoom>>

    suspend fun getRoomList(objectListFetch: ObjectListFetch): Result<List<Room>>
    suspend fun updateRoom(id: PrimaryKey, body: UpdateRoomBody): Result<Boolean>
    suspend fun getPrivateRoomCount(): Result<Long>
    suspend fun getPublicRoomCount(): Result<Long>
    suspend fun getPrivateRoomPaginationResult(
        primaryKeyFetch: PrimaryKeyFetch,
        word: String? = null,
    ): Result<PaginationResult<RawRoom>>
}

interface FileDatabase {
    suspend fun getFileRecord(owner: PrimaryKey, name: String): Result<FileRecord?>
    suspend fun getFileRecordByIds(ids: List<PrimaryKey>): Result<List<FileRecord>>
    suspend fun getFileRecordListByOwner(owner: PrimaryKey): Result<List<FileRecord>>
    suspend fun getFileRecordByNames(names: List<String>): Result<List<FileRecord>>
    suspend fun getUploadRecord(id: PrimaryKey): Result<UploadRecord?>
    suspend fun insertFileRefs(fileRefs: List<FileRef>): Result<Unit>

    suspend fun getFileRecordPaginationList(
        uid: PrimaryKey,
        primaryKeyFetch: PrimaryKeyFetch,
    ): Result<PaginationResult<FileRecord>>

    suspend fun getAllFileRecordPaginationList(primaryKeyFetch: PrimaryKeyFetch): Result<PaginationResult<FileRecord>>

    suspend fun insertFileRecord(
        fileRecordList: List<FileRecord>,
        ownerId: PrimaryKey,
        ownerType: ObjectType
    ): Result<Unit>

    suspend fun insertUploadRecord(record: UploadRecord): Result<UploadRecord>

    /**
     * 只有在插入文件记录成功后才更新上传记录状态，避免文件上传了但是配额没有更新的情况
     */
    suspend fun updateUploadRecordStatus(
        quotaInfo: QuotaInfo,
        record: UploadRecord,
        fileRecordList: List<FileRecord>,
    ): Result<List<FileRecord>>

    suspend fun getFileCount(): Result<Long>
    suspend fun getFileVolume(): Result<Long>

    suspend fun getUploadRecordPaginationList(
        uid: PrimaryKey,
        primaryKeyFetch: PrimaryKeyFetch,
    ): Result<PaginationResult<UploadRecord>>

    suspend fun getFileRefsByFileId(
        fileId: PrimaryKey,
        primaryKeyFetch: PrimaryKeyFetch
    ): Result<PaginationResult<FileRef>>
}

interface ContainerDatabase {
    suspend fun isMemberJoined(objectId: PrimaryKey, uid: PrimaryKey?): Result<Boolean>
    suspend fun addMember(member: Member): Result<Member>

    suspend fun updateMemberStatus(member: Member): Result<Member>

    suspend fun deleteMember(containerId: PrimaryKey, id: PrimaryKey): Result<Unit>
    suspend fun getJoinedUserList(roomId: PrimaryKey): Result<List<Member>>
    suspend fun getUserJoinedTime(parentIds: List<PrimaryKey>, uid: PrimaryKey): Result<List<Member>>

    suspend fun getMemberCount(parentIds: List<PrimaryKey>): Result<List<Pair<Long, Long>>>
    suspend fun getContainerInfo(parentIds: List<PrimaryKey>, uid: PrimaryKey?,): Result<Map<PrimaryKey, ContainerInfo>>

    suspend fun getTopicReadList(parentIds: List<PrimaryKey>, uid: PrimaryKey): Result<List<UserTopicRead>>

    suspend fun getMemberPaginationResult(
        objectId: PrimaryKey?,
        word: String?,
        fetch: PrimaryKeyFetch
    ): Result<PaginationResult<RawUser>>

    suspend fun getMemberWithUserPaginationResult(
        objectId: PrimaryKey,
        fetch: PrimaryKeyFetch
    ): Result<PaginationResult<Pair<Member, RawUser>>>

    suspend fun getMemberWithUserByUids(
        objectId: PrimaryKey,
        uidList: List<PrimaryKey>
    ): Result<List<Pair<Member, RawUser>>>

    suspend fun getQuotaInfo(ownerId: PrimaryKey, quotaType: QuotaType): Result<Quota?>
    suspend fun insertQuota(quota: Quota): Result<Unit>

    suspend fun getLatestTopicInContainer(
        parentIds: List<PrimaryKey>,
        uid: PrimaryKey?
    ): Result<Map<PrimaryKey, PrimaryKey?>>

    suspend fun getMember(containerId: PrimaryKey, id: PrimaryKey): Result<Member?>
    suspend fun getMemberByIds(
        uid: PrimaryKey,
        objectIds: List<PrimaryKey>,
    ): Result<List<Pair<Long, NestedMemberInfo?>>>
}

interface AdminDatabase {
    suspend fun batchAddUser(users: List<User>)
    suspend fun batchAddCommunities(communities: List<Community>, members: List<Member>)

    suspend fun batchAddRooms(roomList: List<Room>, membersList: List<Member>)

    suspend fun getAllMembers(distinct: List<String>): Result<List<Triple<String, Long, String>>>
    suspend fun batchAddEncryptTopicKeys(encryptedKeys: List<Triple<PrimaryKey, ByteArray, Long>>): Result<Unit>

    suspend fun batchAddTopics(
        tuples: List<InsertTopicTuple>,
        userMap: Map<String, User>,
        objectType: ObjectType
    ): Result<Unit>

    suspend fun createTaskRecord(record: TaskRecord): Result<TaskRecord>
    suspend fun batchAddSubscription(list: List<UserSubscription>): Result<Unit>
}

interface PanelAccountDatabase {
    suspend fun getPanelAccount(id: PrimaryKey): Result<PanelAccount?>
    suspend fun addPanelAccount(panelAccount: PanelAccount): Result<Unit>
    suspend fun getUserAuthDataById(id: PrimaryKey): Result<UserAuthData?>
    suspend fun getUserAuthDataByAddress(address: String): Result<UserAuthData?>
    suspend fun getRawUserAndPublicKeyByAddress(ad: String): Result<Pair<RawPanelAccount, String>?>
    suspend fun isUserNotExistsByPublicKey(pk: String): Result<Boolean>
}

const val PUBLIC_KEY_LENGTH = 4096
const val ADDRESS_LENGTH = 100
const val USER_NICKNAME = 20
const val COMMUNITY_NAME_LENGTH = 20
const val AID_LENGTH = 20
const val ROOM_NAME_LENGTH = 20

// 最长60，如果超过60 会进行裁切然后在后面添加uuid，保存时预留一部分空间
const val MEDIA_NAME_LENGTH = 120
