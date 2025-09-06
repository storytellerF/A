package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.backend.core.*
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.a.backend.core.types.toTopicInfo
import com.storyteller_f.a.backend.service.*
import com.storyteller_f.a.backend.service.index.DocumentSearch
import com.storyteller_f.a.backend.service.index.TopicDocument
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.obj.NewRoomTopic
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.*
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import io.github.aakira.napier.Napier
import kotlinx.collections.immutable.toImmutableList
import org.apache.pdfbox.examples.signature.CreateSignature
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.FontMappers
import org.apache.pdfbox.pdmodel.font.PDType0Font
import rst.pdfbox.layout.elements.Document
import rst.pdfbox.layout.elements.Paragraph
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import kotlin.collections.map
import kotlin.collections.sumOf

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
    if (!checkContent(content)) {
        return Result.failure(CustomBadRequestException("invalid"))
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
            ).mapResultIfNotNull { value ->
                getUserInfo(ObjectFetch.IdFetch(uid)).mapResultIfNotNull { userInfo ->
                    createTopicSnapshot(value, userInfo, uid)
                }
            }
        } else {
            Result.failure(ForbiddenException("Permission denied"))
        }
    }
}

private suspend fun Backend.createTopicSnapshot(
    topicInfo: TopicInfo,
    creatorInfo: UserInfo,
    uid: PrimaryKey,
): Result<FileInfo?> {
    val topicId = topicInfo.id
    return getUserInfo(
        ObjectFetch.IdFetch(topicInfo.author)
    ).mapResultIfNotNull { userInfo ->
        val name = "$uid/$topicId.pdf"
        val pdfFile = File("/tmp/$name")
        val signedFile = File("/tmp/${pdfFile.nameWithoutExtension}_signed.pdf")
        try {
            generateSignedSnapshot(
                userInfo,
                creatorInfo,
                topicInfo,
                customConfig.snapshotKeyStore?.let {
                    SnapshotVerify.KeyStoreVerify(it.path, it.pass, pdfFile, signedFile)
                } ?: SnapshotVerify.NoneVerify(pdfFile)
            ).mapResultIfNotNull {
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
                ).map {
                    it.firstOrNull()
                }
            }
        } finally {
            pdfFile.delete()
            signedFile.delete()
        }
    }
}

sealed class SnapshotVerify(open val path: File) {
    class KeyStoreVerify(
        val keyStorePath: String,
        val password: String,
        override val path: File,
        val signedFile: File,
    ) : SnapshotVerify(path)

    class NoneVerify(override val path: File) : SnapshotVerify(path)
}

fun generateSignedSnapshot(
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
    return runCatching {
        generateSnapshot(authorInfo, creatorInfo, topicInfo, saveToFile, content.plain)
    }.map {
        when (snapshotVerify) {
            is SnapshotVerify.KeyStoreVerify -> {
                val password = snapshotVerify.password
                val store = KeyStore.getInstance("PKCS12").apply {
                    load(FileInputStream(snapshotVerify.keyStorePath), password.toCharArray())
                }
                CreateSignature(store, password.toCharArray())
                    .signDetached(saveToFile, snapshotVerify.signedFile, "https://freetsa.org/tsr")
            }

            is SnapshotVerify.NoneVerify -> Unit
        }
    }
}

private fun generateSnapshot(
    authorInfo: UserInfo,
    creatorInfo: UserInfo,
    topicInfo: TopicInfo,
    saveToFile: File,
    content: String,
) {
    Document().apply {
        val font = loadSystemFont(pdDocument, content)
        add(Paragraph().apply {
            addText(
                "pub by ${if (authorInfo.aid == null) authorInfo.address else authorInfo.aid}",
                14f,
                font
            )
        })
        add(Paragraph().apply {
            addText("pub at ${topicInfo.createdTime}", 14f, font)
        })
        add(Paragraph().apply {
            addText(
                "capture by ${if (creatorInfo.aid == null) creatorInfo.address else creatorInfo.aid}",
                14f,
                font
            )
        })
        add(Paragraph().apply {
            addText("capture at ${now()}", 14f, font)
        })
        add(Paragraph().apply {
            addText(content, 14f, font)
        })
        save(saveToFile)
    }
}

private fun loadSystemFont(
    document: PDDocument,
    content: String,
): PDType0Font? {
    val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val font = graphicsEnvironment.allFonts.firstOrNull {
        it.canDisplayUpTo(content) == -1
    }
    // 使用 PDFBox 加载字体
    return PDType0Font.load(
        document,
        FontMappers.instance().getTrueTypeFont(font?.name, null).font,
        true
    )
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
        combinedDatabase.topicDatabase.getSubTopicInfo(
            uid,
            primaryKeyFetch,
            parentId,
            pinType
        ).mapResult { (data, count) ->
            processTopicAfterGet(data, uid, true).mapIfNotNull {
                PaginationResult(it, count)
            }
        }
    }
}

suspend fun Backend.processTopicAfterGet(
    processedTopics: List<TopicInfo>,
    uid: PrimaryKey?,
    addLatestSubTopic: Boolean,
): Result<List<TopicInfo>?> {
    return merge(
        {
            (if (addLatestSubTopic) {
                Result.success(processedTopics.flatMap { t ->
                    combinedDatabase.topicDatabase.getLatestTopicInfo(uid, t.id).getOrThrow()
                }.groupBy {
                    it.parentId
                })
            } else {
                Result.success(emptyMap())
            }).mapResult { subTopicsMap ->
                val uidList = processedTopics.map {
                    it.author
                } + subTopicsMap.flatMap {
                    it.value
                }.map {
                    it.author
                }.distinct()
                getUserInfoList(ObjectListFetch.IdListFetch(uidList)).map { users ->
                    mergeAuthorInfoAndSubTopics(users, processedTopics, subTopicsMap)
                }
            }
        },
        {
            combinedDatabase.topicDatabase.getReactionInfoPaginationResult(processedTopics.map {
                it.id
            }, uid, ReactionFetch(null, 20)).map {
                it.list
            }.map {
                it.groupBy(ReactionInfo::objectId)
            }
        }
    ).mapResultIfNotNull { (topics, reactionMap) ->
        processTopicFileObject(topics).mapIfNotNull {
            it.map { topic ->
                topic.copy(extension = topic.extension?.copy(reactions = reactionMap[topic.id]?.toImmutableList()))
            }
        }
    }
}

private fun mergeAuthorInfoAndSubTopics(
    users: List<UserInfo>,
    processedTopics: List<TopicInfo>,
    subTopicsMap: Map<PrimaryKey, List<TopicInfo>>
): List<TopicInfo> {
    val userMap = users.associateBy { it.id }
    return processedTopics.map {
        val authorInfo =
            userMap[it.author] ?: throw CustomBadRequestException("author is null")
        val processedSubTopics = subTopicsMap[it.id]?.map { subTopic ->
            subTopic.copy(
                extension = TopicInfo.Extension(
                    authorInfo = userMap[subTopic.author]
                        ?: throw CustomBadRequestException("author is null")
                )
            )
        }?.toImmutableList()
        it.copy(
            extension = TopicInfo.Extension(
                authorInfo,
                subTopics = processedSubTopics
            )
        )
    }
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
                Result.success(DocumentSearch.Topics(parentId))
            }
        }
    } else {
        Result.success(DocumentSearch.CommunityRoot)
    }.mapResultIfNotNull { documentSearch ->
        topicSearchService.searchDocument(
            word,
            documentSearch = documentSearch,
            primaryKeyFetch
        ).mapResult { (list, total) ->
            processTopicsDocument(uid, list).mapIfNotNull {
                PaginationResult(it, total)
            }
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
                info.copy(content = content.copy(list = list))
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
                documentSearch = DocumentSearch.Recommend(uid, it),
                primaryKeyFetch = primaryKeyFetch
            )
        }
    } else {
        topicSearchService.searchDocument(
            documentSearch = DocumentSearch.RecommendNotLogin,
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
        val groupBy = infos.associateBy { it.id }
        processTopicAfterGet(ids.mapNotNull {
            groupBy[it]
        }, uid, addLatestSubTopic = true)
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
