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
    modelStorage: ModelStorage,
    communityId: PrimaryKey,
) :
    CommunityViewModel() {
    val modelCollection = ModelCollection.Communities
    override val handler: LoadingHandler<CommunityInfo> =
        CachedLoadingHandler(
            modelStorage.communityStorage.observeDatum(communityId),
            viewModelScope,
            { t ->
                modelStorage.communityStorage.save(modelCollection, t)
            }
        ) { sessionManager.getCommunityInfo(communityId) }
}

class AidCommunityViewModel(
    sessionManager: SessionManager,
    modelStorage: ModelStorage,
    aid: String,
) :
    CommunityViewModel() {
    val modelCollection = ModelCollection.Communities
    override val handler: LoadingHandler<CommunityInfo> =
        CachedLoadingHandler(
            modelStorage.communityStorage.observeDatum(aid),
            viewModelScope,
            { t ->
                modelStorage.communityStorage.save(modelCollection, t)
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
) : PagingViewModel<SectionLoadParams, CommunityInfo>() {
    private val modelCollection = ModelCollection.SearchCommunity(joinStatusSearch, word, target)

    override val flow = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection,
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
        ) { info ->
            modelStorage.communityStorage.save(modelCollection, info)
        },
    ) {
        CompatPagingSource(
            modelStorage.communityStorage.observeData(modelCollection),
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
) : PagingViewModel<PrimaryKey, RoomInfo>() {
    private val modelCollection = ModelCollection.SearchRoom(word, community)

    override val flow: Flow<PagingData<RoomInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection,
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                searchRooms(size, key, joinStatusSearch, word, community)
            },
        ) { info ->
            modelStorage.roomStorage.save(modelCollection, info)
        },
    ) {
        CompatPagingSource(
            modelStorage.roomStorage.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class WorldViewModel(
    val sessionManager: SessionManager,
    modelStorage: ModelStorage,
) :
    PagingViewModel<SectionLoadParams, TopicInfo>() {
    private val modelCollection = ModelCollection.Recommend

    @OptIn(FlowPreview::class)
    override val flow: Flow<PagingData<TopicInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection,
            IntermediatePagingSource(
                SectionPagingSource(
                    listOf(
                        RegularPagingSource(sessionManager) { loadKey, size ->
                            getRecommendTopics(loadKey, size)
                        }
                    )
                ),
                SectionLoadParams::class
            ),
        ) { info ->
            modelStorage.topicStorage.save(modelCollection, info)
        },
    ) {
        CompatPagingSource(
            modelStorage.topicStorage.observeData(modelCollection),
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
    PagingViewModel<SectionLoadParams, TopicInfo>() {
    private val modelCollection = ModelCollection.TopicList(id)

    @OptIn(FlowPreview::class)
    override val flow: Flow<PagingData<TopicInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection,
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
                SectionLoadParams::class
            ),
        ) { info ->
            modelStorage.topicStorage.save(modelCollection, info)
        },
    ) {

        WrappedPagingSource(
            CompatPagingSource(
                modelStorage.topicStorage.observeData(
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
    val modelCollection = ModelCollection.Rooms
    override val handler: LoadingHandler<RoomInfo> =
        CachedLoadingHandler(
            modelStorage.roomStorage.observeDatum(
                communityId
            ),
            viewModelScope,
            { t ->
                modelStorage.roomStorage.save(modelCollection, t)
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
    val modelCollection = ModelCollection.Rooms
    override val handler: LoadingHandler<RoomInfo> =
        CachedLoadingHandler(
            modelStorage.roomStorage.observeDatum(aid),
            viewModelScope,
            { t ->
                modelStorage.roomStorage.save(modelCollection, t)
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
    PagingViewModel<PrimaryKey, TopicInfo>() {
    private val modelCollection = ModelCollection.SearchTopic(word, parentId)

    override val flow: Flow<PagingData<TopicInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection,
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                sessionManager.searchTopics(size, word, parentId, parentType, key)
            },
        ) { info ->
        },
    ) {
        CompatPagingSource(
            modelStorage.topicStorage.observeData(modelCollection),
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
    PagingViewModel<PrimaryKey, MediaInfo>() {
    private val modelCollection = ModelCollection.Medias(objectId)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<MediaInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection,
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                sessionManager.getMediaList(objectId, objectType, key, size)
            },
        ) { info ->
        },
    ) {
        CompatPagingSource(
            modelStorage.ossStorage.observeData(modelCollection),
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
    val modelCollection = ModelCollection.Users
    override val handler: LoadingHandler<UserInfo> =
        CachedLoadingHandler(
            modelStorage.userStorage.observeDatum(id),
            viewModelScope,
            { t ->
                modelStorage.userStorage.save(modelCollection, t)
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
    val modelCollection = ModelCollection.Users
    override val handler: LoadingHandler<UserInfo> =
        CachedLoadingHandler(
            modelStorage.userStorage.observeDatum(aid),
            viewModelScope,
            { t ->
                modelStorage.userStorage.save(modelCollection, t)
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
    PagingViewModel<PrimaryKey, UserInfo>() {
    private val modelCollection = ModelCollection.Members(word, objectId)

    override val flow: Flow<PagingData<UserInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection,
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
        CompatPagingSource(
            modelStorage.userStorage.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

class ReactionsViewModel(
    sessionManager: SessionManager,
    objectId: PrimaryKey,
    modelStorage: ModelStorage,
) : PagingViewModel<String, ReactionInfo>() {
    val modelCollection = ModelCollection.ReactionList(objectId)

    @OptIn(ExperimentalPagingApi::class)
    override val flow = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection,
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                sessionManager.getReactions(objectId, size, key)
            },
        ) { info ->
            modelStorage.reactionStorage.save(modelCollection, info)
        }
    ) {
        CompatPagingSource(
            modelStorage.reactionStorage.observeData(modelCollection),
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
    val modelCollection = ModelCollection.Topics
    override val handler: LoadingHandler<TopicInfo> =
        CachedLoadingHandler(
            modelStorage.topicStorage.observeDatum(topicId),
            viewModelScope,
            { t ->
                modelStorage.topicStorage.save(modelCollection, t)
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
    val modelCollection = ModelCollection.Topics
    override val handler: LoadingHandler<TopicInfo> =
        CachedLoadingHandler(
            modelStorage.topicStorage.observeDatum(aid),
            viewModelScope,
            { t ->
                modelStorage.topicStorage.save(modelCollection, t)
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
    modelStorage: ModelStorage,
    uid: PrimaryKey,
    searchType: TitleSearchType,
    status: TitleStatus? = null,
    type: TitleType? = null,
    scopeId: PrimaryKey? = null,
) : PagingViewModel<PrimaryKey, TitleInfo>() {
    private val modelCollection =
        ModelCollection.SearchTitle(uid, searchType, status, type, scopeId)

    override val flow: Flow<PagingData<TitleInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection,
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                sessionManager.userTitles(uid, key, size, searchType, status, type, scopeId)
            },
        ) { info ->
        },
    ) {
        CompatPagingSource(
            modelStorage.titleStorage.observeData(modelCollection),
            IntKeyConverter
        )
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
    private val modelStorage: ModelStorage,
) : ViewModel() {
    val lock = Mutex()
    val modelCollection = ModelCollection.Download
    private val queue = Channel<String> {
    }
    val handlers = mutableMapOf<String, CachedLoadingHandler<DownloadInfo>>()

    suspend fun download(mediaInfo: MediaInfo): CachedLoadingHandler<DownloadInfo> {
        val key = mediaInfo.id.toString()
        val path = Path(SystemTemporaryDirectory, "downloads", key, mediaInfo.name)
        return lock.withLock {
            handlers.getOrPut(key) {
                CachedLoadingHandler(
                    modelStorage.downloadStorage.observeDatum(
                        mediaInfo.id
                    ),
                    viewModelScope,
                    {
                        modelStorage.downloadStorage.save(modelCollection, it)
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

        modelStorage.downloadStorage.save(
            modelCollection,
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

    private suspend fun getDocument(
        path: Path,
        mediaInfo: MediaInfo,
    ): DownloadInfo {
        val document =
            modelStorage.downloadStorage.getDocument(modelCollection, mediaInfo.id)
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
                modelStorage.downloadStorage.save(modelCollection, t)
                return t
            }
            return document.copy(status = DownloadStatus.NOT_DOWNLOADED)
        }
        val t = DownloadInfo(mediaInfo, DownloadStatus.NOT_DOWNLOADED, "", path.toString())
        modelStorage.downloadStorage.save(modelCollection, t)
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
    val modelStorage: ModelStorage,
    sessionManager: SessionManager,
) :
    PagingViewModel<PrimaryKey, AlternativeAccountInfo>() {
    val modelCollection = ModelCollection.Alternatives

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<AlternativeAccountInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection,
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                sessionManager.getAlternativeAccounts(key, size)
            },
        ) { info ->
        },
    ) {
        CompatPagingSource(
            modelStorage.alternativesStorage.observeData(
                modelCollection,
            ),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}
