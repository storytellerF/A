package com.storyteller_f.a.app.model

import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.compontents.DialogSaveState
import com.storyteller_f.a.app.topic.processEncryptedTopic
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKey
import com.storyteller_f.shared.type.toPrimaryKeyOrNull
import io.github.aakira.napier.Napier
import io.ktor.client.*
import kotbase.Expression
import kotbase.MutableDocument
import kotbase.ktx.all
import kotbase.ktx.orderBy
import kotbase.ktx.select
import kotbase.ktx.where
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class OnCommunityJoined(val communityId: PrimaryKey, val newInfo: CommunityInfo)
data class OnCommunityExited(val communityId: PrimaryKey, val newInfo: CommunityInfo)

class CommunityViewModel(private val requestInfo: suspend HttpClient.() -> Result<CommunityInfo>) :
    SimpleViewModel<CommunityInfo>() {
    val dialog = DialogSaveState()

    constructor(communityId: PrimaryKey) : this({
        getCommunityInfo(communityId, LoginViewModel.currentIsAlreadySignUp)
    })

    constructor(communityAid: String) : this({
        getCommunityInfoByAid(communityAid, LoginViewModel.currentIsAlreadySignUp)
    })

    init {
        load()
        viewModelScope.launch {
            bus.collect { i ->
                val id = handler.data.value?.id
                when (i) {
                    is OnCommunityJoined -> {
                        if (i.communityId == id) {
                            update(i.newInfo)
                        }
                    }

                    is OnCommunityExited -> {
                        if (i.communityId == id) {
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
    private val word: String
) : PagingViewModel<PrimaryKey, CommunityInfo>({
    SimplePagingSource {
        serviceCatching {
            client.searchCommunity(it, 10, joinStatusSearch, word).getOrThrow()
        }.map {
            APagingData(it.data, it.pagination?.nextPageToken?.toPrimaryKeyOrNull())
        }
    }
})

@OptIn(ExperimentalPagingApi::class)
class RoomsViewModel(
    private val joinStatusSearch: JoinStatusSearch,
    private val word: String,
    val community: PrimaryKey? = null
) : PagingViewModel<PrimaryKey, RoomInfo>({
    SimplePagingSource {
        serviceCatching {
            client.searchRooms(10, it, joinStatusSearch, word, community).getOrThrow()
        }.map {
            APagingData(it.data, it.pagination?.nextPageToken?.toPrimaryKey())
        }
    }
})

data class OnRoomJoined(val id: PrimaryKey, val newInfo: RoomInfo)
data class OnRoomExited(val id: PrimaryKey, val newInfo: RoomInfo)

@OptIn(ExperimentalPagingApi::class)
class TopicsViewModel(id: PrimaryKey, val type: ObjectType) : PagingViewModel<PrimaryKey, TopicInfo>({
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
}, TopicsRemoteMediator("topics$id") { loadKey ->
    val info = when (type) {
        ObjectType.ROOM -> client.getRoomTopics(id, loadKey, 20)
        ObjectType.COMMUNITY -> client.getCommunityTopics(id, loadKey, 20)
        else -> client.getTopicTopics(id, loadKey, 20)
    }.getOrThrow()
    info.copy(processEncryptedTopic(info.data))
})

@OptIn(ExperimentalPagingApi::class)
class TopicsRemoteMediator(
    private val collectionName: String,
    val networkService: suspend (PrimaryKey?) -> ServerResponse<TopicInfo>
) :
    RemoteMediator<PrimaryKey, TopicInfo>() {
    private val scope = database.defaultScope
    private val collection
        get() = scope.getCollection(collectionName) ?: database.createCollection(
            collectionName
        )

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
            val response = networkService(loadKey)
            if (loadType == LoadType.REFRESH) {
                database.deleteCollection(collectionName)
            }
            response.data.forEach {
                val rawId = it.id.toString(2)
                collection.save(
                    MutableDocument(
                        if (rawId.length == 64) {
                            rawId
                        } else {
                            "0$rawId"
                        },
                        Json.encodeToString(it)
                    )
                )
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

class RoomViewModel(private val requestInfo: suspend HttpClient.() -> Result<RoomInfo>) : SimpleViewModel<RoomInfo>() {
    val dialog = DialogSaveState()

    constructor(roomId: PrimaryKey) : this({
        requestRoomInfo(roomId, LoginViewModel.currentIsAlreadySignUp)
    })

    constructor(roomAid: String) : this({
        requestRoomInfoByAid(roomAid, LoginViewModel.currentIsAlreadySignUp)
    })

    init {
        load()
        viewModelScope.launch {
            bus.collect { i ->
                if (handler.state.value !is LoadingState.Loading) {
                    val id = handler.data.value?.id
                    when (i) {
                        is OnRoomJoined -> {
                            if (i.id == id) {
                                update(i.newInfo)
                            }
                        }

                        is OnRoomExited -> {
                            if (i.id == id) {
                                update(i.newInfo)
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun loadInternal() = requestInfo(client)
}

@OptIn(ExperimentalPagingApi::class)
class TopicSearchViewModel(word: List<String>, parentId: PrimaryKey?, parentType: ObjectType?) :
    PagingViewModel<PrimaryKey, TopicInfo>({
        SimplePagingSource {
            serviceCatching {
                client.searchTopics(it, 10, word, parentId, parentType).getOrThrow()
            }.map {
                APagingData(it.data, it.pagination?.nextPageToken?.toPrimaryKeyOrNull())
            }
        }
    })

class MediaListViewModel(private val objectId: PrimaryKey, private val objectType: ObjectType) :
    SimpleViewModel<ServerResponse<MediaInfo>>() {
    init {
        load()
    }

    override suspend fun loadInternal() = client.getMediaList(objectId, objectType)
}
class UserViewModel(private val requestInfo: suspend HttpClient.() -> Result<UserInfo>) : SimpleViewModel<UserInfo>() {
    constructor(userId: PrimaryKey) : this({
        getUserInfo(userId)
    })

    constructor(userAid: String) : this({
        getUserInfoByAid(userAid)
    })

    init {
        load()
    }

    override suspend fun loadInternal() = requestInfo(client)
}

@OptIn(ExperimentalPagingApi::class)
class WorldViewModel : PagingViewModel<PrimaryKey, TopicInfo>({
    SimplePagingSource {
        serviceCatching {
            client.getWorldTopics(it, 10, LoginViewModel.currentIsAlreadySignUp).getOrThrow()
        }.map {
            APagingData(it.data, it.pagination?.nextPageToken?.toPrimaryKeyOrNull())
        }
    }
})

@OptIn(ExperimentalPagingApi::class)
class MemberViewModel(private val objectId: PrimaryKey, private val word: String, private val objectType: ObjectType) :
    PagingViewModel<PrimaryKey, UserInfo>({
        RegularPagingSource {
            when (objectType) {
                ObjectType.COMMUNITY -> searchCommunityMembers(objectId, it, 10, word)
                ObjectType.ROOM -> searchRoomMembers(objectId, it, 10, word)
                else -> searchAllMembers(it, 10, word)
            }.getOrThrow()
        }
    })

data class OnTopicChanged(val topicInfo: TopicInfo)

class ReactionsViewModel(private val objectId: PrimaryKey) : SimpleViewModel<ServerResponse<ReactionInfo>>() {
    init {
        load()
    }

    override suspend fun loadInternal() = client.getReactions(objectId)
}

class TopicViewModel(private val requestInfo: suspend HttpClient.() -> Result<TopicInfo>) :
    SimpleViewModel<TopicInfo>() {
    constructor(topicId: PrimaryKey) : this({
        getTopicInfo(topicId)
    })

    constructor(topicAid: String) : this({
        getTopicInfoByAid(topicAid)
    })

    constructor(topicInfo: TopicInfo) : this({
        Result.success(topicInfo)
    })

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
class RoomKeysViewModel(private val id: PrimaryKey, private: Boolean) :
    SimpleViewModel<List<Pair<PrimaryKey, String>>>() {

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
