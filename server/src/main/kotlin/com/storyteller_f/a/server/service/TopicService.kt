package com.storyteller_f.a.server.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.*
import com.storyteller_f.a.server.route.RouteTopics
import com.storyteller_f.index.TopicDocument
import com.storyteller_f.media.UploadPack
import com.storyteller_f.shared.model.AMEDIA_BUCKET
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.*
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.tables.*
import com.storyteller_f.tables.Topics
import com.storyteller_f.tables.getTopicsByPredicate
import com.storyteller_f.types.PaginationResult
import io.ktor.server.plugins.*
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

suspend fun addTopicAtCommunity(uid: PrimaryKey, backend: Backend, newTopic: NewTopic): Result<TopicInfo?> {
    if (newTopic.parentType == ObjectType.ROOM) {
        return Result.failure(ForbiddenException("can't use api to add topic in room"))
    }
    val content = newTopic.content.trim()
    if (content.isEmpty()) {
        return Result.failure(CustomBadRequestException("content is empty"))
    }
    return checkRootWritePermission(
        newTopic.parentType,
        newTopic.parentId,
        uid
    ).mapResultNotNull { (rootType, rootId, hasWrite) ->
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
                    lastModifiedTime = now(),
                )
                val plain = TopicContent.Plain(content)
                DatabaseFactory.saveTopic(topic, backend, plain).mapResult { topicInfo ->
                    processMediaAndAuthor(
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

suspend fun createTopicSnapshot(uid: PrimaryKey, topicId: PrimaryKey, backend: Backend): Result<MediaInfo?> {
    return DatabaseFactory.getRawUserById(uid).mapResultNotNull { (first) ->
        checkRootReadPermission(ObjectType.TOPIC, topicId, uid).mapResultNotNull { (hasRead) ->
            if (hasRead) {
                DatabaseFactory.getSimpleTopic(topicId).mapResultNotNull { value ->
                    createTopicSnapshot(value, first, backend, uid)
                }
            } else {
                Result.failure(ForbiddenException("Permission denied."))
            }
        }
    }
}

private suspend fun createTopicSnapshot(
    topicInfo: TopicInfo,
    creatorInfo: UserInfo,
    backend: Backend,
    uid: PrimaryKey
): Result<MediaInfo?> {
    val topicId = topicInfo.id
    return backend.topicSearchService.getDocuments(listOf(topicId)).map { value -> value.firstOrNull() }
        .mapResultNotNull { documents ->
            DatabaseFactory.getRawUserById(topicInfo.author).mapResultNotNull { (first) ->
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
                    ).mapResultNotNull {
                        backend.mediaService.upload(AMEDIA_BUCKET, listOf(UploadPack(name, pdfFile))).mapResult {
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
    topicId: PrimaryKey,
    uid: PrimaryKey?,
    backend: Backend,
    fillHasCommented: Boolean?
): Result<TopicInfo?> {
    if (uid == null && fillHasCommented == true) return Result.failure(UnauthorizedException())
    return checkRootReadPermission(
        ObjectType.TOPIC,
        topicId,
        uid
    ).mapResultNotNull { (hasRead, hasJoined, isPrivate) ->
        if (hasRead) {
            DatabaseFactory.getTopicInfo(topicId, null, uid).mapResultNotNull { info ->
                processTopicsContent(isPrivate, backend, listOf(info), uid).map {
                    it.first()
                }
            }.mapNotNull { value ->
                value.copy(hasJoined = hasJoined)
            }
        } else {
            Result.failure(ForbiddenException("Permission Denied"))
        }
    }
}

suspend fun getTopicByAid(
    aid: String,
    uid: PrimaryKey?,
    backend: Backend,
    fillHasCommented: Boolean?
): Result<TopicInfo?> {
    if (uid == null && fillHasCommented == true) return Result.failure(UnauthorizedException())
    return DatabaseFactory.getTopicInfo(null, aid, uid).mapResultNotNull { info ->
        checkRootReadPermission(
            ObjectType.TOPIC,
            info.id,
            uid
        ).mapResultNotNull { (hasRead, hasJoined, isPrivate) ->
            if (hasRead) {
                processTopicsContent(isPrivate, backend, listOf(info), uid).map {
                    it.first()
                }
            } else {
                Result.failure(ForbiddenException("Permission Denied"))
            }.mapNotNull { value ->
                value.copy(hasJoined = hasJoined)
            }
        }

    }

}

suspend fun getTopLevelTopicsInObject(
    parentId: PrimaryKey,
    parentType: ObjectType,
    uid: PrimaryKey? = null,
    backend: Backend,
    preTopicId: PrimaryKey?,
    nextTopicId: PrimaryKey?,
    size: Int,
    fillHasCommented: Boolean?
): Result<PaginationResult<TopicInfo>?> {
    return checkRootReadPermission(parentType, parentId, uid).mapResultNotNull { (hasRead, _, isPrivate) ->
        if (isPrivate && !hasRead) {
            Result.failure(ForbiddenException("Permission Denied"))
        } else {
            getTopicsPagingByPredicate(uid, preTopicId, nextTopicId, size, fillHasCommented) { ->
                Topics.parentId eq parentId
            }.mapResult { (data, count) ->
                processTopicsContent(isPrivate, backend, data, uid).map {
                    PaginationResult(it, count)
                }
            }
        }
    }
}

private suspend fun processTopicsContent(
    isPrivate: Boolean,
    backend: Backend,
    data: List<TopicInfo>,
    uid: PrimaryKey?
): Result<List<TopicInfo>> = when {
    !isPrivate -> backend.topicSearchService.getDocuments(data.map {
        it.id
    }).mapResult { documents ->
        processMediaAndAuthor(backend, data, documents)
    }.map { topicContents ->
        topicContents
    }

    uid == null -> Result.failure(ForbiddenException())

    else -> DatabaseFactory.getEncryptedTopicContents(data, uid).map { topicContents ->
        data.mapIndexed { index, l ->
            l.copy(content = topicContents[index], isPrivate = true)
        }
    }
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
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey?,
): Result<RootReadPermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            DatabaseFactory.getTopicRoot(parentId).mapResultNotNull { (rootId, rootType) ->
                checkRootReadPermission(rootType, rootId, uid)
            }
        }

        ObjectType.ROOM -> {
            DatabaseFactory.getRoomCommunityId(parentId).mapResult { communityId ->
                if (communityId == null && uid == null) {
                    Result.failure(UnauthorizedException())
                } else {
                    isMemberJoined(parentId, uid).map { hasJoined ->
                        RootReadPermission(hasJoined || communityId != null, hasJoined, communityId == null)
                    }
                }
            }
        }

        ObjectType.COMMUNITY -> {
            DatabaseFactory.checkCommunityExists(parentId).mapResultNotNull {
                isMemberJoined(parentId, uid).map { hasJoined ->
                    RootReadPermission(true, hasJoined, false)
                }
            }
        }

        ObjectType.USER -> DatabaseFactory.getRawUserById(parentId).mapNotNull {
            RootReadPermission(hasRead = true, hasJoined = false, isPrivate = false)
        }

        ObjectType.TITLE -> Result.success(RootReadPermission(true, false, false))
    }
}

suspend fun checkRootWritePermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootWritePermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            DatabaseFactory.getTopicRoot(parentId).mapResultNotNull { (rootId, rootType) ->
                checkRootWritePermission(rootType, rootId, uid)
            }
        }

        ObjectType.ROOM -> {
            DatabaseFactory.getRoomCommunityId(parentId).mapResult {
                isMemberJoined(parentId, uid).map { hasJoined ->
                    RootWritePermission(parentType, parentId, hasJoined)
                }
            }
        }

        ObjectType.COMMUNITY -> {
            DatabaseFactory.checkCommunityExists(parentId).mapResultNotNull {
                isMemberJoined(parentId, uid).map { hasJoined ->
                    RootWritePermission(parentType, parentId, hasJoined)
                }
            }
        }

        ObjectType.USER -> {
            DatabaseFactory.getRawUserById(parentId).mapNotNull { (first) ->
                RootWritePermission(parentType, parentId, first.id == uid)
            }
        }

        ObjectType.TITLE -> Result.success(RootWritePermission(parentType, parentId, false))
    }
}

suspend fun checkRootAdminPermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootAdminPermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            DatabaseFactory.getTopicRoot(parentId).mapResultNotNull { (rootId, rootType) ->
                checkRootAdminPermission(rootType, rootId, uid)
            }
        }

        ObjectType.ROOM -> {
            DatabaseFactory.getRoomSource(parentId).mapNotNull {
                RootAdminPermission(parentType, parentId, it.first.creator == uid)
            }
        }

        ObjectType.COMMUNITY -> {
            DatabaseFactory.getCommonCommunity(parentId).mapNotNull {
                RootAdminPermission(parentType, parentId, it.communityInfo.owner == uid)
            }
        }

        ObjectType.USER -> {
            DatabaseFactory.getRawUserById(parentId).mapNotNull { (first) ->
                RootAdminPermission(parentType, parentId, first.id == uid)
            }
        }

        ObjectType.TITLE -> Result.success(RootAdminPermission(parentType, parentId, false))
    }
}

suspend fun searchPublicTopics(
    preTopicId: PrimaryKey?,
    nextTopicId: PrimaryKey?,
    size: Int,
    search: RouteTopics.Search,
    backend: Backend,
    uid: PrimaryKey?
): Result<PaginationResult<TopicInfo>?> {
    if (search.rootType != null && search.rootType != ObjectType.COMMUNITY && search.rootType != ObjectType.USER) {
        return Result.failure(BadRequestException("can't search private topic"))
    }
    return backend.topicSearchService.searchDocument(
        size,
        search.word,
        preTopicId = preTopicId,
        nextTopicId = nextTopicId,
        author = search.author,
        root = when {
            search.rootId != null && search.rootType != null -> search.rootId to search.rootType
            else -> null
        },
        parent = when {
            search.parentId != null && search.parentType != null -> search.parentId to search.parentType
            else -> null
        },
    ).mapResult { (list, total) ->
        processTopicsDocument(uid, search.parent.fillHasCommented, backend, list).map {
            PaginationResult(it, total)
        }
    }
}

suspend fun processMediaAndAuthor(
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
    return backend.mediaService.get(AMEDIA_BUCKET, mediaNameList).map { mediaUrls ->
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
    }.mapResult { infos ->
        val ids = infos.map {
            it.author
        }
        DatabaseFactory.getUsersByIds(ids, backend).map { users ->
            val userMap = users.associate {
                it.id to it
            }
            infos.map {
                it.copy(extension = TopicInfo.Extension(userMap[it.author]!!))
            }
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
    preTopicId: PrimaryKey?,
    nextTopicId: PrimaryKey?,
    size: Int,
    uid: PrimaryKey?,
    fillHasCommented: Boolean?,
): Result<PaginationResult<TopicInfo>?> {
    return backend.topicSearchService.searchDocument(
        size,
        preTopicId = preTopicId,
        nextTopicId = nextTopicId,
        parent = null to ObjectType.COMMUNITY,
    ).mapResult { (list, total) ->
        processTopicsDocument(uid, fillHasCommented, backend, list).map {
            PaginationResult(it, total)
        }
    }
}

private suspend fun processTopicsDocument(
    uid: PrimaryKey?,
    fillHasCommented: Boolean?,
    backend: Backend,
    list: List<TopicDocument>
): Result<List<TopicInfo>> {
    val ids = list.map {
        it.id
    }
    return getTopicsByPredicate(uid, fillHasCommented) {
        Topics.id inList ids
    }.mapResult { infos ->
        processMediaAndAuthor(backend, infos, list)
    }
}

suspend fun getTopicByIds(
    ids: List<PrimaryKey>,
    uid: PrimaryKey?,
    fillHasCommented: Boolean?,
    backend: Backend
): Result<List<TopicInfo>> {
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
    return getTopicsByPredicate(uid, fillHasCommented) {
        Topics.id inList ids
    }.mapResult { infos ->
        val privateList = infos.filter {
            private.contains(it.id)
        }

        val publicList = infos.filter {
            !private.contains(it.id)
        }
        processTopicsContent(true, backend, privateList, uid).mapResult { privateContents ->
            processTopicsContent(false, backend, publicList, uid).map { publicContents ->
                publicContents + privateContents
            }
        }
    }
}
