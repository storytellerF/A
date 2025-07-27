package com.storyteller_f.a.app.compose_app.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import com.storyteller_f.a.app.compose_app.common.*
import com.storyteller_f.a.app.compose_app.compontents.DialogSaveState
import com.storyteller_f.a.app.compose_app.pages.UploadSession
import com.storyteller_f.a.app.compose_app.pages.topic.upload
import com.storyteller_f.a.client.core.*
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.extractMarkdownHeadline
import com.storyteller_f.shared.utils.extractMarkdownMediaLink
import com.storyteller_f.storage.*
import de.jonasbroeckmann.kzip.Zip
import de.jonasbroeckmann.kzip.extractTo
import de.jonasbroeckmann.kzip.open
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.json.Json

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
data class OnAddReaction(val info: ReactionInfo, val topicInfo: TopicInfo)
data class OnRemoveReaction(val info: ReactionInfo, val topicInfo: TopicInfo)

abstract class CommunityViewModel :
    SimpleViewModel<CommunityInfo>() {
    val dialog = DialogSaveState()
}

class IdCommunityViewModel(
    sessionManager: SessionManager,
    documentStorage: Storage,
    communityId: PrimaryKey,
) :
    CommunityViewModel() {
    val loader = DocumentExpression.IdEq("id", communityId)
    val collectionName = CollectionName.Communities
    override val handler: LoadingHandler<CommunityInfo> =
        CachedLoadingHandler(
            documentStorage.communityStorage.observeDatum(collectionName, communityId),
            viewModelScope,
            { t ->
                documentStorage.communityStorage.save(collectionName, t)
            }
        ) { sessionManager.getCommunityInfo(communityId) }
}

class AidCommunityViewModel(
    sessionManager: SessionManager,
    documentStorage: Storage,
    aid: String,
) :
    CommunityViewModel() {
    val collectionName = CollectionName.Communities
    override val handler: LoadingHandler<CommunityInfo> =
        CachedLoadingHandler(
            documentStorage.communityStorage.observeDatum(collectionName, aid),
            viewModelScope,
            { t ->
                documentStorage.communityStorage.save(collectionName, t)
            }
        ) { sessionManager.getCommunityInfoByAid(aid) }
}

@OptIn(ExperimentalPagingApi::class)
class CommunitiesViewModel(
    sessionManager: SessionManager,
    json: Json,
    documentStorage: Storage,
    joinStatusSearch: JoinStatusSearch,
    word: String = "",
    target: PrimaryKey? = null,
) : PagingViewModel<SectionLoadParams, CommunityInfo>() {
    private val collectionName = CollectionName.SearchCommunity(joinStatusSearch, word, target)

    override val flow = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            documentStorage,
            collectionName,
            IntermediatePagingSource(
                SectionPagingSource(
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
                ),
                SectionLoadParams::class,
                json
            ),
        ) { info ->
            documentStorage.communityStorage.save(collectionName, info)
        },
    ) {
        CustomStoragePagingSource({ key, loadSize, invalidate ->
            documentStorage.communityStorage.observeData(collectionName, key, loadSize, invalidate)
        }) { info ->
            info?.id?.toString()
        }
    }.flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class RoomsViewModel(
    sessionManager: SessionManager,
    documentStorage: Storage,
    joinStatusSearch: JoinStatusSearch,
    word: String = "",
    val community: PrimaryKey? = null,
) : PagingViewModel<PrimaryKey, RoomInfo>() {
    private val collectionName = CollectionName.SearchRoom(word, community)

    override val flow: Flow<PagingData<RoomInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            documentStorage,
            collectionName,
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                searchRooms(size, key, joinStatusSearch, word, community)
            },
        ) { info ->
            documentStorage.roomStorage.save(collectionName, info)
        },
    ) {
        CustomStoragePagingSource(
            { key, params, invalidate ->
                documentStorage.roomStorage.observeData(collectionName, key, params, invalidate)
            }
        ) { info ->
            info?.id?.toString()
        }
    }.flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class WorldViewModel(
    val sessionManager: SessionManager,
    documentStorage: Storage,
    json: Json,
) :
    PagingViewModel<SectionLoadParams, TopicInfo>() {
    private val collectionName = CollectionName.Recommend

    @OptIn(FlowPreview::class)
    override val flow: Flow<PagingData<TopicInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            documentStorage,
            collectionName,
            IntermediatePagingSource(
                SectionPagingSource(
                    listOf(
                        RegularPagingSource(sessionManager) { loadKey, size ->
                            getRecommendTopics(loadKey, size)
                        }
                    )
                ),
                SectionLoadParams::class,
                json
            ),
        ) { info ->
            documentStorage.topicStorage.save(collectionName, info)
        },
    ) {
        CustomStoragePagingSource(
            { key, size, invalidate ->
                documentStorage.topicStorage.observeData(collectionName, key, size, invalidate)
            }
        ) { info ->
            info?.id?.toString()
        }
    }.flow.map {
        it.map { topicInfo ->
            extractHeadlineIfPlain(topicInfo)
        }
    }.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class TopicsViewModel(
    sessionManager: SessionManager,
    documentStorage: Storage,
    id: PrimaryKey,
    type: ObjectType,
    json: Json,
) :
    PagingViewModel<SectionLoadParams, TopicInfo>() {
    private val collectionName = CollectionName.TopicList(id)

    @OptIn(FlowPreview::class)
    override val flow: Flow<PagingData<TopicInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            documentStorage,
            collectionName,
            IntermediatePagingSource(
                SectionPagingSource(
                    listOf(
                        RegularPagingSource(
                            sessionManager
                        ) { loadKey, size ->
                            getTopicList(type, id, loadKey, size, TopicPinSearch.PINNED)
                        },
                        RegularPagingSource(
                            sessionManager
                        ) { loadKey, size ->
                            getTopicList(type, id, loadKey, size, TopicPinSearch.UNPINNED)
                        }
                    )
                ),
                SectionLoadParams::class,
                json
            ),
        ) { info ->
            documentStorage.topicStorage.save(collectionName, info)
        },
    ) {
        DecryptPagingSource(
            CustomStoragePagingSource(
                { key, loadSize, invalidate ->
                    documentStorage.topicStorage.observeData(
                        collectionName,
                        key,
                        loadSize,
                        invalidate
                    )
                },
            ) { info ->
                info?.id?.toString()
            },
            sessionManager
        )
    }.flow.map {
        it.map { topicInfo ->
            extractHeadlineIfPlain(topicInfo)
        }
    }.cachedIn(viewModelScope)
}

class DecryptPagingSource(
    val rawSource: CustomStoragePagingSource<TopicInfo>,
    val manager: SessionManager,
) :
    PagingSource<String, TopicInfo>() {
    override fun getRefreshKey(state: PagingState<String, TopicInfo>): String? {
        return null
    }

    override suspend fun load(params: LoadParams<String>): LoadResult<String, TopicInfo> {
        val result = rawSource.load(params)
        if (result !is LoadResult.Page<String, TopicInfo>) return result
        return result.copy(data = processEncryptedTopic(result.data, manager))
    }
}

private fun extractHeadlineIfPlain(it: TopicInfo): TopicInfo {
    val content = it.content
    return if (content is TopicContent.Plain) {
        it.copy(
            content = TopicContent.Extracted(
                extractMarkdownHeadline(content.plain),
                content.list,
                content.plain
            )
        )
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
    documentStorage: Storage,
    communityId: PrimaryKey,
) :
    RoomViewModel() {
    val collectionName = CollectionName.Rooms
    override val handler: LoadingHandler<RoomInfo> =
        CachedLoadingHandler(
            documentStorage.roomStorage.observeDatum(
                collectionName,
                communityId
            ),
            viewModelScope,
            { t ->
                documentStorage.roomStorage.save(collectionName, t)
            }
        ) {
            sessionManager.getRoomInfo(communityId)
        }
}

class AidRoomViewModel(
    sessionManager: SessionManager,
    documentStorage: Storage,
    aid: String,
) : RoomViewModel() {
    val collectionName = CollectionName.Rooms
    override val handler: LoadingHandler<RoomInfo> =
        CachedLoadingHandler(
            documentStorage.roomStorage.observeDatum(collectionName, aid),
            viewModelScope,
            { t ->
                documentStorage.roomStorage.save(collectionName, t)
            }
        ) {
            sessionManager.getRoomInfoByAid(aid)
        }
}

@OptIn(ExperimentalPagingApi::class)
class TopicSearchViewModel(
    sessionManager: SessionManager,
    documentStorage: Storage,
    word: List<String>,
    parentId: PrimaryKey?,
    parentType: ObjectType?,
) :
    PagingViewModel<PrimaryKey, TopicInfo>() {
    private val collectionName = CollectionName.SearchTopic(word, parentId)

    override val flow: Flow<PagingData<TopicInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            documentStorage,
            collectionName,
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                sessionManager.searchTopics(size, word, parentId, parentType, key)
            },
        ) { info ->
        },
    ) {
        CustomStoragePagingSource(
            { key, params, invalidate ->
                documentStorage.topicStorage.observeData(collectionName, key, params, invalidate)
            }
        ) { info ->
            info?.id?.toString()
        }
    }.flow.cachedIn(viewModelScope)
}

class MediaListViewModel(
    sessionManager: SessionManager,
    documentStorage: Storage,
    objectId: PrimaryKey,
    objectType: ObjectType,
) :
    PagingViewModel<PrimaryKey, MediaInfo>() {
    private val collectionName = CollectionName.Medias(objectId)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<MediaInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            documentStorage,
            collectionName,
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                sessionManager.getMediaList(objectId, objectType, key, size)
            },
        ) { info ->
        },
    ) {
        CustomStoragePagingSource(
            { key, params, invalidate ->
                documentStorage.mediasStorage.observeData(collectionName, key, params, invalidate)
            }
        ) { info ->
            info?.id?.toString()
        }
    }.flow.cachedIn(viewModelScope)
}

abstract class UserViewModel :
    SimpleViewModel<UserInfo>()

class IdUserViewModel(
    sessionManager: SessionManager,
    documentStorage: Storage,
    id: PrimaryKey,
) :
    UserViewModel() {
    val collectionName = CollectionName.Users
    override val handler: LoadingHandler<UserInfo> =
        CachedLoadingHandler(
            documentStorage.userStorage.observeDatum(collectionName, id),
            viewModelScope,
            { t ->
                documentStorage.userStorage.save(collectionName, t)
            }
        ) {
            sessionManager.getUserInfo(id)
        }
}

class AidUserViewModel(
    sessionManager: SessionManager,
    documentStorage: Storage,
    aid: String,
) : UserViewModel() {
    val collectionName = CollectionName.Users
    override val handler: LoadingHandler<UserInfo> =
        CachedLoadingHandler(
            documentStorage.userStorage.observeDatum(collectionName, aid),
            viewModelScope,
            { t ->
                documentStorage.userStorage.save(collectionName, t)
            }
        ) {
            sessionManager.getUserInfoByAid(aid)
        }
}

@OptIn(ExperimentalPagingApi::class)
class MemberViewModel(
    sessionManager: SessionManager,
    documentStorage: Storage,
    objectId: PrimaryKey,
    word: String,
    objectType: ObjectType,
) :
    PagingViewModel<PrimaryKey, UserInfo>() {
    private val collectionName = CollectionName.Members(word, objectId)

    override val flow: Flow<PagingData<UserInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            documentStorage,
            collectionName,
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                when (objectType) {
                    ObjectType.COMMUNITY -> searchCommunityMembers(objectId, key, size, word)
                    ObjectType.ROOM -> searchRoomMembers(objectId, key, size, word)
                    else -> searchAllMembers(key, size, word)
                }
            },
        ) { info ->
        },
    ) {
        CustomStoragePagingSource(
            { key, params, invalidate ->
                documentStorage.userStorage.observeData(collectionName, key, params, invalidate)
            }
        ) { info ->
            info?.id?.toString()
        }
    }.flow.cachedIn(viewModelScope)
}

class ReactionsViewModel(
    sessionManager: SessionManager,
    objectId: PrimaryKey,
    documentStorage: Storage,
    json: Json,
) : PagingViewModel<String, ReactionInfo>() {
    val collectionName = CollectionName.ReactionList(objectId)

    @OptIn(ExperimentalPagingApi::class)
    override val flow = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            documentStorage,
            collectionName,
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                sessionManager.getReactions(objectId, size, key)
            },
        ) { info ->
            documentStorage.reactionStorage.save(collectionName, info)
        }
    ) {
        CustomStoragePagingSource(
            { key, size, invalidate ->
                documentStorage.reactionStorage.observeData(collectionName, key, size, invalidate)
            },
            {
                it?.let {
                    collectionName.encodeKey(json, it)
                }
            }
        )
    }.flow.cachedIn(viewModelScope)
}

abstract class TopicViewModel :
    SimpleViewModel<TopicInfo>()

class IdTopicViewModel(
    sessionManager: SessionManager,
    documentStorage: Storage,
    topicId: PrimaryKey,
) :
    TopicViewModel() {
    val collectionName = CollectionName.Topics
    override val handler: LoadingHandler<TopicInfo> =
        CachedLoadingHandler(
            documentStorage.topicStorage.observeDatum(collectionName, topicId),
            viewModelScope,
            { t ->
                documentStorage.topicStorage.save(collectionName, t)
            }
        ) {
            sessionManager.getTopicInfo(topicId).map {
                processEncryptedTopic(listOf(it), sessionManager).first()
            }
        }
}

class AidTopicViewModel(
    sessionManager: SessionManager,
    documentStorage: Storage,
    aid: String,
) :
    TopicViewModel() {
    val collectionName = CollectionName.Topics
    override val handler: LoadingHandler<TopicInfo> =
        CachedLoadingHandler(
            documentStorage.topicStorage.observeDatum(collectionName, aid),
            viewModelScope,
            { t ->
                documentStorage.topicStorage.save(collectionName, t)
            }
        ) {
            sessionManager.getTopicInfoByAid(aid)
        }
}

class RoomKeysViewModel(
    sessionManager: SessionManager,
    private val id: PrimaryKey,
    val private: Boolean,
) :
    SimpleViewModel<List<UserPubKeyInfo>>() {
    override val handler: LoadingHandler<List<UserPubKeyInfo>> =
        SimpleLoadingHandler(viewModelScope) {
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
    documentStorage: Storage,
    uid: PrimaryKey,
    searchType: TitleSearchType,
    status: TitleStatus? = null,
    type: TitleType? = null,
    scopeId: PrimaryKey? = null,
) : PagingViewModel<PrimaryKey, TitleInfo>() {
    private val collectionName = CollectionName.SearchTitle(uid, searchType, status, type, scopeId)

    override val flow: Flow<PagingData<TitleInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            documentStorage,
            collectionName,
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                sessionManager.userTitles(uid, key, size, searchType, status, type, scopeId)
            },
        ) { info ->
        },
    ) {
        CustomStoragePagingSource(
            { key, params, invalidate ->
                documentStorage.titleStorage.observeData(collectionName, key, params, invalidate)
            }
        ) { info ->
            info?.id?.toString()
        }
    }.flow.cachedIn(viewModelScope)
}

class UploadViewModel(
    sessionManager: SessionManager,
    uploader: UploadSession,
    myUid: PrimaryKey,
) :
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

class DownloadViewModel(
    private val sessionManager: SessionManager,
    private val documentStorage: Storage,
) : ViewModel() {
    val lock = Mutex()
    val collectionName = CollectionName.Download
    private val queue = Channel<String> {
    }
    val handlers = mutableMapOf<String, CachedLoadingHandler<DownloadInfo>>()

    suspend fun download(mediaInfo: MediaInfo): CachedLoadingHandler<DownloadInfo> {
        val key = mediaInfo.id.toString()
        val path = Path(SystemTemporaryDirectory, "downloads", key, mediaInfo.name)
        return lock.withLock {
            handlers.getOrPut(key) {
                CachedLoadingHandler(
                    documentStorage.downloadStorage.observeDatum(
                        collectionName,
                        mediaInfo.id
                    ),
                    viewModelScope,
                    {
                        documentStorage.downloadStorage.save(collectionName, it)
                    }
                ) {
                    sessionManager.serviceCatching {
                        path.parent?.let { SystemFileSystem.createDirectories(it) }
                        downloadIfNeed(mediaInfo, path)
                    }.recover {
                        DownloadInfo(
                            mediaInfo,
                            DownloadStatus.FAILED,
                            it.message.toString(),
                            path.toString()
                        )
                    }
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
        mediaInfo: MediaInfo,
        path: Path,
    ): DownloadInfo {
        val downloadInfo = getDocument(path, mediaInfo)
        Napier.i(tag = "download") {
            "downloadInfo $downloadInfo"
        }
        if (downloadInfo.status == DownloadStatus.DOWNLOADED) {
            return downloadInfo
        }

        documentStorage.downloadStorage.save(
            collectionName,
            downloadInfo.copy(status = DownloadStatus.DOWNLOADING)
        )
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
        path: Path,
        mediaInfo: MediaInfo,
    ): DownloadInfo {
        val document = documentStorage.downloadStorage.getDocument(collectionName, mediaInfo.id)
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
                documentStorage.downloadStorage.save(collectionName, t)
                return t
            }
            return document.copy(status = DownloadStatus.NOT_DOWNLOADED)
        }
        val t = DownloadInfo(mediaInfo, DownloadStatus.NOT_DOWNLOADED, "", path.toString())
        documentStorage.downloadStorage.save(collectionName, t)
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
                    sessionManager.getMediaByName(it, objectTuple.objectId, objectTuple.objectType)
                        .getOrThrow()
                }
            }
        }
}

class AlternativeAccountsViewModel(
    val documentStorage: Storage,
    sessionManager: SessionManager,
) :
    PagingViewModel<PrimaryKey, AlternativeAccountInfo>() {
    val collectionName = CollectionName.Alternatives

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<AlternativeAccountInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            documentStorage,
            collectionName,
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                sessionManager.getAlternativeAccounts(key, size)
            },
        ) { info ->
        },
    ) {
        CustomStoragePagingSource(
            { key, params, invalidate ->
                documentStorage.alternativesStorage.observeData(
                    collectionName,
                    key,
                    params,
                    invalidate
                )
            }
        ) { info ->
            info?.id?.toString()
        }
    }.flow.cachedIn(viewModelScope)
}
