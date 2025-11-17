package com.storyteller_f.a.panel.common

import androidx.lifecycle.*
import androidx.paging.*
import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.app.core.common.CachedLoadingHandler
import com.storyteller_f.a.app.core.common.PagingViewModel
import com.storyteller_f.a.app.core.common.RegularPagingSource
import com.storyteller_f.a.app.core.common.SimpleViewModel
import com.storyteller_f.a.app.core.common.buildPager
import com.storyteller_f.a.client.core.LoadingHandler
import com.storyteller_f.a.client.core.PanelSessionManager
import com.storyteller_f.a.client.core.getAllCommunities
import com.storyteller_f.a.client.core.getAllFiles
import com.storyteller_f.a.client.core.getAllPrivateRooms
import com.storyteller_f.a.client.core.getAllPublicRooms
import com.storyteller_f.a.client.core.getAllTitles
import com.storyteller_f.a.client.core.getAllTopics
import com.storyteller_f.a.client.core.getAllUsers
import com.storyteller_f.a.client.core.getUserById
import com.storyteller_f.a.client.core.getUserFiles
import com.storyteller_f.a.client.core.getUserJoinedCommunities
import com.storyteller_f.a.client.core.getUserJoinedRooms
import com.storyteller_f.a.client.core.getUserLogs
import com.storyteller_f.a.client.core.getUserReceivedTitles
import com.storyteller_f.a.client.core.overview
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.PanelOverview
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserLogInfo
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.storage.CommunityCollection
import com.storyteller_f.storage.FileCollection
import com.storyteller_f.storage.ModelStorage
import com.storyteller_f.storage.RoomCollection
import com.storyteller_f.storage.TitleCollection
import com.storyteller_f.storage.TopicCollection
import com.storyteller_f.storage.UserCollection
import com.storyteller_f.storage.getName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AllUsersViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
) : PagingViewModel<UserInfo>() {
    val modelCollection = UserCollection.AllUsers

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<UserInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.user
    ) { key, size ->
        sessionManager.getAllUsers(PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class AllCommunitiesViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
) : PagingViewModel<CommunityInfo>() {
    private val modelCollection = CommunityCollection.AllCommunities

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<CommunityInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.community
    ) { key, size ->
        sessionManager.getAllCommunities(PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class AllPublicRoomsViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
) : PagingViewModel<RoomInfo>() {
    private val modelCollection = RoomCollection.AllRooms(false)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<RoomInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.room
    ) { key, size ->
        sessionManager.getAllPublicRooms(PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class AllPrivateRoomsViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
) : PagingViewModel<RoomInfo>() {
    private val modelCollection = RoomCollection.AllRooms(true)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<RoomInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.room
    ) { key, size ->
        sessionManager.getAllPrivateRooms(PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class AllTopicsViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
) : PagingViewModel<TopicInfo>() {
    private val modelCollection = TopicCollection.AllTopics

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<TopicInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.topic
    ) { key, size ->
        sessionManager.getAllTopics(PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class AllTitlesViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
) : PagingViewModel<TitleInfo>() {
    private val modelCollection = TitleCollection.AllTitles

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<TitleInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.title
    ) { key, size ->
        sessionManager.getAllTitles(PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class AllFilesViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
) : PagingViewModel<FileInfo>() {
    private val modelCollection = FileCollection.FileList(0)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<FileInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.fileInfo
    ) { key, size ->
        sessionManager.getAllFiles(PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class OverviewViewModel(sessionManager: PanelSessionManager, modelStorage: ModelStorage) :
    SimpleViewModel<PanelOverview>() {
    override val handler: LoadingHandler<PanelOverview> = CachedLoadingHandler(
        modelStorage.overview.observeDatum(),
        viewModelScope,
        {
            modelStorage.overview.save(it)
        }
    ) {
        sessionManager.overview()
    }
}

class AddUserViewModel : ViewModel() {
    val privateKey = MutableStateFlow("")
    val nickname = MutableStateFlow("")
    val aid = MutableStateFlow("")
    val publicKey = privateKey.map {
        getAlgo().run {
            getDerPublicKeyFromPrivateKey(it).getOrNull()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val address = publicKey.map {
        getAlgo().run {
            it?.let { derPublicKeyStr -> calcAddress(derPublicKeyStr).getOrNull() }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun updateNickname(nickname: String) {
        this.nickname.value = nickname
    }

    fun updateAid(aid: String) {
        this.aid.value = aid
    }

    fun updatePrivateKey(privateKey: String) {
        this.privateKey.value = privateKey
    }

    fun autoGeneratePrivateKey() {
        viewModelScope.launch {
            getAlgo().generatePemKeyPair().onSuccess { (privateKey, _) ->
                updatePrivateKey(privateKey)
            }
        }
    }
}
class IdUserViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    id: PrimaryKey
) : SimpleViewModel<UserInfo>() {
    override val handler: LoadingHandler<UserInfo> = CachedLoadingHandler(
        modelStorage.user.observeDatum(id),
        viewModelScope,
        { modelStorage.user.save(UserCollection.Users, it) }
    ) {
        sessionManager.getUserById(id)
    }
}

class UserJoinedCommunitiesViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    uid: PrimaryKey,
) : PagingViewModel<CommunityInfo>() {
    private val modelCollection = CommunityCollection.SearchCommunity(
        joinStatusSearch = JoinStatusSearch.JOINED,
        word = "",
        target = uid
    )

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<CommunityInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.community
    ) { key, size ->
        sessionManager.getUserJoinedCommunities(uid, PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class UserJoinedRoomsViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    uid: PrimaryKey,
) : PagingViewModel<RoomInfo>() {
    private val modelCollection = RoomCollection.SearchRoom(
        word = "",
        communityId = null,
        joinStatusSearch = JoinStatusSearch.JOINED
    )

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<RoomInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.room
    ) { key, size ->
        sessionManager.getUserJoinedRooms(uid, PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class UserReceivedTitlesViewModel(
    private val sessionManager: PanelSessionManager,
    private val modelStorage: ModelStorage,
    private val uid: PrimaryKey,
) : PagingViewModel<TitleInfo>() {
    private val modelCollection = TitleCollection.SearchTitle(
        uid = uid,
        searchType = TitleSearchType.RECEIVER,
    )

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<TitleInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.title
    ) { key, size ->
        sessionManager.getUserReceivedTitles(
            uid,
            CustomApi.Users.Id.Titles.TitleQuery(
                searchType = TitleSearchType.RECEIVER,
                nextPageToken = key,
                size = size
            )
        )
    }.flow.cachedIn(viewModelScope)
}

class UserFilesViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    uid: PrimaryKey,
) : PagingViewModel<FileInfo>() {
    private val modelCollection = FileCollection.FileList(uid)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<FileInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.fileInfo
    ) { key, size ->
        sessionManager.getUserFiles(uid, PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class UserLogsViewModel(
    private val sessionManager: PanelSessionManager,
    private val uid: PrimaryKey,
) : PagingViewModel<UserLogInfo>() {
    override val flow: Flow<PagingData<UserLogInfo>> = Pager(PagingConfig(pageSize = 20)) {
        RegularPagingSource<UserLogInfo> { key, size ->
            sessionManager.getUserLogs(uid, PaginationQuery(key, size = size))
        }
    }.flow.cachedIn(viewModelScope)
}
