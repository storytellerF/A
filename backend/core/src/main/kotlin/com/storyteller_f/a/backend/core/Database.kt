package com.storyteller_f.a.backend.core

import com.storyteller_f.a.backend.core.types.AssetTransaction
import com.storyteller_f.a.backend.core.types.ChildAccount
import com.storyteller_f.a.backend.core.types.Community
import com.storyteller_f.a.backend.core.types.FileRecord
import com.storyteller_f.a.backend.core.types.MemberJoin
import com.storyteller_f.a.backend.core.types.Quota
import com.storyteller_f.a.backend.core.types.RawChildAccount
import com.storyteller_f.a.backend.core.types.RawCommunity
import com.storyteller_f.a.backend.core.types.RawRoom
import com.storyteller_f.a.backend.core.types.RawUser
import com.storyteller_f.a.backend.core.types.ReactionRecord
import com.storyteller_f.a.backend.core.types.Room
import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.a.backend.core.types.Title
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.a.backend.core.types.UploadRecord
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.core.types.UserDevice
import com.storyteller_f.a.backend.core.types.UserLog
import com.storyteller_f.a.backend.core.types.UserTopicRead
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.PresetCommunity
import com.storyteller_f.shared.obj.PresetTopic
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

data class PaginationResult<T>(val list: List<T>, val total: Long)

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
)

interface CombinedDatabase {
    val userDatabase: UserDatabase
    val topicDatabase: TopicDatabase
    val titleDatabase: TitleDatabase
    val communityDatabase: CommunityDatabase
    val roomData: RoomDatabase
    val fileDatabase: FileDatabase
    val containerDatabase: ContainerDatabase
    val cliDatabase: CliDatabase

    suspend fun init()
    suspend fun clean()
}

interface UserDatabase {
    suspend fun getUserAid(id: PrimaryKey): Result<String?>
    suspend fun getRawUser(objectFetch: ObjectFetch): Result<RawUser?>
    suspend fun getRawUserAndPublicKeyByAddress(ad: String): Result<Pair<RawUser, String>?>
    suspend fun createUser(user: User): Result<Unit>
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
        assetTransactions: List<AssetTransaction>,
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
}

interface TopicDatabase {
    suspend fun getTopicRootTuple(parentId: PrimaryKey): Result<ObjectTuple?>
    suspend fun getTopicInfo(fetch: ObjectFetch, uid: PrimaryKey?): Result<TopicInfo?>
    suspend fun getTopicInfoListByIds(
        uid: PrimaryKey?,
        ids: List<PrimaryKey>
    ): Result<List<TopicInfo>>

    suspend fun getSubTopicInfo(
        uid: PrimaryKey?,
        primaryKeyFetch: PrimaryKeyFetch,
        parentId: PrimaryKey,
        pinType: TopicPinSearch?
    ): Result<PaginationResult<TopicInfo>>

    suspend fun getLatestTopicInfo(uid: PrimaryKey?, parentId: PrimaryKey): Result<List<TopicInfo>>

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun saveEncryptedTopic(topic: Topic, content: TopicContent.Encrypted): Result<TopicInfo>
    suspend fun savePlainTopic(topic: Topic, content: TopicContent.Plain): Result<Unit>
    suspend fun updateTopicStatus(topicId: PrimaryKey, newValue: Boolean): Result<Boolean>
    suspend fun getTopicList(primaryKeyFetch: PrimaryKeyFetch): Result<List<Topic>>
    suspend fun getTopicCommentCount(
        topicIdList: List<PrimaryKey>,
    ): Result<List<Pair<Long, Long>>>

    suspend fun isUserCommented(uid: PrimaryKey, topicId: List<PrimaryKey>): Result<List<Long>>
    suspend fun processTopicToTopicInfo(
        uid: PrimaryKey?,
        topics: List<Topic>
    ): Result<List<TopicInfo>>

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
}

interface TitleDatabase {
    suspend fun getTitlePaginationResult(
        primaryKeyFetch: PrimaryKeyFetch,
        uid: PrimaryKey,
        searchType: TitleSearchType,
        type: TitleType? = null,
        scopeId: PrimaryKey? = null,
    ): Result<PaginationResult<TitleInfo>>
}

interface CommunityDatabase {
    suspend fun getRawCommunity(
        objectFetch: ObjectFetch,
        fillJoinInfo: Boolean? = null,
        uid: PrimaryKey? = null,
    ): Result<RawCommunity?>

    suspend fun getJoinedCommunityIds(uid: PrimaryKey): Result<List<Long>>
    suspend fun getCommunityPaginationResult(
        word: String?,
        hasPosterSearch: PosterSearch?,
        primaryKeyFetch: PrimaryKeyFetch,
        joinSearch: JoinSearch,
    ): Result<PaginationResult<RawCommunity>?>

    suspend fun createCommunity(community: Community): Result<Unit>
    suspend fun createCommunityRooms(rooms: List<Room>): Result<Unit>
    suspend fun getCommunityJoinedTimeByIds(
        uid: PrimaryKey,
        communityIds: List<PrimaryKey>,
    ): Result<List<Pair<Long, LocalDateTime>>>

    suspend fun getRawCommunities(objectListFetch: ObjectListFetch): Result<List<RawCommunity>>
    suspend fun updateCommunity(id: PrimaryKey, body: UpdateCommunityBody): Result<Boolean>
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

    suspend fun processRoomListToRawRoom(
        uid: PrimaryKey?,
        rooms: List<Room>,
    ): Result<List<RawRoom>>

    suspend fun createRoom(room: Room): Result<Unit>
    suspend fun getRawRooms(objectListFetch: ObjectListFetch): Result<List<RawRoom>>
    suspend fun getRoomList(objectListFetch: ObjectListFetch): Result<List<Room>>
    suspend fun updateRoom(id: PrimaryKey, body: UpdateRoomBody): Result<Boolean>
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
}

interface ContainerDatabase {
    suspend fun isMemberJoined(objectId: PrimaryKey, uid: PrimaryKey?): Result<Boolean>
    suspend fun joinContainer(
        id: PrimaryKey,
        uid: PrimaryKey,
        time: LocalDateTime,
        objectType: ObjectType,
    ): Result<Unit>

    suspend fun exitContainer(containerId: PrimaryKey, id: PrimaryKey): Result<Int>
    suspend fun getJoinedUserList(roomId: PrimaryKey): Result<List<MemberJoin>>
    suspend fun getUserJoinedTime(
        parentIds: List<PrimaryKey>,
        uid: PrimaryKey
    ): Result<List<MemberJoin>>

    suspend fun getMemberCount(parentIds: List<PrimaryKey>): Result<List<Pair<Long, Long>>>
    suspend fun getContainerInfo(
        parentIds: List<PrimaryKey>,
        uid: PrimaryKey?,
    ): Result<Triple<Map<PrimaryKey, MemberJoin>, Map<PrimaryKey, UserTopicRead>, Map<Long, Long>>>

    suspend fun getTopicReadList(
        parentIds: List<PrimaryKey>,
        uid: PrimaryKey
    ): Result<List<UserTopicRead>>

    suspend fun getMemberPaginationResult(
        objectId: PrimaryKey?,
        word: String?,
        fetch: PrimaryKeyFetch,
    ): Result<PaginationResult<RawUser>>

    suspend fun getQuotaInfo(ownerId: PrimaryKey, quotaType: QuotaType): Result<QuotaInfo?>
    suspend fun insertQuota(quota: Quota): Result<Unit>
}

interface CliDatabase {
    suspend fun batchAddUser(users: List<User>)
    suspend fun batchAddCommunities(
        communities: List<Community>,
        memberList: List<Pair<PrimaryKey, List<PrimaryKey>>>,
    )
    suspend fun batchAddRooms(
        roomList: List<Room>,
        membersList: List<Pair<List<PrimaryKey>, PrimaryKey>>
    )
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
}
