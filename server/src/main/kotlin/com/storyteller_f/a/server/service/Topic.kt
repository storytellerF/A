package com.storyteller_f.a.server.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.Backend
import com.storyteller_f.ForbiddenException
import com.storyteller_f.UnauthorizedException
import com.storyteller_f.a.server.route.RouteTopics
import com.storyteller_f.index.TopicDocument
import com.storyteller_f.media.UploadPack
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.*
import com.storyteller_f.tables.*
import com.storyteller_f.types.PaginationResult
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.FontMappers
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import java.awt.GraphicsEnvironment
import java.io.File
import java.lang.Exception

suspend fun RoutingContext.addTopicAtCommunity(uid: PrimaryKey, backend: Backend): Result<TopicInfo?> {
    val newTopic = call.receive<NewTopic>()
    if (newTopic.content is TopicContent.Encrypted) {
        return Result.failure(ForbiddenException("Community only accept unencrypted content."))
    }
    if (newTopic.parentType == ObjectType.ROOM) {
        return Result.failure(ForbiddenException("can't use api to add topic in room"))
    }
    val content = (newTopic.content as TopicContent.Plain).plain
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
                    author = uid,
                    parentId = newTopic.parentId,
                    parentType = newTopic.parentType,
                    rootId = rootId,
                    rootType = rootType,
                    lastModifiedTime = now(),
                    id = newId,
                    createdTime = now(),
                )
                saveTopic(topic, backend, content, rootId, rootType, newTopic, uid).mapResult {
                    backend.topicSearchService.getDocument(listOf(newId)).mapResult { documents ->
                        val topicInfo = topic.toTopicInfo()
                        processMediaLink(backend, listOf(topicInfo), documents).map {
                            it.firstOrNull()
                        }
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
    return getRawUserById(uid).mapResultNotNull { (first) ->
        checkRootReadPermission(ObjectType.TOPIC, topicId, uid).mapResultNotNull { (hasRead) ->
            if (hasRead) {
                getSimpleTopic(topicId).mapResultNotNull { value ->
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
    return backend.topicSearchService.getDocument(listOf(topicId)).map { value -> value.firstOrNull() }
        .mapResultNotNull { documents ->
            getRawUserById(topicInfo.author).mapResultNotNull { (first) ->
                val name = "$uid/$topicId.pdf"
                val saveTo = File("/tmp/$name")
                saveTo.parentFile?.let {
                    if (!it.exists() && !it.mkdirs()) {
                        Result.failure(Exception("failed"))
                    } else {
                        generateSnapshot(first, documents, creatorInfo, topicInfo, saveTo)
                        backend.mediaService.upload("amedia", listOf(UploadPack(name, saveTo))).mapResult {
                            saveTo.delete()
                            Result.success(it.firstOrNull())
                        }
                    }
                } ?: Result.failure(Exception("failed"))
            }
        }
}

private fun generateSnapshot(
    authorInfo: UserInfo,
    topicDocument: TopicDocument,
    creatorInfo: UserInfo,
    topicInfo: TopicInfo,
    saveToFile: File,
) {
    val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    PDDocument().use { document ->
        val firstPage = PDPage()
        PDPageContentStream(document, firstPage).use { stream ->
            stream.beginText()
            // 创建字体文件
            val font = graphicsEnvironment.allFonts.firstOrNull {
                it.canDisplayUpTo(topicDocument.content) == -1
            }

            // 使用 PDFBox 加载字体
            val pdFont =
                PDType0Font.load(document, FontMappers.instance().getTrueTypeFont(font?.name, null).font, true)
            stream.setFont(pdFont, 12f)
            stream.newLineAtOffset(100F, 700F)
            stream.setLeading(14.5f)
            stream.showText("pub by ${if (authorInfo.aid == null) authorInfo.address else authorInfo.aid}")
            stream.newLine()
            stream.showText("pub at ${topicInfo.createdTime}")
            stream.newLine()
            topicDocument.content.split("\n").forEach {
                it.chunked(50).forEach { s ->
                    stream.showText(s)
                    stream.newLine()
                }
            }
            stream.showText("capture by ${if (creatorInfo.aid == null) creatorInfo.address else creatorInfo.aid}")
            stream.newLine()
            stream.showText("capture at ${now()}")
            stream.endText()
        }
        document.addPage(firstPage)
        document.save(saveToFile)
    }
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
            getCommonTopic(topicId, uid).mapResultNotNull { info ->
                when {
                    !isPrivate -> backend.topicSearchService.getDocument(listOf(topicId)).mapResult { value ->
                        processMediaLink(backend, listOf(info), value).map {
                            it.first()
                        }
                    }

                    uid == null -> Result.failure(UnauthorizedException())
                    else -> getEncryptedTopics(topicId, uid).map { value ->
                        val encrypted = value.firstOrNull()
                        if (encrypted != null) {
                            info.copy(content = encrypted, isPrivate = true)
                        } else {
                            info
                        }
                    }
                }
            }.mapNotNull { value ->
                value.copy(hasJoined = hasJoined)
            }
        } else {
            Result.failure(ForbiddenException())
        }
    }
}

suspend fun getTopics(
    parentId: PrimaryKey,
    parentType: ObjectType,
    uid: PrimaryKey? = null,
    backend: Backend,
    preTopicId: PrimaryKey?,
    nextTopicId: PrimaryKey?,
    size: Int,
    fillHasCommented: Boolean?
): Result<PaginationResult<TopicInfo>?> {
    val predicate: SqlExpressionBuilder.() -> Op<Boolean> = {
        Topics.parentId eq parentId and (Topics.parentType eq parentType)
    }
    return checkRootReadPermission(parentType, parentId, uid).mapResultNotNull { (hasRead, _, isPrivate) ->
        when {
            !isPrivate -> commonPaginationTopics(
                uid,
                preTopicId,
                nextTopicId,
                size,
                fillHasCommented,
                predicate
            ).mapResult { (data, count) ->
                backend.topicSearchService.getDocument(data.map {
                    it.id
                }).mapResult { documents ->
                    processMediaLink(backend, data, documents)
                }.map { topicContents ->
                    PaginationResult(topicContents, count)
                }
            }

            hasRead && uid != null -> {
                commonPaginationTopics(
                    uid,
                    preTopicId,
                    nextTopicId,
                    size,
                    fillHasCommented,
                    predicate
                ).mapResult { (data, count) ->
                    getEncryptedTopic(data, uid).map { topicContents ->
                        PaginationResult(data.mapIndexed { index, l ->
                            l.copy(content = topicContents[index], isPrivate = true)
                        }, count)
                    }
                }
            }

            else -> {
                Result.failure(ForbiddenException())
            }
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

suspend fun checkRootReadPermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey?,
): Result<RootReadPermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            getTopicRoot(parentId).mapResultNotNull { (rootId, rootType) ->
                checkRootReadPermission(rootType, rootId, uid)
            }
        }

        ObjectType.ROOM -> {
            getRoomCommunityId(parentId).mapResult { communityId ->
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
            checkCommunityExists(parentId).mapResultNotNull {
                isMemberJoined(parentId, uid).map { hasJoined ->
                    RootReadPermission(true, hasJoined, false)
                }
            }
        }

        ObjectType.USER -> getRawUserById(parentId).mapNotNull {
            RootReadPermission(true, false, false)
        }
    }
}

suspend fun checkRootWritePermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootWritePermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            getTopicRoot(parentId).mapResultNotNull { (rootId, rootType) ->
                checkRootWritePermission(rootType, rootId, uid)
            }
        }

        ObjectType.ROOM -> {
            getRoomCommunityId(parentId).mapResult {
                isMemberJoined(parentId, uid).map { hasJoined ->
                    RootWritePermission(parentType, parentId, hasJoined)
                }
            }
        }

        ObjectType.COMMUNITY -> {
            checkCommunityExists(parentId).mapResultNotNull {
                isMemberJoined(parentId, uid).map { hasJoined ->
                    RootWritePermission(parentType, parentId, hasJoined)
                }
            }
        }

        ObjectType.USER -> {
            getRawUserById(parentId).mapNotNull { (first) ->
                RootWritePermission(parentType, parentId, first.id == uid)
            }
        }
    }
}

suspend fun searchPublicTopics(
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
        nextTopicId,
        search.author,
        if (search.rootId != null && search.rootType != null) search.rootId to search.rootType else null,
        if (search.parentId != null && search.parentType != null) search.parentId to search.parentType else null,
    ).mapResult { (list, total) ->
        val ids = list.map {
            it.id
        }
        commonTopics(uid, null, nextTopicId, size, search.parent.fillHasCommented) {
            Topics.id inList ids
        }.mapResult { infos ->
            processMediaLink(backend, infos, list).map {
                PaginationResult(it, total)
            }
        }
    }
}

fun processMediaLink(
    backend: Backend,
    infos: List<TopicInfo>,
    documentList: List<TopicDocument?>
): Result<List<TopicInfo>> {
    val documentMap = documentList.filterNotNull().associateBy { it.id }
    val list = documentMediaList(documentList)
    // 所有用到的media
    val mediaNameList = list.map {
        it.second
    }.distinct()
    // 根据topicId 保存的mediaName 的map
    val mediaMap = list.groupBy {
        it.first
    }
    return backend.mediaService.get("amedia", mediaNameList).map { mediaUrls ->
        val mediaInfoMap = mediaUrls.mapIndexedNotNull { index, url ->
            url?.let {
                mediaNameList[index] to it
            }
        }.associateBy {
            it.first
        }
        infos.map { info ->
            documentMap[info.id]?.let { document ->
                val m = mediaMap[document.id]?.mapNotNull {
                    val mediaName = it.second
                    mediaInfoMap[mediaName]?.second
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
    nextTopicId: PrimaryKey?,
    size: Int,
    uid: PrimaryKey?,
    fillHasCommented: Boolean,
): Result<PaginationResult<TopicInfo>?> {
    return backend.topicSearchService.searchDocument(
        size,
        nextTopicId = nextTopicId,
        parent = null to ObjectType.COMMUNITY,
    ).mapResult { (list, total) ->
        val ids = list.map {
            it.id
        }
        commonTopics(uid, null, nextTopicId, size, fillHasCommented) {
            Topics.id inList ids
        }.mapResult { infos ->
            processMediaLink(backend, infos, list).map {
                PaginationResult(it, total)
            }
        }
    }
}
