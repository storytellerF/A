package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.backend.core.*
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.a.backend.core.types.toTopicInfo
import com.storyteller_f.a.backend.service.*
import com.storyteller_f.a.backend.service.index.DocumentSearch
import com.storyteller_f.a.backend.service.index.TopicDocument
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.*
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

suspend fun Backend.createPublicTopic(
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
    ).mapResultIfNotNull { (rootType, rootId, hasWrite) ->
        when {
            rootType == ObjectType.ROOM -> {
                Result.failure(ForbiddenException("can't use api to add topic in room"))
            }

            hasWrite -> {
                val newId = SnowflakeFactory.nextId()
                val topic = Topic(
                    id = newId,
                    createdTime = now(),
                    author = uid,
                    parentId = newTopic.parentId,
                    parentType = newTopic.parentType,
                    rootId = rootId,
                    rootType = rootType,
                    content.encodeToByteArray(),
                    false,
                    lastModifiedTime = null,
                )
                val plain = TopicContent.Plain(content)

                topicSearchService.saveDocument(
                    listOf(TopicDocument.Companion.fromTopic(topic, plain))
                ).getOrThrow()
                exposedDatabase.topicDatabase.savePlainTopic(topic, plain).map {
                    topic.toTopicInfo(content = plain)
                }.mapResult { topicInfo ->
                    addUserLog(uid, UserLogType.CREATE, topicInfo.tuple())
                    processTopicAfterCreate(topicInfo, uid)
                }
            }

            else -> {
                Result.failure(ForbiddenException("Permission denied."))
            }
        }
    }
}

suspend fun Backend.processTopicAfterCreate(
    topicInfo: TopicInfo,
    uid: PrimaryKey
): Result<TopicInfo?> = merge({
    val content = topicInfo.content
    if (content is TopicContent.Plain) {
        processTopicMedia(
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
    return getUserInfo(ObjectFetch.IdFetch(uid)).mapResultIfNotNull { userInfo ->
        checkRootReadPermission(
            ObjectType.TOPIC,
            topicId,
            uid
        ).mapResultIfNotNull { (hasRead, _, isPrivate) ->
            if (hasRead && !isPrivate) {
                exposedDatabase.topicDatabase.getTopicInfo(
                    ObjectFetch.IdFetch(topicId),
                    null
                ).mapResultIfNotNull { value ->
                    createTopicSnapshot(value, userInfo, uid)
                }
            } else {
                Result.failure(ForbiddenException("Permission denied"))
            }
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
                uploadFiles(
                    listOf(
                        UploadPack(
                            pdfFile,
                            "$topicId.pdf",
                            uid,
                            ObjectType.USER,
                            pdfFile.length(),
                            "application/pdf",
                            null,
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
            exposedDatabase.topicDatabase.getTopicInfo(
                ObjectFetch.IdFetch(topicId),
                uid
            ).mapResultIfNotNull { info ->
                processTopicAfterGet(listOf(info), uid, true).mapIfNotNull {
                    it.first()
                }
            }.mapIfNotNull { value ->
                value.copy(hasJoined = hasJoined)
            }
        } else {
            Result.failure(ForbiddenException("Permission Denied"))
        }
    }
}

suspend fun Backend.getTopicByAid(
    aid: String,
    uid: PrimaryKey?,
    fillHasCommented: Boolean?,
): Result<TopicInfo?> {
    if (uid == null && fillHasCommented == true) return Result.failure(UnauthorizedException())
    return exposedDatabase.topicDatabase.getTopicInfo(ObjectFetch.AidFetch(aid), uid)
        .mapResultIfNotNull { info ->
            checkRootReadPermission(
                ObjectType.TOPIC,
                info.id,
                uid
            ).mapResultIfNotNull { (hasRead, hasJoined) ->
                if (hasRead) {
                    processTopicAfterGet(listOf(info), uid, true).mapIfNotNull {
                        it.first()
                    }
                } else {
                    Result.failure(ForbiddenException("Permission Denied"))
                }.mapIfNotNull { value ->
                    value.copy(hasJoined = hasJoined)
                }
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
            exposedDatabase.topicDatabase.getSubTopicInfo(
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
                    exposedDatabase.topicDatabase.getLatestTopicInfo(uid, t.id).getOrThrow()
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
            exposedDatabase.topicDatabase.getReactionInfoPaginationResult(processedTopics.map {
                it.id
            }, uid, ReactionFetch(null, 20)).map {
                it.list
            }.map {
                it.groupBy(ReactionInfo::objectId)
            }
        }
    ).mapResultIfNotNull { (topics, reactionMap) ->
        processTopicMedia(topics).mapIfNotNull {
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

suspend fun Backend.processTopicMedia(
    infos: List<TopicInfo>,
): Result<List<TopicInfo>?> {
    // id + mediaLink，此处的mediaLink 应该包含前缀，内部会自动添加前缀
    val mediaList = documentMediaList(infos).filter {
        val temp = File(it.second)
        temp.canonicalPath == temp.absolutePath
    }
    // 所有用到的media
    val mediaNameList = mediaList.map {
        it.second
    }.distinct()
    // 根据topicId 保存的mediaName 的map
    val mediaMap = mediaList.groupBy {
        it.first
    }
    return getMediaInfoList(mediaNameList).mapIfNotNull { mediaUrls ->
        val mediaInfoMap = mediaUrls.filterNotNull().mapIndexed { index, url ->
            mediaNameList[index] to url
        }.associate {
            it.first to it.second
        }
        infos.map { info ->
            val content = info.content
            if (content is TopicContent.Plain) {
                val m = mediaMap[info.id]?.mapNotNull {
                    val mediaName = it.second
                    mediaInfoMap[mediaName]
                }.orEmpty()
                info.copy(content = content.copy(list = m))
            } else {
                info
            }
        }
    }
}

fun documentMediaList(documentList: List<TopicInfo>): List<Pair<PrimaryKey, String>> {
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
        exposedDatabase.communityDatabase.getJoinedCommunityIds(uid).mapResult {
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
    return exposedDatabase.topicDatabase.getTopicInfoListByIds(uid, ids).mapResult { infos ->
        processTopicAfterGet(infos, uid, addLatestSubTopic = true)
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
    return exposedDatabase.topicDatabase.getTopicInfoListByIds(uid, ids).mapResult { infos ->
        processTopicAfterGet(infos, uid, true)
    }
}

suspend fun Backend.updateTopicPin(
    uid: PrimaryKey,
    topicId: PrimaryKey,
    newValue: Boolean,
) =
    checkRootAdminPermission(ObjectType.TOPIC, topicId, uid).mapResultIfNotNull {
        if (it.hasAdmin) {
            exposedDatabase.topicDatabase.getTopicInfo(
                ObjectFetch.IdFetch(topicId),
                uid
            ).mapResultIfNotNull { info ->
                if (info.isPin == newValue) {
                    Result.success(info)
                } else {
                    exposedDatabase.topicDatabase.updateTopicStatus(topicId, newValue)
                        .map { isSuccess ->
                            if (isSuccess) {
                                info.copy(isPin = newValue)
                            } else {
                                info
                            }
                        }
                }
            }
        } else {
            Result.failure(CustomBadRequestException("Permission Denied"))
        }
    }
