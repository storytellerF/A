package com.storyteller_f.a.server.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.*
import com.storyteller_f.a.server.auth.addUserLog
import com.storyteller_f.a.server.route.RouteTopics
import com.storyteller_f.index.DocumentSearch
import com.storyteller_f.index.TopicDocument
import com.storyteller_f.media.UploadPack
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TopicPinSearch
import com.storyteller_f.shared.type.TopicPinSearch.PINNED
import com.storyteller_f.shared.type.TopicPinSearch.UNPINNED
import com.storyteller_f.shared.type.UnauthorizedException
import com.storyteller_f.shared.utils.*
import com.storyteller_f.tables.*
import com.storyteller_f.tables.ObjectFetch.*
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.PagingFetch
import io.ktor.http.ContentType
import io.ktor.server.plugins.*
import org.apache.pdfbox.examples.signature.CreateSignature
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.FontMappers
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.jetbrains.exposed.sql.and
import rst.pdfbox.layout.elements.Document
import rst.pdfbox.layout.elements.Paragraph
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore

suspend fun Backend.createPublicTopic(
    uid: PrimaryKey,
    newTopic: NewTopic
): Result<TopicInfo?> {
    if (newTopic.parentType == ObjectType.ROOM) {
        return Result.failure(ForbiddenException("can't use api to add topic in room"))
    }

    val content = newTopic.content.trim()

    if (content.isEmpty()) {
        return Result.failure(CustomBadRequestException("content is empty"))
    } else if (!checkContent(content)) {
        return Result.failure(CustomBadRequestException("invalid"))
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
                    false,
                    lastModifiedTime = null,
                )
                val plain = TopicContent.Plain(content)
                savePlainTopic(topic, plain).mapResult { topicInfo ->
                    this.addUserLog(uid, UserLogType.CREATE, topicInfo.tuple())
                    processTopicMedia(
                        listOf(topicInfo),
                        listOf(TopicDocument.fromTopic(topic, plain))
                    ).mapIfNotNull {
                        it.firstOrNull()
                    }
                }
            }

            else -> {
                Result.failure(ForbiddenException("Permission denied."))
            }
        }
    }
}

suspend fun Backend.createTopicSnapshot(
    uid: PrimaryKey,
    topicId: PrimaryKey
): Result<MediaInfo?> {
    return getRawUser(uid).mapResultIfNotNull { (first) ->
        checkRootReadPermission(ObjectType.TOPIC, topicId, uid).mapResultIfNotNull { (hasRead) ->
            if (hasRead) {
                getDirectTopic(topicId).mapResultIfNotNull { value ->
                    createTopicSnapshot(value, first, uid)
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
    uid: PrimaryKey
): Result<MediaInfo?> {
    val topicId = topicInfo.id
    return topicSearchService.getDocuments(listOf(topicId)).map { value -> value.firstOrNull() }
        .mapResultIfNotNull { documents ->
            getRawUser(topicInfo.author).mapResultIfNotNull { (first) ->
                val name = "$uid/$topicId.pdf"
                val pdfFile = File("/tmp/$name")
                val signedFile = File("/tmp/${pdfFile.nameWithoutExtension}_signed.pdf")
                try {
                    generateSignedSnapshot(
                        first,
                        creatorInfo,
                        topicInfo,
                        pdfFile,
                        signedFile,
                        documents.content,
                        snapshotVerify
                    ).mapResultIfNotNull {
                        uploadFiles(
                            listOf(
                                UploadPack(
                                    pdfFile,
                                    "$topicId.pdf",
                                    uid,
                                    pdfFile.length(),
                                    ContentType.Application.Pdf.contentType,
                                    null,
                                )
                            )
                        ).map {
                            pdfFile.delete()
                            it.firstOrNull()
                        }
                    }
                } finally {
                    pdfFile.delete()
                    signedFile.delete()
                }
            }
        }
}

fun generateSignedSnapshot(
    authorInfo: UserInfo,
    creatorInfo: UserInfo,
    topicInfo: TopicInfo,
    saveToFile: File,
    signedFile: File,
    content: String,
    snapshot: Pair<String, String>
): Result<Unit?> {
    val parent = saveToFile.parentFile
    return if (parent != null) {
        if (!parent.exists() && !parent.mkdirs()) {
            Result.failure(Exception("failed"))
        } else {
            generateSnapshot(authorInfo, creatorInfo, topicInfo, saveToFile, content)
            runCatching {
                val keyStorePath = snapshot.first
                val password = snapshot.second
                if (keyStorePath.isNotEmpty() && password.isNotEmpty()) {
                    val store = KeyStore.getInstance("PKCS12").apply {
                        load(FileInputStream(keyStorePath), password.toCharArray())
                    }
                    CreateSignature(store, password.toCharArray())
                        .signDetached(saveToFile, signedFile, "https://freetsa.org/tsr")
                }
            }
        }
    } else {
        Result.success(null)
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
            addText("capture by ${if (creatorInfo.aid == null) creatorInfo.address else creatorInfo.aid}", 14f, font)
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
    content: String
): PDType0Font? {
    val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val font = graphicsEnvironment.allFonts.firstOrNull {
        it.canDisplayUpTo(content) == -1
    }
    // 使用 PDFBox 加载字体
    return PDType0Font.load(document, FontMappers.instance().getTrueTypeFont(font?.name, null).font, true)
}

suspend fun Backend.getTopic(
    topicId: PrimaryKey,
    uid: PrimaryKey?,
    fillHasCommented: Boolean?
): Result<TopicInfo?> {
    if (uid == null && fillHasCommented == true) return Result.failure(UnauthorizedException())
    return checkRootReadPermission(
        ObjectType.TOPIC,
        topicId,
        uid
    ).mapResultIfNotNull { (hasRead, hasJoined, isPrivate) ->
        if (hasRead) {
            getTopicInfo(
                IdFetch(topicId),
                uid
            ).mapResultIfNotNull { info ->
                processTopicsContent(listOf(info), uid, isPrivate).mapIfNotNull {
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
    fillHasCommented: Boolean?
): Result<TopicInfo?> {
    if (uid == null && fillHasCommented == true) return Result.failure(UnauthorizedException())
    return getTopicInfo(AidFetch(aid), uid).mapResultIfNotNull { info ->
        checkRootReadPermission(
            ObjectType.TOPIC,
            info.id,
            uid
        ).mapResultIfNotNull { (hasRead, hasJoined, isPrivate) ->
            if (hasRead) {
                processTopicsContent(listOf(info), uid, isPrivate).mapIfNotNull {
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
    pagingFetch: PagingFetch,
    pinType: TopicPinSearch? = null,
): Result<PaginationResult<TopicInfo>?> {
    return checkRootReadPermission(
        parentType,
        parentId,
        uid
    ).mapResultIfNotNull { (hasRead, _, isPrivate) ->
        if (isPrivate && !hasRead) {
            Result.failure(ForbiddenException("Permission Denied"))
        } else {
            this.getTopicsPagingByPredicate(uid, fillHasCommented, pagingFetch) { ->
                val baseQuery = Topics.parentId eq parentId
                when (pinType) {
                    PINNED -> baseQuery and (Topics.pinned eq true)
                    UNPINNED -> baseQuery and (Topics.pinned eq false)
                    else -> baseQuery
                }
            }.mapResult { (data, count) ->
                processTopicsContent(data, uid, isPrivate).mapIfNotNull {
                    PaginationResult(it, count)
                }
            }
        }
    }
}

suspend fun Backend.processTopicExtension(
    processedTopics: List<TopicInfo>,
    uid: PrimaryKey?,
    isPrivate: Boolean,
    addLatestSubTopic: Boolean,
) = getUsersByIds(processedTopics.map {
    it.author
}.distinct()).mapResultIfNotNull { users ->
    val userMap = users.associateBy { it.id }
    if (addLatestSubTopic) {
        val subTopics = processedTopics.flatMap { t ->
            this.getTopicsByPredicate(uid, fillHasCommented = false, addPinOrder = true, addPagingQuery = {
                bindPaginationQuery(Topics, PagingFetch(null, null, 2))
            }) {
                Topics.parentId eq t.id
            }.getOrThrow()
        }
        if (subTopics.isEmpty()) {
            Result.success(emptyList())
        } else {
            processTopicsContent(subTopics, uid, isPrivate, false)
        }.mapIfNotNull { processedSubTopics ->
            processedSubTopics.groupBy {
                it.parentId
            }
        }
    } else {
        Result.success(emptyMap())
    }.mapResultIfNotNull { processedSubTopicMap ->
        commonReactions(uid, processedTopics.map {
            it.id
        }).map {
            it.groupBy {
                it.objectId
            }
        }.map { reactionMap ->
            processedTopics.map {
                val authorInfo = userMap[it.author] ?: throw CustomBadRequestException("author is null")
                it.copy(
                    extension = TopicInfo.Extension(
                        authorInfo,
                        subTopics = processedSubTopicMap[it.id],
                        reactions = reactionMap[it.id],
                    )
                )
            }
        }
    }
}

suspend fun Backend.processTopicsContent(
    data: List<TopicInfo>,
    uid: PrimaryKey?,
    isPrivate: Boolean,
    addLatestSubTopic: Boolean = true
): Result<List<TopicInfo>?> = when {
    !isPrivate -> topicSearchService.getDocuments(data.map {
        it.id
    }).mapResult { documents ->
        processTopicMedia(data, documents)
    }.map { topicContents ->
        topicContents
    }

    uid == null -> Result.failure(ForbiddenException())

    else -> getEncryptedTopicContents(data, uid).map { topicContents ->
        data.mapIndexed { index, l ->
            l.copy(content = topicContents[index], isPrivate = true)
        }
    }
}.mapResultIfNotNull {
    this.processTopicExtension(it, uid, isPrivate, addLatestSubTopic)
}

data class RootReadPermission(
    val hasRead: Boolean,
    val hasJoined: Boolean,
    val isPrivate: Boolean
)

data class RootWritePermission(
    val objectType: ObjectType,
    val objectId: PrimaryKey,
    val hasWrite: Boolean
)

data class RootAdminPermission(
    val objectType: ObjectType,
    val objectId: PrimaryKey,
    val hasAdmin: Boolean
)

suspend fun Backend.checkRootReadPermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey?,
): Result<RootReadPermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            getTopicRoot(parentId).mapResultIfNotNull { (rootId, rootType) ->
                this.checkRootReadPermission(rootType, rootId, uid)
            }
        }

        ObjectType.ROOM -> {
            getRoomCommunityId(parentId).mapResult { communityId ->
                if (communityId == null && uid == null) {
                    Result.failure(UnauthorizedException())
                } else {
                    this.isMemberJoined(parentId, uid).map { hasJoined ->
                        RootReadPermission(hasJoined || communityId != null, hasJoined, communityId == null)
                    }
                }
            }
        }

        ObjectType.COMMUNITY -> {
            checkCommunityExists(parentId).mapResultIfNotNull {
                this.isMemberJoined(parentId, uid).map { hasJoined ->
                    RootReadPermission(true, hasJoined, false)
                }
            }
        }

        ObjectType.USER -> checkUserExists(parentId).mapIfNotNull {
            RootReadPermission(hasRead = true, hasJoined = false, isPrivate = false)
        }

        ObjectType.TITLE -> Result.success(RootReadPermission(hasRead = true, hasJoined = false, isPrivate = false))
        ObjectType.MEDIA -> TODO()
    }
}

suspend fun Backend.checkRootWritePermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootWritePermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            getTopicRoot(parentId).mapResultIfNotNull { (rootId, rootType) ->
                this.checkRootWritePermission(rootType, rootId, uid)
            }
        }

        ObjectType.ROOM -> {
            getRoomCommunityId(parentId).mapResult {
                this.isMemberJoined(parentId, uid).map { hasJoined ->
                    RootWritePermission(parentType, parentId, hasJoined)
                }
            }
        }

        ObjectType.COMMUNITY -> {
            checkCommunityExists(parentId).mapResultIfNotNull {
                this.isMemberJoined(parentId, uid).map { hasJoined ->
                    RootWritePermission(parentType, parentId, hasJoined)
                }
            }
        }

        ObjectType.USER -> {
            if (uid == parentId) {
                checkUserExists(parentId).mapIfNotNull {
                    RootWritePermission(parentType, parentId, parentId == uid)
                }
            } else {
                Result.failure(ForbiddenException("Permission denied"))
            }
        }

        ObjectType.TITLE -> Result.success(RootWritePermission(parentType, parentId, false))
        ObjectType.MEDIA -> TODO()
    }
}

suspend fun Backend.checkRootAdminPermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootAdminPermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            getTopicRoot(parentId).mapResultIfNotNull { (rootId, rootType) ->
                this.checkRootAdminPermission(rootType, rootId, uid)
            }
        }

        ObjectType.ROOM -> {
            getRoom(IdFetch(parentId), true, uid).mapIfNotNull {
                RootAdminPermission(parentType, parentId, it.first.creator == uid)
            }
        }

        ObjectType.COMMUNITY -> {
            getCommunity(IdFetch(parentId)).mapIfNotNull {
                RootAdminPermission(parentType, parentId, it.communityInfo.owner == uid)
            }
        }

        ObjectType.USER -> {
            checkUserExists(parentId).mapIfNotNull {
                RootAdminPermission(parentType, parentId, parentId == uid)
            }
        }

        ObjectType.TITLE -> Result.success(RootAdminPermission(parentType, parentId, false))
        ObjectType.MEDIA -> TODO()
    }
}

suspend fun Backend.searchPublicTopics(
    search: RouteTopics.Search,
    pagingFetch: PagingFetch,
    uid: PrimaryKey?
): Result<PaginationResult<TopicInfo>?> {
    if (search.word != null && search.word.sumOf {
            it.length
        } > 20) {
        return Result.failure(CustomBadRequestException("word too long"))
    }
    return if (search.parentId != null && search.parentType != null) {
        this.checkRootReadPermission(search.parentType, search.parentId, uid).mapResultIfNotNull {
            if (it.isPrivate) {
                Result.failure(BadRequestException("can't search in private chat"))
            } else {
                Result.success(DocumentSearch.Topics(search.parentId))
            }
        }
    } else {
        Result.success(DocumentSearch.CommunityRoot)
    }.mapResultIfNotNull { documentSearch ->
        topicSearchService.searchDocument(
            search.word,
            documentSearch = documentSearch,
            pagingFetch
        ).mapResult { (list, total) ->
            processTopicsDocument(uid, search.parent.fillHasCommented, list).mapIfNotNull {
                PaginationResult(it, total)
            }
        }
    }
}

suspend fun Backend.processTopicMedia(
    infos: List<TopicInfo>,
    documentList: List<TopicDocument?>
): Result<List<TopicInfo>?> {
    val documentMap = documentList.filterNotNull().associateBy { it.id }
    // id + mediaLink，此处的mediaLink 应该包含前缀，内部会自动添加前缀
    val mediaList = documentMediaList(documentList).filter {
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
            documentMap[info.id]?.let { document ->
                val m = mediaMap[document.id]?.mapNotNull {
                    val mediaName = it.second
                    mediaInfoMap[mediaName]
                }.orEmpty()
                info.copy(content = TopicContent.Plain(document.content, m))
            } ?: info
        }
    }
}

fun documentMediaList(documentList: List<TopicDocument?>): List<Pair<PrimaryKey, String>> {
    return documentList.flatMap { document ->
        if (document != null) {
            val mediaLinks = extractMarkdownMediaLink(document.content)
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
    fillHasCommented: Boolean?,
    pagingFetch: PagingFetch
): Result<PaginationResult<TopicInfo>?> {
    return if (uid != null) {
        getJoinedCommunityIds(uid).mapResult {
            topicSearchService.searchDocument(
                documentSearch = DocumentSearch.Recommend(uid, it),
                pagingFetch = pagingFetch
            )
        }
    } else {
        topicSearchService.searchDocument(
            documentSearch = DocumentSearch.RecommendNotLogin,
            pagingFetch = pagingFetch
        )
    }.mapResult { (list, total) ->
        processTopicsDocument(uid, fillHasCommented, list).mapIfNotNull {
            PaginationResult(it, total)
        }
    }
}

private suspend fun Backend.processTopicsDocument(
    uid: PrimaryKey?,
    fillHasCommented: Boolean?,
    list: List<TopicDocument>,
): Result<List<TopicInfo>?> {
    val ids = list.map {
        it.id
    }
    if (ids.isEmpty()) {
        return Result.success(emptyList())
    }
    return this.getTopicsByPredicate(uid, fillHasCommented) {
        Topics.id inList ids
    }.mapResult { infos ->
        this.processTopicMedia(infos.sortedByDescending {
            it.id
        }, list).mapResultIfNotNull {
            this.processTopicExtension(it, uid, isPrivate = false, addLatestSubTopic = true)
        }
    }
}

suspend fun Backend.getTopicByIds(
    ids: List<PrimaryKey>,
    uid: PrimaryKey?,
    fillHasCommented: Boolean?
): Result<List<TopicInfo>?> {
    if (ids.isEmpty()) {
        return Result.success(emptyList())
    }
    val map = ids.map {
        this.checkRootReadPermission(ObjectType.TOPIC, it, uid) to it
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
    return this.getTopicsByPredicate(uid, fillHasCommented) {
        Topics.id inList ids
    }.mapResult { infos ->
        val privateList = infos.filter {
            private.contains(it.id)
        }

        val publicList = infos.filter {
            !private.contains(it.id)
        }
        this.processTopicsContent(privateList, uid, true).mapResultIfNotNull { privateContents ->
            this.processTopicsContent(publicList, uid, false).mapIfNotNull { publicContents ->
                publicContents + privateContents
            }
        }
    }
}

suspend fun Backend.updateTopicPin(
    uid: PrimaryKey,
    topicId: PrimaryKey,
    newValue: Boolean
) =
    this.checkRootAdminPermission(ObjectType.TOPIC, topicId, uid).mapResultIfNotNull {
        if (it.hasAdmin) {
            getTopicInfo(
                IdFetch(topicId),
                uid
            ).mapResultIfNotNull { info ->
                if (info.isPin == newValue) {
                    Result.success(info)
                } else {
                    updateTopicStatus(topicId, newValue).map { isSuccess ->
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
