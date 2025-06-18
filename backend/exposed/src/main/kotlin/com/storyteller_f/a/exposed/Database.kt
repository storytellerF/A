package com.storyteller_f.a.exposed

import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.ReactionFetch
import com.storyteller_f.a.exposed.query.PaginationResult
import com.storyteller_f.backend.service.ExposedDatabaseSession
import com.storyteller_f.backend.service.tables.Community
import com.storyteller_f.backend.service.tables.CommunityRawResult
import com.storyteller_f.backend.service.tables.Media
import com.storyteller_f.backend.service.tables.MemberJoin
import com.storyteller_f.backend.service.tables.ReactionRecord
import com.storyteller_f.backend.service.tables.Room
import com.storyteller_f.backend.service.tables.RoomRawResult
import com.storyteller_f.backend.service.tables.TaskRecord
import com.storyteller_f.backend.service.tables.Topic
import com.storyteller_f.backend.service.tables.User
import com.storyteller_f.backend.service.tables.UserDevice
import com.storyteller_f.backend.service.tables.UserLog
import com.storyteller_f.backend.service.tables.UserRawResult
import com.storyteller_f.backend.service.tables.UserTopicRead
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.ReactionRecordInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserPubKeyInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.type.JoinSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PosterSearch
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TaskRecordType
import com.storyteller_f.shared.type.TitleSearchType
import com.storyteller_f.shared.type.TitleType
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder

interface Database {
    val userDatabase: UserDatabase
    val topicDatabase: TopicDatabase
    val titleDatabase: TitleDatabase
    val communityDatabase: CommunityDatabase
    val roomData: RoomDatabase
}

interface UserDatabase {
    suspend fun getUserAid(id: PrimaryKey): Result<String?>
    suspend fun getUserRawResult(objectFetch: ObjectFetch): Result<UserRawResult?>
    suspend fun getMemberPaginationResult(
        objectId: PrimaryKey?,
        word: String?,
        primaryKeyFetch: PrimaryKeyFetch
    ): Result<PaginationResult<UserRawResult>>

    suspend fun getUserRawResultAndPublicKeyByAddress(ad: String): Result<Pair<UserRawResult, String>?>
    suspend fun createUser(user: User): Result<Unit>
    suspend fun isUserNotExists(pk: String): Result<Boolean>
    suspend fun updateUserInfo(id: PrimaryKey, newUser: UpdateUserBody): Result<Boolean>
    suspend fun checkUserExists(id: Long): Result<Boolean>
    suspend fun getUserAuthDataByAid(predicate: SqlExpressionBuilder.() -> Op<Boolean>): Result<Pair<String, Long>?>
    suspend fun getUserAuthDataBy(predicate: SqlExpressionBuilder.() -> Op<Boolean>): Result<Pair<String, Long>?>
    suspend fun getUserRawResultList(objectListFetch: ObjectListFetch): Result<List<UserRawResult>>
    suspend fun getUserAcgByIds(objectListFetch: ObjectListFetch): Result<List<Pair<Long, Long>>>
    suspend fun getUserAlternatUserRawResultList(uid: PrimaryKey): Result<List<UserRawResult>>
    suspend fun addReadLog(userTopicRead: UserTopicRead): Result<Unit>
    suspend fun getTopicReadList(parentIds: List<PrimaryKey>, uid: PrimaryKey): Result<List<UserTopicRead>>
    suspend fun insertUserLog(log: UserLog): Result<Unit>
    suspend fun addDevice(uid: PrimaryKey, endpointUrl: String): Result<Unit>
    suspend fun removeDevice(uid: PrimaryKey, endpointUrl: String): Result<Int>
    suspend fun getUserDevices(uid: List<PrimaryKey>): Result<List<UserDevice>>
    suspend fun isMemberJoined(objectId: PrimaryKey, uid: PrimaryKey?): Result<Boolean>
    suspend fun addRoomJoin(room: PrimaryKey, id: PrimaryKey, time: LocalDateTime): Result<Unit>
    suspend fun exit(containerId: PrimaryKey, id: PrimaryKey): Result<Int>
    suspend fun addCommunityJoin(id: PrimaryKey, community: PrimaryKey, time: LocalDateTime): Result<Unit>
    suspend fun getJoinedUserList(roomId: PrimaryKey): Result<List<MemberJoin>>
    suspend fun getUserJoinedTime(parentIds: List<PrimaryKey>, uid: PrimaryKey): Result<List<MemberJoin>>
    suspend fun getMemberCount(parentIds: List<PrimaryKey>): Result<List<Pair<Long, Long>>>
    suspend fun getContainerInfo(
        parentIds: List<PrimaryKey>,
        uid: PrimaryKey?
    ): Result<Triple<Map<PrimaryKey, MemberJoin>, Map<PrimaryKey, UserTopicRead>, Map<Long, Long>>>

    suspend fun addAcgForUser(
        acgList: List<Pair<PrimaryKey, Int>>,
        userAcgMap: Map<Long, Long>,
        list: List<Topic>,
        taskRecordId: PrimaryKey,
    ): Result<Unit>

    suspend fun getMediaPaginationList(
        uid: PrimaryKey,
        primaryKeyFetch: PrimaryKeyFetch
    ): Result<Pair<List<Media>, Long>>

    suspend fun getMedia(owner: PrimaryKey, name: String): Result<Media?>
    suspend fun getMediaByIds(ids: List<PrimaryKey>): Result<List<Media>>
    suspend fun getMediaListByOwner(owner: PrimaryKey): Result<List<Media>>
    suspend fun getMediaByNames(names: List<String?>): Result<List<Media>>
    suspend fun insertMediaRefs(
        objectId: PrimaryKey,
        objectType: ObjectType,
        mediaName: List<Pair<PrimaryKey, String>>
    ): Result<List<ResultRow>>

    suspend fun getLatestTaskRecord(type: TaskRecordType): Result<TaskRecord?>
}

interface TopicDatabase {
    suspend fun getTopicRootTuple(parentId: PrimaryKey): Result<ObjectTuple?>
    suspend fun getTopicInfo(fetch: ObjectFetch, uid: PrimaryKey?): Result<TopicInfo?>
    suspend fun getTopicInfoListByPredicate(uid: PrimaryKey?, queryBuilder: Query.() -> Query): Result<List<TopicInfo>>
    suspend fun getTopicPaginationResultByPredicate(
        uid: PrimaryKey?,
        primaryKeyFetch: PrimaryKeyFetch,
        extraQuery: Query.() -> Query
    ): Result<PaginationResult<TopicInfo>>

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun saveEncryptedTopic(topic: Topic, content: TopicContent.Encrypted): Result<TopicInfo>
    suspend fun updateTopicStatus(topicId: PrimaryKey, newValue: Boolean): Result<Boolean>
    suspend fun getTopicList(firstId: PrimaryKey): Result<List<Topic>>
    suspend fun ExposedDatabaseSession.getTopicCommentCount(
        topicIdList: List<PrimaryKey>
    ): Result<List<Pair<Long, Long>>>
    suspend fun ExposedDatabaseSession.isUserCommented(uid: PrimaryKey, topicId: List<PrimaryKey>): Result<List<Long>>
    suspend fun processTopicToTopicInfo(uid: PrimaryKey?, topics: List<Topic>): Result<List<TopicInfo>>
    suspend fun processByteArrayToTopicContent(
        topics: List<Topic>,
        uid: PrimaryKey?
    ): Result<Map<PrimaryKey, TopicContent>>

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun ExposedDatabaseSession.getEncryptedTopicContents(
        data: List<Topic>,
        uid: PrimaryKey
    ): Result<List<TopicContent.Encrypted>>

    suspend fun statsReactionRecord(reactionRecord: ReactionRecord): Result<Unit>
    suspend fun getReactionInfoPaginationResult(
        objectId: List<PrimaryKey>,
        uid: PrimaryKey?,
        reactionFetch: ReactionFetch
    ): Result<PaginationResult<ReactionInfo>>

    suspend fun hasReactedEmoji(objectIdList: List<PrimaryKey>, uid: PrimaryKey): Result<List<Pair<Long, String>>>
    suspend fun getReactionInfo(uid: PrimaryKey, objectId: PrimaryKey, emojiText: String): Result<ReactionInfo?>
    suspend fun hasReactedForEmoji(objectId: PrimaryKey, uid: PrimaryKey, emoji: String): Result<Boolean>
    suspend fun deleteReaction(uid: PrimaryKey, emoji: String, objectId: PrimaryKey): Result<Boolean>
    suspend fun getReactionRecordInfo(uid: PrimaryKey, emoji: String, objectId: PrimaryKey): Result<ReactionRecordInfo?>
    suspend fun deleteReaction(reactionId: PrimaryKey): Result<Boolean>
    suspend fun insertReaction(reactionRecord: ReactionRecord): Result<Unit>
    suspend fun getReactionCount(objectIdList: List<PrimaryKey>): Result<List<Pair<Long, Long>>>
    suspend fun getReactionCountForEmoji(objectId: List<PrimaryKey>, emoji: String): Result<List<Pair<Long, Long>>>
}

interface TitleDatabase {
    suspend fun getTitlePaginationResult(
        primaryKeyFetch: PrimaryKeyFetch,
        uid: PrimaryKey,
        searchType: TitleSearchType,
        type: TitleType? = null,
        scopeId: PrimaryKey? = null
    ): Result<PaginationResult<TitleInfo>>
}

interface CommunityDatabase {
    suspend fun checkCommunityExists(parentId: PrimaryKey): Result<List<Long>>
    suspend fun getCommunityRawResult(
        objectFetch: ObjectFetch,
        fillJoinInfo: Boolean? = null,
        uid: PrimaryKey? = null
    ): Result<CommunityRawResult?>

    suspend fun getJoinedCommunityIds(uid: PrimaryKey): Result<List<Long>>
    suspend fun getCommunityPaginationResult(
        uid: PrimaryKey?,
        word: String?,
        hasPosterSearch: PosterSearch?,
        primaryKeyFetch: PrimaryKeyFetch,
        joinSearch: JoinSearch
    ): Result<PaginationResult<CommunityRawResult>?>

    suspend fun processCommunityToCommunityRawResult(
        uid: PrimaryKey?,
        communities: List<Community>
    ): Result<List<CommunityRawResult>>

    suspend fun createCommunity(community: Community): Result<Unit>
    suspend fun getCommunityJoinedTimeByIds(
        uid: PrimaryKey,
        communityIds: List<PrimaryKey>
    ): Result<List<Pair<Long, LocalDateTime>>>

    suspend fun getCommunityRawResults(objectListFetch: ObjectListFetch): Result<List<CommunityRawResult>>
    suspend fun updateCommunity(id: PrimaryKey, body: UpdateCommunityBody): Result<Boolean>
}

interface RoomDatabase {
    suspend fun checkRoomIsPrivate(roomId: PrimaryKey): Result<Boolean?>
    suspend fun getRoomPaginationResult(
        uid: PrimaryKey?,
        word: String?,
        community: PrimaryKey?,
        primaryKeyFetch: PrimaryKeyFetch,
        joinSearch: JoinSearch
    ): Result<PaginationResult<RoomRawResult>>

    suspend fun getRoomCommunityId(parentId: PrimaryKey): Result<PrimaryKey?>
    suspend fun getRoomPubKeyPaginationResult(
        roomId: PrimaryKey,
        primaryKeyFetch: PrimaryKeyFetch
    ): Result<PaginationResult<UserPubKeyInfo>>

    suspend fun getRoomRawResult(
        objectFetch: ObjectFetch,
        fillJoinInfo: Boolean? = null,
        uid: PrimaryKey? = null
    ): Result<RoomRawResult?>

    suspend fun ExposedDatabaseSession.processRoomListToRoomRawResult(
        uid: PrimaryKey?,
        rooms: List<Room>
    ): Result<List<RoomRawResult>>

    suspend fun createRoom(room: Room): Result<Unit>
    suspend fun getRoomRawResultList(objectListFetch: ObjectListFetch): Result<List<RoomRawResult>>
    suspend fun getRoomList(objectListFetch: ObjectListFetch): Result<List<Room>>
    suspend fun updateRoom(id: PrimaryKey, body: UpdateRoomBody): Result<Boolean>
}
