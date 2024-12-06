package com.storyteller_f.a.server.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.ForbiddenException
import com.storyteller_f.UnauthorizedException
import com.storyteller_f.index.TopicDocument
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import com.storyteller_f.types.PaginationResult
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.apache.fontbox.ttf.OTFParser
import org.apache.pdfbox.io.RandomAccessReadBufferedFile
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.io.File

@Resource("/topics")
class RouteTopics(val fillHasCommented: Boolean? = null) {
    @Resource("search")
    class Search(
        @Suppress("unused") val parent: RouteTopics = RouteTopics(),
        val word: List<String>? = null,
        val parentId: PrimaryKey? = null,
        val parentType: ObjectType? = null,
        val rootId: PrimaryKey? = null,
        val rootType: ObjectType? = null,
        val author: PrimaryKey? = null,
    )

    @Resource("recommend")
    class Recommend(val parent: RouteTopics)

    @Resource("{id}")
    class Id(@Suppress("unused") val parent: RouteTopics, val id: PrimaryKey) {
        @Resource("snapshot")
        class Snapshot(val parent: Id)

        @Resource("topics")
        class Topics(val parent: Id)

        @Resource("reactions")
        class Reactions(val parent: Id)
    }
}

@Resource("reactions")
class RouteReactions {
    @Resource("delete")
    class Delete(val parent: RouteReactions)
}

suspend fun RoutingContext.addTopicAtCommunity(uid: PrimaryKey, backend: Backend): Result<TopicInfo?> {
    val newTopic = call.receive<NewTopic>()
    if (newTopic.content is TopicContent.Encrypted) {
        return Result.failure(ForbiddenException("Community only accept unencrypted content."))
    }
    val content = (newTopic.content as TopicContent.Plain).plain
    return checkRootWritePermission(
        newTopic.parentType,
        newTopic.parentId,
        uid
    ).mapResultNotNull { (rootType, rootId, hasWrite) ->
        if (hasWrite) {
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
            DatabaseFactory.dbQuery {
                val newTopicId = Topic.new(topic)
                backend.topicDocumentService.saveDocument(
                    listOf(
                        TopicDocument(
                            newTopicId,
                            content,
                            rootId,
                            rootType.name,
                            newTopic.parentId,
                            newTopic.parentType.name,
                            uid
                        )
                    )
                )
                topic.toTopicInfo().copy(content = TopicContent.Plain(content))
            }
        } else {
            Result.failure(ForbiddenException("Permission denied."))
        }
    }
}

suspend fun getTopicSnapshot(id: PrimaryKey, topicId: PrimaryKey, backend: Backend): Result<File?> {
    return DatabaseFactory.first(User::toUserInfo, User::wrapRow) {
        User.findById(id)
    }.mapResultNotNull { creatorInfo ->
        checkRootReadPermission(ObjectType.TOPIC, topicId, id).mapResultNotNull { (hasRead) ->
            if (hasRead) {
                getSimpleTopic(topicId).mapResultNotNull { value ->
                    getTopicSnapshot(value, creatorInfo, backend)
                }
            } else {
                Result.failure(ForbiddenException("Permission denied."))
            }
        }
    }
}

private suspend fun getTopicSnapshot(
    topicInfo: TopicInfo,
    creatorInfo: UserInfo,
    backend: Backend
): Result<File?> {
    val topicId = topicInfo.id
    return backend.topicDocumentService.getDocument(listOf(topicId)).map { value -> value.firstOrNull() }
        .mapResultNotNull { documents ->
            DatabaseFactory.first(User::toUserInfo, User::wrapRow) {
                User.findById(topicInfo.author)
            }.mapNotNull { authorInfo ->
                PDDocument().use { document ->
                    val firstPage = PDPage()
                    PDPageContentStream(document, firstPage).use { stream ->
                        stream.beginText()
                        val otf = OTFParser().parse(
                            RandomAccessReadBufferedFile(
                                File(
                                    "~/DIN-Regular.otf".replace(
                                        "~",
                                        System.getProperty("user.home")
                                    )
                                )
                            )
                        )
                        stream.setFont(PDType0Font.load(document, otf, false), 12f)
                        stream.newLineAtOffset(100F, 700F)
                        stream.setLeading(14.5f)
                        stream.showText(if (authorInfo.aid == null) authorInfo.address else authorInfo.aid)
                        stream.newLine()
                        stream.showText(documents.content)
                        stream.newLine()
                        stream.showText(if (creatorInfo.aid == null) creatorInfo.address else creatorInfo.aid)
                        stream.newLine()
                        stream.showText(topicInfo.createdTime.toString())
                        stream.newLine()
                        stream.showText(now().toString())
                        stream.endText()
                    }
                    document.addPage(firstPage)
                    document.save("/tmp/1.pdf")
                }
                File("/tmp/1.pdf")
            }
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
                if (isPrivate) {
                    if (uid == null) {
                        Result.failure(UnauthorizedException())
                    } else {
                        DatabaseFactory.dbQuery { getEncryptedTopicContent(listOf(topicId), uid) }.map { value ->
                            value.firstOrNull()?.let { id -> info.copy(content = id) }
                        }
                    }
                } else {
                    backend.topicDocumentService.getDocument(listOf(topicId)).map { value ->
                        value.firstOrNull()?.content?.let {
                            info.copy(content = TopicContent.Plain(it))
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
            !isPrivate -> commonPaginationSearchTopics(
                uid,
                predicate,
                preTopicId,
                nextTopicId,
                size,
                fillHasCommented
            ).mapResult { (data, count) ->
                backend.topicDocumentService.getDocument(data.map {
                    it.id
                }).map {
                    it.mapNotNull {
                        it?.let { it1 -> TopicContent.Plain(it1.content) }
                    }
                }.map { topicContents ->
                    PaginationResult(data.mapIndexed { index, l ->
                        l.copy(content = topicContents[index])
                    }, count)
                }
            }

            hasRead && uid != null -> {
                commonPaginationSearchTopics(
                    uid,
                    predicate,
                    preTopicId,
                    nextTopicId,
                    size,
                    fillHasCommented
                ).mapResult { (data, count) ->
                    DatabaseFactory.dbQuery {
                        getEncryptedTopicContent(data.map {
                            it.id
                        }, uid)
                    }.map { topicContents ->
                        PaginationResult(data.mapIndexed { index, l ->
                            l.copy(content = topicContents[index])
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

@OptIn(ExperimentalStdlibApi::class)
fun getEncryptedTopicContent(topicId: List<PrimaryKey>, uid: PrimaryKey): List<TopicContent.Encrypted> {
    val aesMap = EncryptedTopicKeys.selectAll().where {
        EncryptedTopicKeys.topicId inList topicId and (EncryptedTopicKeys.uid eq uid)
    }.map {
        EncryptedTopicKey.wrapRow(it)
    }.associate {
        it.topicId to mapOf((it.uid to it.encryptedAes.toHexString()))
    }
    val contentMap = EncryptedTopics.selectAll().where {
        EncryptedTopics.topicId inList topicId
    }.map {
        EncryptedTopic.wrapRow(it)
    }.associate {
        it.topicId to it.content.toHexString()
    }
    return topicId.map {
        val map = aesMap[it] ?: emptyMap()
        val content = contentMap[it].orEmpty()
        TopicContent.Encrypted(content, map)
    }
}

data class RootReadPermission(
    val hasRead: Boolean,
    val hasJoined: Boolean,
    val isPrivate: Boolean
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
            getCommunitySource(parentId).mapResultNotNull { value ->
                isMemberJoined(parentId, uid).map { hasJoined ->
                    RootReadPermission(true, hasJoined, false)
                }
            }
        }

        ObjectType.USER -> TODO()
    }
}

suspend fun checkRootWritePermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey,
): Result<Triple<ObjectType, PrimaryKey, Boolean>?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            getTopicRoot(parentId).mapResultNotNull { (rootId, rootType) ->
                checkRootWritePermission(rootType, rootId, uid)
            }
        }

        ObjectType.ROOM -> {
            getRoomCommunityId(parentId).mapResult {
                isMemberJoined(parentId, uid).map { hasJoined ->
                    Triple(parentType, parentId, hasJoined)
                }
            }
        }

        ObjectType.COMMUNITY -> {
            getCommunitySource(parentId).mapResult {
                isMemberJoined(parentId, uid).map { hasJoined ->
                    Triple(parentType, parentId, hasJoined)
                }
            }
        }

        ObjectType.USER -> TODO()
    }
}

suspend fun searchPublicTopics(
    nextTopicId: PrimaryKey?,
    size: Int,
    search: RouteTopics.Search,
    backend: Backend,
    uid: PrimaryKey?
): Result<PaginationResult<TopicInfo>?> {
    return backend.topicDocumentService.searchDocument(
        search.word,
        size,
        nextTopicId,
        search.author,
        if (search.rootId != null && search.rootType != null) search.rootId to search.rootType else null,
        if (search.parentId != null && search.parentType != null) search.parentId to search.parentType else null,
    ).mapResult { documents ->
        val map = documents.list.associate {
            it.id to it
        }
        val ids = documents.list.map {
            it.id
        }
        commonSearchTopics(uid, {
            Topics.id inList ids
        }, null, nextTopicId, size, search.parent.fillHasCommented).map { infos ->
            infos.mapNotNull { t ->
                map[t.id]?.let {
                    t.copy(content = TopicContent.Plain(it.content))
                }
            }
        }.map { value ->
            PaginationResult(value, documents.total)
        }
    }
}

suspend fun recommendTopics(
    backend: Backend,
    preTopicId: PrimaryKey?,
    nextTopicId: PrimaryKey?,
    size: Int,
    uid: PrimaryKey?,
    fillHasCommented: Boolean,
): Result<PaginationResult<TopicInfo>?> {
    val predicate: SqlExpressionBuilder.() -> Op<Boolean> = {
        Topics.parentType eq ObjectType.COMMUNITY
    }
    return commonPaginationSearchTopics(
        uid,
        predicate,
        preTopicId,
        nextTopicId,
        size,
        fillHasCommented
    ).mapResult { (data, count) ->
        backend.topicDocumentService.getDocument(data.map {
            it.id
        }).map { value ->
            PaginationResult(data.mapIndexed { i, t ->
                value[i]?.let {
                    t.copy(content = TopicContent.Plain(it.content))
                } ?: t
            }, count)
        }
    }
}
