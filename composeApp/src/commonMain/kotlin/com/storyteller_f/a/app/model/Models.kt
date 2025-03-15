package com.storyteller_f.a.app.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import com.storyteller_f.a.app.UploadSession
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.compontents.DialogSaveState
import com.storyteller_f.a.app.pages.topic.upload
import com.storyteller_f.a.app.updateDocument
import com.storyteller_f.a.app.updateDocumentInParent
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.*
import com.storyteller_f.shared.type.*
import com.storyteller_f.shared.utils.extractMarkdownHeadline
import io.ktor.client.*
import kotbase.Expression
import kotbase.Ordering
import kotbase.ktx.*
import kotlinx.coroutines.channels.Channel
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

class IdCommunityViewModel(client: HttpClient, communityId: PrimaryKey) : CommunityViewModel(client, {
    getCommunityInfo(communityId)
}) {
    override val handler: LoadingHandler<CommunityInfo> =
        buildCachedLoaderHandler(
            ::load,
            viewModelScope,
            getOrCreateCollection("communities"),
            { data, t ->
                save(kotbase.MutableDocument(communityId.toString(), data))
                save(kotbase.MutableDocument(t.aid, data))
            }
        ) {
            "id" equalTo communityId
        }

    init {
        load()
    }
}

class AidCommunityViewModel(client: HttpClient, aid: String) : CommunityViewModel(client, {
    getCommunityInfoByAid(aid)
}) {
    override val handler: LoadingHandler<CommunityInfo> = buildCachedLoaderHandler(
        ::load,
        viewModelScope,
        getOrCreateCollection("communities"),
        { data, t ->
            save(kotbase.MutableDocument(aid, data))
            save(kotbase.MutableDocument(t.id.toString(), data))
        }
    ) {
        "aid" equalTo aid
    }

    init {
        load()
    }
}

@OptIn(ExperimentalPagingApi::class)
class CommunitiesViewModel(
    client: HttpClient,
    private val joinStatusSearch: JoinStatusSearch,
    private val word: String,
    private val target: PrimaryKey? = null,
    private val collectionName: String = "communities_${word}_${target}_$joinStatusSearch"
) : PagingViewModel<SectionLoadParams<PrimaryKey>, CommunityInfo>(
    client,
    CustomRemoteMediator(
        collectionName,
        {
            if (it != null) {
                getSectionLoadParams(collectionName, it.id)
            } else {
                SectionLoadParams(1, null)
            }
        },
        { info, key ->
            saveSectionLoadParams(collectionName, key, info.id)
            getOrCreateCollection(collectionName).save(
                kotbase.MutableDocument(
                    info.id.toString(),
                    Json.encodeToString(info)
                )
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
            select = select(all()),
            collectionName = collectionName,
            key = { info ->
                info?.id?.let {
                    SectionLoadParams(0, it)
                }
            },
            queryProvider = {
                where {
                    val param = it?.param
                    if (param != null) {
                        "id" lessThan param
                    } else {
                        Expression.intValue(0) equalTo Expression.intValue(0)
                    }
                }.orderBy {
                    Ordering.expression(Expression.property("poster").isNotValued())
                    "id".descending()
                }
            },
            jsonStringMapper = { json: String ->
                runCatching {
                    Json.decodeFromString<CommunityInfo>(json)
                }.getOrNull()
            }
        )
    }
)

@OptIn(ExperimentalPagingApi::class)
class RoomsViewModel(
    private val joinStatusSearch: JoinStatusSearch,
    private val word: String,
    val community: PrimaryKey? = null,
    client: HttpClient,
    private val collectionName: String = "rooms_${word}_$community"
) : PagingViewModel<PrimaryKey, RoomInfo>(
    client,
    singleSourceMediator(
        collectionName,
        RegularPagingSource(client) { key, size ->
            searchRooms(size, key, joinStatusSearch, word, community)
        }
    ),
    {
        singleSourceDatabaseSource(collectionName)
    }
)

@OptIn(ExperimentalPagingApi::class)
class TopicsViewModel(
    id: PrimaryKey,
    val type: ObjectType? = null,
    client: HttpClient,
    private val collectionName: String = "topics_$id"
) :
    PagingViewModel<SectionLoadParams<PrimaryKey>, TopicInfo>(
        client,
        CustomRemoteMediator(
            collectionName,
            { topicInfo ->
                if (topicInfo != null) {
                    getSectionLoadParams(collectionName, topicInfo.id)
                } else {
                    SectionLoadParams(1, null)
                }
            },
            { info, key ->
                saveSectionLoadParams(collectionName, key, info.id)
                updateDocument(collectionName, info)
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
                select = select(all()),
                collectionName = collectionName,
                key = { info ->
                    info?.id?.let {
                        SectionLoadParams(0, it)
                    }
                },
                queryProvider = {
                    where {
                        val param = it?.param
                        if (param != null) {
                            "id" lessThan param
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
        }
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
                        if (select(all()).from(getOrCreateCollection("topics_0"))
                                .where(Expression.property("id").equalTo(topicInfo.id)).execute().next() != null
                        ) {
                            updateDocument("topics_0", extractHeadlineIfPlain(topicInfo))
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
        buildCachedLoaderHandler(
            ::load,
            viewModelScope,
            getOrCreateCollection("rooms"),
            { data, t ->
                save(communityId.toString(), data)
                save(t.aid, data)
            }
        ) {
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
        buildCachedLoaderHandler(::load, viewModelScope, getOrCreateCollection("rooms"), { data, t ->
            save(aid, data)
            save(t.id, data)
        }) {
            "aid" equalTo aid
        }

    init {
        load()
    }
}

@OptIn(ExperimentalPagingApi::class)
class TopicSearchViewModel(
    word: List<String>,
    parentId: PrimaryKey?,
    parentType: ObjectType?,
    client: HttpClient,
    private val collectionName: String = "topics_search_${word}_$parentId"
) :
    PagingViewModel<PrimaryKey, TopicInfo>(
        client = client,
        remoteMediator = singleSourceMediator(
            collectionName,
            RegularPagingSource(client) { key, size ->
                client.searchTopics(size, word, parentId, parentType, key)
            }
        ),
        sourceBuilder = {
            singleSourceDatabaseSource(collectionName)
        }
    )

private inline fun <reified T : Identifiable> singleSourceDatabaseSource(collectionName: String) =
    CustomQueryPagingSource(
        select = select(all()),
        collectionName = collectionName,
        key = { info ->
            info?.id
        },
        queryProvider = {
            where {
                val param = it
                if (param != null) {
                    "id" lessThan param
                } else {
                    Expression.intValue(0) equalTo Expression.intValue(0)
                }
            }.orderBy {
                Ordering.expression(Expression.property("poster").isNotValued())
                "id".descending()
            }
        },
        jsonStringMapper = { json: String ->
            runCatching {
                Json.decodeFromString<T>(json)
            }.getOrNull()
        }
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
        buildCachedLoaderHandler(
            ::load,
            viewModelScope,
            getOrCreateCollection("users"),
            { data, t ->
                save(communityId, data)
                t.aid?.let { save(it, data) }
            }
        ) {
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
        buildCachedLoaderHandler(::load, viewModelScope, getOrCreateCollection("users"), { data, t ->
            save(aid, data)
            save(t.id, data)
        }) {
            "aid" equalTo aid
        }

    init {
        load()
    }
}

@OptIn(ExperimentalPagingApi::class)
class MemberViewModel(
    objectId: PrimaryKey,
    word: String,
    objectType: ObjectType,
    client: HttpClient,
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
            }
        ),
        sourceBuilder = {
            singleSourceDatabaseSource(collectionName)
        }
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
        buildCachedLoaderHandler(
            ::load,
            viewModelScope,
            getOrCreateCollection("topics"),
            { data, t ->
                save(topicId, data)
                t.aid?.let { save(it, data) }
            }
        ) {
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
        buildCachedLoaderHandler(::load, viewModelScope, getOrCreateCollection("topics"), { data, t ->
            save(aid, data)
            save(t.id, data)
        }) {
            "aid" equalTo aid
        }

    init {
        load()
    }
}

class RoomKeysViewModel(private val id: PrimaryKey, private: Boolean, client: HttpClient) :
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
        {
            it?.id
        },
        { info, _ ->
            getOrCreateCollection(collectionName).save(
                info.id,
                Json.encodeToString(info)
            )
        },
        RegularPagingSource(client) { key, size ->
            client.userTitles(uid, key, size, searchType, status, type, scopeId)
        }
    ),
    sourceBuilder = {
        singleSourceDatabaseSource(collectionName)
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
