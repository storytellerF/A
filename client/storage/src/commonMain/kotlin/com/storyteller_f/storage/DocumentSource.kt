package com.storyteller_f.storage

import com.storyteller_f.shared.model.AlternativeAccountInfo
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKey
import com.storyteller_f.storage.DocumentExpression.Less
import com.storyteller_f.storage.DocumentSourceOrder.Desc
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass

sealed interface DocumentSourceOrder {
    data class Asc(val field: String) : DocumentSourceOrder
    data class Desc(val field: String) : DocumentSourceOrder
}

interface DocumentCollection<T> {
    fun saveDocument(id: String, t: T)
    fun getDocument(expression: DocumentExpression): T?
    fun getDocument(id: String): T?
    fun exists(expression: DocumentExpression): Boolean
    fun deleteDocument(id: String)
    fun observeDatum(expression: DocumentExpression): Flow<T?>
    fun observeData(
        orders: List<DocumentSourceOrder>,
        size: Int,
        vararg expressions: DocumentExpression,
        invalidate: () -> Unit
    ): DocumentObservable<T>

    fun observeDatum(id: PrimaryKey): Flow<T?> {
        return observeDatum(DocumentExpression.IdEq("id", id))
    }
}

sealed interface DocumentExpression {
    data class IdEq(val field: String, val value: PrimaryKey) : DocumentExpression
    data class StrEq(val field: String, val value: String) : DocumentExpression
    data class Less(val field: String, val value: PrimaryKey) : DocumentExpression
    data class StrLess(val field: String, val value: String) : DocumentExpression
    data class IntLess(val field: String, val value: Int) : DocumentExpression
}

interface DocumentSource {
    fun <T : Any> getCollection(name: String, clazz: KClass<T>): DocumentCollection<T>
    fun <T : Any> getCollectionByPrefix(
        prefix: String,
        clazz: KClass<T>
    ): List<DocumentCollection<T>>

    suspend fun clearCollection(collectionName: String)

    companion object {
        val EMPTY = object : DocumentSource {
            override fun <T : Any> getCollection(
                name: String,
                clazz: KClass<T>
            ): DocumentCollection<T> {
                TODO("Not yet implemented")
            }

            override fun <T : Any> getCollectionByPrefix(
                prefix: String,
                clazz: KClass<T>
            ): List<DocumentCollection<T>> {
                TODO("Not yet implemented")
            }

            override suspend fun clearCollection(collectionName: String) {
                TODO("Not yet implemented")
            }
        }
    }
}

inline fun <reified T : Any> DocumentSource.getCollection(name: String): DocumentCollection<T> {
    return getCollection(name, T::class)
}

inline fun <reified T : Any> DocumentCollection<T>.save(key: PrimaryKey, data: T) {
    saveDocument(key.toString(), data)
}

inline fun <reified T : Any> DocumentCollection<T>.save(key: String?, data: T) {
    key ?: return
    saveDocument(key, data)
}

fun <T> DocumentCollection<T>.getDocument(id: PrimaryKey): T? {
    return getDocument(id.toString())
}

fun <T> DocumentCollection<T>.deleteDocument(id: PrimaryKey) {
    deleteDocument(id.toString())
}

fun <T> DocumentCollection<T>.update(id: PrimaryKey, block: (T) -> T) {
    val document = getDocument(id) ?: return
    val value = block(document)
    saveDocument(id.toString(), value)
}

class UserDocumentStorage(
    val documentSource: DocumentSource
) : UserStorage {
    override fun observeDatum(collectionName: CollectionName, id: PrimaryKey): Flow<UserInfo?> {
        require(collectionName is CollectionName.Users)
        return documentSource.getCollection<UserInfo>(collectionName.getName()).observeDatum(id)
    }

    override fun save(collectionName: CollectionName, t: UserInfo) {
        with(documentSource.getCollection<UserInfo>(CollectionName.Users.getName())) {
            save(t.id, t)
            save(t.aid, t)
        }
        when (collectionName) {
            is CollectionName.SearchUser ->
                documentSource.getCollection<UserInfo>(collectionName.getName()).save(t.id, t)

            else -> {
            }
        }
    }

    override fun observeData(
        collectionName: CollectionName,
        key: String?,
        loadSize: Int,
        invalidate: () -> Unit
    ): DocumentObservable<UserInfo> {
        return when (collectionName) {
            is CollectionName.Members -> {
                val expressions = if (key != null) {
                    arrayOf(Less("id", key.toPrimaryKey()))
                } else {
                    emptyArray<Less>()
                }
                documentSource.getCollection<UserInfo>(collectionName.getName()).observeData(
                    listOf(Desc("id")),
                    loadSize,
                    *expressions
                ) {
                    invalidate()
                }
            }

            else -> throw Exception("unsupported")
        }
    }

    override fun observeDatum(
        collectionName: CollectionName,
        key: String
    ): Flow<UserInfo?> {
        require(collectionName is CollectionName.Users)
        return documentSource.getCollection<UserInfo>(collectionName.getName()).observeDatum(
            DocumentExpression.StrEq("aid", key)
        )
    }
}

class CommunityDocumentStorage(
    val documentSource: DocumentSource
) :
    CommunityStorage {
    override fun observeDatum(
        collectionName: CollectionName,
        id: PrimaryKey
    ): Flow<CommunityInfo?> {
        require(collectionName is CollectionName.Communities)
        return documentSource.getCollection<CommunityInfo>(collectionName.getName())
            .observeDatum(id)
    }

    override fun observeDatum(
        collectionName: CollectionName,
        key: String
    ): Flow<CommunityInfo?> {
        require(collectionName is CollectionName.Communities)
        return documentSource.getCollection<CommunityInfo>(collectionName.getName())
            .observeDatum(DocumentExpression.StrEq("aid", key))
    }

    override fun save(collectionName: CollectionName, t: CommunityInfo) {
        with(documentSource.getCollection<CommunityInfo>(CollectionName.Communities.getName())) {
            save(t.id, t)
            save(t.aid, t)
        }
        when (collectionName) {
            is CollectionName.SearchCommunity -> {
                documentSource.getCollection<CommunityInfo>(collectionName.getName()).save(t.id, t)
            }

            else -> {}
        }
    }

    override fun observeData(
        collectionName: CollectionName,
        key: String?,
        loadSize: Int,
        invalidate: () -> Unit
    ): DocumentObservable<CommunityInfo> {
        return when (collectionName) {
            is CollectionName.SearchCommunity -> {
                documentSource.getCollection<CommunityInfo>(collectionName.getName()).observeData(
                    listOf(Desc("hasPoster"), Desc("id")),
                    loadSize,
                    *if (key != null) {
                        arrayOf(Less("id", key.toPrimaryKey()))
                    } else {
                        emptyArray()
                    }
                ) {
                    invalidate()
                }
            }

            else -> throw Exception("unsupported")
        }
    }

    override fun getDocument(
        collectionName: CollectionName,
        id: PrimaryKey
    ): CommunityInfo? {
        return documentSource.getCollection<CommunityInfo>(collectionName.getName()).getDocument(id)
    }
}

class TopicDocumentStorage(
    val documentSource: DocumentSource
) : TopicStorage {
    override fun observeDatum(collectionName: CollectionName, id: PrimaryKey): Flow<TopicInfo?> {
        require(collectionName is CollectionName.Topics)
        return documentSource.getCollection<TopicInfo>(collectionName.getName()).observeDatum(id)
    }

    override fun save(collectionName: CollectionName, t: TopicInfo) {
        with(documentSource.getCollection<TopicInfo>(CollectionName.Topics.getName())) {
            save(t.id, t)
            save(t.aid, t)
        }
        documentSource.getCollection<TopicInfo>(collectionName.getName()).save(t.id, t)
        when (collectionName) {
            is CollectionName.Recommend, is CollectionName.TopicList, is CollectionName.SearchTopic -> {
                documentSource.getCollection<TopicInfo>(collectionName.getName()).save(t.id, t)
            }

            else -> {}
        }
    }

    override fun observeData(
        collectionName: CollectionName,
        key: String?,
        loadSize: Int,
        invalidate: () -> Unit
    ): DocumentObservable<TopicInfo> {
        return when (collectionName) {
            is CollectionName.Recommend -> {
                val expressions = if (key != null) {
                    arrayOf(Less("id", key.toPrimaryKey()))
                } else {
                    emptyArray()
                }
                documentSource.getCollection<TopicInfo>(collectionName.getName()).observeData(
                    listOf(Desc("id")),
                    loadSize,
                    *expressions
                ) {
                    invalidate()
                }
            }

            is CollectionName.TopicList -> {
                val expressions = if (key != null) {
                    arrayOf(Less("id", key.toPrimaryKey()))
                } else {
                    emptyArray()
                }
                documentSource.getCollection<TopicInfo>(collectionName.getName()).observeData(
                    listOf(DocumentSourceOrder.Asc("pinned"), Desc("id")),
                    loadSize,
                    *expressions
                ) {
                    invalidate()
                }
            }

            is CollectionName.SearchTopic -> {
                val expressions = if (key != null) {
                    arrayOf(Less("id", key.toPrimaryKey()))
                } else {
                    emptyArray<Less>()
                }
                documentSource.getCollection<TopicInfo>(collectionName.getName()).observeData(
                    listOf(Desc("id")),
                    loadSize,
                    *expressions
                ) {
                    invalidate()
                }
            }

            else -> throw Exception("unsupported")
        }
    }

    override fun observeDatum(
        collectionName: CollectionName,
        key: String
    ): Flow<TopicInfo?> {
        require(collectionName is CollectionName.Topics)
        return documentSource.getCollection<TopicInfo>(collectionName.getName()).observeDatum(
            DocumentExpression.StrEq("aid", key)
        )
    }

    override fun getDocument(
        collectionName: CollectionName,
        id: PrimaryKey
    ): TopicInfo? {
        return documentSource.getCollection<TopicInfo>(collectionName.getName()).getDocument(id)
    }
}

class TitleDocumentStorage(
    val documentSource: DocumentSource
) : TitleStorage {
    override fun observeDatum(collectionName: CollectionName, id: PrimaryKey): Flow<TitleInfo?> {
        require(collectionName is CollectionName.Titles)
        return documentSource.getCollection<TitleInfo>(collectionName.getName()).observeDatum(id)
    }

    override fun save(collectionName: CollectionName, t: TitleInfo) {
        when (collectionName) {
            is CollectionName.Titles -> {
                documentSource.getCollection<TitleInfo>("titles").save(t.id, t)
            }

            else -> {}
        }
    }

    override fun observeData(
        collectionName: CollectionName,
        key: String?,
        loadSize: Int,
        invalidate: () -> Unit
    ): DocumentObservable<TitleInfo> {
        return when (collectionName) {
            is CollectionName.SearchTitle -> {
                val param = key
                val expressions = if (param != null) {
                    arrayOf(Less("id", param.toPrimaryKey()))
                } else {
                    emptyArray<Less>()
                }
                documentSource.getCollection<TitleInfo>(collectionName.getName()).observeData(
                    listOf(Desc("id")),
                    loadSize,
                    *expressions
                ) {
                    invalidate()
                }
            }

            else -> throw Exception("unsupported")
        }
    }
}

class RoomDocumentStorage(
    val documentSource: DocumentSource
) : RoomStorage {
    override fun observeDatum(collectionName: CollectionName, id: PrimaryKey): Flow<RoomInfo?> {
        require(collectionName is CollectionName.Rooms)
        return documentSource.getCollection<RoomInfo>(collectionName.getName()).observeDatum(id)
    }

    override fun save(collectionName: CollectionName, t: RoomInfo) {
        with(documentSource.getCollection<RoomInfo>(CollectionName.Rooms.getName())) {
            save(t.id, t)
            save(t.aid, t)
        }
        when (collectionName) {
            is CollectionName.SearchRoom -> {
                documentSource.getCollection<RoomInfo>(collectionName.getName()).save(t.id, t)
            }

            else -> {}
        }
    }

    override fun observeData(
        collectionName: CollectionName,
        key: String?,
        loadSize: Int,
        invalidate: () -> Unit
    ): DocumentObservable<RoomInfo> {
        return when (collectionName) {
            is CollectionName.SearchRoom -> {
                val expressions = if (key != null) {
                    arrayOf(Less("id", key.toPrimaryKey()))
                } else {
                    emptyArray<Less>()
                }
                documentSource.getCollection(
                    collectionName.getName(),
                    RoomInfo::class
                ).observeData(listOf(Desc("id")), loadSize, *expressions) {
                    invalidate()
                }
            }

            else -> throw Exception("unsupported")
        }
    }

    override fun observeDatum(
        collectionName: CollectionName,
        key: String
    ): Flow<RoomInfo?> {
        require(collectionName is CollectionName.Rooms)
        return documentSource.getCollection<RoomInfo>(collectionName.getName()).observeDatum(
            DocumentExpression.StrEq("aid", key)
        )
    }
}

class RemoteKeyDocumentStorage(
    val documentSource: DocumentSource,
) : RemoteKeyStorage {
    override fun getPreRemoteKey(collectionName: CollectionName): RemoteKeys? {
        return documentSource.getCollection<RemoteKeys>("pre_remote_keys")
            .getDocument(collectionName.getName())
    }

    override fun getNextRemoteKey(collectionName: CollectionName): RemoteKeys? {
        return documentSource.getCollection<RemoteKeys>("next_remote_keys")
            .getDocument(collectionName.getName())
    }

    override fun savePreRemoteKey(remoteKeys: RemoteKeys) {
        documentSource.getCollection<RemoteKeys>("pre_remote_keys")
            .saveDocument(remoteKeys.collectionName, remoteKeys)
    }

    override fun saveNextRemoteKey(remoteKeys: RemoteKeys) {
        documentSource.getCollection<RemoteKeys>("next_remote_keys")
            .saveDocument(remoteKeys.collectionName, remoteKeys)
    }

    override fun deletePreRemoteKey(collectionName: CollectionName) {
        documentSource.getCollection<RemoteKeys>("pre_remote_keys")
            .deleteDocument(collectionName.getName())
    }

    override fun deleteNextRemoteKey(collectionName: CollectionName) {
        documentSource.getCollection<RemoteKeys>("next_remote_keys")
            .deleteDocument(collectionName.getName())
    }
}

class ReactionDocumentStorage(val documentSource: DocumentSource, val json: Json) : ReactionStorage {

    override fun observeDatum(collectionName: CollectionName, id: PrimaryKey): Flow<ReactionInfo?> {
        require(collectionName is CollectionName.Reactions)
        return documentSource.getCollection<ReactionInfo>(collectionName.getName()).observeDatum(id)
    }

    override fun save(collectionName: CollectionName, t: ReactionInfo) {
        documentSource.getCollection<ReactionInfo>(collectionName.getName()).save("${t.objectId}-${t.emoji}", t)
    }

    override fun observeData(
        collectionName: CollectionName,
        key: String?,
        loadSize: Int,
        invalidate: () -> Unit
    ): DocumentObservable<ReactionInfo> {
        return when (collectionName) {
            is CollectionName.ReactionList -> {
                val param = key?.let {
                    collectionName.decodeKey(json, it)
                }
                val expressions = if (param != null) {
                    arrayOf(
                        Less("count", param.count),
                        Less("lastReactionId", param.reactionId)
                    )
                } else {
                    emptyArray()
                }

                documentSource.getCollection<ReactionInfo>(collectionName.getName()).observeData(
                    listOf(
                        Desc("count")
                    ),
                    loadSize,
                    *expressions
                ) {
                    invalidate()
                }
            }

            else -> throw Exception("unsupported")
        }
    }
}

class AlternativesDocumentStorage(val documentSource: DocumentSource) : AlternativesStorage {

    override fun save(
        collectionName: CollectionName,
        t: AlternativeAccountInfo
    ) {
        TODO("Not yet implemented")
    }

    override fun observeData(
        collectionName: CollectionName,
        key: String?,
        loadSize: Int,
        invalidate: () -> Unit
    ): DocumentObservable<AlternativeAccountInfo> {
        return when (collectionName) {
            is CollectionName.Alternatives -> {
                val expressions = if (key != null) {
                    arrayOf(Less("id", key.toPrimaryKey()))
                } else {
                    emptyArray<Less>()
                }
                documentSource.getCollection<AlternativeAccountInfo>(collectionName.getName())
                    .observeData(
                        listOf<Desc>(Desc("id")),
                        loadSize,
                        *expressions
                    ) {
                        invalidate()
                    }
            }

            else -> throw Exception("unsupported")
        }
    }
}

class MediasDocumentStorage(val documentSource: DocumentSource) : MediasStorage {

    override fun save(
        collectionName: CollectionName,
        t: MediaInfo
    ) {
        TODO("Not yet implemented")
    }

    override fun observeData(
        collectionName: CollectionName,
        key: String?,
        loadSize: Int,
        invalidate: () -> Unit
    ): DocumentObservable<MediaInfo> {
        return when (collectionName) {
            is CollectionName.Medias -> {
                val expressions = if (key != null) {
                    arrayOf(Less("id", key.toPrimaryKey()))
                } else {
                    emptyArray<Less>()
                }
                documentSource.getCollection<MediaInfo>(collectionName.getName()).observeData(
                    listOf<Desc>(Desc("id")),
                    loadSize,
                    *expressions
                ) {
                    invalidate()
                }
            }

            else -> throw Exception("unsupported")
        }
    }
}

class DownloadDocumentStorage(val documentSource: DocumentSource) : DownloadStorage {

    override fun save(
        collectionName: CollectionName,
        t: DownloadInfo
    ) {
        documentSource.getCollection<DownloadInfo>(collectionName.getName()).save(t.mediaInfo.id, t)
    }

    override fun observeDatum(
        collectionName: CollectionName,
        id: PrimaryKey
    ): Flow<DownloadInfo?> {
        return documentSource.getCollection<DownloadInfo>(collectionName.getName()).observeDatum(
            DocumentExpression.IdEq("_id", id)
        )
    }

    override fun getDocument(
        collectionName: CollectionName,
        id: PrimaryKey
    ): DownloadInfo? {
        return documentSource.getCollection<DownloadInfo>(collectionName.getName()).getDocument(id)
    }
}

class DocumentStorage(documentSource: DocumentSource, json: Json) : Storage {
    override val userStorage: UserStorage =
        UserDocumentStorage(documentSource)
    override val communityStorage: CommunityStorage =
        CommunityDocumentStorage(documentSource)
    override val topicStorage: TopicStorage =
        TopicDocumentStorage(documentSource)
    override val titleStorage: TitleStorage =
        TitleDocumentStorage(documentSource)
    override val roomStorage: RoomStorage =
        RoomDocumentStorage(documentSource)
    override val remoteKeyStorage: RemoteKeyStorage = RemoteKeyDocumentStorage(documentSource)
    override val reactionStorage: ReactionStorage = ReactionDocumentStorage(documentSource, json)
    override val alternativesStorage: AlternativesStorage =
        AlternativesDocumentStorage(documentSource)
    override val mediasStorage: MediasStorage = MediasDocumentStorage(documentSource)
    override val downloadStorage: DownloadStorage = DownloadDocumentStorage(documentSource)
}
