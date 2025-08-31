package com.storyteller_f.a.app.compose_app.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import com.storyteller_f.a.api.core.PaginationQuery
import com.storyteller_f.a.app.compose_app.common.*
import com.storyteller_f.a.app.compose_app.compontents.DialogSaveState
import com.storyteller_f.a.client.core.*
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.extractMarkdownHeadline
import com.storyteller_f.shared.utils.extractMarkdownMediaLink
import com.storyteller_f.shared.utils.now
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory

data class OnTopicChanged(val topicInfo: TopicInfo)
data class OnTopicCreated(val topicInfo: TopicInfo)

data class OnMediaUploaded(val fileInfos: List<FileInfo>)

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
    modelStorage: ModelStorage,
    communityId: PrimaryKey,
) :
    CommunityViewModel() {
    val modelCollection = CommunityCollection.Communities
    override val handler: LoadingHandler<CommunityInfo> =
        CachedLoadingHandler(
            modelStorage.communityInfoStorage.observeDatum(communityId),
            viewModelScope,
            { t ->
                modelStorage.communityInfoStorage.save(modelCollection, t)
            }
        ) { sessionManager.getCommunityInfo(communityId) }
}

class AidCommunityViewModel(
    sessionManager: SessionManager,
    modelStorage: ModelStorage,
    aid: String,
) :
    CommunityViewModel() {
    val modelCollection = CommunityCollection.Communities
    override val handler: LoadingHandler<CommunityInfo> =
        CachedLoadingHandler(
            modelStorage.communityInfoStorage.observeDatum(aid),
            viewModelScope,
            { t ->
                modelStorage.communityInfoStorage.save(modelCollection, t)
            }
        ) { sessionManager.getCommunityInfoByAid(aid) }
}

@OptIn(ExperimentalPagingApi::class)
class CommunitiesViewModel(
    sessionManager: SessionManager,
    modelStorage: ModelStorage,
    joinStatusSearch: JoinStatusSearch,
    word: String = "",
    target: PrimaryKey? = null,
) : PagingViewModel<CommunityInfo>() {
    private val modelCollection =
        CommunityCollection.SearchCommunity(joinStatusSearch, word, target)

    override val flow = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection.getName(),
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
                SectionLoadParams::class
            ),
        ) { data, clean ->
            if (clean) {
                modelStorage.communityInfoStorage.clean(modelCollection)
            }
            data.forEach {
                modelStorage.communityInfoStorage.save(modelCollection, it)
            }
        },
    ) {
        CompatPagingSource(
            modelStorage.communityInfoStorage.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class RoomsViewModel(
    sessionManager: SessionManager,
    modelStorage: ModelStorage,
    joinStatusSearch: JoinStatusSearch,
    word: String = "",
    val community: PrimaryKey? = null,
) : PagingViewModel<RoomInfo>() {
    private val modelCollection = RoomCollection.SearchRoom(word, community)

    override val flow: Flow<PagingData<RoomInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection.getName(),
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                searchRooms(size, key, joinStatusSearch, word, community)
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.roomInfoStorage.clean(modelCollection)
            }
            data.forEach {
                modelStorage.roomInfoStorage.save(modelCollection, it)
            }
        },
    ) {
        CompatPagingSource(
            modelStorage.roomInfoStorage.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class WorldViewModel(
    val sessionManager: SessionManager,
    modelStorage: ModelStorage,
) :
    PagingViewModel<TopicInfo>() {
    private val modelCollection = TopicCollection.Recommend

    @OptIn(FlowPreview::class)
    override val flow: Flow<PagingData<TopicInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection.getName(),
            IntermediatePagingSource(
                SectionPagingSource(
                    listOf(
                        RegularPagingSource(sessionManager) { loadKey, size ->
                            getRecommendTopics(PaginationQuery(loadKey, size = size))
                        }
                    )
                ),
                SectionLoadParams::class
            ),
        ) { data, clean ->
            if (clean) {
                modelStorage.topicInfoStorage.clean(modelCollection)
            }
            data.forEach {
                modelStorage.topicInfoStorage.save(modelCollection, it)
            }
        },
    ) {
        CompatPagingSource(
            modelStorage.topicInfoStorage.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.map {
        it.map { topicInfo ->
            extractHeadlineIfPlain(topicInfo)
        }
    }.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class TopicsViewModel(
    sessionManager: SessionManager,
    modelStorage: ModelStorage,
    id: PrimaryKey,
    type: ObjectType,
) :
    PagingViewModel<TopicInfo>() {
    private val modelCollection = TopicCollection.TopicList(id)

    @OptIn(FlowPreview::class)
    override val flow: Flow<PagingData<TopicInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection.getName(),
            IntermediatePagingSource(
                SectionPagingSource(
                    listOf(
                        RegularPagingSource(
                            sessionManager
                        ) { loadKey, size ->
                            getTopicList(
                                type,
                                id,
                                TopicPinSearch.PINNED,
                                PaginationQuery(loadKey, null, size = size)
                            )
                        },
                        RegularPagingSource(
                            sessionManager
                        ) { loadKey, size ->
                            getTopicList(
                                type,
                                id,
                                TopicPinSearch.UNPINNED,
                                PaginationQuery(loadKey, null, size = size)
                            )
                        }
                    )
                ),
                SectionLoadParams::class
            ),
        ) { data, refresh ->
            data.forEach {
                modelStorage.topicInfoStorage.save(modelCollection, it)
            }
        },
    ) {

        WrappedPagingSource(
            CompatPagingSource(
                modelStorage.topicInfoStorage.observeData(
                    modelCollection,
                ),
                IntKeyConverter
            )
        ) {
            processEncryptedTopic(it, sessionManager).map { topicInfo ->
                extractHeadlineIfPlain(topicInfo)
            }
        }
    }.flow.cachedIn(viewModelScope)
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
    modelStorage: ModelStorage,
    communityId: PrimaryKey,
) :
    RoomViewModel() {
    val modelCollection = RoomCollection.Rooms
    override val handler: LoadingHandler<RoomInfo> =
        CachedLoadingHandler(
            modelStorage.roomInfoStorage.observeDatum(
                communityId
            ),
            viewModelScope,
            { t ->
                modelStorage.roomInfoStorage.save(modelCollection, t)
            }
        ) {
            sessionManager.getRoomInfo(communityId)
        }
}

class AidRoomViewModel(
    sessionManager: SessionManager,
    modelStorage: ModelStorage,
    aid: String,
) : RoomViewModel() {
    val modelCollection = RoomCollection.Rooms
    override val handler: LoadingHandler<RoomInfo> =
        CachedLoadingHandler(
            modelStorage.roomInfoStorage.observeDatum(aid),
            viewModelScope,
            { t ->
                modelStorage.roomInfoStorage.save(modelCollection, t)
            }
        ) {
            sessionManager.getRoomInfoByAid(aid)
        }
}

interface CompatKeyConverter<F, T> {
    fun from(f: F): T
    fun to(t: T): F
}

object IntKeyConverter : CompatKeyConverter<Int, String> {
    override fun from(f: Int): String {
        return f.toString()
    }

    override fun to(t: String): Int {
        return t.toInt()
    }
}

@OptIn(ExperimentalPagingApi::class)
class TopicSearchViewModel(
    sessionManager: SessionManager,
    modelStorage: ModelStorage,
    word: List<String>,
    parentId: PrimaryKey?,
    parentType: ObjectType?,
) :
    PagingViewModel<TopicInfo>() {
    private val modelCollection = TopicCollection.SearchTopic(word, parentId)

    override val flow: Flow<PagingData<TopicInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection.getName(),
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                sessionManager.searchTopics(size, word, parentId, parentType, key)
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.topicInfoStorage.clean(modelCollection)
            }
            data.forEach {
                modelStorage.topicInfoStorage.save(modelCollection, it)
            }
        },
    ) {
        CompatPagingSource(
            modelStorage.topicInfoStorage.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

class MediaListViewModel(
    sessionManager: SessionManager,
    modelStorage: ModelStorage,
    objectId: PrimaryKey,
    objectType: ObjectType,
) :
    PagingViewModel<FileInfo>() {
    private val modelCollection = MediasCollection(objectId)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<FileInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection.getName(),
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                sessionManager.getMediaList(objectId, objectType, key, size)
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.fileInfoStorage.clean(modelCollection)
            }
            data.forEach {
                modelStorage.fileInfoStorage.save(modelCollection, it)
            }
        },
    ) {
        CompatPagingSource(
            modelStorage.fileInfoStorage.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

abstract class UserViewModel :
    SimpleViewModel<UserInfo>()

class IdUserViewModel(
    sessionManager: SessionManager,
    modelStorage: ModelStorage,
    id: PrimaryKey,
) :
    UserViewModel() {
    val modelCollection = UserCollection.Users
    override val handler: LoadingHandler<UserInfo> =
        CachedLoadingHandler(
            modelStorage.userInfoStorage.observeDatum(id),
            viewModelScope,
            { t ->
                modelStorage.userInfoStorage.save(modelCollection, t)
            }
        ) {
            sessionManager.getUserInfo(id)
        }
}

class AidUserViewModel(
    sessionManager: SessionManager,
    modelStorage: ModelStorage,
    aid: String,
) : UserViewModel() {
    val modelCollection = UserCollection.Users
    override val handler: LoadingHandler<UserInfo> =
        CachedLoadingHandler(
            modelStorage.userInfoStorage.observeDatum(aid),
            viewModelScope,
            { t ->
                modelStorage.userInfoStorage.save(modelCollection, t)
            }
        ) {
            sessionManager.getUserInfoByAid(aid)
        }
}

@OptIn(ExperimentalPagingApi::class)
class MemberViewModel(
    sessionManager: SessionManager,
    modelStorage: ModelStorage,
    objectId: PrimaryKey,
    word: String,
    objectType: ObjectType,
) :
    PagingViewModel<UserInfo>() {
    private val modelCollection = UserCollection.Members(word, objectId)

    override val flow: Flow<PagingData<UserInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection.getName(),
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                when (objectType) {
                    ObjectType.COMMUNITY -> searchCommunityMembers(objectId, key, size, word)
                    ObjectType.ROOM -> searchRoomMembers(objectId, key, size, word)
                    else -> searchAllMembers(key, size, word)
                }
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.userInfoStorage.clean(modelCollection)
            }
            data.forEach {
                modelStorage.userInfoStorage.save(modelCollection, it)
            }
        },
    ) {
        CompatPagingSource(
            modelStorage.userInfoStorage.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

class ReactionsViewModel(
    sessionManager: SessionManager,
    objectId: PrimaryKey,
    modelStorage: ModelStorage,
) : PagingViewModel<ReactionInfo>() {
    val modelCollection = ReactionCollection.ReactionList(objectId)

    @OptIn(ExperimentalPagingApi::class)
    override val flow = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection.getName(),
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                sessionManager.getReactions(objectId, size, key)
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.reactionInfoStorage.clean(modelCollection)
            }
            data.forEach {
                modelStorage.reactionInfoStorage.save(modelCollection, it)
            }
        }
    ) {
        CompatPagingSource(
            modelStorage.reactionInfoStorage.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

abstract class TopicViewModel :
    SimpleViewModel<TopicInfo>()

class IdTopicViewModel(
    sessionManager: SessionManager,
    modelStorage: ModelStorage,
    topicId: PrimaryKey,
) :
    TopicViewModel() {
    val modelCollection = TopicCollection.Topics
    override val handler: LoadingHandler<TopicInfo> =
        CachedLoadingHandler(
            modelStorage.topicInfoStorage.observeDatum(topicId),
            viewModelScope,
            { t ->
                modelStorage.topicInfoStorage.save(modelCollection, t)
            }
        ) {
            sessionManager.getTopicInfo(topicId).map {
                processEncryptedTopic(listOf(it), sessionManager).first()
            }
        }
}

class AidTopicViewModel(
    sessionManager: SessionManager,
    modelStorage: ModelStorage,
    aid: String,
) :
    TopicViewModel() {
    val modelCollection = TopicCollection.Topics
    override val handler: LoadingHandler<TopicInfo> =
        CachedLoadingHandler(
            modelStorage.topicInfoStorage.observeDatum(aid),
            viewModelScope,
            { t ->
                modelStorage.topicInfoStorage.save(modelCollection, t)
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
                        sessionManager.getRoomMembersPublicKeys(
                            this@RoomKeysViewModel.id,
                            PaginationQuery(last, size = 100)
                        )
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
    modelStorage: ModelStorage,
    uid: PrimaryKey,
    searchType: TitleSearchType,
    status: TitleStatus? = null,
    type: TitleType? = null,
    scopeId: PrimaryKey? = null,
) : PagingViewModel<TitleInfo>() {
    private val modelCollection =
        TitleCollection.SearchTitle(uid, searchType, status, type, scopeId)

    override val flow: Flow<PagingData<TitleInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection.getName(),
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                sessionManager.userTitles(uid, key, size, searchType, status, type, scopeId)
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.titleInfoStorage.clean(modelCollection)
            }
            data.forEach {
                modelStorage.titleInfoStorage.save(modelCollection, it)
            }
        },
    ) {
        CompatPagingSource(
            modelStorage.titleInfoStorage.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

class UploadHandler(
    private val scope: CoroutineScope,
    private val load: suspend UploadHandler.() -> Unit
) {
    val state: MutableStateFlow<LoadingState?> = MutableStateFlow(null)

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            load()
        }
    }
}

class UploadViewModel(
    myUid: PrimaryKey,
    modelStorage: ModelStorage,
) : PagingViewModel<UploadInfo>() {
    override val flow = Pager(PagingConfig(10)) {
        modelStorage.uploadInfoStorage.observeData(
            UploadCollection(myUid)
        )
    }.flow.cachedIn(viewModelScope)
}

class DownloadHandler<T>(
    flow: Flow<T?>,
    private val scope: CoroutineScope,
    private val load: suspend DownloadHandler<T>.() -> Unit
) : LoadingHandler<T> {
    override val state: MutableStateFlow<LoadingState?> = MutableStateFlow(null)

    @OptIn(FlowPreview::class)
    override val data = flow.stateIn(scope, SharingStarted.Lazily, null)

    init {
        refresh()
    }

    override fun refresh() {
        scope.launch {
            load()
        }
    }
}

class DownloadViewModel(
    private val sessionManager: SessionManager,
    private val modelStorage: ModelStorage,
) : ViewModel() {
    val lock = Mutex()
    val modelCollection = DownloadCollection
    private val queue = Channel<String> {
    }
    val handlers = mutableMapOf<String, LoadingHandler<DownloadInfo>>()

    suspend fun download(fileInfo: FileInfo?): LoadingHandler<DownloadInfo> {
        val key = fileInfo?.id.toString()
        val path = Path(SystemTemporaryDirectory, "downloads", key, fileInfo?.name.toString())
        return lock.withLock {
            handlers.getOrPut(key) {
                DownloadHandler(
                    modelStorage.downloadInfoStorage.observeDatum(
                        fileInfo?.id ?: 0
                    ),
                    viewModelScope,
                ) {
                    downloadFile(path, fileInfo)
                }
            }
        }
    }

    private suspend fun DownloadHandler<DownloadInfo>.downloadFile(
        path: Path,
        fileInfo: FileInfo?
    ) {
        fileInfo ?: return
        state.markLoading()
        sessionManager.serviceCatching {
            path.parent?.let { SystemFileSystem.createDirectories(it) }
            downloadIfNeed(fileInfo, path)
            state.markDone()
        }.onFailure {
            Napier.e(it) {
                "download failed ${fileInfo.fullName}"
            }
            modelStorage.downloadInfoStorage.save(
                modelCollection,
                DownloadInfo(
                    fileInfo,
                    DownloadStatus.FAILED,
                    it.message.toString(),
                    path.toString()
                )
            )
            state.markError(it)
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
        fileInfo: FileInfo,
        path: Path,
    ) {
        val downloadInfo = getDocument(path, fileInfo)
        Napier.i(tag = "download") {
            "downloadInfo $downloadInfo"
        }
        if (downloadInfo.status == DownloadStatus.DOWNLOADED) {
            return
        }
        val downloadingInfo = downloadInfo.copy(status = DownloadStatus.DOWNLOADING)
        modelStorage.downloadInfoStorage.save(modelCollection, downloadingInfo)
        segmentedDownload(this, fileInfo, path, downloadingInfo)
        if (path.toString().endsWith(".zip")) {
            Zip.open(path).use { zip ->
                zip.extractTo(Path(path.parent!!, "${path.name}.extracted"))
            }
        }
        modelStorage.downloadInfoStorage.save(
            modelCollection,
            downloadInfo.copy(
                status = DownloadStatus.DOWNLOADED,
                message = "download success ${now()}"
            )
        )
    }

    suspend fun segmentedDownload(
        client: HttpClient,
        fileInfo: FileInfo,
        path: Path,
        downloadingInfo: DownloadInfo
    ) {
        var downloadedBytes = 0L

        // Check if the file already exists and get its size to resume the download
        if (SystemFileSystem.exists(path)) {
            downloadedBytes = SystemFileSystem.metadataOrNull(path)?.size ?: 0L
        }

        client.prepareGet(fileInfo.url) {
            // Set the Range header to request the remaining part of the file
            header(HttpHeaders.Range, "bytes=$downloadedBytes-")
        }.execute { httpResponse ->
            // Check for successful partial or full content
            val httpStatus = httpResponse.status
            if (httpStatus != HttpStatusCode.OK && httpStatus != HttpStatusCode.PartialContent) {
                throw Exception("Download failed: ${httpStatus.description}")
            }

            val channel: ByteReadChannel = httpResponse.body()
            val totalContentLength = httpResponse.contentLength()

            // Open the sink in append mode to continue writing to the file
            SystemFileSystem.sink(path, append = true).use { sink ->
                while (!channel.isClosedForRead) {
                    val chunk = channel.readRemaining(10240)
                    if (chunk.exhausted()) continue

                    downloadedBytes += chunk.remaining
                    chunk.transferTo(sink)

                    // Save the updated progress
                    val totalSizeMessage = if (totalContentLength != null) {
                        " of ${totalContentLength + downloadedBytes - chunk.remaining} bytes"
                    } else {
                        ""
                    }
                    modelStorage.downloadInfoStorage.save(
                        modelCollection,
                        downloadingInfo.copy(message = "Received $downloadedBytes bytes$totalSizeMessage")
                    )
                }
            }
        }
    }

    private suspend fun getDocument(
        path: Path,
        fileInfo: FileInfo,
    ): DownloadInfo {
        val document =
            modelStorage.downloadInfoStorage.getDocument(modelCollection, fileInfo.id)
        if (document != null) {
            val isDownloaded = document.status == DownloadStatus.DOWNLOADED
            val isFileExists = SystemFileSystem.exists(path)
            val isMediaSizeMatch = SystemFileSystem.metadataOrNull(path)?.size == fileInfo.size
            return when {
                isDownloaded && isFileExists && isMediaSizeMatch -> document
                document.status == DownloadStatus.DOWNLOADING -> document
                document.status == DownloadStatus.FAILED -> {
                    val t = document.copy(status = DownloadStatus.NOT_DOWNLOADED)
                    modelStorage.downloadInfoStorage.save(modelCollection, t)
                    t
                }

                else -> document.copy(status = DownloadStatus.NOT_DOWNLOADED)
            }
        }
        val t = DownloadInfo(fileInfo, DownloadStatus.NOT_DOWNLOADED, "", path.toString())
        modelStorage.downloadInfoStorage.save(modelCollection, t)
        return t
    }
}

class MarkdownMediasViewModel(
    val sessionManager: SessionManager,
    private val content: String,
    private val objectTuple: ObjectTuple,
) :
    SimpleViewModel<List<FileInfo>>() {
    override val handler: LoadingHandler<List<FileInfo>> = SimpleLoadingHandler(viewModelScope) {
        runCatching {
            extractMarkdownMediaLink(content).map {
                sessionManager.getMediaByName(it, objectTuple.objectId, objectTuple.objectType)
                    .getOrThrow()
            }
        }
    }
}

class ChildAccountsViewModel(
    modelStorage: ModelStorage,
    sessionManager: SessionManager,
) :
    PagingViewModel<ChildAccountInfo>() {
    val modelCollection = ChildAccountCollection

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<ChildAccountInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection.NAME,
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                sessionManager.getAlternativeAccounts(key, size)
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.childAccountStorage.clean(modelCollection)
            }
            data.forEach {
                modelStorage.childAccountStorage.save(modelCollection, it)
            }
        },
    ) {
        CompatPagingSource(
            modelStorage.childAccountStorage.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}
