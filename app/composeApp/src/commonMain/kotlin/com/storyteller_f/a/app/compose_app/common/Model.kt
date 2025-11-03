package com.storyteller_f.a.app.compose_app.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.app.compose_app.CustomUserSessionManager
import com.storyteller_f.a.app.core.common.CachedLoadingHandler
import com.storyteller_f.a.app.core.common.CompatPagingSource
import com.storyteller_f.a.app.core.common.CustomRemoteMediator
import com.storyteller_f.a.app.core.common.IntKeyConverter
import com.storyteller_f.a.app.core.common.IntermediatePagingSource
import com.storyteller_f.a.app.core.common.PagingViewModel
import com.storyteller_f.a.app.core.common.RegularPagingSource
import com.storyteller_f.a.app.core.common.SectionLoadParams
import com.storyteller_f.a.app.core.common.SectionPagingSource
import com.storyteller_f.a.app.core.common.SimpleViewModel
import com.storyteller_f.a.app.core.components.DialogSaveState
import com.storyteller_f.a.app.core.utils.SavedSession
import com.storyteller_f.a.app.core.utils.loadFontFromLocal
import com.storyteller_f.a.client.core.ClientSessionState
import com.storyteller_f.a.client.core.LoadingHandler
import com.storyteller_f.a.client.core.LoadingState
import com.storyteller_f.a.client.core.SimpleLoadingHandler
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.getChildAccounts
import com.storyteller_f.a.client.core.getCommunityInfo
import com.storyteller_f.a.client.core.getCommunityInfoByAid
import com.storyteller_f.a.client.core.getFavorites
import com.storyteller_f.a.client.core.getMediaByName
import com.storyteller_f.a.client.core.getMediaList
import com.storyteller_f.a.client.core.getReactions
import com.storyteller_f.a.client.core.getRecommendTopics
import com.storyteller_f.a.client.core.getRoomInfo
import com.storyteller_f.a.client.core.getRoomInfoByAid
import com.storyteller_f.a.client.core.getRoomMembersPublicKeys
import com.storyteller_f.a.client.core.getSubscriptions
import com.storyteller_f.a.client.core.getTopicInfo
import com.storyteller_f.a.client.core.getTopicInfoByAid
import com.storyteller_f.a.client.core.getTopicList
import com.storyteller_f.a.client.core.getUserInfo
import com.storyteller_f.a.client.core.getUserInfoByAid
import com.storyteller_f.a.client.core.getUserOverview
import com.storyteller_f.a.client.core.processEncryptedTopic
import com.storyteller_f.a.client.core.searchAllMembers
import com.storyteller_f.a.client.core.searchCommunity
import com.storyteller_f.a.client.core.searchCommunityMembers
import com.storyteller_f.a.client.core.searchRoomMembers
import com.storyteller_f.a.client.core.searchRooms
import com.storyteller_f.a.client.core.searchTopics
import com.storyteller_f.a.client.core.userTitles
import com.storyteller_f.shared.model.ChildAccountInfo
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.PosterSearch
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.TitleStatus
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.TopicPinSearch
import com.storyteller_f.shared.model.UserFavoriteInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserOverview
import com.storyteller_f.shared.model.UserPubKeyInfo
import com.storyteller_f.shared.model.UserSubscriptionInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.extractMarkdownHeadline
import com.storyteller_f.shared.utils.extractMarkdownMediaLink
import com.storyteller_f.storage.ChildAccountStorage
import com.storyteller_f.storage.CommunityCollection
import com.storyteller_f.storage.DownloadStatus
import com.storyteller_f.storage.MediasCollection
import com.storyteller_f.storage.ModelStorage
import com.storyteller_f.storage.ReactionCollection
import com.storyteller_f.storage.RoomCollection
import com.storyteller_f.storage.TitleCollection
import com.storyteller_f.storage.TopicCollection
import com.storyteller_f.storage.UploadCollection
import com.storyteller_f.storage.UploadInfo
import com.storyteller_f.storage.UserCollection
import com.storyteller_f.storage.UserFavoriteStorage
import com.storyteller_f.storage.UserSubscriptionStorage
import com.storyteller_f.storage.WrappedPagingSource
import com.storyteller_f.storage.getName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

abstract class CommunityViewModel :
    SimpleViewModel<CommunityInfo>() {
    val dialog = DialogSaveState()
}

class IdCommunityViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    communityId: PrimaryKey,
) :
    CommunityViewModel() {
    val modelCollection = CommunityCollection.Communities
    override val handler: LoadingHandler<CommunityInfo> =
        CachedLoadingHandler(
            modelStorage.community.observeDatum(communityId),
            viewModelScope,
            { t ->
                modelStorage.community.save(modelCollection, t)
            }
        ) { sessionManager.getCommunityInfo(communityId) }
}

class AidCommunityViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    aid: String,
) :
    CommunityViewModel() {
    val modelCollection = CommunityCollection.Communities
    override val handler: LoadingHandler<CommunityInfo> =
        CachedLoadingHandler(
            modelStorage.community.observeDatum(aid),
            viewModelScope,
            { t ->
                modelStorage.community.save(modelCollection, t)
            }
        ) { sessionManager.getCommunityInfoByAid(aid) }
}

@OptIn(ExperimentalPagingApi::class)
class CommunitiesViewModel(
    sessionManager: UserSessionManager,
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
                        RegularPagingSource { key, size ->
                            sessionManager.searchCommunity(
                                size,
                                joinStatusSearch,
                                word,
                                target,
                                key,
                                PosterSearch.HAS_POSTER
                            )
                        },
                        RegularPagingSource { key, size ->
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
                modelStorage.community.clean(modelCollection)
            }
            data.forEach {
                modelStorage.community.save(modelCollection, it)
            }
        },
    ) {
        CompatPagingSource(
            modelStorage.community.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class RoomsViewModel(
    sessionManager: UserSessionManager,
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
            RegularPagingSource { key, size ->
                sessionManager.searchRooms(size, key, joinStatusSearch, word, community)
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.room.clean(modelCollection)
            }
            data.forEach {
                modelStorage.room.save(modelCollection, it)
            }
        },
    ) {
        CompatPagingSource(
            modelStorage.room.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class WorldViewModel(
    val sessionManager: UserSessionManager,
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
                        RegularPagingSource { loadKey, size ->
                            sessionManager.getRecommendTopics(PaginationQuery(loadKey, size = size))
                        }
                    )
                ),
                SectionLoadParams::class
            ),
        ) { data, clean ->
            if (clean) {
                modelStorage.topic.clean(modelCollection)
            }
            data.forEach {
                modelStorage.topic.save(modelCollection, it)
            }
        },
    ) {
        CompatPagingSource(
            modelStorage.topic.observeData(modelCollection),
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
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    id: PrimaryKey,
    type: ObjectType,
) : PagingViewModel<TopicInfo>() {
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
                        RegularPagingSource { loadKey, size ->
                            sessionManager.getTopicList(
                                type,
                                id,
                                TopicPinSearch.PINNED,
                                PaginationQuery(loadKey, null, size = size)
                            )
                        },
                        RegularPagingSource { loadKey, size ->
                            sessionManager.getTopicList(
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
            if (refresh) {
                modelStorage.topic.clean(modelCollection)
            }
            data.forEach {
                modelStorage.topic.save(modelCollection, it)
            }
        },
    ) {

        WrappedPagingSource(
            CompatPagingSource(
                modelStorage.topic.observeData(
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
                content.fileInfos,
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
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    communityId: PrimaryKey,
) :
    RoomViewModel() {
    val modelCollection = RoomCollection.Rooms
    override val handler: LoadingHandler<RoomInfo> =
        CachedLoadingHandler(
            modelStorage.room.observeDatum(
                communityId
            ),
            viewModelScope,
            { t ->
                modelStorage.room.save(modelCollection, t)
            }
        ) {
            sessionManager.getRoomInfo(communityId)
        }
}

class AidRoomViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    aid: String,
) : RoomViewModel() {
    val modelCollection = RoomCollection.Rooms
    override val handler: LoadingHandler<RoomInfo> =
        CachedLoadingHandler(
            modelStorage.room.observeDatum(aid),
            viewModelScope,
            { t ->
                modelStorage.room.save(modelCollection, t)
            }
        ) {
            sessionManager.getRoomInfoByAid(aid)
        }
}

@OptIn(ExperimentalPagingApi::class)
class TopicSearchViewModel(
    sessionManager: UserSessionManager,
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
            RegularPagingSource { key, size ->
                sessionManager.searchTopics(size, word, parentId, parentType, key)
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.topic.clean(modelCollection)
            }
            data.forEach {
                modelStorage.topic.save(modelCollection, it)
            }
        },
    ) {
        CompatPagingSource(
            modelStorage.topic.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

class MediaListViewModel(
    sessionManager: UserSessionManager,
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
            RegularPagingSource { key, size ->
                sessionManager.getMediaList(objectId, objectType, key, size)
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.fileInfo.clean(modelCollection)
            }
            data.forEach {
                modelStorage.fileInfo.save(modelCollection, it)
            }
        },
    ) {
        CompatPagingSource(
            modelStorage.fileInfo.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

abstract class UserViewModel :
    SimpleViewModel<UserInfo>()

class IdUserViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    id: PrimaryKey,
) :
    UserViewModel() {
    val modelCollection = UserCollection.Users
    override val handler: LoadingHandler<UserInfo> =
        CachedLoadingHandler(
            modelStorage.user.observeDatum(id),
            viewModelScope,
            { t ->
                modelStorage.user.save(modelCollection, t)
            }
        ) {
            sessionManager.getUserInfo(id)
        }
}

class AidUserViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    aid: String,
) : UserViewModel() {
    val modelCollection = UserCollection.Users
    override val handler: LoadingHandler<UserInfo> =
        CachedLoadingHandler(
            modelStorage.user.observeDatum(aid),
            viewModelScope,
            { t ->
                modelStorage.user.save(modelCollection, t)
            }
        ) {
            sessionManager.getUserInfoByAid(aid)
        }
}

@OptIn(ExperimentalPagingApi::class)
class MemberViewModel(
    sessionManager: UserSessionManager,
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
            RegularPagingSource { key, size ->
                sessionManager.run {
                    when (objectType) {
                        ObjectType.COMMUNITY -> searchCommunityMembers(objectId, key, size, word)
                        ObjectType.ROOM -> searchRoomMembers(objectId, key, size, word)
                        else -> searchAllMembers(key, size, word)
                    }
                }
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.user.clean(modelCollection)
            }
            data.forEach {
                modelStorage.user.save(modelCollection, it)
            }
        },
    ) {
        CompatPagingSource(
            modelStorage.user.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

class ReactionsViewModel(
    sessionManager: UserSessionManager,
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
            RegularPagingSource { key, size ->
                sessionManager.getReactions(objectId, size, key)
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.reaction.clean(modelCollection)
            }
            data.forEach {
                modelStorage.reaction.save(modelCollection, it)
            }
        }
    ) {
        CompatPagingSource(
            modelStorage.reaction.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

abstract class TopicViewModel :
    SimpleViewModel<TopicInfo>()

class IdTopicViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    topicId: PrimaryKey,
) :
    TopicViewModel() {
    val modelCollection = TopicCollection.Topics
    override val handler: LoadingHandler<TopicInfo> =
        CachedLoadingHandler(
            modelStorage.topic.observeDatum(topicId),
            viewModelScope,
            { t ->
                modelStorage.topic.save(modelCollection, t)
            }
        ) {
            sessionManager.getTopicInfo(topicId).map {
                processEncryptedTopic(listOf(it), sessionManager).first()
            }
        }
}

class AidTopicViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    aid: String,
) :
    TopicViewModel() {
    val modelCollection = TopicCollection.Topics
    override val handler: LoadingHandler<TopicInfo> =
        CachedLoadingHandler(
            modelStorage.topic.observeDatum(aid),
            viewModelScope,
            { t ->
                modelStorage.topic.save(modelCollection, t)
            }
        ) {
            sessionManager.getTopicInfoByAid(aid)
        }
}

class RoomKeysViewModel(
    sessionManager: UserSessionManager,
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
    sessionManager: UserSessionManager,
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
            RegularPagingSource { key, size ->
                sessionManager.userTitles(uid, size, searchType, key, status, type, scopeId)
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.title.clean(modelCollection)
            }
            data.forEach {
                modelStorage.title.save(modelCollection, it)
            }
        },
    ) {
        CompatPagingSource(
            modelStorage.title.observeData(modelCollection),
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
        modelStorage.upload.observeData(
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
    modelStorage: ModelStorage,
    fileId: PrimaryKey?,
) : ViewModel() {
    val data =
        fileId?.let { modelStorage.download.observeDatum(it) } ?: MutableStateFlow(null)

    val fontFamily = data.distinctUntilChanged { t1, t2 ->
        t1?.status == t2?.status
    }.map {
        if (it?.status == DownloadStatus.PROCESSED) {
            loadFontFromLocal(it.path + ".extracted")
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

class MarkdownMediasViewModel(
    val sessionManager: UserSessionManager,
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
    sessionManager: UserSessionManager,
) : PagingViewModel<ChildAccountInfo>() {

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<ChildAccountInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            ChildAccountStorage.COLLECTION_NAME,
            RegularPagingSource { key, size ->
                sessionManager.getChildAccounts(key, size)
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.childAccount.clean()
            }
            data.forEach {
                modelStorage.childAccount.save(it)
            }
        },
    ) {
        CompatPagingSource(
            modelStorage.childAccount.observeData(),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

class SessionHistoryViewModel(val sessionManager: CustomUserSessionManager) :
    SimpleViewModel<SavedSession>() {

    val manager = sessionManager.sessionHistoryManager

    override val handler: LoadingHandler<SavedSession>
        get() = SimpleLoadingHandler(viewModelScope) {
            runCatching {
                manager.getSavedSession()
            }
        }

    fun deleteSession(alias: String) {
        manager.removeSession(alias)
    }

    fun getSession(alias: String): Boolean {
        val userPass = manager.buildSession(alias) ?: return false
        sessionManager.model.updateState(ClientSessionState.Success(userPass))
        manager.logSession(alias)
        return true
    }
}

class FavoritesViewModel(sessionManager: UserSessionManager, modelStorage: ModelStorage) :
    PagingViewModel<UserFavoriteInfo>() {

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<UserFavoriteInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            UserFavoriteStorage.COLLECTION_NAME,
            RegularPagingSource { key, size ->
                sessionManager.getFavorites(PaginationQuery(key, size = size))
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.favorite.clean()
            }
            data.forEach {
                modelStorage.favorite.save(it)
            }
        },
    ) {
        CompatPagingSource(
            modelStorage.favorite.observeData(),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

class SubscriptionsViewModel(sessionManager: UserSessionManager, modelStorage: ModelStorage) :
    PagingViewModel<UserSubscriptionInfo>() {

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<UserSubscriptionInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            UserSubscriptionStorage.COLLECTION_NAME,
            RegularPagingSource { key, size ->
                sessionManager.getSubscriptions(PaginationQuery(key, size = size))
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.subscription.clean()
            }
            data.forEach {
                modelStorage.subscription.save(it)
            }
        },
    ) {
        CompatPagingSource(
            modelStorage.subscription.observeData(),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

class UserOverviewViewModel(sessionManager: UserSessionManager, modelStorage: ModelStorage) :
    SimpleViewModel<UserOverview>() {
    override val handler: LoadingHandler<UserOverview> = CachedLoadingHandler(
        modelStorage.userOverview.observeDatum(),
        viewModelScope,
        {
            modelStorage.userOverview.save(it)
        }
    ) {
        sessionManager.getUserOverview()
    }
}
