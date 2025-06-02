package com.storyteller_f.a.app.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import app.cash.paging.PagingData
import app.cash.paging.cachedIn
import com.storyteller_f.a.app.UploadSession
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.compontents.DialogSaveState
import com.storyteller_f.a.app.pages.topic.upload
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.*
import com.storyteller_f.shared.utils.extractMarkdownHeadline
import com.storyteller_f.storage.DatabaseExpression
import com.storyteller_f.storage.DatabaseOrder
import com.storyteller_f.storage.DatabaseSource
import com.storyteller_f.storage.save
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.io.buffered
import kotlinx.serialization.serializer

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
    databaseSource: DatabaseSource,
    communityId: PrimaryKey,
    scopeName: String?
) :
    CommunityViewModel() {
    override val handler: LoadingHandler<CommunityInfo> =
        CachedLoadingHandler(
            databaseSource,
            "communities",
            viewModelScope,
            DatabaseExpression.IdEq("id", communityId),
            { sessionManager.getCommunityInfo(communityId) },
            serializer<CommunityInfo>(),
            scopeName
        ) { data, t ->
            save(communityId.toString(), data)
            save(t.aid, data)
        }
}

class AidCommunityViewModel(
    sessionManager: SessionManager,
    databaseSource: DatabaseSource,
    aid: String,
    scopeName: String?
) :
    CommunityViewModel() {
    private val serializer = serializer<CommunityInfo>()
    override val handler: LoadingHandler<CommunityInfo> = CachedLoadingHandler(
        databaseSource,
        "communities",
        viewModelScope,
        DatabaseExpression.StrEq("aid", aid),
        { sessionManager.getCommunityInfoByAid(aid) },
        serializer,
        scopeName
    ) { data, t ->
        save(aid, data)
        save(t.id.toString(), data)
    }
}

@OptIn(ExperimentalPagingApi::class)
class CommunitiesViewModel(
    sessionManager: SessionManager,
    private val databaseSource: DatabaseSource,
    private val joinStatusSearch: JoinStatusSearch,
    private val word: String,
    private val target: PrimaryKey? = null,
    scopeName: String?,
) : PagingViewModel<SectionLoadParams<PrimaryKey>, CommunityInfo>() {
    private val collectionName: String = "communities_${word}_${target}_$joinStatusSearch"

    override val flow = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = sectionMediator(
            sessionManager,
            collectionName,
            scopeName,
            databaseSource
        ) { userSessionViewModel ->
            listOf(
                RegularPagingSource(userSessionViewModel) { key, size ->
                    userSessionViewModel.searchCommunity(
                        size,
                        joinStatusSearch,
                        word,
                        target,
                        key,
                        PosterSearch.HAS_POSTER
                    )
                },
                RegularPagingSource(userSessionViewModel) { key, size ->
                    userSessionViewModel.searchCommunity(
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
        sectionPagingSource(
            databaseSource,
            collectionName,
            scopeName,
            listOf(DatabaseOrder.Desc("id"), DatabaseOrder.Desc("hasPoster"))
        )
    }.flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class RoomsViewModel(
    sessionManager: SessionManager,
    databaseSource: DatabaseSource,
    private val joinStatusSearch: JoinStatusSearch,
    private val word: String,
    val community: PrimaryKey? = null,
    scopeName: String?,
) : PagingViewModel<PrimaryKey, RoomInfo>() {
    private val collectionName: String = "rooms_${word}_$community"

    override val flow: Flow<PagingData<RoomInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = singleSourceMediator(
            databaseSource,
            collectionName,
            scopeName,
            RegularPagingSource(sessionManager) { key, size ->
                searchRooms(size, key, joinStatusSearch, word, community)
            }
        ),
    ) {
        singleSourceDatabaseSource(collectionName, scopeName, databaseSource)
    }.flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class TopicsViewModel(
    val sessionManager: SessionManager,
    private val databaseSource: DatabaseSource,
    id: PrimaryKey,
    scopeName: String?,
    val type: ObjectType? = null,
) :
    PagingViewModel<SectionLoadParams<PrimaryKey>, TopicInfo>() {
    private val collectionName: String = "topics_$id"

    @OptIn(FlowPreview::class)
    override val flow: Flow<PagingData<TopicInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = sectionMediator<TopicInfo>(
            sessionManager,
            collectionName,
            scopeName,
            databaseSource,
            {
                with(databaseSource.getCollection("topics", scopeName)) {
                    save(it.id, it)
                    it.aid?.let { aid -> save(aid, it) }
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
        sectionPagingSource(
            databaseSource,
            collectionName,
            scopeName,
            listOf(DatabaseOrder.Asc("pinned"), DatabaseOrder.Desc("id"))
        ) {
            processEncryptedTopic(this, sessionManager.sessionModel).map {
                extractHeadlineIfPlain(it)
            }
        }
    }.flow.debounce(500).cachedIn(viewModelScope)
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
    databaseSource: DatabaseSource,
    communityId: PrimaryKey,
    scopeName: String?
) :
    RoomViewModel() {
    override val handler: LoadingHandler<RoomInfo> =
        CachedLoadingHandler(
            databaseSource,
            "rooms",
            viewModelScope,
            DatabaseExpression.IdEq("id", communityId),
            {
                sessionManager.getRoomInfo(communityId)
            },
            serializer<RoomInfo>(),
            scopeName
        ) { data, t ->
            save(communityId, data)
            save(t.aid, data)
        }
}

class AidRoomViewModel(
    sessionManager: SessionManager,
    databaseSource: DatabaseSource,
    aid: String,
    scopeName: String?
) : RoomViewModel() {
    override val handler: LoadingHandler<RoomInfo> =
        CachedLoadingHandler(
            databaseSource,
            "rooms",
            viewModelScope,
            DatabaseExpression.StrEq("aid", aid),
            {
                sessionManager.getRoomInfoByAid(aid)
            },
            serializer<RoomInfo>(),
            scopeName
        ) { data, t ->
            save(aid, data)
            save(t.id, data)
        }
}

@OptIn(ExperimentalPagingApi::class)
class TopicSearchViewModel(
    sessionManager: SessionManager,
    databaseSource: DatabaseSource,
    word: List<String>,
    parentId: PrimaryKey?,
    parentType: ObjectType?,
    scopeName: String?,
) :
    PagingViewModel<PrimaryKey, TopicInfo>() {
    private val collectionName: String = "topics_search_${word}_$parentId"

    override val flow: Flow<PagingData<TopicInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = singleSourceMediator(
            databaseSource,
            collectionName,
            scopeName,
            RegularPagingSource(sessionManager) { key, size ->
                sessionManager.searchTopics(size, word, parentId, parentType, key)
            }
        ),
    ) {
        singleSourceDatabaseSource(collectionName, scopeName, databaseSource)
    }.flow.cachedIn(viewModelScope)
}

class MediaListViewModel(
    sessionManager: SessionManager,
    databaseSource: DatabaseSource,
    private val objectId: PrimaryKey,
    private val objectType: ObjectType,
    scopeName: String?,
) :
    PagingViewModel<PrimaryKey, MediaInfo>() {
    private val collectionName = "medias_$objectId"

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<MediaInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = singleSourceMediator(
            databaseSource,
            collectionName,
            scopeName,
            RegularPagingSource(sessionManager) { key, size ->
                sessionManager.getMediaList(objectId, objectType, key, size)
            }
        ),
    ) {
        singleSourceDatabaseSource(collectionName, scopeName, databaseSource)
    }.flow.cachedIn(viewModelScope)
}

class AllMediaListViewModel(
    sessionManager: SessionManager,
    private val objectId: PrimaryKey,
    private val objectType: ObjectType
) :
    SimpleViewModel<ServerResponse<MediaInfo>>() {
    override val handler: LoadingHandler<ServerResponse<MediaInfo>> = SimpleLoadingHandler(viewModelScope) {
        sessionManager.getAllMediaList(objectId, objectType)
    }

    init {
        viewModelScope.launch {
            bus.collect {
                when (it) {
                    is OnMediaUploaded -> {
                        val old = handler.data.value ?: ServerResponse(persistentListOf())
                        update(old.copy(data = old.data.toMutableList().apply {
                            addAll(0, it.mediaInfos)
                        }.toImmutableList()))
                    }
                }
            }
        }
    }
}

abstract class UserViewModel :
    SimpleViewModel<UserInfo>()

class IdUserViewModel(
    sessionManager: SessionManager,
    databaseSource: DatabaseSource,
    id: PrimaryKey,
    scopeName: String?
) :
    UserViewModel() {
    override val handler: LoadingHandler<UserInfo> =
        CachedLoadingHandler(
            databaseSource,
            "users",
            viewModelScope,
            DatabaseExpression.IdEq("id", id),
            {
                sessionManager.getUserInfo(id)
            },
            serializer<UserInfo>(),
            scopeName
        ) { data, t ->
            save(id, data)
            t.aid?.let { save(it, data) }
        }
}

class AidUserViewModel(
    sessionManager: SessionManager,
    databaseSource: DatabaseSource,
    aid: String,
    scopeName: String?
) : UserViewModel() {
    override val handler: LoadingHandler<UserInfo> =
        CachedLoadingHandler(
            databaseSource,
            "users",
            viewModelScope,
            DatabaseExpression.StrEq("aid", aid),
            {
                sessionManager.getUserInfoByAid(aid)
            },
            serializer<UserInfo>(),
            scopeName
        ) { data, t ->
            save(aid, data)
            save(t.id, data)
        }
}

@OptIn(ExperimentalPagingApi::class)
class MemberViewModel(
    sessionManager: SessionManager,
    databaseSource: DatabaseSource,
    objectId: PrimaryKey,
    word: String,
    objectType: ObjectType,
    scopeName: String?,
) :
    PagingViewModel<PrimaryKey, UserInfo>() {
    private val collectionName: String = "members_${objectId}_${word}_$"

    override val flow: Flow<PagingData<UserInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = singleSourceMediator(
            databaseSource,
            collectionName,
            scopeName,
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
        singleSourceDatabaseSource(collectionName, scopeName, databaseSource)
    }.flow.cachedIn(viewModelScope)
}

class ReactionsViewModel(sessionManager: SessionManager, private val objectId: PrimaryKey) :
    SimpleViewModel<ServerResponse<ReactionInfo>>() {
    override val handler: LoadingHandler<ServerResponse<ReactionInfo>> = SimpleLoadingHandler(viewModelScope) {
        sessionManager.getReactions(objectId, 20)
    }
}

abstract class TopicViewModel :
    SimpleViewModel<TopicInfo>()

class IdTopicViewModel(
    sessionManager: SessionManager,
    databaseSource: DatabaseSource,
    topicId: PrimaryKey,
    scopeName: String?
) :
    TopicViewModel() {
    override val handler: LoadingHandler<TopicInfo> =
        CachedLoadingHandler(
            databaseSource,
            "topics",
            viewModelScope,
            DatabaseExpression.IdEq("id", topicId),
            {
                sessionManager.getTopicInfo(topicId)
            },
            serializer<TopicInfo>(),
            scopeName
        ) { data, t ->
            save(topicId, data)
            t.aid?.let { save(it, data) }
        }
}

class AidTopicViewModel(
    sessionManager: SessionManager,
    databaseSource: DatabaseSource,
    aid: String,
    scopeName: String?
) :
    TopicViewModel() {
    override val handler: LoadingHandler<TopicInfo> =
        CachedLoadingHandler(
            databaseSource,
            "topics",
            viewModelScope,
            DatabaseExpression.StrEq("aid", aid),
            {
                sessionManager.getTopicInfoByAid(aid)
            },
            serializer<TopicInfo>(),
            scopeName
        ) { data, t ->
            save(aid, data)
            save(t.id, data)
        }
}

class RoomKeysViewModel(sessionManager: SessionManager, private val id: PrimaryKey, val private: Boolean) :
    SimpleViewModel<List<UserPubKeyInfo>>() {
    override val handler: LoadingHandler<List<UserPubKeyInfo>> = SimpleLoadingHandler(viewModelScope) {
        runCatching {
            if (!private) return@runCatching emptyList()
            val result = mutableListOf<UserPubKeyInfo>()
            var last: PrimaryKey? = null
            while (true) {
                val list =
                    sessionManager.requestRoomKeys(this@RoomKeysViewModel.id, last, 100)
                        .getOrThrow()
                result.addAll(list.data)
                val nextKey = list.pagination?.nextPageToken?.toPrimaryKeyOrNull() ?: break
                last = nextKey
            }
            result
        }
    }
}

@OptIn(ExperimentalPagingApi::class)
class TitlesViewModel(
    sessionManager: SessionManager,
    databaseSource: DatabaseSource,
    uid: PrimaryKey,
    searchType: TitleSearchType,
    status: TitleStatus? = null,
    type: TitleType? = null,
    scopeId: PrimaryKey? = null,
    scopeName: String?,
) : PagingViewModel<PrimaryKey, TitleInfo>() {
    private val collectionName: String = "titles_${uid}_${searchType}_${status}_${type}_$scopeId"

    override val flow: Flow<PagingData<TitleInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = singleSourceMediator(
            databaseSource,
            collectionName,
            scopeName,
            RegularPagingSource(
                sessionManager
            ) { key, size ->
                sessionManager.userTitles(uid, key, size, searchType, status, type, scopeId)
            }
        ),
    ) {
        singleSourceDatabaseSource(collectionName, scopeName, databaseSource)
    }.flow.cachedIn(viewModelScope)
}

class UploadViewModel(sessionManager: SessionManager, private val uploader: UploadSession, myUid: PrimaryKey) :
    ViewModel() {
    private val queue = Channel<Int> {
    }
    val handlers = uploader.list.mapIndexed { i, e ->
        FixedLoadingHandler<MediaInfo> {
            retry(i)
        } to e
    }

    init {
        viewModelScope.launch {
            for (e in queue) {
                val (handler, userSessionViewModelFile) = handlers[e]
                handler.request {
                    runCatching {
                        upload(
                            sessionManager,
                            myUid ob ObjectType.USER,
                            UploadData(
                                userSessionViewModelFile.size,
                                userSessionViewModelFile.name,
                                userSessionViewModelFile.contentType
                            )
                        ) {
                            userSessionViewModelFile.source()?.buffered() ?: throw Exception("upload failed")
                        }.first()
                    }
                }
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
            if (index in 0 until uploader.list.size && handlers[index].first.state.value is LoadingState.Error) {
                queue.send(index)
            }
        }
    }
}
