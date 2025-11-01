package com.storyteller_f.a.backend.core

import com.storyteller_f.a.backend.core.types.AssetTransaction
import com.storyteller_f.a.backend.core.types.ChildAccount
import com.storyteller_f.a.backend.core.types.Community
import com.storyteller_f.a.backend.core.types.FileRecord
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
import com.storyteller_f.a.backend.core.types.ReactionRecord
import com.storyteller_f.a.backend.core.types.Room
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
import com.storyteller_f.shared.model.UserOverview
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
import kotlinx.datetime.LocalDateTime

data class PaginationResult<T>(val list: List<T>, val total: Long)

data class ContainerInfo(
    val member: Member?,
    val userTopicRead: UserTopicRead?,
    val memberCount: Long?,
    val latestTopicId: PrimaryKey?,
)

sealed interface JoinSearch {
    data class Joined(val uid: PrimaryKey) : JoinSearch
    data class NotJoined(val uid: PrimaryKey) : JoinSearch
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

    suspend fun init()
    suspend fun clean()
    suspend fun migration()

    fun isDup(throwable: Throwable): Boolean
}

interface UserDatabase {
    suspend fun getUserAid(id: PrimaryKey): Result<String?>
    suspend fun getRawUser(objectFetch: ObjectFetch): Result<RawUser?>
    suspend fun getRawUserAndPublicKeyByAddress(ad: String): Result<Pair<RawUser, String>?>
    suspend fun createUser(user: User): Result<User>
    suspend fun isUserNotExistsByPublicKey(pk: String): Result<Boolean>
    suspend fun updateUserInfo(id: PrimaryKey, newUser: UpdateUserBody): Result<Boolean>
    suspend fun getUserAuthDataById(id: PrimaryKey): Result<Pair<String, Long>?>
    suspend fun getUserAuthDataByAid(aid: String): Result<Pair<String, Long>?>
    suspend fun getUserAuthDataByAddress(address: String): Result<Pair<String, Long>?>
    suspend fun getRawUsers(objectListFetch: ObjectListFetch): Result<List<RawUser>>
    suspend fun getUserAcgByIds(objectListFetch: ObjectListFetch): Result<List<Pair<Long, Long>>>
    suspend fun addReadLog(userTopicRead: UserTopicRead): Result<Unit>
    suspend fun insertUserLog(log: UserLog): Result<Unit>
    suspend fun addDevice(uid: PrimaryKey, endpointUrl: String): Result<Unit>
    suspend fun removeDevice(uid: PrimaryKey, endpointUrl: String): Result<Int>
    suspend fun getUserDevices(uid: List<PrimaryKey>): Result<List<UserDevice>>
    suspend fun addAcgForUser(
        record: TaskRecord,
        assetTransactions: List<AssetTransaction>
    ): Result<Unit>

    suspend fun getLatestTaskRecord(type: TaskRecordType): Result<TaskRecord?>
    suspend fun getRawChildAccountPaginationListByHost(
        hostId: PrimaryKey,
        fetch: PrimaryKeyFetch,
    ): Result<PaginationResult<RawChildAccount>>

    suspend fun getRawChildAccount(uid: PrimaryKey): Result<ChildAccount?>

    suspend fun createChildAccount(
        hostId: PrimaryKey,
        privateKey: String,
        user: User
    ): Result<Unit>

    suspend fun getAllUsers(primaryKeyFetch: PrimaryKeyFetch): Result<PaginationResult<RawUser>>
    suspend fun getUserCount(): Result<Long>
    suspend fun getUserFavorites(
        uid: PrimaryKey,
        fetch: PrimaryKeyFetch
    ): Result<PaginationResult<UserFavorite>>

    suspend fun addFavorite(userFavorite: UserFavorite): Result<UserFavorite>
    suspend fun removeFavorite(id: PrimaryKey): Result<Unit>
    suspend fun getFavorite(id: PrimaryKey): Result<UserFavorite?>
    suspend fun getFavorite(uid: PrimaryKey, objectId: PrimaryKey): Result<UserFavorite?>

    suspend fun getUserSubscriptions(
        uid: PrimaryKey,
        fetch: PrimaryKeyFetch
    ): Result<PaginationResult<UserSubscription>>

    suspend fun addSubscription(userSubscription: UserSubscription): Result<UserSubscription>
    suspend fun removeSubscription(id: PrimaryKey): Result<Unit>
    suspend fun getSubscription(id: PrimaryKey): Result<UserSubscription?>
    suspend fun getSubscription(uid: PrimaryKey, objectId: PrimaryKey): Result<UserSubscription?>
    suspend fun getUserOverview(uid: PrimaryKey): Result<UserOverview>
}

interface TopicDatabase {
    suspend fun getTopicRootTuple(parentId: PrimaryKey): Result<ObjectTuple?>
    suspend fun getRawTopic(fetch: ObjectFetch, uid: PrimaryKey?): Result<RawTopic?>
    suspend fun getRawTopicListByIds(
        uid: PrimaryKey?,
        ids: List<PrimaryKey>
    ): Result<List<RawTopic>>

    suspend fun getRawTopicByParentId(
        uid: PrimaryKey?,
        primaryKeyFetch: PrimaryKeyFetch,
        parentId: PrimaryKey,
        pinType: TopicPinSearch?
    ): Result<PaginationResult<RawTopic>>

    suspend fun getLatestRawTopic(uid: PrimaryKey?, parentId: PrimaryKey): Result<List<RawTopic>>

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun saveEncryptedTopic(topic: Topic, content: TopicContent.Encrypted): Result<Unit>
    suspend fun savePlainTopic(topic: Topic, content: TopicContent.Plain): Result<Unit>
    suspend fun updateTopicStatus(topicId: PrimaryKey, newValue: Boolean): Result<Boolean>
    suspend fun getTopicList(primaryKeyFetch: PrimaryKeyFetch): Result<List<Topic>>
    suspend fun getTopicCommentCount(
        topicIdList: List<PrimaryKey>,
    ): Result<List<Pair<Long, Long>>>

    suspend fun isUserCommented(uid: PrimaryKey, topicId: List<PrimaryKey>): Result<List<Long>>

    suspend fun processByteArrayToTopicContent(
        topics: List<Topic>,
        uid: PrimaryKey?,
    ): Result<Map<PrimaryKey, TopicContent>>

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun getEncryptedTopicContents(
        data: List<Topic>,
        uid: PrimaryKey,
    ): Result<List<TopicContent.Encrypted>>

    suspend fun statsReactionRecord(
        objectId: PrimaryKey,
        emoji: String,
        objectType: ObjectType
    ): Result<Unit>

    suspend fun getReactionInfoPaginationResult(
        objectId: List<PrimaryKey>,
        uid: PrimaryKey?,
        reactionFetch: ReactionFetch,
    ): Result<PaginationResult<ReactionInfo>>

    suspend fun hasReactedEmoji(
        objectIdList: List<PrimaryKey>,
        uid: PrimaryKey
    ): Result<List<Pair<Long, String>>>

    suspend fun getReactionInfo(
        uid: PrimaryKey,
        objectId: PrimaryKey,
        emojiText: String
    ): Result<ReactionInfo?>

    suspend fun hasReactedForEmoji(
        objectId: PrimaryKey,
        uid: PrimaryKey,
        emoji: String
    ): Result<Boolean>

    suspend fun deleteReaction(
        uid: PrimaryKey,
        emoji: String,
        objectId: PrimaryKey
    ): Result<Boolean>

    suspend fun getReactionRecordInfo(
        uid: PrimaryKey,
        emoji: String,
        objectId: PrimaryKey
    ): Result<ReactionRecordInfo?>

    suspend fun deleteReaction(reactionId: PrimaryKey): Result<Boolean>
    suspend fun insertReaction(reactionRecord: ReactionRecord): Result<Unit>
    suspend fun getReactionCount(objectIdList: List<PrimaryKey>): Result<List<Pair<Long, Long>>>
    suspend fun getReactionCountForEmoji(
        objectId: List<PrimaryKey>,
        emoji: String
    ): Result<List<Triple<Long, Long, PrimaryKey?>>>

    suspend fun createTitle(title: Title, topic: Topic): Result<Unit>
    suspend fun getTopicCount(): Result<Long>
}

interface TitleDatabase {
    suspend fun getTitlePaginationResult(
        primaryKeyFetch: PrimaryKeyFetch,
        uid: PrimaryKey,
        searchType: TitleSearchType,
        type: TitleType? = null,
        scopeId: PrimaryKey? = null,
    ): Result<PaginationResult<RawTitle>>
}

interface CommunityDatabase {
    suspend fun getRawCommunity(
        objectFetch: ObjectFetch,
        fillJoinInfo: Boolean? = null,
        uid: PrimaryKey? = null
    ): Result<RawCommunity?>

    suspend fun getJoinedCommunityIds(uid: PrimaryKey): Result<List<Long>>
    suspend fun getCommunityPaginationResult(
        word: String?,
        hasPosterSearch: PosterSearch?,
        primaryKeyFetch: PrimaryKeyFetch,
        joinSearch: JoinSearch,
    ): Result<PaginationResult<RawCommunity>?>

    suspend fun createCommunity(community: Community, memberId: PrimaryKey): Result<Unit>
    suspend fun getCommunityJoinedTimeByIds(
        uid: PrimaryKey,
        communityIds: List<PrimaryKey>,
    ): Result<List<Pair<Long, LocalDateTime?>>>

    suspend fun getRawCommunities(objectListFetch: ObjectListFetch): Result<List<RawCommunity>>
    suspend fun updateCommunity(id: PrimaryKey, body: UpdateCommunityBody): Result<Boolean>
    suspend fun getCommunityCount(): Result<Long>
}

interface RoomDatabase {
    suspend fun checkRoomIsPrivate(roomId: PrimaryKey): Result<Boolean?>
    suspend fun getRoomPaginationResult(
        uid: PrimaryKey?,
        word: String?,
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
    suspend fun getRawRooms(objectListFetch: ObjectListFetch): Result<List<RawRoom>>
    suspend fun getRoomList(objectListFetch: ObjectListFetch): Result<List<Room>>
    suspend fun updateRoom(id: PrimaryKey, body: UpdateRoomBody): Result<Boolean>
    suspend fun getPrivateRoomCount(): Result<Long>
    suspend fun getPublicRoomCount(): Result<Long>
}

interface FileDatabase {
    suspend fun getFileRecord(owner: PrimaryKey, name: String): Result<FileRecord?>
    suspend fun getFileRecordByIds(ids: List<PrimaryKey>): Result<List<FileRecord>>
    suspend fun getFileRecordListByOwner(owner: PrimaryKey): Result<List<FileRecord>>
    suspend fun getFileRecordByNames(names: List<String>): Result<List<FileRecord>>
    suspend fun insertFileRefs(
        objectId: PrimaryKey,
        objectType: ObjectType,
        mediaName: List<Pair<PrimaryKey, String>>,
    ): Result<Unit>

    suspend fun getFileRecordPaginationList(
        uid: PrimaryKey,
        primaryKeyFetch: PrimaryKeyFetch,
    ): Result<PaginationResult<FileRecord>>

    suspend fun insertFileRecord(
        fileRecordList: List<FileRecord>,
        ownerId: PrimaryKey,
        ownerType: ObjectType
    ): Result<Unit>

    suspend fun insertUploadRecord(record: UploadRecord): Result<Unit>
    suspend fun deleteUploadRecord(
        id: PrimaryKey,
        quotaInfo: QuotaInfo,
        length: Long
    ): Result<Unit>

    suspend fun getFileCount(): Result<Long>
    suspend fun getFileVolume(): Result<Long>
}

interface ContainerDatabase {
    suspend fun isMemberJoined(objectId: PrimaryKey, uid: PrimaryKey?): Result<Boolean>
    suspend fun joinContainer(
        id: PrimaryKey,
        uid: PrimaryKey,
        time: LocalDateTime,
        objectType: ObjectType,
        member: Member,
    ): Result<Unit>

    suspend fun exitContainer(containerId: PrimaryKey, id: PrimaryKey): Result<Unit>
    suspend fun getJoinedUserList(roomId: PrimaryKey): Result<List<Member>>
    suspend fun getUserJoinedTime(
        parentIds: List<PrimaryKey>,
        uid: PrimaryKey
    ): Result<List<Member>>

    suspend fun getMemberCount(parentIds: List<PrimaryKey>): Result<List<Pair<Long, Long>>>
    suspend fun getContainerInfo(
        parentIds: List<PrimaryKey>,
        uid: PrimaryKey?,
    ): Result<Map<PrimaryKey, ContainerInfo>>

    suspend fun getTopicReadList(
        parentIds: List<PrimaryKey>,
        uid: PrimaryKey
    ): Result<List<UserTopicRead>>

    suspend fun getMemberPaginationResult(
        objectId: PrimaryKey?,
        word: String?,
        fetch: PrimaryKeyFetch
    ): Result<PaginationResult<RawUser>>

    suspend fun getQuotaInfo(ownerId: PrimaryKey, quotaType: QuotaType): Result<Quota?>
    suspend fun insertQuota(quota: Quota): Result<Unit>

    suspend fun getLatestTopicInContainer(
        parentIds: List<PrimaryKey>,
        uid: PrimaryKey?
    ): Result<Map<PrimaryKey, PrimaryKey?>>
}

interface AdminDatabase {
    suspend fun batchAddUser(users: List<User>)
    suspend fun batchAddCommunities(communities: List<Community>, members: List<Member>)

    suspend fun batchAddRooms(roomList: List<Room>, membersList: List<Member>)

    suspend fun getAllMembers(distinct: List<String>): Result<List<Triple<String, Long, String>>>
    suspend fun batchAddEncryptTopics(
        tuples: List<InsertTopicTuple>,
        userMap: Map<String, User>,
        roomMap: Map<String, Room>,
        encryptedKeys: List<Triple<PrimaryKey, ByteArray, Long>>
    )

    suspend fun batchAddTopics(
        tuples: List<InsertTopicTuple>,
        userMap: Map<String, User>,
        objectType: ObjectType
    )
    suspend fun createTaskRecord(record: TaskRecord): Result<TaskRecord>
}

interface PanelAccountDatabase {
    suspend fun getPanelAccount(id: PrimaryKey): Result<PanelAccount?>
    suspend fun addPanelAccount(panelAccount: PanelAccount): Result<Unit>
    suspend fun getUserAuthDataById(id: PrimaryKey): Result<Pair<String, Long>?>
    suspend fun getUserAuthDataByAddress(address: String): Result<Pair<String, Long>?>
    suspend fun getRawUserAndPublicKeyByAddress(ad: String): Result<Pair<RawPanelAccount, String>?>
    suspend fun isUserNotExistsByPublicKey(pk: String): Result<Boolean>
}

const val PUBLIC_KEY_LENGTH = 512
const val ADDRESS_LENGTH = 100
const val USER_NICKNAME = 20
const val COMMUNITY_NAME_LENGTH = 20
const val AID_LENGTH = 20
const val ROOM_NAME_LENGTH = 20

// 最长60，如果超过60 会进行裁切然后在后面添加uuid，保存时预留一部分空间
const val MEDIA_NAME_LENGTH = 120
