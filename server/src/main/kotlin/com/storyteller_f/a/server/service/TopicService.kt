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
import com.storyteller_f.shared.obj.TopicPinSearch
import com.storyteller_f.shared.obj.TopicPinSearch.PINNED
import com.storyteller_f.shared.obj.TopicPinSearch.UNPINNED
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.*
import com.storyteller_f.tables.*
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.PagingFetch
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

suspend fun createPublicTopic(
    backend: Backend,
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
        backend,
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
                DatabaseFactory.savePlainTopic(backend, topic, plain).mapResult { topicInfo ->
                    addUserLog(backend, uid, UserLogType.CREATE, topicInfo.tuple())
                    processTopicMedia(
                        backend,
                        listOf(topicInfo),
                        listOf(TopicDocument.fromTopic(topic, plain))
                    ).map {
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

suspend fun createTopicSnapshot(
    backend: Backend,
    uid: PrimaryKey,
    topicId: PrimaryKey
): Result<MediaInfo?> {
    return DatabaseFactory.getRawUserById(backend, uid).mapResultIfNotNull { (first) ->
        checkRootReadPermission(backend, ObjectType.TOPIC, topicId, uid).mapResultIfNotNull { (hasRead) ->
            if (hasRead) {
                DatabaseFactory.getSimpleTopic(backend, topicId).mapResultIfNotNull { value ->
                    createTopicSnapshot(backend, value, first, uid)
                }
            } else {
                Result.failure(ForbiddenException("Permission denied"))
            }
        }
    }
}

private suspend fun createTopicSnapshot(
    backend: Backend,
    topicInfo: TopicInfo,
    creatorInfo: UserInfo,
    uid: PrimaryKey
): Result<MediaInfo?> {
    val topicId = topicInfo.id
    return backend.topicSearchService.getDocuments(listOf(topicId)).map { value -> value.firstOrNull() }
        .mapResultIfNotNull { documents ->
            DatabaseFactory.getRawUserById(backend, topicInfo.author).mapResultIfNotNull { (first) ->
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
                        backend.snapshotVerify
                    ).mapResultIfNotNull {
                        backend.mediaService.upload(
                            AMEDIA_DEFAULT_BUCKET,
                            listOf(UploadPack(name, pdfFile))
                        ).mapResult {
                            pdfFile.delete()
                            Result.success(it.firstOrNull())
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

suspend fun getTopic(
    backend: Backend,
    topicId: PrimaryKey,
    uid: PrimaryKey?,
    fillHasCommented: Boolean?
): Result<TopicInfo?> {
    if (uid == null && fillHasCommented == true) return Result.failure(UnauthorizedException())
    return checkRootReadPermission(
        backend,
        ObjectType.TOPIC,
        topicId,
        uid
    ).mapResultIfNotNull { (hasRead, hasJoined, isPrivate) ->
        if (hasRead) {
            DatabaseFactory.getTopicInfo(
                backend,
                ObjectFetch.IdFetch(topicId),
                uid
            ).mapResultIfNotNull { info ->
                processTopicsContent(backend, isPrivate, listOf(info), uid).map {
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

suspend fun getTopicByAid(
    backend: Backend,
    aid: String,
    uid: PrimaryKey?,
    fillHasCommented: Boolean?
): Result<TopicInfo?> {
    if (uid == null && fillHasCommented == true) return Result.failure(UnauthorizedException())
    return DatabaseFactory.getTopicInfo(backend, ObjectFetch.AidFetch(aid), uid).mapResultIfNotNull { info ->
        checkRootReadPermission(
            backend,
            ObjectType.TOPIC,
            info.id,
            uid
        ).mapResultIfNotNull { (hasRead, hasJoined, isPrivate) ->
            if (hasRead) {
                processTopicsContent(backend, isPrivate, listOf(info), uid).map {
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

suspend fun getTopLevelTopicsInObject(
    backend: Backend,
    parentId: PrimaryKey,
    parentType: ObjectType,
    uid: PrimaryKey? = null,
    fillHasCommented: Boolean?,
    pagingFetch: PagingFetch,
    pinType: TopicPinSearch? = null,
): Result<PaginationResult<TopicInfo>?> {
    return checkRootReadPermission(
        backend,
        parentType,
        parentId,
        uid
    ).mapResultIfNotNull { (hasRead, _, isPrivate) ->
        if (isPrivate && !hasRead) {
            Result.failure(ForbiddenException("Permission Denied"))
        } else {
            getTopicsPagingByPredicate(backend, uid, fillHasCommented, pagingFetch) { ->
                val baseQuery = Topics.parentId eq parentId
                when (pinType) {
                    PINNED -> baseQuery and (Topics.pinned eq true)
                    UNPINNED -> baseQuery and (Topics.pinned eq false)
                    else -> baseQuery
                }
            }.mapResult { (data, count) ->
                processTopicsContent(backend, isPrivate, data, uid).map {
                    PaginationResult(it, count)
                }
            }
        }
    }
}

private suspend fun processTopicExtension(
    backend: Backend,
    processedTopics: List<TopicInfo>,
    uid: PrimaryKey?,
    isPrivate: Boolean,
    addLatestSubTopic: Boolean,
) = DatabaseFactory.getUsersByIds(backend, processedTopics.map {
    it.author
}.distinct()).mapResult { users ->
    val userMap = users.associateBy { it.id }
    if (addLatestSubTopic) {
        val subTopics = processedTopics.flatMap { t ->
            getTopicsByPredicate(backend, uid, false, {
                it.bindPaginationQuery(Topics, PagingFetch(null, null, 2))
            }, true, {
                Topics.parentId eq t.id
            }).getOrThrow()
        }
        if (subTopics.isEmpty()) {
            Result.success(emptyList())
        } else {
            processTopicsContent(backend, isPrivate, subTopics, uid, false)
        }.map { processedSubTopics ->
            processedSubTopics.groupBy {
                it.parentId
            }
        }
    } else {
        Result.success(emptyMap())
    }.map { processedSubTopicMap ->
        val reactionMap = processedTopics.associate {
            it.id to reactionList(backend, it.id, uid, uid != null).getOrNull()?.data
        }
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

private suspend fun processTopicsContent(
    backend: Backend,
    isPrivate: Boolean,
    data: List<TopicInfo>,
    uid: PrimaryKey?,
    addLatestSubTopic: Boolean = true
): Result<List<TopicInfo>> = when {
    !isPrivate -> backend.topicSearchService.getDocuments(data.map {
        it.id
    }).mapResult { documents ->
        processTopicMedia(backend, data, documents)
    }.map { topicContents ->
        topicContents
    }

    uid == null -> Result.failure(ForbiddenException())

    else -> DatabaseFactory.getEncryptedTopicContents(backend, data, uid).map { topicContents ->
        data.mapIndexed { index, l ->
            l.copy(content = topicContents[index], isPrivate = true)
        }
    }
}.mapResult {
    processTopicExtension(backend, it, uid, isPrivate, addLatestSubTopic)
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

suspend fun checkRootReadPermission(
    backend: Backend,
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey?,
): Result<RootReadPermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            DatabaseFactory.getTopicRoot(backend, parentId).mapResultIfNotNull { (rootId, rootType) ->
                checkRootReadPermission(backend, rootType, rootId, uid)
            }
        }

        ObjectType.ROOM -> {
            DatabaseFactory.getRoomCommunityId(backend, parentId).mapResult { communityId ->
                if (communityId == null && uid == null) {
                    Result.failure(UnauthorizedException())
                } else {
                    isMemberJoined(backend, parentId, uid).map { hasJoined ->
                        RootReadPermission(hasJoined || communityId != null, hasJoined, communityId == null)
                    }
                }
            }
        }

        ObjectType.COMMUNITY -> {
            DatabaseFactory.checkCommunityExists(backend, parentId).mapResultIfNotNull {
                isMemberJoined(backend, parentId, uid).map { hasJoined ->
                    RootReadPermission(true, hasJoined, false)
                }
            }
        }

        ObjectType.USER -> DatabaseFactory.getRawUserById(backend, parentId).mapIfNotNull {
            RootReadPermission(hasRead = true, hasJoined = false, isPrivate = false)
        }

        ObjectType.TITLE -> Result.success(RootReadPermission(hasRead = true, hasJoined = false, isPrivate = false))
    }
}

suspend fun checkRootWritePermission(
    backend: Backend,
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootWritePermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            DatabaseFactory.getTopicRoot(backend, parentId).mapResultIfNotNull { (rootId, rootType) ->
                checkRootWritePermission(backend, rootType, rootId, uid)
            }
        }

        ObjectType.ROOM -> {
            DatabaseFactory.getRoomCommunityId(backend, parentId).mapResult {
                isMemberJoined(backend, parentId, uid).map { hasJoined ->
                    RootWritePermission(parentType, parentId, hasJoined)
                }
            }
        }

        ObjectType.COMMUNITY -> {
            DatabaseFactory.checkCommunityExists(backend, parentId).mapResultIfNotNull {
                isMemberJoined(backend, parentId, uid).map { hasJoined ->
                    RootWritePermission(parentType, parentId, hasJoined)
                }
            }
        }

        ObjectType.USER -> {
            if (uid == parentId) {
                DatabaseFactory.getRawUserById(backend, parentId).mapIfNotNull { (userInfo) ->
                    RootWritePermission(parentType, parentId, userInfo.id == uid)
                }
            } else {
                Result.failure(ForbiddenException("Permission denied"))
            }
        }

        ObjectType.TITLE -> Result.success(RootWritePermission(parentType, parentId, false))
    }
}

suspend fun checkRootAdminPermission(
    backend: Backend,
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootAdminPermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            DatabaseFactory.getTopicRoot(backend, parentId).mapResultIfNotNull { (rootId, rootType) ->
                checkRootAdminPermission(backend, rootType, rootId, uid)
            }
        }

        ObjectType.ROOM -> {
            DatabaseFactory.getRoomSource(backend, ObjectFetch.IdFetch(parentId), true, uid).mapIfNotNull {
                RootAdminPermission(parentType, parentId, it.first.creator == uid)
            }
        }

        ObjectType.COMMUNITY -> {
            DatabaseFactory.getCommunity(backend, ObjectFetch.IdFetch(parentId)).mapIfNotNull {
                RootAdminPermission(parentType, parentId, it.communityInfo.owner == uid)
            }
        }

        ObjectType.USER -> {
            DatabaseFactory.getRawUserById(backend, parentId).mapIfNotNull { (first) ->
                RootAdminPermission(parentType, parentId, first.id == uid)
            }
        }

        ObjectType.TITLE -> Result.success(RootAdminPermission(parentType, parentId, false))
    }
}

suspend fun searchPublicTopics(
    backend: Backend,
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
        checkRootReadPermission(backend, search.parentType, search.parentId, uid).mapResultIfNotNull {
            if (it.isPrivate) {
                Result.failure(BadRequestException("can't search in private chat"))
            } else {
                Result.success(DocumentSearch.Topics(search.parentId))
            }
        }
    } else {
        Result.success(DocumentSearch.CommunityRoot)
    }.mapResultIfNotNull { documentSearch ->
        backend.topicSearchService.searchDocument(
            search.word,
            documentSearch = documentSearch,
            pagingFetch
        ).mapResult { (list, total) ->
            processTopicsDocument(backend, uid, search.parent.fillHasCommented, list).map {
                PaginationResult(it, total)
            }
        }
    }
}

suspend fun processTopicMedia(
    backend: Backend,
    infos: List<TopicInfo>,
    documentList: List<TopicDocument?>
): Result<List<TopicInfo>> {
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
    return backend.mediaService.get(AMEDIA_DEFAULT_BUCKET, mediaNameList).map { mediaUrls ->
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

suspend fun recommendTopics(
    backend: Backend,
    uid: PrimaryKey?,
    fillHasCommented: Boolean?,
    pagingFetch: PagingFetch
): Result<PaginationResult<TopicInfo>?> {
    return if (uid != null) {
        DatabaseFactory.getJoinedCommunityIds(backend, uid).mapResult {
            backend.topicSearchService.searchDocument(
                documentSearch = DocumentSearch.Recommend(uid, it),
                pagingFetch = pagingFetch
            )
        }
    } else {
        backend.topicSearchService.searchDocument(
            documentSearch = DocumentSearch.RecommendNotLogin,
            pagingFetch = pagingFetch
        )
    }.mapResult { (list, total) ->
        processTopicsDocument(backend, uid, fillHasCommented, list).map {
            PaginationResult(it, total)
        }
    }
}

private suspend fun processTopicsDocument(
    backend: Backend,
    uid: PrimaryKey?,
    fillHasCommented: Boolean?,
    list: List<TopicDocument>,
): Result<List<TopicInfo>> {
    val ids = list.map {
        it.id
    }
    if (ids.isEmpty()) {
        return Result.success(emptyList())
    }
    return getTopicsByPredicate(backend, uid, fillHasCommented) {
        Topics.id inList ids
    }.mapResult { infos ->
        processTopicMedia(backend, infos.sortedByDescending {
            it.id
        }, list).mapResult {
            processTopicExtension(backend, it, uid, isPrivate = false, addLatestSubTopic = true)
        }
    }
}

suspend fun getTopicByIds(
    backend: Backend,
    ids: List<PrimaryKey>,
    uid: PrimaryKey?,
    fillHasCommented: Boolean?
): Result<List<TopicInfo>> {
    if (ids.isEmpty()) {
        return Result.success(emptyList())
    }
    val map = ids.map {
        checkRootReadPermission(backend, ObjectType.TOPIC, it, uid) to it
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
    return getTopicsByPredicate(backend, uid, fillHasCommented) {
        Topics.id inList ids
    }.mapResult { infos ->
        val privateList = infos.filter {
            private.contains(it.id)
        }

        val publicList = infos.filter {
            !private.contains(it.id)
        }
        processTopicsContent(backend, true, privateList, uid).mapResult { privateContents ->
            processTopicsContent(backend, false, publicList, uid).map { publicContents ->
                publicContents + privateContents
            }
        }
    }
}

suspend fun updateTopicPin(
    backend: Backend,
    uid: PrimaryKey,
    topicId: PrimaryKey,
    newValue: Boolean
) =
    checkRootAdminPermission(backend, ObjectType.TOPIC, topicId, uid).mapResultIfNotNull {
        if (it.hasAdmin) {
            DatabaseFactory.getTopicInfo(
                backend,
                ObjectFetch.IdFetch(topicId),
                uid
            ).mapResultIfNotNull { info ->
                if (info.isPin == newValue) {
                    Result.success(info)
                } else {
                    DatabaseFactory.updateTopicStatus(backend, topicId, newValue).map { isSuccess ->
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
