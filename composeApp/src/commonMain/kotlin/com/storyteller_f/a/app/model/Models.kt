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
import com.storyteller_f.shared.obj.*
import com.storyteller_f.shared.type.*
import com.storyteller_f.shared.utils.extractMarkdownHeadline
import io.ktor.client.*
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
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

abstract class CommunityViewModel(
    client: HttpClient
) :
    SimpleViewModel<CommunityInfo>(client) {
    val dialog = DialogSaveState()
}

class IdCommunityViewModel(client: HttpClient, databaseSource: DatabaseSource, communityId: PrimaryKey) :
    CommunityViewModel(client) {
    override val handler: LoadingHandler<CommunityInfo> =
        CachedLoadingHandler(
            databaseSource,
            "communities",
            viewModelScope,
            Expression.IdEq("id", communityId),
            { client.getCommunityInfo(communityId) },
            serializer<CommunityInfo>()
        ) { data, t ->
            save(communityId.toString(), data)
            save(t.aid, data)
        }
}

class AidCommunityViewModel(client: HttpClient, databaseSource: DatabaseSource, aid: String) :
    CommunityViewModel(client) {
    private val serializer = serializer<CommunityInfo>()
    override val handler: LoadingHandler<CommunityInfo> = CachedLoadingHandler(
        databaseSource,
        "communities",
        viewModelScope,
        Expression.StrEq("aid", aid),
        { client.getCommunityInfoByAid(aid) },
        serializer
    ) { data, t ->
        save(aid, data)
        save(t.id.toString(), data)
    }
}

@OptIn(ExperimentalPagingApi::class)
class CommunitiesViewModel(
    client: HttpClient,
    private val databaseSource: DatabaseSource,
    private val joinStatusSearch: JoinStatusSearch,
    private val word: String,
    private val target: PrimaryKey? = null,
) : PagingViewModel<SectionLoadParams<PrimaryKey>, CommunityInfo>() {
    private val collectionName: String = "communities_${word}_${target}_$joinStatusSearch"

    override val flow = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = sectionMediator(client, collectionName, databaseSource) { client ->
            listOf(
                RegularPagingSource(client) { key, size ->
                    client.searchCommunity(size, joinStatusSearch, word, target, key, PosterSearch.HAS_POSTER)
                },
                RegularPagingSource(client) { key, size ->
                    client.searchCommunity(size, joinStatusSearch, word, target, key, PosterSearch.NO_POSTER)
                }
            )
        },
    ) {
        sectionPagingSource(databaseSource, collectionName, listOf(Order.Desc("hasPoster"), Order.Desc("id")))
    }.flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class RoomsViewModel(
    client: HttpClient,
    databaseSource: DatabaseSource,
    private val joinStatusSearch: JoinStatusSearch,
    private val word: String,
    val community: PrimaryKey? = null,
) : PagingViewModel<PrimaryKey, RoomInfo>() {
    private val collectionName: String = "rooms_${word}_$community"

    override val flow: Flow<PagingData<RoomInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = singleSourceMediator(
            databaseSource,
            collectionName,
            RegularPagingSource(client) { key, size ->
                searchRooms(size, key, joinStatusSearch, word, community)
            }
        ),
    ) {
        singleSourceDatabaseSource(collectionName, databaseSource)
    }.flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class TopicsViewModel(
    client: HttpClient,
    private val databaseSource: DatabaseSource,
    id: PrimaryKey,
    val type: ObjectType? = null,
) :
    PagingViewModel<SectionLoadParams<PrimaryKey>, TopicInfo>() {
    private val collectionName: String = "topics_$id"

    override val flow: Flow<PagingData<TopicInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = sectionMediator<TopicInfo>(
            client,
            collectionName,
            databaseSource,
            {
                with(databaseSource.getCollection("topics")) {
                    save(it.id, it)
                    it.aid?.let { aid -> save(aid, it) }
                }
            }
        ) {
            if (id == DEFAULT_PRIMARY_KEY) {
                listOf(RegularPagingSource(client) { loadKey, size ->
                    getRecommendTopics(loadKey, size)
                })
            } else {
                listOf(
                    RegularPagingSource(client) { loadKey, size ->
                        getTopicList(type, id, loadKey, size, TopicPinSearch.PINNED)
                    },
                    RegularPagingSource(client) { loadKey, size ->
                        getTopicList(type, id, loadKey, size, TopicPinSearch.UNPINNED)
                    }
                )
            }
        },
    ) {
        sectionPagingSource(databaseSource, collectionName, listOf(Order.Asc("pinned"), Order.Desc("id"))) {
            processEncryptedTopic(this).map {
                extractHeadlineIfPlain(it)
            }
        }
    }.flow.cachedIn(viewModelScope)
}

private fun extractHeadlineIfPlain(it: TopicInfo): TopicInfo {
    val content = it.content
    return if (content is TopicContent.Plain) {
        it.copy(content = TopicContent.Extracted(extractMarkdownHeadline(content.plain), content.list, content.plain))
    } else {
        it
    }
}

abstract class RoomViewModel(client: HttpClient) :
    SimpleViewModel<RoomInfo>(client) {
    val dialog = DialogSaveState()

}

class IdRoomViewModel(client: HttpClient, databaseSource: DatabaseSource, communityId: PrimaryKey) : RoomViewModel(
    client
) {
    override val handler: LoadingHandler<RoomInfo> =
        CachedLoadingHandler(
            databaseSource,
            "rooms",
            viewModelScope,
            Expression.IdEq("id", communityId),
            {
                client.getRoomInfo(communityId)
            },
            serializer<RoomInfo>()
        ) { data, t ->
            save(communityId, data)
            save(t.aid, data)
        }
}

class AidRoomViewModel(client: HttpClient, databaseSource: DatabaseSource, aid: String) : RoomViewModel(client) {
    override val handler: LoadingHandler<RoomInfo> =
        CachedLoadingHandler(
            databaseSource,
            "rooms",
            viewModelScope,
            Expression.StrEq("aid", aid),
            {
                client.getRoomInfoByAid(aid)
            },
            serializer<RoomInfo>()
        ) { data, t ->
            save(aid, data)
            save(t.id, data)
        }
}

@OptIn(ExperimentalPagingApi::class)
class TopicSearchViewModel(
    client: HttpClient,
    databaseSource: DatabaseSource,
    word: List<String>,
    parentId: PrimaryKey?,
    parentType: ObjectType?,
) :
    PagingViewModel<PrimaryKey, TopicInfo>() {
    private val collectionName: String = "topics_search_${word}_$parentId"

    override val flow: Flow<PagingData<TopicInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = singleSourceMediator(
            databaseSource,
            collectionName,
            RegularPagingSource(client) { key, size ->
                client.searchTopics(size, word, parentId, parentType, key)
            }
        ),
    ) {
        singleSourceDatabaseSource(collectionName, databaseSource)
    }.flow.cachedIn(viewModelScope)
}

class MediaListViewModel(
    client: HttpClient,
    databaseSource: DatabaseSource,
    private val objectId: PrimaryKey,
    private val objectType: ObjectType
) :
    PagingViewModel<PrimaryKey, MediaInfo>() {
    private val collectionName = "medias_$objectId"

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<MediaInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = singleSourceMediator(
            databaseSource,
            collectionName,
            RegularPagingSource(client) { key, size ->
                client.getMediaList(objectId, objectType, key, size)
            }
        ),
    ) {
        singleSourceDatabaseSource(collectionName, databaseSource)
    }.flow.cachedIn(viewModelScope)
}

class AllMediaListViewModel(client: HttpClient, private val objectId: PrimaryKey, private val objectType: ObjectType) :
    SimpleViewModel<ServerResponse<MediaInfo>>(client) {
    override val handler: LoadingHandler<ServerResponse<MediaInfo>> = SimpleLoadingHandler(viewModelScope) {
        client.getAllMediaList(objectId, objectType)
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

abstract class UserViewModel(
    client: HttpClient
) :
    SimpleViewModel<UserInfo>(client)

class IdUserViewModel(client: HttpClient, databaseSource: DatabaseSource, id: PrimaryKey) : UserViewModel(
    client
) {
    override val handler: LoadingHandler<UserInfo> =
        CachedLoadingHandler(
            databaseSource,
            "users",
            viewModelScope,
            Expression.IdEq("id", id),
            {
                client.getUserInfo(id)
            },
            serializer<UserInfo>()
        ) { data, t ->
            save(id, data)
            t.aid?.let { save(it, data) }
        }
}

class AidUserViewModel(client: HttpClient, databaseSource: DatabaseSource, aid: String) : UserViewModel(client) {
    override val handler: LoadingHandler<UserInfo> =
        CachedLoadingHandler(
            databaseSource,
            "users",
            viewModelScope,
            Expression.StrEq("aid", aid),
            {
                client.getUserInfoByAid(aid)
            },
            serializer<UserInfo>()
        ) { data, t ->
            save(aid, data)
            save(t.id, data)
        }
}

@OptIn(ExperimentalPagingApi::class)
class MemberViewModel(
    client: HttpClient,
    databaseSource: DatabaseSource,
    objectId: PrimaryKey,
    word: String,
    objectType: ObjectType,
) :
    PagingViewModel<PrimaryKey, UserInfo>() {
    private val collectionName: String = "members_${objectId}_${word}_$"

    override val flow: Flow<PagingData<UserInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = singleSourceMediator(
            databaseSource,
            collectionName,
            RegularPagingSource(
                client
            ) { key, size ->
                when (objectType) {
                    ObjectType.COMMUNITY -> searchCommunityMembers(objectId, key, size, word)
                    ObjectType.ROOM -> searchRoomMembers(objectId, key, size, word)
                    else -> searchAllMembers(key, size, word)
                }
            }
        ),
    ) {
        singleSourceDatabaseSource(collectionName, databaseSource)
    }.flow.cachedIn(viewModelScope)
}

class ReactionsViewModel(client: HttpClient, private val objectId: PrimaryKey) :
    SimpleViewModel<ServerResponse<ReactionInfo>>(
        client
    ) {
    override val handler: LoadingHandler<ServerResponse<ReactionInfo>> = SimpleLoadingHandler(viewModelScope) {
        client.getReactions(objectId)
    }
}

abstract class TopicViewModel(client: HttpClient) :
    SimpleViewModel<TopicInfo>(client)

class IdTopicViewModel(client: HttpClient, databaseSource: DatabaseSource, topicId: PrimaryKey) : TopicViewModel(
    client
) {
    override val handler: LoadingHandler<TopicInfo> =
        CachedLoadingHandler(
            databaseSource,
            "topics",
            viewModelScope,
            Expression.IdEq("id", topicId),
            {
                client.getTopicInfo(topicId)
            },
            serializer<TopicInfo>()
        ) { data, t ->
            save(topicId, data)
            t.aid?.let { save(it, data) }
        }
}

class AidTopicViewModel(client: HttpClient, databaseSource: DatabaseSource, aid: String) : TopicViewModel(client) {
    override val handler: LoadingHandler<TopicInfo> =
        CachedLoadingHandler(
            databaseSource,
            "topics",
            viewModelScope,
            Expression.StrEq("aid", aid),
            {
                client.getTopicInfoByAid(aid)
            },
            serializer<TopicInfo>()
        ) { data, t ->
            save(aid, data)
            save(t.id, data)
        }
}

class RoomKeysViewModel(client: HttpClient, private val id: PrimaryKey, val private: Boolean) :
    SimpleViewModel<List<Pair<PrimaryKey, String>>>(client) {
    override val handler: LoadingHandler<List<Pair<PrimaryKey, String>>> = SimpleLoadingHandler(viewModelScope) {
        runCatching {
            if (!this@RoomKeysViewModel.private) return@runCatching emptyList()
            val result = mutableListOf<Pair<PrimaryKey, String>>()
            var last: PrimaryKey? = null
            while (true) {
                val list =
                    this@RoomKeysViewModel.client.requestRoomKeys(this@RoomKeysViewModel.id, last, 100).getOrThrow()
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
    client: HttpClient,
    databaseSource: DatabaseSource,
    uid: PrimaryKey,
    searchType: TitleSearchType,
    status: TitleStatus? = null,
    type: TitleType? = null,
    scopeId: PrimaryKey? = null,
    private val collectionName: String = "titles_${uid}_${searchType}_${status}_${type}_$scopeId"
) : PagingViewModel<PrimaryKey, TitleInfo>() {
    override val flow: Flow<PagingData<TitleInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = singleSourceMediator(databaseSource, collectionName, RegularPagingSource(client) { key, size ->
            client.userTitles(uid, key, size, searchType, status, type, scopeId)
        }),
    ) {
        singleSourceDatabaseSource(collectionName, databaseSource)
    }.flow.cachedIn(viewModelScope)
}

class UploadViewModel(client: HttpClient, private val uploader: UploadSession, myUid: PrimaryKey) : ViewModel() {
    private val queue = Channel<Int> {
    }
    val handlers = uploader.list.mapIndexed { i, e ->
        FixedLoadingHandler<MediaInfo>({
            retry(i)
        }) to e
    }

    init {
        viewModelScope.launch {
            for (e in queue) {
                val (handler, clientFile) = handlers[e]
                handler.request {
                    runCatching {
                        upload(
                            client,
                            myUid ob ObjectType.USER,
                            clientFile.size,
                            clientFile.name,
                            clientFile.contentType
                        ) {
                            clientFile.source()?.buffered() ?: throw Exception("upload failed")
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
