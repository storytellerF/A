package com.storyteller_f.a.app.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import com.storyteller_f.a.app.UploadSession
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.compontents.DialogSaveState
import com.storyteller_f.a.app.pages.topic.upload
import com.storyteller_f.a.app.saveTopicInDatabase
import com.storyteller_f.a.app.saveTopicInDatabaseByParent
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.*
import com.storyteller_f.shared.type.*
import com.storyteller_f.shared.utils.extractMarkdownHeadline
import io.ktor.client.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

data class OnTopicChanged(val topicInfo: TopicInfo)
data class OnTopicCreated(val topicInfo: TopicInfo)

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
    client: HttpClient,
    private val requestInfo: suspend HttpClient.() -> Result<CommunityInfo>
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

class IdCommunityViewModel(client: HttpClient, databaseSource: DatabaseSource, communityId: PrimaryKey) :
    CommunityViewModel(client, {
        getCommunityInfo(communityId)
    }) {
    override val handler: LoadingHandler<CommunityInfo> =
        CachedLoadingHandler(
            ::load,
            serializer<CommunityInfo>(),
            { data, t ->
                save(communityId.toString(), data)
                save(t.aid, data)
            },
            viewModelScope,
            Expression.One("id", communityId),
            "communities",
            databaseSource
        )

    init {
        load()
    }
}

class AidCommunityViewModel(client: HttpClient, databaseSource: DatabaseSource, aid: String) :
    CommunityViewModel(client, {
        getCommunityInfoByAid(aid)
    }) {
    private val serializer = serializer<CommunityInfo>()
    override val handler: LoadingHandler<CommunityInfo> = CachedLoadingHandler(
        ::load,
        serializer,
        { data, t ->
            save(aid, data)
            save(t.id.toString(), data)
        },
        viewModelScope,
        Expression.Two("aid", aid),
        "communities",
        databaseSource
    )

    init {
        load()
    }
}

@OptIn(ExperimentalPagingApi::class)
class CommunitiesViewModel(
    client: HttpClient,
    private val databaseSource: DatabaseSource,
    private val joinStatusSearch: JoinStatusSearch,
    private val word: String,
    private val target: PrimaryKey? = null,
    private val collectionName: String = "communities_${word}_${target}_$joinStatusSearch"
) : PagingViewModel<SectionLoadParams<PrimaryKey>, CommunityInfo>(
    client,
    CustomRemoteMediator(
        collectionName,
        databaseSource,
        {
            if (it != null) {
                getSectionLoadParams(collectionName, it.id, databaseSource)
            } else {
                SectionLoadParams(1, null)
            }
        },
        { info, key ->
            saveSectionLoadParams(collectionName, key, info.id, databaseSource)
            databaseSource.getCollection(collectionName).saveDocument(
                info.id.toString(),
                Json.encodeToString(info)
            )
        },
        SectionPagingSource(
            listOf(
                RegularPagingSource(client) { key, size ->
                    client.searchCommunity(size, joinStatusSearch, word, target, key, PosterSearch.HAS_POSTER)
                },
                RegularPagingSource(client) { key, size ->
                    client.searchCommunity(size, joinStatusSearch, word, target, key, PosterSearch.NO_POSTER)
                }
            )
        )
    ),
    {
        CustomQueryPagingSource(
            collectionName,
            databaseSource,
            serializer<CommunityInfo>(),
            { info ->
                info?.id?.let {
                    SectionLoadParams(0, it)
                }
            },
            {
                val param = it?.param
                if (param != null) {
                    Expression.Less("id", param)
                } else {
                    null
                }
            },
            listOf(Order.NotNull("poster"), Order.Desc("id"))
        )
    }
)

@OptIn(ExperimentalPagingApi::class)
class RoomsViewModel(
    client: HttpClient,
    databaseSource: DatabaseSource,
    private val joinStatusSearch: JoinStatusSearch,
    private val word: String,
    val community: PrimaryKey? = null,
    private val collectionName: String = "rooms_${word}_$community"
) : PagingViewModel<PrimaryKey, RoomInfo>(
    client,
    singleSourceMediator(
        collectionName,
        RegularPagingSource(client) { key, size ->
            searchRooms(size, key, joinStatusSearch, word, community)
        },
        databaseSource
    ),
    {
        singleSourceDatabaseSource(collectionName, databaseSource)
    }
)

@OptIn(ExperimentalPagingApi::class)
class TopicsViewModel(
    client: HttpClient,
    private val databaseSource: DatabaseSource,
    id: PrimaryKey,
    val type: ObjectType? = null,
    private val collectionName: String = "topics_$id"
) :
    PagingViewModel<SectionLoadParams<PrimaryKey>, TopicInfo>(
        client,
        CustomRemoteMediator(
            collectionName,
            databaseSource,
            { topicInfo ->
                if (topicInfo != null) {
                    getSectionLoadParams(collectionName, topicInfo.id, databaseSource)
                } else {
                    SectionLoadParams(1, null)
                }
            },
            { info, key ->
                saveSectionLoadParams(collectionName, key, info.id, databaseSource)
                saveTopicInDatabase(collectionName, info, databaseSource)
            },
            if (id == DEFAULT_PRIMARY_KEY) {
                SectionPagingSource(listOf(RegularPagingSource(client) { loadKey, size ->
                    getRecommendTopics(loadKey, size)
                }))
            } else {
                SectionPagingSource(
                    listOf(
                        RegularPagingSource(client) { loadKey, size ->
                            getTopicList(type, id, loadKey, size, TopicPinSearch.PINNED)
                        },
                        RegularPagingSource(client) { loadKey, size ->
                            getTopicList(type, id, loadKey, size, TopicPinSearch.UNPINNED)
                        }
                    )
                )
            }
        ),
        {
            CustomQueryPagingSource(
                collectionName = collectionName,
                databaseSource,
                serializer<TopicInfo>(),
                key = { info ->
                    info?.id?.let {
                        SectionLoadParams(0, it)
                    }
                },
                queryProvider = {
                    val param = it?.param
                    if (param != null) {
                        Expression.Less("id", param)
                    } else {
                        null
                    }
                },
                order = listOf(Order.Asc("pinned"), Order.Desc("id")),
                extraProcessor = {
                    processEncryptedTopic(this).map {
                        extractHeadlineIfPlain(it)
                    }
                }
            )
        }
    ) {
    init {
        viewModelScope.launch {
            bus.collect { value ->
                if (value is OnTopicChanged) {
                    val topicInfo = value.topicInfo
                    if (id == topicInfo.parentId) {
                        saveTopicInDatabaseByParent(extractHeadlineIfPlain(topicInfo), databaseSource)
                    } else if (id == DEFAULT_PRIMARY_KEY) {
                        // 尝试更新到推荐
                        if (databaseSource.getCollection("topics_0").exists(
                                Expression.One("id", topicInfo.id),
                            )
                        ) {
                            saveTopicInDatabase("topics_0", extractHeadlineIfPlain(topicInfo), databaseSource)
                        }
                    }
                } else if (value is OnTopicCreated) {
                    val topicInfo = value.topicInfo
                    if (id == topicInfo.parentId) {
                        saveTopicInDatabaseByParent(extractHeadlineIfPlain(topicInfo), databaseSource)
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

abstract class RoomViewModel(client: HttpClient, private val requestInfo: suspend HttpClient.() -> Result<RoomInfo>) :
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

class IdRoomViewModel(client: HttpClient, databaseSource: DatabaseSource, communityId: PrimaryKey) : RoomViewModel(
    client,
    {
        getRoomInfo(communityId)
    }
) {
    override val handler: LoadingHandler<RoomInfo> =
        CachedLoadingHandler(
            ::load,
            serializer<RoomInfo>(),
            { data, t ->
                save(communityId, data)
                save(t.aid, data)
            },
            viewModelScope,
            Expression.One("id", communityId),
            "rooms",
            databaseSource
        )

    init {
        load()
    }
}

class AidRoomViewModel(client: HttpClient, databaseSource: DatabaseSource, aid: String) : RoomViewModel(client, {
    getRoomInfoByAid(aid)
}) {
    override val handler: LoadingHandler<RoomInfo> =
        CachedLoadingHandler(
            ::load,
            serializer<RoomInfo>(),
            { data, t ->
                save(aid, data)
                save(t.id, data)
            },
            viewModelScope,
            Expression.Two("aid", aid),
            "rooms",
            databaseSource
        )

    init {
        load()
    }
}

@OptIn(ExperimentalPagingApi::class)
class TopicSearchViewModel(
    client: HttpClient,
    databaseSource: DatabaseSource,
    word: List<String>,
    parentId: PrimaryKey?,
    parentType: ObjectType?,
    private val collectionName: String = "topics_search_${word}_$parentId"
) :
    PagingViewModel<PrimaryKey, TopicInfo>(
        client = client,
        remoteMediator = singleSourceMediator(
            collectionName,
            RegularPagingSource(client) { key, size ->
                client.searchTopics(size, word, parentId, parentType, key)
            },
            databaseSource
        ),
        sourceBuilder = {
            singleSourceDatabaseSource(collectionName, databaseSource)
        }
    )

private inline fun <reified T : Identifiable> singleSourceDatabaseSource(
    collectionName: String,
    databaseSource: DatabaseSource
) =
    CustomQueryPagingSource(
        collectionName = collectionName,
        databaseSource,
        serializer<T>(),
        key = { info ->
            info?.id
        },
        queryProvider = {
            val param = it
            if (param != null) {
                Expression.Less("id", param)
            } else {
                null
            }
        },
        order = listOf(Order.Desc("id")),
    )

class MediaListViewModel(client: HttpClient, private val objectId: PrimaryKey, private val objectType: ObjectType) :
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
    client: HttpClient,
    private val requestInfo: suspend HttpClient.() -> Result<UserInfo>
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

class IdUserViewModel(client: HttpClient, databaseSource: DatabaseSource, communityId: PrimaryKey) : UserViewModel(
    client,
    {
        getUserInfo(communityId)
    }
) {
    override val handler: LoadingHandler<UserInfo> =
        CachedLoadingHandler(
            ::load,
            serializer<UserInfo>(),
            { data, t ->
                save(communityId, data)
                t.aid?.let { save(it, data) }
            },
            viewModelScope,
            Expression.One("id", communityId),
            "users",
            databaseSource
        )

    init {
        load()
    }
}

class AidUserViewModel(client: HttpClient, databaseSource: DatabaseSource, aid: String) : UserViewModel(client, {
    getUserInfoByAid(aid)
}) {
    override val handler: LoadingHandler<UserInfo> =
        CachedLoadingHandler(
            ::load,
            serializer<UserInfo>(),
            { data, t ->
                save(aid, data)
                save(t.id, data)
            },
            viewModelScope,
            Expression.Two("aid", aid),
            "users",
            databaseSource
        )

    init {
        load()
    }
}

@OptIn(ExperimentalPagingApi::class)
class MemberViewModel(
    client: HttpClient,
    databaseSource: DatabaseSource,
    objectId: PrimaryKey,
    word: String,
    objectType: ObjectType,
    private val collectionName: String = "members_${objectId}_${word}_$"
) :
    PagingViewModel<PrimaryKey, UserInfo>(
        client = client,
        remoteMediator = singleSourceMediator(
            collectionName,
            RegularPagingSource(
                client
            ) { key, size ->
                when (objectType) {
                    ObjectType.COMMUNITY -> searchCommunityMembers(objectId, key, size, word)
                    ObjectType.ROOM -> searchRoomMembers(objectId, key, size, word)
                    else -> searchAllMembers(key, size, word)
                }
            },
            databaseSource
        ),
        sourceBuilder = {
            singleSourceDatabaseSource(collectionName, databaseSource)
        }
    )

class ReactionsViewModel(client: HttpClient, private val objectId: PrimaryKey) :
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
                                else -> info.copy(count = info.count + 1, hasReacted = true)
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

abstract class TopicViewModel(client: HttpClient, private val requestInfo: suspend HttpClient.() -> Result<TopicInfo>) :
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

class IdTopicViewModel(client: HttpClient, databaseSource: DatabaseSource, topicId: PrimaryKey) : TopicViewModel(
    client,
    {
        getTopicInfo(topicId)
    }
) {
    override val handler: LoadingHandler<TopicInfo> =
        CachedLoadingHandler(
            ::load,
            serializer<TopicInfo>(),
            { data, t ->
                save(topicId, data)
                t.aid?.let { save(it, data) }
            },
            viewModelScope,
            Expression.One("id", topicId),
            "topics",
            databaseSource
        )

    init {
        load()
    }
}

class AidTopicViewModel(client: HttpClient, databaseSource: DatabaseSource, aid: String) : TopicViewModel(client, {
    getTopicInfoByAid(aid)
}) {
    override val handler: LoadingHandler<TopicInfo> =
        CachedLoadingHandler(::load, serializer<TopicInfo>(), { data, t ->
            save(aid, data)
            save(t.id, data)
        }, viewModelScope, Expression.Two("aid", aid), ("topics"), databaseSource)

    init {
        load()
    }
}

class RoomKeysViewModel(client: HttpClient, private val id: PrimaryKey, private: Boolean) :
    SimpleViewModel<List<Pair<PrimaryKey, String>>>(client) {
    override val handler: LoadingHandler<List<Pair<PrimaryKey, String>>> = SimpleLoadingHandler(::load)

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
) : PagingViewModel<PrimaryKey, TitleInfo>(
    client = client,
    remoteMediator = CustomRemoteMediator(
        collectionName,
        databaseSource,
        {
            it?.id
        },
        { info, _ ->
            databaseSource.getCollection(collectionName).save(
                info.id,
                Json.encodeToString(info)
            )
        },
        RegularPagingSource(client) { key, size ->
            client.userTitles(uid, key, size, searchType, status, type, scopeId)
        }
    ),
    sourceBuilder = {
        singleSourceDatabaseSource(collectionName, databaseSource)
    }
)

class UploadViewModel(client: HttpClient, private val uploader: UploadSession, myUid: PrimaryKey) : ViewModel() {
    private val queue = Channel<Int> {
    }
    val handlers = uploader.list.mapIndexed { i, e ->
        SimpleLoadingHandler<MediaInfo> {
            retry(i)
        } to e
    }

    init {
        viewModelScope.launch {
            for (e in queue) {
                val (handler, clientFile) = handlers[e]
                handler.request {
                    runCatching {
                        upload(client, clientFile.name, clientFile.contentType, clientFile.size, myUid, null) {
                            clientFile.readAll() ?: throw Exception("read file failed")
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

    private fun retry(index: Int) {
        viewModelScope.launch {
            if (index in 0 until uploader.list.size && handlers[index].first.state.value is LoadingState.Error) {
                queue.send(index)
            }
        }
    }
}
