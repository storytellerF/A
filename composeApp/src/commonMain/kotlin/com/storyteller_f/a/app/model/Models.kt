package com.storyteller_f.a.app.model

import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.compontents.DialogSaveState
import com.storyteller_f.a.app.updateDocument
import com.storyteller_f.a.app.updateDocumentInParent
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.obj.TitleSearchType
import com.storyteller_f.shared.obj.TopicPinSearch
import com.storyteller_f.shared.type.*
import com.storyteller_f.shared.utils.extractMarkdownHeadline
import io.github.aakira.napier.Napier
import io.ktor.client.*
import kotbase.Expression
import kotbase.ktx.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class OnTopicChanged(val topicInfo: TopicInfo)

data class OnMediaUploaded(val mediaInfos: List<MediaInfo>)

data class OnUserUpdated(val newUser: UserInfo)

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
    private val requestInfo: suspend HttpClient.() -> Result<CommunityInfo>,
    client: HttpClient
) :
    SimpleViewModel<CommunityInfo>(client) {
    val dialog = DialogSaveState()

    init {
        viewModelScope.launch {
            bus.collect { i ->
                val id = handler.data.value?.id
                when (i) {
                    is OnCommunityJoined -> {
                        if (i.info.id == id) {
                            update(i.info)
                        }
                    }

                    is OnCommunityExited -> {
                        if (i.info.id == id) {
                            update(i.info)
                        }
                    }

                    is OnCommunityUpdated -> {
                        if (i.info.id == id) {
                            update(i.info)
                        }
                    }
                }
            }
        }
    }

    override suspend fun loadInternal() = requestInfo(client)
}

class IdCommunityViewModel(client: HttpClient, communityId: PrimaryKey) : CommunityViewModel({
    getCommunityInfo(communityId)
}, client) {
    override val handler: LoadingHandler<CommunityInfo> =
        buildCachedLoaderHandler(viewModelScope, ::load, getOrCreateCollection("communities"), communityId.toString()) {
            "id" equalTo communityId
        }
    init {
        load()
    }
}

class AidCommunityViewModel(client: HttpClient, aid: String) : CommunityViewModel({
    getCommunityInfoByAid(aid)
}, client) {
    override val handler: LoadingHandler<CommunityInfo> = buildCachedLoaderHandler(
        viewModelScope,
        ::load,
        getOrCreateCollection("communities"),
        aid,
    ) {
        "aid" equalTo aid
    }
    init {
        load()
    }
}

@OptIn(ExperimentalPagingApi::class)
class CommunitiesViewModel(
    private val joinStatusSearch: JoinStatusSearch,
    private val word: String,
    private val target: PrimaryKey? = null,
    client: HttpClient
) : PagingViewModel<PrimaryKey, CommunityInfo>(
    {
        RegularPagingSource(client) {
            client.searchCommunity(10, joinStatusSearch, word, target, it)
        }
    },
    client = client
)

@OptIn(ExperimentalPagingApi::class)
class RoomsViewModel(
    private val joinStatusSearch: JoinStatusSearch,
    private val word: String,
    val community: PrimaryKey? = null,
    client: HttpClient
) : PagingViewModel<PrimaryKey, RoomInfo>(
    {
        RegularPagingSource(client) {
            client.searchRooms(10, it, joinStatusSearch, word, community)
        }
    },
    client = client
)

@OptIn(ExperimentalPagingApi::class)
class TopicsViewModel(id: PrimaryKey, val type: ObjectType? = null, client: HttpClient) :
    PagingViewModel<PrimaryKey, TopicInfo>(
        {
            CustomQueryPagingSource(
                select = select(all()),
                collectionName = "topics$id",
                queryProvider = {
                    where {
                        if (it != null) {
                            "id" lessThan it
                        } else {
                            Expression.intValue(0) equalTo Expression.intValue(0)
                        }
                    }.orderBy {
                        "pinned".ascending()
                        "id".descending()
                    }
                },
                jsonStringMapper = { json: String ->
                    runCatching {
                        Json.decodeFromString<TopicInfo>(json)
                    }.getOrNull()
                },
                extraProcessor = {
                    processEncryptedTopic(this).map {
                        extractHeadlineIfPlain(it)
                    }
                }
            )
        },
        TopicsRemoteMediator("topics$id", object : NetworkService<TopicInfo> {
            var loadPinned = true
            override suspend fun invoke(loadKey: PrimaryKey?, size: Int): ServerResponse<TopicInfo> {
                return if (loadPinned && id != DEFAULT_PRIMARY_KEY) {
                    val firstPage = loadOnePageTopicList(loadKey, size, TopicPinSearch.PINNED)
                    if (firstPage.pagination?.nextPageToken == null) {
                        loadPinned = false
                        val secondPage = loadOnePageTopicList(null, size, TopicPinSearch.UNPINNED)
                        secondPage.copy(data = firstPage.data + secondPage.data)
                    } else {
                        firstPage
                    }
                } else {
                    loadOnePageTopicList(loadKey, size, TopicPinSearch.UNPINNED)
                }
            }

            private suspend fun loadOnePageTopicList(
                loadKey: PrimaryKey?,
                size: Int,
                pinType: TopicPinSearch
            ): ServerResponse<TopicInfo> {
                return when {
                    id == DEFAULT_PRIMARY_KEY -> client.getRecommendTopics(loadKey, size)
                    type == ObjectType.ROOM -> client.getRoomTopics(id, loadKey, size, pinType)
                    type == ObjectType.COMMUNITY -> client.getCommunityTopics(id, loadKey, size, pinType)
                    type == ObjectType.USER -> client.getUserTopics(id, loadKey, size, pinType)
                    else -> client.getTopicTopics(id, loadKey, size, pinType)
                }.getOrThrow()
            }
        }),
        client
    ) {
    init {
        viewModelScope.launch {
            bus.collect { value ->
                if (value is OnTopicChanged) {
                    val topicInfo = value.topicInfo
                    if (id == topicInfo.parentId) {
                        updateDocumentInParent(extractHeadlineIfPlain(topicInfo))
                    } else if (id == DEFAULT_PRIMARY_KEY) {
                        // 尝试更新到推荐
                        if (select(all()).from(getOrCreateCollection("topics0"))
                                .where(Expression.property("id").equalTo(topicInfo.id)).execute().next() != null
                        ) {
                            updateDocument("topics0", extractHeadlineIfPlain(topicInfo))
                        }
                    }
                }
            }
        }
    }
}

private fun extractHeadlineIfPlain(it: TopicInfo): TopicInfo {
    val content = it.content
    return if (content is TopicContent.Plain) {
        it.copy(content = TopicContent.Extracted(extractMarkdownHeadline(content.plain), content.list, content.plain))
    } else {
        it
    }
}

interface NetworkService<T> {
    suspend fun invoke(loadKey: PrimaryKey?, size: Int): ServerResponse<T>
}

@OptIn(ExperimentalPagingApi::class)
class TopicsRemoteMediator(
    private val collectionName: String,
    private val networkService: NetworkService<TopicInfo>
) :
    RemoteMediator<PrimaryKey, TopicInfo>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<PrimaryKey, TopicInfo>
    ): MediatorResult {
        Napier.v(tag = "pagination") {
            "mediator load $loadType"
        }
        val loadKey = when (loadType) {
            LoadType.REFRESH -> null
            LoadType.PREPEND -> return MediatorResult.Success(
                endOfPaginationReached = true
            )

            LoadType.APPEND -> {
                val lastItem = state.lastItemOrNull()
                    ?: return MediatorResult.Success(
                        endOfPaginationReached = true
                    )

                lastItem.id
            }
        }
        return try {
            val response = networkService.invoke(loadKey, state.config.pageSize)
            if (loadType == LoadType.REFRESH) {
                database.deleteCollection(collectionName)
            }
            response.data.forEach {
                updateDocument(collectionName, it)
            }
            Napier.v(tag = "pagination") {
                "mediator success $loadKey"
            }
            MediatorResult.Success(
                endOfPaginationReached = response.pagination?.nextPageToken == null
            )
        } catch (e: Exception) {
            Napier.e(e, tag = "pagination") {
                "mediator load error"
            }
            MediatorResult.Error(e)
        }
    }
}

abstract class RoomViewModel(private val requestInfo: suspend HttpClient.() -> Result<RoomInfo>, client: HttpClient) :
    SimpleViewModel<RoomInfo>(client) {
    val dialog = DialogSaveState()

    init {
        viewModelScope.launch {
            bus.collect { i ->
                val id = handler.data.value?.id
                when (i) {
                    is OnRoomJoined -> {
                        if (i.info.id == id) {
                            update(i.info)
                        }
                    }

                    is OnRoomExited -> {
                        if (i.info.id == id) {
                            update(i.info)
                        }
                    }

                    is OnRoomUpdated -> {
                        if (i.info.id == id) {
                            update(i.info)
                        }
                    }
                }
            }
        }
    }

    override suspend fun loadInternal() = requestInfo(client)
}

class IdRoomViewModel(client: HttpClient, communityId: PrimaryKey) : RoomViewModel({
    getRoomInfo(communityId)
}, client) {
    override val handler: LoadingHandler<RoomInfo> =
        buildCachedLoaderHandler(viewModelScope, ::load, getOrCreateCollection("rooms"), communityId.toString()) {
            "id" equalTo communityId
        }
    init {
        load()
    }
}

class AidRoomViewModel(client: HttpClient, aid: String) : RoomViewModel({
    getRoomInfoByAid(aid)
}, client) {
    override val handler: LoadingHandler<RoomInfo> =
        buildCachedLoaderHandler(viewModelScope, ::load, getOrCreateCollection("rooms"), aid) {
            "aid" equalTo aid
        }
    init {
        load()
    }
}

@OptIn(ExperimentalPagingApi::class)
class TopicSearchViewModel(word: List<String>, parentId: PrimaryKey?, parentType: ObjectType?, client: HttpClient) :
    PagingViewModel<PrimaryKey, TopicInfo>(
        {
            RegularPagingSource(client) {
                client.searchTopics(10, word, parentId, parentType, it)
            }
        },
        client = client
    )

class MediaListViewModel(private val objectId: PrimaryKey, private val objectType: ObjectType, client: HttpClient) :
    SimpleViewModel<ServerResponse<MediaInfo>>(client) {
    override val handler: LoadingHandler<ServerResponse<MediaInfo>> = SimpleLoadingHandler(::load)

    init {
        load()
        viewModelScope.launch {
            bus.collect {
                if (it is OnMediaUploaded) {
                    val old = handler.data.value ?: ServerResponse(emptyList())
                    update(old.copy(data = old.data.toMutableList().apply {
                        addAll(0, it.mediaInfos)
                    }))
                }
            }
        }
    }

    override suspend fun loadInternal() = client.getMediaList(objectId, objectType)
}

abstract class UserViewModel(
    private val requestInfo: suspend HttpClient.() -> Result<UserInfo>,
    client: HttpClient
) :
    SimpleViewModel<UserInfo>(client) {

    init {
        viewModelScope.launch {
            bus.collect {
                if (it is OnUserUpdated && it.newUser.id == handler.data.value?.id) {
                    update(it.newUser)
                }
            }
        }
    }

    override suspend fun loadInternal() = requestInfo(client)
}

class IdUserViewModel(client: HttpClient, communityId: PrimaryKey) : UserViewModel({
    getUserInfo(communityId)
}, client) {
    override val handler: LoadingHandler<UserInfo> =
        buildCachedLoaderHandler(viewModelScope, ::load, getOrCreateCollection("users"), communityId.toString()) {
            "id" equalTo communityId
        }
    init {
        load()
    }
}

class AidUserViewModel(client: HttpClient, aid: String) : UserViewModel({
    getUserInfoByAid(aid)
}, client) {
    override val handler: LoadingHandler<UserInfo> =
        buildCachedLoaderHandler(viewModelScope, ::load, getOrCreateCollection("users"), aid) {
            "aid" equalTo aid
        }
    init {
        load()
    }
}

@OptIn(ExperimentalPagingApi::class)
class MemberViewModel(objectId: PrimaryKey, word: String, objectType: ObjectType, client: HttpClient) :
    PagingViewModel<PrimaryKey, UserInfo>(
        {
            RegularPagingSource(
                client
            ) {
                when (objectType) {
                    ObjectType.COMMUNITY -> searchCommunityMembers(objectId, it, 10, word)
                    ObjectType.ROOM -> searchRoomMembers(objectId, it, 10, word)
                    else -> searchAllMembers(it, 10, word)
                }
            }
        },
        client = client
    )

class ReactionsViewModel(private val objectId: PrimaryKey, client: HttpClient) :
    SimpleViewModel<ServerResponse<ReactionInfo>>(
        client
    ) {
    override val handler: LoadingHandler<ServerResponse<ReactionInfo>> = SimpleLoadingHandler(::load)

    init {
        load()
        viewModelScope.launch {
            bus.collect {
                if (it is OnAddReaction) {
                    if (it.topicId == objectId) {
                        handler.data.value?.data.orEmpty().map { info ->
                            when {
                                info.emoji != it.emoji -> info
                                info.hasReacted -> info
                                else -> info.copy(count = info.count, hasReacted = true)
                            }
                        }
                    }
                } else if (it is OnRemoveReaction) {
                    if (it.topicId == objectId) {
                        handler.data.value?.data.orEmpty().map { info ->
                            when {
                                info.emoji != it.emoji -> info
                                !info.hasReacted -> info
                                else -> info.copy(count = info.count - 1, hasReacted = false)
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun loadInternal() = client.getReactions(objectId)
}

abstract class TopicViewModel(private val requestInfo: suspend HttpClient.() -> Result<TopicInfo>, client: HttpClient) :
    SimpleViewModel<TopicInfo>(client) {

    init {
        viewModelScope.launch {
            bus.collect { value ->
                val id = handler.data.value?.id
                if (value is OnTopicChanged) {
                    if (value.topicInfo.id == id) {
                        update(value.topicInfo)
                    }
                }
            }
        }
    }

    override suspend fun loadInternal(): Result<TopicInfo> {
        return serviceCatching {
            val info = requestInfo(client).getOrThrow()
            processEncryptedTopic(listOf(info)).first()
        }
    }
}

class IdTopicViewModel(client: HttpClient, topicId: PrimaryKey) : TopicViewModel({
    getTopicInfo(topicId)
}, client) {
    override val handler: LoadingHandler<TopicInfo> =
        buildCachedLoaderHandler(viewModelScope, ::load, getOrCreateCollection("topics"), topicId.toString()) {
            "id" equalTo topicId
        }
    init {
        load()
    }
}

class AidTopicViewModel(client: HttpClient, aid: String) : TopicViewModel({
    getTopicInfoByAid(aid)
}, client) {
    override val handler: LoadingHandler<TopicInfo> =
        buildCachedLoaderHandler(viewModelScope, ::load, getOrCreateCollection("topics"), aid) {
            "aid" equalTo aid
        }
    init {
        load()
    }
}

class RoomKeysViewModel(private val id: PrimaryKey, private: Boolean, client: HttpClient) :
    SimpleViewModel<List<Pair<PrimaryKey, String>>>(client) {

    init {
        if (private) {
            load()
        }
    }

    override val handler: LoadingHandler<List<Pair<PrimaryKey, String>>> = SimpleLoadingHandler(::load)

    override suspend fun loadInternal() = runCatching {
        val result = mutableListOf<Pair<PrimaryKey, String>>()
        var last: PrimaryKey? = null
        while (true) {
            val list = client.requestRoomKeys(id, last, 100).getOrThrow()
            result.addAll(list.data)
            val nextKey = list.pagination?.nextPageToken?.toPrimaryKeyOrNull() ?: break
            last = nextKey
        }
        result
    }
}

@OptIn(ExperimentalPagingApi::class)
class TitlesViewModel(
    client: HttpClient,
    uid: PrimaryKey,
    searchType: TitleSearchType,
    status: TitleStatus? = null,
    type: TitleType? = null,
    scopeId: PrimaryKey? = null
) : PagingViewModel<PrimaryKey, TitleInfo>({
    RegularPagingSource(client) {
        client.userTitles(uid, it, 10, searchType, status, type, scopeId)
    }
}, client = client)
