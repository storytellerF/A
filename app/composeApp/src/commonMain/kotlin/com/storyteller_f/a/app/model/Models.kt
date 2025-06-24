package com.storyteller_f.a.app.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.map
import app.cash.paging.PagingData
import app.cash.paging.cachedIn
import com.storyteller_f.a.app.UploadSession
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.compontents.DialogSaveState
import com.storyteller_f.a.app.pages.topic.upload
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.*
import com.storyteller_f.shared.utils.extractMarkdownHeadline
import com.storyteller_f.shared.utils.extractMarkdownMediaLink
import com.storyteller_f.storage.StorageExpression
import com.storyteller_f.storage.StorageOrder
import com.storyteller_f.storage.StorageSource
import com.storyteller_f.storage.save
import de.jonasbroeckmann.kzip.Zip
import de.jonasbroeckmann.kzip.extractTo
import de.jonasbroeckmann.kzip.open
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.Serializable

data class OnTopicChanged(val topicInfo: TopicInfo)
data class OnTopicCreated(val topicInfo: TopicInfo)

data class OnMediaUploaded(val mediaInfos: List<MediaInfo>)

data class OnUserUpdated(val info: UserInfo)

data class OnTitleCreated(val title: TitleInfo)

data class OnCommunityCreated(val info: CommunityInfo)
data class OnCommunityJoined(val info: CommunityInfo)
data class OnCommunityExited(val info: CommunityInfo)

data class OnCommunityUpdated(val info: CommunityInfo)
data class OnRoomCreated(val info: RoomInfo)
data class OnRoomJoined(val info: RoomInfo)
data class OnRoomExited(val info: RoomInfo)

data class OnRoomUpdated(val info: RoomInfo)
data class OnAddReaction(val topicId: PrimaryKey, val emoji: String)
data class OnRemoveReaction(val topicId: PrimaryKey, val emoji: String)

abstract class CommunityViewModel :
    SimpleViewModel<CommunityInfo>() {
    val dialog = DialogSaveState()
}

class IdCommunityViewModel(
    sessionManager: SessionManager,
    storageSource: StorageSource,
    communityId: PrimaryKey,
) :
    CommunityViewModel() {
    override val handler: LoadingHandler<CommunityInfo> =
        CachedLoadingHandler(
            storageSource.getCollection("community", CommunityInfo::class),
            viewModelScope,
            StorageExpression.IdEq("id", communityId),
            { sessionManager.getCommunityInfo(communityId) },
        ) { t ->
            save(communityId, t)
            saveDocument(t.aid, t)
        }
}

class AidCommunityViewModel(
    sessionManager: SessionManager,
    storageSource: StorageSource,
    aid: String,
) :
    CommunityViewModel() {
    override val handler: LoadingHandler<CommunityInfo> = CachedLoadingHandler(
        storageSource.getCollection("community", CommunityInfo::class),
        viewModelScope,
        StorageExpression.StrEq("aid", aid),
        { sessionManager.getCommunityInfoByAid(aid) }
    ) { t ->
        saveDocument(aid, t)
        save(t.id, t)
    }
}

@OptIn(ExperimentalPagingApi::class)
class CommunitiesViewModel(
    sessionManager: SessionManager,
    private val storageSource: StorageSource,
    private val joinStatusSearch: JoinStatusSearch,
    private val word: String,
    private val target: PrimaryKey? = null,
) : PagingViewModel<SectionLoadParams, CommunityInfo>() {
    private val collectionName: String = "communities_${word}_${target}_$joinStatusSearch"

    override val flow = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = sectionRemoteMediator(
            sessionManager,
            collectionName,
            storageSource
        ) { sessionManager ->
            listOf(
                RegularPagingSource(sessionManager) { key, size ->
                    sessionManager.searchCommunity(
                        size,
                        joinStatusSearch,
                        word,
                        target,
                        key,
                        PosterSearch.HAS_POSTER
                    )
                },
                RegularPagingSource(sessionManager) { key, size ->
                    sessionManager.searchCommunity(
                        size,
                        joinStatusSearch,
                        word,
                        target,
                        key,
                        PosterSearch.NO_POSTER
                    )
                }
            )
        },
    ) {
        CustomDatabasePagingSource(
            storageSource.getCollection(
                collectionName,
                CommunityInfo::class
            ),
            listOf(StorageOrder.Desc("hasPoster"), StorageOrder.Desc("id")),
            {
                if (it != null) {
                    arrayOf(StorageExpression.Less("id", it.toPrimaryKey()))
                } else {
                    emptyArray()
                }
            },
        ) { info ->
            info?.id?.toString()
        }
    }.flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class RoomsViewModel(
    sessionManager: SessionManager,
    storageSource: StorageSource,
    private val joinStatusSearch: JoinStatusSearch,
    private val word: String,
    val community: PrimaryKey? = null,
) : PagingViewModel<PrimaryKey, RoomInfo>() {
    private val collectionName: String = "rooms_${word}_$community"

    override val flow: Flow<PagingData<RoomInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = primaryKeyRemoteMediator(
            storageSource,
            collectionName,
            RegularPagingSource(sessionManager) { key, size ->
                searchRooms(size, key, joinStatusSearch, word, community)
            }
        ),
    ) {
        primaryKeyPagingSource(collectionName, storageSource)
    }.flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class TopicsViewModel(
    val sessionManager: SessionManager,
    private val storageSource: StorageSource,
    id: PrimaryKey,
    val type: ObjectType? = null,
) :
    PagingViewModel<SectionLoadParams, TopicInfo>() {
    private val collectionName: String = "topics_$id"

    @OptIn(FlowPreview::class)
    override val flow: Flow<PagingData<TopicInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = sectionRemoteMediator<TopicInfo>(
            sessionManager,
            collectionName,
            storageSource,
            {
                with(storageSource.getCollection("topics", TopicInfo::class)) {
                    save(it.id, it)
                    it.aid?.let { aid -> saveDocument(aid, it) }
                }
            }
        ) {
            if (id == DEFAULT_PRIMARY_KEY) {
                listOf(RegularPagingSource(sessionManager) { loadKey, size ->
                    getRecommendTopics(loadKey, size)
                })
            } else {
                listOf(
                    RegularPagingSource(sessionManager) { loadKey, size ->
                        getTopicList(type, id, loadKey, size, TopicPinSearch.PINNED)
                    },
                    RegularPagingSource(sessionManager) { loadKey, size ->
                        getTopicList(type, id, loadKey, size, TopicPinSearch.UNPINNED)
                    }
                )
            }
        },
    ) {
        CustomDatabasePagingSource(
            storageSource.getCollection(
                collectionName,
                TopicInfo::class
            ),
            listOf(StorageOrder.Asc("pinned"), StorageOrder.Desc("id")),
            {
                if (it != null) {
                    arrayOf(StorageExpression.Less("id", it.toPrimaryKey()))
                } else {
                    emptyArray()
                }
            },
        ) { info ->
            info?.id?.toString()
        }
    }.flow.map {
        it.map {
            processEncryptedTopic(listOf(it), sessionManager).map {
                extractHeadlineIfPlain(it)
            }.first()
        }
    }.debounce(500).cachedIn(viewModelScope)
}

private fun extractHeadlineIfPlain(it: TopicInfo): TopicInfo {
    val content = it.content
    return if (content is TopicContent.Plain) {
        it.copy(content = TopicContent.Extracted(extractMarkdownHeadline(content.plain), content.list, content.plain))
    } else {
        it
    }
}

abstract class RoomViewModel :
    SimpleViewModel<RoomInfo>() {
    val dialog = DialogSaveState()
}

class IdRoomViewModel(
    sessionManager: SessionManager,
    storageSource: StorageSource,
    communityId: PrimaryKey,
) :
    RoomViewModel() {
    override val handler: LoadingHandler<RoomInfo> =
        CachedLoadingHandler(
            storageSource.getCollection("room", RoomInfo::class),
            viewModelScope,
            StorageExpression.IdEq("id", communityId),
            {
                sessionManager.getRoomInfo(communityId)
            }
        ) { t ->
            save(communityId, t)
            saveDocument(t.aid, t)
        }
}

class AidRoomViewModel(
    sessionManager: SessionManager,
    storageSource: StorageSource,
    aid: String,
) : RoomViewModel() {
    override val handler: LoadingHandler<RoomInfo> =
        CachedLoadingHandler(
            storageSource.getCollection("room", RoomInfo::class),
            viewModelScope,
            StorageExpression.StrEq("aid", aid),
            {
                sessionManager.getRoomInfoByAid(aid)
            }
        ) { t ->
            saveDocument(aid, t)
            save(t.id, t)
        }
}

@OptIn(ExperimentalPagingApi::class)
class TopicSearchViewModel(
    sessionManager: SessionManager,
    storageSource: StorageSource,
    word: List<String>,
    parentId: PrimaryKey?,
    parentType: ObjectType?,
) :
    PagingViewModel<PrimaryKey, TopicInfo>() {
    private val collectionName: String = "topics_search_${word}_$parentId"

    override val flow: Flow<PagingData<TopicInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = primaryKeyRemoteMediator(
            storageSource,
            collectionName,
            RegularPagingSource(sessionManager) { key, size ->
                sessionManager.searchTopics(size, word, parentId, parentType, key)
            }
        ),
    ) {
        primaryKeyPagingSource(collectionName, storageSource)
    }.flow.cachedIn(viewModelScope)
}

class MediaListViewModel(
    sessionManager: SessionManager,
    storageSource: StorageSource,
    private val objectId: PrimaryKey,
    private val objectType: ObjectType,
) :
    PagingViewModel<PrimaryKey, MediaInfo>() {
    private val collectionName = "medias_$objectId"

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<MediaInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = primaryKeyRemoteMediator(
            storageSource,
            collectionName,
            RegularPagingSource(sessionManager) { key, size ->
                sessionManager.getMediaList(objectId, objectType, key, size)
            }
        ),
    ) {
        primaryKeyPagingSource(collectionName, storageSource)
    }.flow.cachedIn(viewModelScope)
}

abstract class UserViewModel :
    SimpleViewModel<UserInfo>()

class IdUserViewModel(
    sessionManager: SessionManager,
    storageSource: StorageSource,
    id: PrimaryKey,
) :
    UserViewModel() {
    override val handler: LoadingHandler<UserInfo> =
        CachedLoadingHandler(
            storageSource.getCollection("users", UserInfo::class),
            viewModelScope,
            StorageExpression.IdEq("id", id),
            {
                sessionManager.getUserInfo(id)
            }
        ) { t ->
            save(id, t)
            t.aid?.let { saveDocument(it, t) }
        }
}

class AidUserViewModel(
    sessionManager: SessionManager,
    storageSource: StorageSource,
    aid: String,
) : UserViewModel() {
    override val handler: LoadingHandler<UserInfo> =
        CachedLoadingHandler(
            storageSource.getCollection("users", UserInfo::class),
            viewModelScope,
            StorageExpression.StrEq("aid", aid),
            {
                sessionManager.getUserInfoByAid(aid)
            }
        ) { t ->
            saveDocument(aid, t)
            save(t.id, t)
        }
}

@OptIn(ExperimentalPagingApi::class)
class MemberViewModel(
    sessionManager: SessionManager,
    storageSource: StorageSource,
    objectId: PrimaryKey,
    word: String,
    objectType: ObjectType,
) :
    PagingViewModel<PrimaryKey, UserInfo>() {
    private val collectionName: String = "members_${objectId}_${word}_$"

    override val flow: Flow<PagingData<UserInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = primaryKeyRemoteMediator(
            storageSource,
            collectionName,
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                when (objectType) {
                    ObjectType.COMMUNITY -> searchCommunityMembers(objectId, key, size, word)
                    ObjectType.ROOM -> searchRoomMembers(objectId, key, size, word)
                    else -> searchAllMembers(key, size, word)
                }
            }
        ),
    ) {
        primaryKeyPagingSource(collectionName, storageSource)
    }.flow.cachedIn(viewModelScope)
}

class ReactionsViewModel(
    sessionManager: SessionManager,
    private val objectId: PrimaryKey,
    storageSource: StorageSource,
) :
    PagingViewModel<String, ReactionInfo>() {
    @OptIn(ExperimentalPagingApi::class)
    override val flow = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = commonRemoteMediator(
            storageSource,
            "reactions_$objectId",
            RegularPagingSource(sessionManager) { key, size ->
                sessionManager.getReactions(objectId, size, key)
            }
        ) { info ->
            saveDocument("${info.objectId}-${info.count}", info)
        }
    ) {
        commonPagingSource(
            "reactions_$objectId",
            storageSource,
        ) { info ->
            info?.let {
                "${info.objectId}-${info.count}"
            }
        }
    }.flow.cachedIn(viewModelScope)
}

abstract class TopicViewModel :
    SimpleViewModel<TopicInfo>()

class IdTopicViewModel(
    sessionManager: SessionManager,
    storageSource: StorageSource,
    topicId: PrimaryKey,
) :
    TopicViewModel() {
    override val handler: LoadingHandler<TopicInfo> =
        CachedLoadingHandler(
            storageSource.getCollection("topics", TopicInfo::class),
            viewModelScope,
            StorageExpression.IdEq("id", topicId),
            {
                sessionManager.getTopicInfo(topicId).map {
                    processEncryptedTopic(listOf(it), sessionManager).first()
                }
            }
        ) { t ->
            save(topicId, t)
            t.aid?.let { saveDocument(it, t) }
        }
}

class AidTopicViewModel(
    sessionManager: SessionManager,
    storageSource: StorageSource,
    aid: String,
) :
    TopicViewModel() {
    override val handler: LoadingHandler<TopicInfo> =
        CachedLoadingHandler(
            storageSource.getCollection("topic", TopicInfo::class),
            viewModelScope,
            StorageExpression.StrEq("aid", aid),
            {
                sessionManager.getTopicInfoByAid(aid)
            }
        ) { t ->
            saveDocument(aid, t)
            save(t.id, t)
        }
}

class RoomKeysViewModel(sessionManager: SessionManager, private val id: PrimaryKey, val private: Boolean) :
    SimpleViewModel<List<UserPubKeyInfo>>() {
    override val handler: LoadingHandler<List<UserPubKeyInfo>> = SimpleLoadingHandler(viewModelScope) {
        runCatching {
            if (!private) return@runCatching emptyList()
            val result = mutableListOf<UserPubKeyInfo>()
            var last: String? = null
            while (true) {
                val list =
                    sessionManager.requestRoomKeys(this@RoomKeysViewModel.id, last, 100)
                        .getOrThrow()
                result.addAll(list.data)
                val nextKey = list.pagination?.nextPageToken ?: break
                last = nextKey
            }
            result
        }
    }
}

@OptIn(ExperimentalPagingApi::class)
class TitlesViewModel(
    sessionManager: SessionManager,
    storageSource: StorageSource,
    uid: PrimaryKey,
    searchType: TitleSearchType,
    status: TitleStatus? = null,
    type: TitleType? = null,
    scopeId: PrimaryKey? = null,
) : PagingViewModel<PrimaryKey, TitleInfo>() {
    private val collectionName: String = "titles_${uid}_${searchType}_${status}_${type}_$scopeId"

    override val flow: Flow<PagingData<TitleInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = primaryKeyRemoteMediator(
            storageSource,
            collectionName,
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                sessionManager.userTitles(uid, key, size, searchType, status, type, scopeId)
            }
        ),
    ) {
        primaryKeyPagingSource(collectionName, storageSource)
    }.flow.cachedIn(viewModelScope)
}

class UploadViewModel(sessionManager: SessionManager, private val uploader: UploadSession, myUid: PrimaryKey) :
    ViewModel() {
    private val queue = Channel<Int> {
    }
    val handlers = uploader.list.map { e ->
        SimpleLoadingHandler(viewModelScope) {
            upload(
                sessionManager,
                myUid ob ObjectType.USER,
                UploadData(
                    e.size,
                    e.name,
                    e.contentType
                )
            ) {
                e.source()?.buffered() ?: throw Exception("upload failed")
            }.map {
                it.first()
            }
        }
    }

    init {
        viewModelScope.launch {
            for (e in queue) {
                handlers[e].refresh()
            }
        }
        viewModelScope.launch {
            uploader.list.forEachIndexed { i, _ ->
                queue.send(i)
            }
        }
    }

    fun retry(index: Int) {
        viewModelScope.launch {
            queue.send(index)
        }
    }
}

enum class DownloadStatus {
    NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED, FAILED
}

@Serializable
data class DownloadInfo(
    val mediaInfo: MediaInfo,
    val status: DownloadStatus,
    val message: String,
    val path: String,
)

class DownloadViewModel(
    private val sessionManager: SessionManager,
    storageSource: StorageSource,
) : ViewModel() {
    val lock = Mutex()
    val collection = storageSource.getCollection("downloads", DownloadInfo::class)
    private val queue = Channel<String> {
    }
    val handlers = mutableMapOf<String, CachedLoadingHandler<DownloadInfo>>()

    suspend fun download(key: String, mediaInfo: MediaInfo): CachedLoadingHandler<DownloadInfo> {
        val path = Path(SystemTemporaryDirectory, "downloads", key, mediaInfo.name)
        return lock.withLock {
            handlers.getOrPut(key) {
                CachedLoadingHandler(
                    collection,
                    viewModelScope,
                    StorageExpression.StrEq("_id", key),
                    {
                        sessionManager.serviceCatching {
                            path.parent?.let { SystemFileSystem.createDirectories(it) }
                            downloadIfNeed(key, mediaInfo, path)
                        }.recover({
                            DownloadInfo(mediaInfo, DownloadStatus.FAILED, it.message.toString(), path.toString())
                        })
                    }
                ) {
                    collection.saveDocument(key, it)
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            for (e in queue) {
                handlers[e]?.refresh()
            }
        }
    }

    private suspend fun HttpClient.downloadIfNeed(
        key: String,
        mediaInfo: MediaInfo,
        path: Path,
    ): DownloadInfo {
        val downloadInfo = getDocument(key, path, mediaInfo)

        if (downloadInfo.status == DownloadStatus.DOWNLOADED || downloadInfo.status == DownloadStatus.DOWNLOADING) {
            return downloadInfo
        }

        collection.saveDocument(key, downloadInfo.copy(status = DownloadStatus.DOWNLOADING))
        SystemFileSystem.sink(path).use {
            prepareGet(mediaInfo.url).execute { httpResponse ->
                val channel: ByteReadChannel = httpResponse.body()
                var count = 0L

                while (!channel.exhausted()) {
                    val chunk = channel.readRemaining()
                    count += chunk.remaining

                    chunk.transferTo(it)
                    println("Received $count bytes from ${httpResponse.contentLength()}")
                }
            }
        }
        if (path.toString().endsWith(".zip")) {
            Zip.open(path).use { zip ->
                zip.extractTo(Path(path.parent!!, "${path.name}.extracted"))
            }
        }
        return downloadInfo.copy(status = DownloadStatus.DOWNLOADED)
    }

    private fun getDocument(
        key: String,
        path: Path,
        mediaInfo: MediaInfo,
    ): DownloadInfo {
        val document = collection.getDocument(key)
        if (document != null) {
            val isDownloaded = document.status == DownloadStatus.DOWNLOADED
            val isFileExists = SystemFileSystem.exists(path)
            val isMediaSizeMatch = SystemFileSystem.metadataOrNull(path)?.size == mediaInfo.size
            if (isDownloaded && isFileExists && isMediaSizeMatch) {
                return document
            } else if (document.status == DownloadStatus.DOWNLOADING) {
                return document
            } else if (document.status == DownloadStatus.FAILED) {
                val t = document.copy(status = DownloadStatus.NOT_DOWNLOADED)
                collection.saveDocument(key, t)
                return t
            }
            return document.copy(status = DownloadStatus.NOT_DOWNLOADED)
        }
        val t = DownloadInfo(mediaInfo, DownloadStatus.NOT_DOWNLOADED, "", path.toString())
        collection.saveDocument(key, t)
        return t
    }
}

class MarkdownMediasViewModel(
    val sessionManager: SessionManager,
    private val content: String,
    private val objectTuple: ObjectTuple,
) :
    SimpleViewModel<List<MediaInfo>>() {
    override val handler: LoadingHandler<List<MediaInfo>>
        get() = SimpleLoadingHandler(viewModelScope) {
            runCatching {
                extractMarkdownMediaLink(content).map {
                    sessionManager.getMediaByName(it, objectTuple.objectId, objectTuple.objectType).getOrThrow()
                }
            }
        }
}

class AlternativeAccountsViewModel(val storageSource: StorageSource, sessionManager: SessionManager) :
    PagingViewModel<PrimaryKey, AlternativeAccountInfo>() {
    val collectionName = "alternatives"

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<AlternativeAccountInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = primaryKeyRemoteMediator(
            storageSource,
            collectionName,
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                sessionManager.getAlternativeAccounts(key, size)
            }
        ),
    ) {
        primaryKeyPagingSource(collectionName, storageSource)
    }.flow.cachedIn(viewModelScope)
}
