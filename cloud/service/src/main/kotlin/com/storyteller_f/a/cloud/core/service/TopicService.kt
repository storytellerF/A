package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.api.NewTopic
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.OffsetFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.ReactionFetch
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.backend.core.fixedSort
import com.storyteller_f.a.backend.core.mapPagingResultIfNotNullNullable
import com.storyteller_f.a.backend.core.mapPagingResultNullable
import com.storyteller_f.a.backend.core.paging
import com.storyteller_f.a.backend.core.service.TopicDocument
import com.storyteller_f.a.backend.core.service.TopicDocumentSearch
import com.storyteller_f.a.backend.core.service.UploadPack
import com.storyteller_f.a.backend.core.types.FileRef
import com.storyteller_f.a.backend.core.types.RawTopic
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.a.backend.core.types.UserSubscription
import com.storyteller_f.a.backend.core.types.toTopicInfo
import com.storyteller_f.a.cloud.pdf.PdfGenerationSpec
import com.storyteller_f.a.cloud.pdf.PdfService
import com.storyteller_f.a.cloud.pdf.SnapshotGeneration
import com.storyteller_f.shared.model.A_FILE_DEFAULT_BUCKET
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.TopicPinSearch
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.obj.NewRoomTopic
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.UNIT_RESULT
import com.storyteller_f.shared.utils.checkContent
import com.storyteller_f.shared.utils.extractMarkdownMediaLink
import com.storyteller_f.shared.utils.firstOrNull
import com.storyteller_f.shared.utils.groupByPair
import com.storyteller_f.shared.utils.ifNotNull
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.shared.utils.trimMarkdownUnusedContent
import io.github.aakira.napier.Napier
import kotlinx.collections.immutable.toImmutableList
import java.io.File
import java.util.ServiceLoader
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

suspend fun Backend.createPlainTopic(
    uid: PrimaryKey,
    newTopic: NewTopic,
): Result<TopicInfo?> {
    if (newTopic.parentType == ObjectType.ROOM) {
        return Result.failure(ForbiddenException("can't use api to add topic in room"))
    }

    val content = newTopic.content.trim()

    if (content.isEmpty()) {
        return Result.failure(CustomBadRequestException("content is empty"))
    }
    checkContent(content).exceptionOrNull()?.let {
        return Result.failure(it)
    }
    if (content.length > 1000) {
        return Result.failure(CustomBadRequestException("too long"))
    }

    return checkRootWritePermission(
        newTopic.parentType,
        newTopic.parentId,
        uid
    ).mapResultIfNotNull { (rootType, rootId, level) ->
        if (level >= 10) {
            Result.failure(CustomBadRequestException("not support"))
        } else if (rootType == ObjectType.ROOM) {
            Result.failure(ForbiddenException("can't use api to add topic in room"))
        } else {
            val parentTuple = ObjectTuple(newTopic.parentId, newTopic.parentType)
            val rootTuple = ObjectTuple(rootId, rootType)
            savePlainTopic(uid, content, level, parentTuple, rootTuple)
        }
    }.ifNotNull {
        addSubscription(uid, it.tuple())
    }
}

private suspend fun Backend.addSubscription(
    uid: PrimaryKey,
    objectTuple: ObjectTuple,
) {
    database.subscription.addSubscription(
        UserSubscription(SnowflakeFactory.nextId(), uid, objectTuple.objectId, objectTuple.objectType, now())
    ).onFailure {
        Napier.e(it) {
            "add user subscription failed"
        }
    }
}

private suspend fun Backend.savePlainTopic(
    uid: PrimaryKey,
    content: String,
    level: Int,
    parentTuple: ObjectTuple,
    rootTuple: ObjectTuple
): Result<TopicInfo?> {
    val trimmedContent = trimMarkdownUnusedContent(content)
    val newId = SnowflakeFactory.nextId()
    val topic = Topic(
        id = newId,
        createdTime = now(),
        author = uid,
        parentId = parentTuple.objectId,
        parentType = parentTuple.objectType,
        rootId = rootTuple.objectId,
        rootType = rootTuple.objectType,
        trimmedContent.encodeToByteArray(),
        false,
        level + 1,
        lastModifiedTime = null,
    )
    val plain = TopicContent.Plain(trimmedContent)
    val topicInfo = RawTopic(topic, plain).toTopicInfo()

    val documentFileList = documentFileList(listOf(topicInfo)).map {
        it.second
    }

    objectStorageService.get(A_FILE_DEFAULT_BUCKET, documentFileList).map { list ->
        list.map {
            it.fullName
        }
    }.onSuccess { fileList ->
        val notExistsFileList = documentFileList.filter {
            !fileList.contains(it)
        }
        if (notExistsFileList.isNotEmpty()) {
            return Result.failure(CustomBadRequestException("${notExistsFileList.joinToString()} not exists"))
        }
    }.onFailure {
        return Result.failure(it)
    }

    topicSearchService.saveDocument(
        listOf(TopicDocument.fromTopic(topic, plain))
    ).onFailure {
        Napier.e(it) {
            "save plain topic document failed"
        }
    }
    return buildFileRefs(documentFileList, topic, uid).mapResult {
        database.topic.savePlainTopic(topic, plain, it).onSuccess {
            addUserLog(uid, UserLogType.CREATE, topicInfo.tuple())
        }
    }.mapResult {
        processTopicAfterCreate(topicInfo, uid)
    }
}

private suspend fun Backend.buildFileRefs(
    documentFileList: List<String>,
    topic: Topic,
    uid: PrimaryKey
): Result<List<FileRef>> {
    return database.file.getFileRecordByNames(documentFileList).map {
        it.associateBy { fileRecord ->
            fileRecord.fullName
        }
    }.map {
        documentFileList.map { mediaName ->
            val fileRecord = it[mediaName]!!
            FileRef(
                id = SnowflakeFactory.nextId(),
                createdTime = now(),
                objectId = topic.id,
                objectType = ObjectType.TOPIC,
                author = uid,
                mediaName = mediaName,
                fileId = fileRecord.id
            )
        }
    }
}

suspend fun Backend.createTopicAtRoom(
    newTopic: NewRoomTopic,
    uid: PrimaryKey,
): Result<TopicInfo?> {
    if (newTopic.parentType != ObjectType.TOPIC && newTopic.parentType != ObjectType.ROOM) {
        return Result.failure(ForbiddenException())
    }
    return checkRootWritePermission(
        newTopic.parentType,
        newTopic.parentId,
        uid
    ).mapResultIfNotNull {
        if (it.level >= 10) {
            Result.failure(CustomBadRequestException("not support"))
        } else {
            database.room.checkRoomIsPrivate(it.rootId).mapResultIfNotNull { isPrivate ->
                createTopicAtRoom(uid, it, newTopic, isPrivate, newTopic.content)
            }
        }
    }.ifNotNull {
        addSubscription(uid, it.tuple())
    }
}

private suspend fun Backend.createTopicAtRoom(
    uid: PrimaryKey,
    permission: RootWritePermission,
    newTopic: NewRoomTopic,
    isPrivate: Boolean,
    content: TopicContent
): Result<TopicInfo?> {
    if (isPrivate) {
        if (content !is TopicContent.Encrypted) {
            return Result.failure(ForbiddenException("Private room only accept encrypted content."))
        }
        return saveEncryptedTopic(permission, content, uid, permission.tuple, newTopic.tuple)
    }
    if (content !is TopicContent.Plain) {
        return Result.failure(ForbiddenException("Public room only accept unencrypted content."))
    }
    return savePlainTopic(uid, content.plain, permission.level, newTopic.tuple, permission.tuple)
}

private suspend fun Backend.saveEncryptedTopic(
    permission: RootWritePermission,
    content: TopicContent.Encrypted,
    uid: PrimaryKey,
    rootTuple: ObjectTuple,
    parentTuple: ObjectTuple,
) = isKeyVerified(permission.rootId, content.encryptedKey).mapResult {
    if (it) {
        val newId = SnowflakeFactory.nextId()
        val bytes = content.bytes
        val topic = Topic(
            newId,
            now(),
            uid,
            parentTuple.objectId,
            parentTuple.objectType,
            rootTuple.objectId,
            rootTuple.objectType,
            bytes,
            true,
            permission.level + 1,
            false,
            null
        )
        database.topic.saveEncryptedTopic(topic, content).map {
            RawTopic(topic, content).toTopicInfo()
        }
    } else {
        Result.failure(ForbiddenException("Key not found ${content.encryptedKey.size}"))
    }
}.mapResultIfNotNull { topicInfo ->
    processTopicAfterCreate(topicInfo, uid)
}

suspend fun Backend.processTopicAfterCreate(
    topicInfo: TopicInfo,
    uid: PrimaryKey
) = runCatching {
    val info = if (topicInfo.content is TopicContent.Plain) {
        processTopicFileObject(listOf(topicInfo)).firstOrNull().getOrThrow()
    } else {
        topicInfo
    }
    val authorInfo = getUserInfo(ObjectFetch.IdFetch(uid)).getOrThrow()
    if (authorInfo != null) {
        info?.copy(extension = TopicInfo.Extension(authorInfo = authorInfo))
    } else {
        info
    }
}

suspend fun Backend.createTopicSnapshot(
    uid: PrimaryKey,
    topicId: PrimaryKey,
): Result<FileInfo?> {
    return checkTopicReadPermission(
        topicId,
        uid
    ).mapResultIfNotNull { (hasRead, _, isPrivate) ->
        if (hasRead && !isPrivate) {
            database.getRawTopic(ObjectFetch.IdFetch(topicId), null)
        } else {
            Result.failure(ForbiddenException("Permission denied"))
        }
    }.mapResultIfNotNull {
        processRawTopicToTopicInfo(listOf(it), uid, false)
    }.firstOrNull().mapResultIfNotNull { topicInfo ->
        getUserInfo(ObjectFetch.IdFetch(uid)).mapResultIfNotNull { userInfo ->
            createTopicSnapshot(topicInfo, userInfo, uid)
        }
    }
}

private suspend fun Backend.createTopicSnapshot(
    topicInfo: TopicInfo,
    creatorInfo: UserInfo,
    uid: PrimaryKey,
): Result<FileInfo?> {
    val topicId = topicInfo.id
    val name = "$uid/$topicId.pdf"
    val pdfFile = File(System.getProperty("java.io.tmpdir"), name)
    val signedFile = File(System.getProperty("java.io.tmpdir"), "${pdfFile.nameWithoutExtension}_signed.pdf")
    return try {
        getUserInfo(
            ObjectFetch.IdFetch(topicInfo.author)
        ).mapResultIfNotNull { userInfo ->
            generateSignedSnapshot(
                userInfo,
                creatorInfo,
                topicInfo,
                customConfig.snapshotKeyStore?.let {
                    SnapshotGeneration.KeyStoreGeneration(it.path, it.pass, pdfFile, signedFile)
                } ?: SnapshotGeneration.SimpleGeneration(pdfFile)
            )
        }.mapResultIfNotNull {
            tryUploadFiles(
                uid,
                ObjectType.USER,
                listOf(UploadPack(pdfFile, "$topicId.pdf", pdfFile.length(), "$uid/$topicId.pdf"))
            )
        }.firstOrNull()
    } finally {
        pdfFile.delete()
        signedFile.delete()
    }
}

@OptIn(ExperimentalUuidApi::class)
suspend fun Backend.generateSignedSnapshot(
    authorInfo: UserInfo,
    creatorInfo: UserInfo,
    topicInfo: TopicInfo,
    snapshotGeneration: SnapshotGeneration,
): Result<Unit?> {
    val content = topicInfo.content
    if (content !is TopicContent.Plain) {
        return Result.failure(CustomBadRequestException("unsupported"))
    }
    val saveToFile = snapshotGeneration.path
    val parent = saveToFile.parentFile ?: return Result.success(null)
    if (!parent.exists() && !parent.mkdirs()) return Result.failure(Exception("failed"))
    val plainContent = content.plain
    val map = mutableMapOf<String, File>()
    val userHome = System.getProperty("user.home")
    val pdfService =
        ServiceLoader.load(PdfService::class.java).firstOrNull() ?: return Result.failure(
            Exception("not register pdf service")
        )
    return try {
        content.fileInfos.forEach {
            val targetFile = File(userHome, "a-temp/${Uuid.random()}")
            map[it.name] = targetFile
            objectStorageService.getInputStream(A_FILE_DEFAULT_BUCKET, it.fullName).getOrThrow()
                .buffered().use { input ->
                    targetFile.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                }
        }
        pdfService.generateSignedSnapshot(
            creatorInfo,
            authorInfo,
            plainContent,
            map,
            snapshotGeneration,
            PdfGenerationSpec(topicInfo.createdTime, now())
        )
    } catch (e: Exception) {
        Result.failure(e)
    } finally {
        map.forEach {
            it.value.delete()
        }
    }
}

suspend fun Backend.getTopic(
    topicId: PrimaryKey,
    uid: PrimaryKey?,
    fillHasCommented: Boolean?,
): Result<TopicInfo?> {
    if (uid == null && fillHasCommented == true) return Result.failure(UnauthorizedException())
    return checkTopicReadPermission(
        topicId,
        uid
    ).mapResultIfNotNull { permission ->
        if (permission.hasRead) {
            uncheckGetTopicById(topicId, uid).mapIfNotNull {
                it.copy(hasJoined = it.hasJoined)
            }
        } else {
            Result.failure(ForbiddenException("Permission Denied"))
        }
    }
}

suspend fun Backend.uncheckGetTopicById(topicId: PrimaryKey, uid: PrimaryKey?): Result<TopicInfo?> {
    return database.getRawTopic(ObjectFetch.IdFetch(topicId), uid).mapResultIfNotNull { info ->
        processRawTopicToTopicInfo(listOf(info), uid, true)
    }.firstOrNull()
}

suspend fun Backend.getTopicByAid(
    aid: String,
    uid: PrimaryKey?,
    fillHasCommented: Boolean?,
): Result<TopicInfo?> {
    if (uid == null && fillHasCommented == true) return Result.failure(UnauthorizedException())
    return database.getRawTopic(ObjectFetch.AidFetch(aid), uid)
        .mapResultIfNotNull { info ->
            checkTopicReadPermission(
                info.topic.id,
                uid
            ).mapResultIfNotNull { (hasRead, hasJoined) ->
                if (hasRead) {
                    Result.success(info.copy(hasJoined = hasJoined))
                } else {
                    Result.failure(ForbiddenException("Permission Denied"))
                }
            }
        }.mapResultIfNotNull { info ->
            processRawTopicToTopicInfo(listOf(info), uid, true)
        }.firstOrNull()
}

suspend fun Backend.getTopicsByParentId(
    parentId: PrimaryKey,
    parentType: ObjectType,
    uid: PrimaryKey? = null,
    fillHasCommented: Boolean?,
    primaryKeyFetch: PrimaryKeyFetch,
    pinType: TopicPinSearch? = null,
): Result<PaginationResult<TopicInfo>?> {
    if (uid == null && fillHasCommented == true) return Result.failure(UnauthorizedException())
    return checkRootReadPermission(
        parentType,
        parentId,
        uid
    ).mapResultIfNotNull { (hasRead, _, isPrivate) ->
        if (isPrivate && !hasRead) {
            Result.failure(ForbiddenException("Permission Denied"))
        } else {
            UNIT_RESULT
        }
    }.mapResultIfNotNull {
        uncheckGetTopicsByParentId(uid, parentId, primaryKeyFetch, pinType)
    }
}

suspend fun Backend.uncheckGetTopicsByParentId(
    uid: PrimaryKey?,
    parentId: PrimaryKey,
    primaryKeyFetch: PrimaryKeyFetch,
    pinType: TopicPinSearch? = null,
) = database.getRawTopicByParentId(uid, primaryKeyFetch, parentId, pinType)
    .mapResultIfNotNull { (data, total) ->
        processRawTopicToTopicInfo(data, uid, true).paging(total)
    }

suspend fun Backend.processRawTopicToTopicInfo(
    topics: List<RawTopic>,
    uid: PrimaryKey?,
    addLatestSubTopic: Boolean,
): Result<List<TopicInfo>?> {
    if (topics.isEmpty()) return Result.success(emptyList())
    return runCatching {
        val rooms = getRoomMapByTopics(topics)
        val subTopicsMap = if (addLatestSubTopic) {
            topics.flatMap { t ->
                database.getLatestRawTopic(uid, t.topic.id).getOrThrow()
            }.groupBy {
                it.topic.parentId
            }
        } else {
            emptyMap()
        }

        val uidList = topics.map {
            it.topic.author
        } + subTopicsMap.flatMap {
            it.value
        }.map {
            it.topic.author
        }.distinct()
        val userMap = getUserInfoList(ObjectListFetch.IdListFetch(uidList)).getOrThrow().associateBy { it.id }
        val processedSubTopic = subTopicsMap.mapValues {
            it.value.map { subTopic ->
                subTopic.toTopicInfo(TopicInfo.Extension(authorInfo = userMap[subTopic.topic.author]))
            }
        }
        val reactionMap = getReactionMap(topics, uid)
        topics.map {
            it.toTopicInfo(
                TopicInfo.Extension(
                    userMap[it.topic.author],
                    subTopics = processedSubTopic[it.topic.id]?.toImmutableList(),
                    reactions = reactionMap[it.topic.id]?.toImmutableList(),
                    roomInfo = if (it.topic.rootType == ObjectType.ROOM) rooms[it.topic.rootId] else null
                )
            )
        }
    }.mapResultIfNotNull {
        processTopicFileObject(it)
    }
}

private suspend fun Backend.getReactionMap(
    topics: List<RawTopic>,
    uid: PrimaryKey?
): Map<PrimaryKey, List<ReactionInfo>> =
    database.reaction.getReactionInfoPaginationResult(topics.map {
        it.topic.id
    }, uid, ReactionFetch(null, 20)).map {
        it.list
    }.map {
        it.groupBy(ReactionInfo::objectId)
    }.getOrThrow()

private suspend fun Backend.getRoomMapByTopics(topics: List<RawTopic>): Map<PrimaryKey, RoomInfo> {
    val roomIds = topics.mapNotNull {
        if (it.topic.rootType == ObjectType.ROOM) {
            it.topic.rootId
        } else {
            null
        }
    }
    return getRoomInfoList(ObjectListFetch.IdListFetch(roomIds)).getOrThrow().associateBy {
        it.id
    }
}

/**
 * 搜索用户主题
 */
suspend fun Backend.searchUserTopics(
    userId: PrimaryKey,
    search: CustomApi.Topics.Users.Id.UserTopicSearchQuery,
    primaryKeyFetch: OffsetFetch,
    uid: PrimaryKey?,
): Result<PaginationResult<TopicInfo>?> {
    if (search.fillHasCommented == true && uid == null) return Result.failure(UnauthorizedException())

    val word = search.word.trim()
    if (word.isBlank()) {
        return Result.success(PaginationResult(emptyList(), 0))
    }
    if (word.length > 20) {
        return Result.failure(CustomBadRequestException("word too long"))
    }

    // 创建TopicDocumentSearch.Topics对象进行用户主题搜索
    return topicSearchService.searchDocument(
        TopicDocumentSearch.Topics(userId, word, fetch = primaryKeyFetch)
    ).mapPagingResultNullable { list ->
        processTopicsDocument(uid, list)
    }
}

/**
 * 搜索房间主题
 */
suspend fun Backend.searchRoomTopics(
    roomId: PrimaryKey,
    search: CustomApi.Topics.Rooms.Id.RoomTopicSearchQuery,
    primaryKeyFetch: OffsetFetch,
    uid: PrimaryKey?,
): Result<PaginationResult<TopicInfo>?> {
    if (search.fillHasCommented == true && uid == null) {
        return Result.failure(UnauthorizedException())
    }
    val word = search.word.trim()
    if (word.isBlank()) {
        return Result.success(PaginationResult(emptyList(), 0))
    }
    if (word.length > 20) {
        return Result.failure(CustomBadRequestException("word too long"))
    }
    return checkRoomReadPermission(roomId, uid).mapResultIfNotNull {
        if (it.isPrivate) {
            Result.failure(CustomBadRequestException("can't search in private room"))
        } else {
            Result.success(Unit)
        }
    }.mapResultIfNotNull {
        // 创建TopicDocumentSearch.Topics对象进行房间主题搜索
        topicSearchService.searchDocument(
            TopicDocumentSearch.Topics(roomId, word, fetch = primaryKeyFetch)
        )
    }.mapPagingResultIfNotNullNullable { list ->
        processTopicsDocument(uid, list)
    }
}

/**
 * 搜索社区主题
 */
suspend fun Backend.searchCommunityTopics(
    communityId: PrimaryKey,
    search: CustomApi.Topics.Communities.Id.CommunityTopicSearchQuery,
    primaryKeyFetch: OffsetFetch,
    uid: PrimaryKey?,
): Result<PaginationResult<TopicInfo>?> {
    if (search.fillHasCommented == true && uid == null) {
        return Result.failure(UnauthorizedException())
    }
    val word = search.word.trim()
    if (word.isBlank()) {
        return Result.success(PaginationResult(emptyList(), 0))
    }
    if (word.length > 20) {
        return Result.failure(CustomBadRequestException("word too long"))
    }
    return checkCommunityReadPermission(communityId, uid).mapResultIfNotNull {
        if (it.isPrivate) {
            Result.failure(CustomBadRequestException("can't search in private community"))
        } else {
            Result.success(Unit)
        }
    }.mapResultIfNotNull {
        // 创建TopicDocumentSearch.Topics对象进行社区主题搜索
        topicSearchService.searchDocument(
            TopicDocumentSearch.Topics(communityId, word, fetch = primaryKeyFetch)
        )
    }.mapPagingResultIfNotNullNullable { list ->
        processTopicsDocument(uid, list)
    }
}

suspend fun Backend.processTopicFileObject(
    infos: List<TopicInfo>,
): Result<List<TopicInfo>?> {
    // id + mediaLink，此处的mediaLink 应该包含前缀，内部会自动添加前缀
    val fileList = documentFileList(infos).filter {
        val temp = File(it.second)
        temp.canonicalPath == temp.absolutePath
    }
    // 所有用到的media
    val fileNameList = fileList.map {
        it.second
    }.distinct()
    // 根据topicId 保存的mediaName 的map
    val fileMap = fileList.groupByPair()
    return getFileInfoList(fileNameList).mapIfNotNull { fileInfos ->
        val mediaInfoMap = fileInfos.filterNotNull().associateBy { it.fullName }
        infos.map { info ->
            val content = info.content
            if (content is TopicContent.Plain) {
                val list = fileMap[info.id]?.mapNotNull {
                    mediaInfoMap[it]
                }.orEmpty()
                info.copy(content = content.copy(fileInfos = list))
            } else {
                info
            }
        }
    }
}

fun documentFileList(documentList: List<TopicInfo>): List<Pair<PrimaryKey, String>> {
    return documentList.flatMap { document ->
        val markdownText = document.content
        if (markdownText is TopicContent.Plain) {
            val mediaLinks = extractMarkdownMediaLink(markdownText.plain)
            mediaLinks.map {
                val prefix = document.author
                document.id to "$prefix/$it"
            }
        } else {
            emptyList()
        }
    }.distinct()
}

suspend fun Backend.recommendTopics(
    uid: PrimaryKey?,
    primaryKeyFetch: OffsetFetch,
    q: CustomApi.Topics.RecommendQuery
): Result<PaginationResult<TopicInfo>?> {
    if (uid == null && q.fillHasCommented == true) {
        return Result.failure(UnauthorizedException())
    }
    return if (uid != null) {
        database.community.getJoinedCommunityIds(uid).mapResult {
            topicSearchService.searchDocument(
                topicDocumentSearch = TopicDocumentSearch.Recommend(uid, it, fetch = primaryKeyFetch)
            )
        }
    } else {
        topicSearchService.searchDocument(
            topicDocumentSearch = TopicDocumentSearch.RecommendNotLogin(fetch = primaryKeyFetch)
        )
    }.mapPagingResultNullable { list ->
        processTopicsDocument(uid, list)
    }
}

private suspend fun Backend.processTopicsDocument(
    uid: PrimaryKey?,
    list: List<TopicDocument>,
): Result<List<TopicInfo>?> {
    val ids = list.map {
        it.id
    }
    if (ids.isEmpty()) {
        return Result.success(emptyList())
    }
    return database.getRawTopicListByIds(uid, ids).mapResult { infos ->
        val processedTopics = fixedSort(infos, ids) {
            it.topic.id
        }
        processRawTopicToTopicInfo(processedTopics, uid, addLatestSubTopic = true)
    }
}

suspend fun Backend.getTopicByIds(
    ids: List<PrimaryKey>,
    uid: PrimaryKey?,
): Result<List<TopicInfo>?> {
    if (ids.isEmpty()) {
        return Result.success(emptyList())
    }
    try {
        ids.forEach {
            val permission = checkTopicReadPermission(it, uid).getOrThrow()
            if (permission == null || !permission.hasRead) {
                return Result.failure(ForbiddenException("Permission Denied"))
            }
        }
    } catch (e: Exception) {
        return Result.failure(e)
    }

    return database.getRawTopicListByIds(uid, ids).mapResult { infos ->
        processRawTopicToTopicInfo(infos, uid, true)
    }
}

suspend fun Backend.updateTopicPin(
    uid: PrimaryKey,
    topicId: PrimaryKey,
    newValue: Boolean,
) = checkTopicAdminPermission(topicId, uid).mapResultIfNotNull {
    database.getRawTopic(ObjectFetch.IdFetch(topicId), uid)
}.mapResultIfNotNull { rawTopic ->
    val topicInfo = rawTopic.toTopicInfo()
    if (rawTopic.topic.isPin == newValue) {
        Result.success(topicInfo)
    } else {
        database.topic.updateTopicStatus(topicId, newValue).map { isSuccess ->
            if (isSuccess) {
                topicInfo.copy(isPin = newValue)
            } else {
                topicInfo
            }
        }
    }
}

suspend fun Backend.getAllTopics(primaryKeyFetch: PrimaryKeyFetch) =
    database.getAllRawTopics(primaryKeyFetch).mapResult { (topics, total) ->
        processRawTopicToTopicInfo(topics, uid = null, addLatestSubTopic = true).paging(total)
    }
