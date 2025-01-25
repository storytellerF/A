package com.storyteller_f.a.app.model

import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.compontents.DialogSaveState
import com.storyteller_f.a.app.pages.topic.processEncryptedTopic
import com.storyteller_f.a.app.updateDocument
import com.storyteller_f.a.app.updateDocumentInParent
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.*
import com.storyteller_f.shared.utils.extractMarkdownHeadline
import io.github.aakira.napier.Napier
import io.ktor.client.*
import kotbase.Expression
import kotbase.ktx.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class OnCommunityJoined(val newInfo: CommunityInfo)
data class OnCommunityExited(val newInfo: CommunityInfo)

data class OnMediaUploaded(val mediaInfo: MediaInfo)
data class OnRoomJoined(val newInfo: RoomInfo)
data class OnRoomExited(val newInfo: RoomInfo)

data class OnUpdateUser(val newUser: UserInfo)

class CommunityViewModel(private val requestInfo: suspend HttpClient.() -> Result<CommunityInfo>, client: HttpClient) :
    SimpleViewModel<CommunityInfo>(client) {
    val dialog = DialogSaveState()

    constructor(communityId: PrimaryKey, client: HttpClient) : this({
        getCommunityInfo(communityId, LoginViewModel.currentIsAlreadySignUp)
    }, client)

    constructor(communityAid: String, client: HttpClient) : this({
        getCommunityInfoByAid(communityAid, LoginViewModel.currentIsAlreadySignUp)
    }, client)

    init {
        load()
        viewModelScope.launch {
            bus.collect { i ->
                val id = handler.data.value?.id
                when (i) {
                    is OnCommunityJoined -> {
                        if (i.newInfo.id == id) {
                            update(i.newInfo)
                        }
                    }

                    is OnCommunityExited -> {
                        if (i.newInfo.id == id) {
                            update(i.newInfo)
                        }
                    }
                }
            }
        }
    }

    override suspend fun loadInternal() = requestInfo(client)
}

@OptIn(ExperimentalPagingApi::class)
class CommunitiesViewModel(
    private val joinStatusSearch: JoinStatusSearch,
    private val word: String,
    private val target: PrimaryKey? = null,
    client: HttpClient
) : PagingViewModel<PrimaryKey, CommunityInfo>(
    {
        SimplePagingSource {
            serviceCatching {
                client.searchCommunity(10, joinStatusSearch, word, target, it).getOrThrow()
            }.map {
                APagingData(it.data, it.pagination?.nextPageToken?.toPrimaryKeyOrNull())
            }
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
        SimplePagingSource {
            serviceCatching {
                client.searchRooms(10, it, joinStatusSearch, word, community).getOrThrow()
            }.map {
                APagingData(it.data, it.pagination?.nextPageToken?.toPrimaryKey())
            }
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
                        "id".descending()
                    }
                },
                jsonStringMapper = { json: String ->
                    kotlin.runCatching {
                        Json.decodeFromString<TopicInfo>(json)
                    }.getOrNull()
                }
            )
        },
        TopicsRemoteMediator("topics$id") { loadKey, size ->
            val info = when {
                id == DEFAULT_PRIMARY_KEY -> client.getRecommendTopics(loadKey, size)
                type == ObjectType.ROOM -> client.getRoomTopics(id, loadKey, size)
                type == ObjectType.COMMUNITY -> client.getCommunityTopics(id, loadKey, size)
                type == ObjectType.USER -> client.getUserTopics(id, loadKey, size)
                else -> client.getTopicTopics(id, loadKey, size)
            }.getOrThrow()
            info.copy(processEncryptedTopic(info.data).map {
                extractHeadlineIfPlain(it)
            })
        },
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

@OptIn(ExperimentalPagingApi::class)
class TopicsRemoteMediator(
    private val collectionName: String,
    val networkService: suspend (PrimaryKey?, Int) -> ServerResponse<TopicInfo>
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
            val response = networkService(loadKey, state.config.pageSize)
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
                endOfPaginationReached = response.data.isEmpty()
            )
        } catch (e: Exception) {
            Napier.e(e, tag = "pagination") {
                "mediator load error"
            }
            MediatorResult.Error(e)
        }
    }
}

class RoomViewModel(private val requestInfo: suspend HttpClient.() -> Result<RoomInfo>, client: HttpClient) :
    SimpleViewModel<RoomInfo>(
        client
    ) {
    val dialog = DialogSaveState()

    constructor(roomId: PrimaryKey, client: HttpClient) : this({
        getRoomInfo(roomId)
    }, client)

    constructor(roomAid: String, client: HttpClient) : this({
        getRoomInfoByAid(roomAid)
    }, client)

    init {
        load()
        viewModelScope.launch {
            bus.collect { i ->
                val id = handler.data.value?.id
                when (i) {
                    is OnRoomJoined -> {
                        if (i.newInfo.id == id) {
                            update(i.newInfo)
                        }
                    }

                    is OnRoomExited -> {
                        if (i.newInfo.id == id) {
                            update(i.newInfo)
                        }
                    }
                }
            }
        }
    }

    override suspend fun loadInternal() = requestInfo(client)
}

@OptIn(ExperimentalPagingApi::class)
class TopicSearchViewModel(word: List<String>, parentId: PrimaryKey?, parentType: ObjectType?, client: HttpClient) :
    PagingViewModel<PrimaryKey, TopicInfo>(
        {
            SimplePagingSource {
                serviceCatching {
                    client.searchTopics(10, word, parentId, parentType, it).getOrThrow()
                }.map {
                    APagingData(it.data, it.pagination?.nextPageToken?.toPrimaryKeyOrNull())
                }
            }
        },
        client = client
    )

class MediaListViewModel(private val objectId: PrimaryKey, private val objectType: ObjectType, client: HttpClient) :
    SimpleViewModel<ServerResponse<MediaInfo>>(client) {
    init {
        load()
        viewModelScope.launch {
            bus.collect {
                if (it is OnMediaUploaded) {
                    val old = handler.data.value ?: ServerResponse(emptyList())
                    update(old.copy(data = old.data.toMutableList().apply {
                        add(0, it.mediaInfo)
                    }))
                }
            }
        }
    }

    override suspend fun loadInternal() = client.getMediaList(objectId, objectType)
}

class UserViewModel(private val requestInfo: suspend HttpClient.() -> Result<UserInfo>, client: HttpClient) :
    SimpleViewModel<UserInfo>(
        client
    ) {
    constructor(userId: PrimaryKey, client: HttpClient) : this({
        getUserInfo(userId)
    }, client)

    constructor(userAid: String, client: HttpClient) : this({
        getUserInfoByAid(userAid)
    }, client)

    init {
        load()
        viewModelScope.launch {
            bus.collect {
                if (it is OnUpdateUser && it.newUser.id == handler.data.value?.id) {
                    update(it.newUser)
                }
            }
        }
    }

    override suspend fun loadInternal() = requestInfo(client)
}

@OptIn(ExperimentalPagingApi::class)
class MemberViewModel(objectId: PrimaryKey, word: String, objectType: ObjectType, client: HttpClient) :
    PagingViewModel<PrimaryKey, UserInfo>(
        {
            RegularPagingSource(
                {
                    when (objectType) {
                        ObjectType.COMMUNITY -> searchCommunityMembers(objectId, it, 10, word)
                        ObjectType.ROOM -> searchRoomMembers(objectId, it, 10, word)
                        else -> searchAllMembers(it, 10, word)
                    }.getOrThrow()
                },
                client
            )
        },
        client = client
    )

data class OnTopicChanged(val topicInfo: TopicInfo)
data class OnAddReaction(val topicId: PrimaryKey, val emoji: String)
data class OnRemoveReaction(val topicId: PrimaryKey, val emoji: String)

class ReactionsViewModel(private val objectId: PrimaryKey, client: HttpClient) :
    SimpleViewModel<ServerResponse<ReactionInfo>>(
        client
    ) {
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

class TopicViewModel(private val requestInfo: suspend HttpClient.() -> Result<TopicInfo>, client: HttpClient) :
    SimpleViewModel<TopicInfo>(client) {
    constructor(topicId: PrimaryKey, client: HttpClient) : this({
        getTopicInfo(topicId)
    }, client)

    constructor(topicAid: String, client: HttpClient) : this({
        getTopicInfoByAid(topicAid)
    }, client)

    init {
        load()
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

class RoomKeysViewModel(private val id: PrimaryKey, private: Boolean, client: HttpClient) :
    SimpleViewModel<List<Pair<PrimaryKey, String>>>(client) {

    init {
        if (private) {
            load()
        }
    }

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
