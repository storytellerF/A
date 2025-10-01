package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.ReactionFetch
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.backend.core.fixedSort
import com.storyteller_f.a.backend.core.service.TopicDocument
import com.storyteller_f.a.backend.core.service.TopicDocumentSearch
import com.storyteller_f.a.backend.core.service.UploadPack
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.a.backend.core.types.toTopicInfo
import com.storyteller_f.a.cloud.pdf.PdfService
import com.storyteller_f.a.cloud.pdf.SnapshotVerify
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
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.UNIT_RESULT
import com.storyteller_f.shared.utils.checkContent
import com.storyteller_f.shared.utils.extractMarkdownMediaLink
import com.storyteller_f.shared.utils.groupByPair
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.merge
import com.storyteller_f.shared.utils.now
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
    }
}

private suspend fun Backend.savePlainTopic(
    uid: PrimaryKey,
    content: String,
    level: Int,
    parentTuple: ObjectTuple,
    rootTuple: ObjectTuple
): Result<TopicInfo?> {
    val newId = SnowflakeFactory.nextId()
    val topic = Topic(
        id = newId,
        createdTime = now(),
        author = uid,
        parentId = parentTuple.objectId,
        parentType = parentTuple.objectType,
        rootId = rootTuple.objectId,
        rootType = rootTuple.objectType,
        content.encodeToByteArray(),
        false,
        level + 1,
        lastModifiedTime = null,
    )
    val plain = TopicContent.Plain(content)
    val topicInfo = topic.toTopicInfo(content = plain)

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
    return combinedDatabase.topicDatabase.savePlainTopic(topic, plain).mapResult {
        addUserLog(uid, UserLogType.CREATE, topicInfo.tuple())
        processTopicAfterCreate(topicInfo, uid)
    }
}

suspend fun Backend.addTopicAtRoom(
    newTopic: NewRoomTopic,
    uid: PrimaryKey,
): Result<TopicInfo?> {
    if (newTopic.parentType != ObjectType.TOPIC && newTopic.parentType != ObjectType.ROOM) {
        return Result.failure(
            ForbiddenException()
        )
    }
    return checkRootWritePermission(
        newTopic.parentType,
        newTopic.parentId,
        uid
    ).mapResultIfNotNull {
        if (it.level >= 10) {
            Result.failure(CustomBadRequestException("not support"))
        } else {
            combinedDatabase.roomDatabase.checkRoomIsPrivate(it.rootId)
                .mapResultIfNotNull { isPrivate ->
                    createTopicAtRoom(uid, it, newTopic, isPrivate, newTopic.content)
                }
        }
    }
}

private suspend fun Backend.createTopicAtRoom(
    uid: PrimaryKey,
    permission: RootWritePermission,
    newTopic: NewRoomTopic,
    isPrivate: Boolean,
    content: TopicContent
) = if (isPrivate) {
    if (content is TopicContent.Encrypted) {
        saveEncryptedTopic(permission, content, uid, permission.tuple, newTopic.tuple)
    } else {
        Result.failure(ForbiddenException("Private room only accept encrypted content."))
    }
} else {
    if (content is TopicContent.Plain) {
        savePlainTopic(uid, content.plain, permission.level, newTopic.tuple, permission.tuple)
    } else {
        Result.failure(ForbiddenException("Public room only accept unencrypted content."))
    }
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
        combinedDatabase.topicDatabase.saveEncryptedTopic(topic, content)
    } else {
        Result.failure(ForbiddenException("Key not found ${content.encryptedKey.size}"))
    }
}.mapResultIfNotNull { topicInfo ->
    processTopicAfterCreate(topicInfo, uid)
}

suspend fun Backend.processTopicAfterCreate(
    topicInfo: TopicInfo,
    uid: PrimaryKey
) = merge({
    val content = topicInfo.content
    if (content is TopicContent.Plain) {
        processTopicFileObject(
            listOf(topicInfo)
        ).mapIfNotNull {
            it.firstOrNull()
        }
    } else {
        Result.success(topicInfo)
    }
}, {
    getUserInfo(ObjectFetch.IdFetch(uid))
}).map {
    val authorInfo = it.second
    if (authorInfo != null) {
        it.first?.copy(extension = TopicInfo.Extension(authorInfo = authorInfo))
    } else {
        it.first
    }
}

suspend fun Backend.createTopicSnapshot(
    uid: PrimaryKey,
    topicId: PrimaryKey,
): Result<FileInfo?> {
    return checkRootReadPermission(
        ObjectType.TOPIC,
        topicId,
        uid
    ).mapResultIfNotNull { (hasRead, _, isPrivate) ->
        if (hasRead && !isPrivate) {
            combinedDatabase.topicDatabase.getTopicInfo(
                ObjectFetch.IdFetch(topicId),
                null
            )
        } else {
            Result.failure(ForbiddenException("Permission denied"))
        }
    }.mapResultIfNotNull {
        processTopicAfterGet(listOf(it), uid, false).map { list ->
            list?.firstOrNull()
        }
    }.mapResultIfNotNull { topicInfo ->
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
    val signedFile =
        File(System.getProperty("java.io.tmpdir"), "${pdfFile.nameWithoutExtension}_signed.pdf")
    return try {
        getUserInfo(
            ObjectFetch.IdFetch(topicInfo.author)
        ).mapResultIfNotNull { userInfo ->
            generateSignedSnapshot(
                userInfo,
                creatorInfo,
                topicInfo,
                customConfig.snapshotKeyStore?.let {
                    SnapshotVerify.KeyStoreVerify(it.path, it.pass, pdfFile, signedFile)
                } ?: SnapshotVerify.NoneVerify(pdfFile)
            )
        }.mapResultIfNotNull {
            tryUploadFiles(
                uid,
                ObjectType.USER,
                listOf(
                    UploadPack(
                        pdfFile,
                        "$topicId.pdf",
                        pdfFile.length(),
                        "$uid/$topicId.pdf"
                    )
                )
            )
        }.mapIfNotNull {
            it.firstOrNull()
        }
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
    snapshotVerify: SnapshotVerify,
): Result<Unit?> {
    val content = topicInfo.content
    if (content !is TopicContent.Plain) {
        return Result.failure(CustomBadRequestException("unsupported"))
    }
    val saveToFile = snapshotVerify.path
    val parent = saveToFile.parentFile
    if (parent == null) return Result.success(null)
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
            map.put(it.name, targetFile)
            objectStorageService.getInputStream(A_FILE_DEFAULT_BUCKET, it.fullName).getOrThrow()
                .buffered().use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
        }
        pdfService.generateSignedSnapshot(
            creatorInfo,
            authorInfo,
            plainContent,
            topicInfo,
            map,
            snapshotVerify
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
    return checkRootReadPermission(
        ObjectType.TOPIC,
        topicId,
        uid
    ).mapResultIfNotNull { (hasRead, hasJoined) ->
        if (hasRead) {
            combinedDatabase.topicDatabase.getTopicInfo(
                ObjectFetch.IdFetch(topicId),
                uid
            ).mapIfNotNull { value ->
                value.copy(hasJoined = hasJoined)
            }
        } else {
            Result.failure(ForbiddenException("Permission Denied"))
        }
    }.mapResultIfNotNull { info ->
        processTopicAfterGet(listOf(info), uid, true).mapIfNotNull {
            it.first()
        }
    }
}

suspend fun Backend.getTopicByAid(
    aid: String,
    uid: PrimaryKey?,
    fillHasCommented: Boolean?,
): Result<TopicInfo?> {
    if (uid == null && fillHasCommented == true) return Result.failure(UnauthorizedException())
    return combinedDatabase.topicDatabase.getTopicInfo(ObjectFetch.AidFetch(aid), uid)
        .mapResultIfNotNull { info ->
            checkRootReadPermission(
                ObjectType.TOPIC,
                info.id,
                uid
            ).mapResultIfNotNull { (hasRead, hasJoined) ->
                if (hasRead) {
                    Result.success(info.copy(hasJoined = hasJoined))
                } else {
                    Result.failure(ForbiddenException("Permission Denied"))
                }
            }
        }.mapResultIfNotNull { info ->
            processTopicAfterGet(listOf(info), uid, true).mapIfNotNull {
                it.first()
            }
        }
}

suspend fun Backend.getTopLevelTopicsInObject(
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
        combinedDatabase.topicDatabase.getSubTopicInfo(uid, primaryKeyFetch, parentId, pinType)
    }.mapResultIfNotNull { (data, count) ->
        processTopicAfterGet(data, uid, true).mapIfNotNull {
            PaginationResult(it, count)
        }
    }
}

suspend fun Backend.processTopicAfterGet(
    topics: List<TopicInfo>,
    uid: PrimaryKey?,
    addLatestSubTopic: Boolean,
): Result<List<TopicInfo>?> {
    return runCatching {
        val rooms = getRoomMapByTopics(topics)
        val subTopicsMap = if (addLatestSubTopic) {
            topics.flatMap { t ->
                combinedDatabase.topicDatabase.getLatestTopicInfo(uid, t.id).getOrThrow()
            }.groupBy {
                it.parentId
            }
        } else {
            emptyMap()
        }

        val uidList = topics.map {
            it.author
        } + subTopicsMap.flatMap {
            it.value
        }.map {
            it.author
        }.distinct()
        val userMap =
            getUserInfoList(ObjectListFetch.IdListFetch(uidList)).getOrThrow().associateBy { it.id }
        val processedSubTopic = subTopicsMap.mapValues {
            it.value.map { subTopic ->
                subTopic.copy(
                    extension = TopicInfo.Extension(
                        authorInfo = userMap[subTopic.author]
                    )
                )
            }
        }
        val reactionMap =
            combinedDatabase.topicDatabase.getReactionInfoPaginationResult(topics.map {
                it.id
            }, uid, ReactionFetch(null, 20)).map {
                it.list
            }.map {
                it.groupBy(ReactionInfo::objectId)
            }.getOrThrow()
        topics.map {
            it.copy(
                extension = TopicInfo.Extension(
                    userMap[it.author],
                    subTopics = processedSubTopic[it.id]?.toImmutableList(),
                    reactions = reactionMap[it.id]?.toImmutableList(),
                    roomInfo = if (it.rootType == ObjectType.ROOM) rooms[it.rootId] else null
                )
            )
        }
    }.mapResultIfNotNull {
        processTopicFileObject(it)
    }
}

private suspend fun Backend.getRoomMapByTopics(topics: List<TopicInfo>): Map<PrimaryKey, RoomInfo> {
    val roomIds = topics.mapNotNull {
        if (it.rootType == ObjectType.ROOM) {
            it.rootId
        } else {
            null
        }
    }
    val rooms = processRawRoomToRoomInfo(
        combinedDatabase.roomDatabase.getRawRooms(ObjectListFetch.IdListFetch(roomIds))
            .getOrThrow()
    ).getOrThrow().associateBy {
        it.id
    }
    return rooms
}

suspend fun Backend.searchPublicTopics(
    search: CustomApi.Topics.TopicSearchQuery,
    primaryKeyFetch: PrimaryKeyFetch,
    uid: PrimaryKey?,
): Result<PaginationResult<TopicInfo>?> {
    if (search.fillHasCommented == true && uid == null) return Result.failure(UnauthorizedException())
    val word = search.word
    if (word != null && word.sumOf {
            it.length
        } > 20) {
        return Result.failure(CustomBadRequestException("word too long"))
    }
    val parentId = search.parentId
    val parentType = search.parentType
    return if (parentId != null && parentType != null) {
        checkRootReadPermission(parentType, parentId, uid).mapResultIfNotNull {
            if (it.isPrivate) {
                Result.failure(CustomBadRequestException("can't search in private chat"))
            } else {
                Result.success(TopicDocumentSearch.Topics(parentId, word))
            }
        }
    } else {
        Result.success(TopicDocumentSearch.CommunityRoot(word))
    }.mapResultIfNotNull { documentSearch ->
        topicSearchService.searchDocument(
            topicDocumentSearch = documentSearch,
            primaryKeyFetch
        )
    }.mapResultIfNotNull { (list, total) ->
        processTopicsDocument(uid, list).mapIfNotNull {
            PaginationResult(it, total)
        }
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
    primaryKeyFetch: PrimaryKeyFetch,
): Result<PaginationResult<TopicInfo>?> {
    return if (uid != null) {
        combinedDatabase.communityDatabase.getJoinedCommunityIds(uid).mapResult {
            topicSearchService.searchDocument(
                topicDocumentSearch = TopicDocumentSearch.Recommend(uid, it),
                primaryKeyFetch = primaryKeyFetch
            )
        }
    } else {
        topicSearchService.searchDocument(
            topicDocumentSearch = TopicDocumentSearch.RecommendNotLogin,
            primaryKeyFetch = primaryKeyFetch
        )
    }.mapResult { (list, total) ->
        processTopicsDocument(uid, list).mapIfNotNull {
            PaginationResult(it, total)
        }
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
    return combinedDatabase.topicDatabase.getTopicInfoListByIds(uid, ids).mapResult { infos ->
        val processedTopics = fixedSort(infos, ids)
        processTopicAfterGet(processedTopics, uid, addLatestSubTopic = true)
    }
}

suspend fun Backend.getTopicByIds(
    ids: List<PrimaryKey>,
    uid: PrimaryKey?,
): Result<List<TopicInfo>?> {
    if (ids.isEmpty()) {
        return Result.success(emptyList())
    }
    val map = ids.map {
        checkRootReadPermission(ObjectType.TOPIC, it, uid) to it
    }
    val private = mutableSetOf<PrimaryKey>()
    map.forEach { (r, id) ->
        val exceptionOrNull = r.exceptionOrNull()
        if (exceptionOrNull != null) {
            return Result.failure(exceptionOrNull)
        } else {
            val permission = r.getOrThrow()
            when {
                permission == null -> return Result.failure(Exception("root not exists"))
                !permission.hasRead -> return Result.failure(ForbiddenException("Permission Denied"))
                permission.isPrivate -> private.add(id)
                else -> {}
            }
        }
    }
    return combinedDatabase.topicDatabase.getTopicInfoListByIds(uid, ids).mapResult { infos ->
        processTopicAfterGet(infos, uid, true)
    }
}

suspend fun Backend.updateTopicPin(
    uid: PrimaryKey,
    topicId: PrimaryKey,
    newValue: Boolean,
) =
    checkRootAdminPermission(ObjectType.TOPIC, topicId, uid).mapResultIfNotNull {
        combinedDatabase.topicDatabase.getTopicInfo(
            ObjectFetch.IdFetch(topicId),
            uid
        )
    }.mapResultIfNotNull { info ->
        if (info.isPin == newValue) {
            Result.success(info)
        } else {
            combinedDatabase.topicDatabase.updateTopicStatus(topicId, newValue)
                .map { isSuccess ->
                    if (isSuccess) {
                        info.copy(isPin = newValue)
                    } else {
                        info
                    }
                }
        }
    }
