package com.storyteller_f.a.backend.exposed

import com.storyteller_f.a.backend.core.*
import com.storyteller_f.a.backend.exposed.query.PaginationResult
import com.storyteller_f.a.backend.exposed.tables.*
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder
import org.jetbrains.exposed.v1.r2dbc.Query

interface CombinedDatabase<T> {
    val userDatabase: UserDatabase<T>
    val topicDatabase: TopicDatabase
    val titleDatabase: TitleDatabase
    val communityDatabase: CommunityDatabase
    val roomData: RoomDatabase
    val mediaDatabase: MediaDatabase
    val containerDatabase: ContainerDatabase
}

interface UserDatabase<T> {
    suspend fun getUserAid(id: PrimaryKey): Result<String?>
    suspend fun getRawUser(objectFetch: ObjectFetch): Result<RawUser<T>?>
    suspend fun getRawUserAndPublicKeyByAddress(ad: String): Result<Pair<RawUser<T>, String>?>
    suspend fun createUser(user: User): Result<Unit>
    suspend fun isUserNotExistsByPublicKey(pk: String): Result<Boolean>
    suspend fun updateUserInfo(id: PrimaryKey, newUser: UpdateUserBody): Result<Boolean>
    suspend fun isUserExistsByUid(id: Long): Result<Boolean>
    suspend fun getUserAuthDataByAid(predicate: SqlExpressionBuilder.() -> Op<Boolean>): Result<Pair<String, Long>?>
    suspend fun getUserAuthDataBy(predicate: SqlExpressionBuilder.() -> Op<Boolean>): Result<Pair<String, Long>?>
    suspend fun getRawUsers(objectListFetch: ObjectListFetch): Result<List<RawUser<T>>>
    suspend fun getUserAcgByIds(objectListFetch: ObjectListFetch): Result<List<Pair<Long, Long>>>
    suspend fun addReadLog(userTopicRead: UserTopicRead): Result<Unit>
    suspend fun insertUserLog(log: UserLog): Result<Unit>
    suspend fun addDevice(uid: PrimaryKey, endpointUrl: String): Result<Unit>
    suspend fun removeDevice(uid: PrimaryKey, endpointUrl: String): Result<Int>
    suspend fun getUserDevices(uid: List<PrimaryKey>): Result<List<UserDevice>>
    suspend fun addAcgForUser(
        acgList: List<Pair<PrimaryKey, Int>>,
        userAcgMap: Map<Long, Long>,
        list: List<Topic>,
        taskRecordId: PrimaryKey,
    ): Result<Unit>
    suspend fun getLatestTaskRecord(type: TaskRecordType): Result<TaskRecord?>
    suspend fun getRawAlternativePaginationListByHost(
        hostId: PrimaryKey,
        fetch: PrimaryKeyFetch,
    ): Result<PaginationResult<RawAlternateAccount>>
    suspend fun createAlternativeAccount(hostId: PrimaryKey, privateKey: String, user: User): Result<Unit>
}

interface TopicDatabase {
    suspend fun getTopicRootTuple(parentId: PrimaryKey): Result<ObjectTuple?>
    suspend fun getTopicInfo(fetch: ObjectFetch, uid: PrimaryKey?): Result<TopicInfo?>
    suspend fun getTopicInfoListByPredicate(uid: PrimaryKey?, queryBuilder: Query.() -> Query): Result<List<TopicInfo>>
    suspend fun getTopicPaginationResultByPredicate(
        uid: PrimaryKey?,
        primaryKeyFetch: PrimaryKeyFetch,
        extraQuery: Query.() -> Query,
    ): Result<PaginationResult<TopicInfo>>

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun saveEncryptedTopic(topic: Topic, content: TopicContent.Encrypted): Result<TopicInfo>
    suspend fun updateTopicStatus(topicId: PrimaryKey, newValue: Boolean): Result<Boolean>
    suspend fun getTopicList(firstId: PrimaryKey): Result<List<Topic>>
    suspend fun getTopicCommentCount(
        topicIdList: List<PrimaryKey>,
    ): Result<List<Pair<Long, Long>>>
    suspend fun isUserCommented(uid: PrimaryKey, topicId: List<PrimaryKey>): Result<List<Long>>
    suspend fun processTopicToTopicInfo(uid: PrimaryKey?, topics: List<Topic>): Result<List<TopicInfo>>
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
    suspend fun checkCommunityExists(parentId: PrimaryKey): Result<List<Long>>
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

interface MediaDatabase {
    suspend fun getMedia(owner: PrimaryKey, name: String): Result<Media?>
    suspend fun getMediaByIds(ids: List<PrimaryKey>): Result<List<Media>>
    suspend fun getMediaListByOwner(owner: PrimaryKey): Result<List<Media>>
    suspend fun getMediaByNames(names: List<String?>): Result<List<Media>>
    suspend fun insertMediaRefs(
        objectId: PrimaryKey,
        objectType: ObjectType,
        mediaName: List<Pair<PrimaryKey, String>>,
    ): Result<Unit>
    suspend fun getMediaPaginationList(
        uid: PrimaryKey,
        primaryKeyFetch: PrimaryKeyFetch,
    ): Result<PaginationResult<Media>>
}

interface ContainerDatabase {
    suspend fun isMemberJoined(objectId: PrimaryKey, uid: PrimaryKey?): Result<Boolean>
    suspend fun joinContainer(
        id: PrimaryKey,
        uid: PrimaryKey,
        time: LocalDateTime,
        objectType: ObjectType,
    ): Result<Unit>
    suspend fun exit(containerId: PrimaryKey, id: PrimaryKey): Result<Int>
    suspend fun getJoinedUserList(roomId: PrimaryKey): Result<List<MemberJoin>>
    suspend fun getUserJoinedTime(parentIds: List<PrimaryKey>, uid: PrimaryKey): Result<List<MemberJoin>>
    suspend fun getMemberCount(parentIds: List<PrimaryKey>): Result<List<Pair<Long, Long>>>
    suspend fun getContainerInfo(
        parentIds: List<PrimaryKey>,
        uid: PrimaryKey?,
    ): Result<Triple<Map<PrimaryKey, MemberJoin>, Map<PrimaryKey, UserTopicRead>, Map<Long, Long>>>
    suspend fun getTopicReadList(parentIds: List<PrimaryKey>, uid: PrimaryKey): Result<List<UserTopicRead>>
    suspend fun getMemberPaginationResult(
        objectId: PrimaryKey?,
        word: String?,
        fetch: PrimaryKeyFetch,
    ): Result<PaginationResult<RawUser<User>>>
}
