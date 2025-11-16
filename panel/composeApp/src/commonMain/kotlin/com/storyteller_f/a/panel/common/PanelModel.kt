package com.storyteller_f.a.panel.common

import androidx.lifecycle.*
import androidx.paging.*
import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.app.core.common.CachedLoadingHandler
import com.storyteller_f.a.app.core.common.CompatPagingSource
import com.storyteller_f.a.app.core.common.CustomRemoteMediator
import com.storyteller_f.a.app.core.common.IntKeyConverter
import com.storyteller_f.a.app.core.common.PagingViewModel
import com.storyteller_f.a.app.core.common.RegularPagingSource
import com.storyteller_f.a.app.core.common.SimpleViewModel
import com.storyteller_f.a.client.core.LoadingHandler
import com.storyteller_f.a.client.core.PanelSessionManager
import com.storyteller_f.a.client.core.getAllCommunities
import com.storyteller_f.a.client.core.getAllFiles
import com.storyteller_f.a.client.core.getAllPrivateRooms
import com.storyteller_f.a.client.core.getAllPublicRooms
import com.storyteller_f.a.client.core.getAllTitles
import com.storyteller_f.a.client.core.getAllTopics
import com.storyteller_f.a.client.core.getAllUsers
import com.storyteller_f.a.client.core.overview
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.PanelOverview
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
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
    override val flow: Flow<PagingData<UserInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelCollection.getName(),
            modelStorage.remoteKey,
            RegularPagingSource { key, size ->
                sessionManager.getAllUsers(PaginationQuery(key, size = size))
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

class AllCommunitiesViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
) : PagingViewModel<CommunityInfo>() {
    private val modelCollection = CommunityCollection.AllCommunities

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<CommunityInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelCollection.getName(),
            modelStorage.remoteKey,
            RegularPagingSource { key, size ->
                sessionManager.getAllCommunities(PaginationQuery(key, size = size))
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.community.clean(modelCollection)
            }
            data.forEach { modelStorage.community.save(modelCollection, it) }
        },
    ) {
        CompatPagingSource(
            modelStorage.community.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

class AllPublicRoomsViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
) : PagingViewModel<RoomInfo>() {
    private val modelCollection = RoomCollection.AllRooms(false)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<RoomInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelCollection.getName(),
            modelStorage.remoteKey,
            RegularPagingSource { key, size ->
                sessionManager.getAllPublicRooms(PaginationQuery(key, size = size))
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.room.clean(modelCollection)
            }
            data.forEach { modelStorage.room.save(modelCollection, it) }
        },
    ) {
        CompatPagingSource(
            modelStorage.room.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

class AllPrivateRoomsViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
) : PagingViewModel<RoomInfo>() {
    private val modelCollection = RoomCollection.AllRooms(true)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<RoomInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelCollection.getName(),
            modelStorage.remoteKey,
            RegularPagingSource { key, size ->
                sessionManager.getAllPrivateRooms(PaginationQuery(key, size = size))
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.room.clean(modelCollection)
            }
            data.forEach { modelStorage.room.save(modelCollection, it) }
        },
    ) {
        CompatPagingSource(
            modelStorage.room.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

class AllTopicsViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
) : PagingViewModel<TopicInfo>() {
    private val modelCollection = TopicCollection.AllTopics

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<TopicInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelCollection.getName(),
            modelStorage.remoteKey,
            RegularPagingSource { key, size ->
                sessionManager.getAllTopics(PaginationQuery(key, size = size))
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.topic.clean(modelCollection)
            }
            data.forEach { modelStorage.topic.save(modelCollection, it) }
        },
    ) {
        CompatPagingSource(
            modelStorage.topic.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

class AllTitlesViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
) : PagingViewModel<TitleInfo>() {
    private val modelCollection = TitleCollection.AllTitles

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<TitleInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelCollection.getName(),
            modelStorage.remoteKey,
            RegularPagingSource { key, size ->
                sessionManager.getAllTitles(PaginationQuery(key, size = size))
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.title.clean(modelCollection)
            }
            data.forEach { modelStorage.title.save(modelCollection, it) }
        },
    ) {
        CompatPagingSource(
            modelStorage.title.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

class AllFilesViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
) : PagingViewModel<FileInfo>() {
    private val modelCollection = FileCollection.FileList(0)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<FileInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelCollection.getName(),
            modelStorage.remoteKey,
            RegularPagingSource { key, size ->
                sessionManager.getAllFiles(PaginationQuery(key, size = size))
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.fileInfo.clean(modelCollection)
            }
            data.forEach { modelStorage.fileInfo.save(modelCollection, it) }
        },
    ) {
        CompatPagingSource(
            modelStorage.fileInfo.observeData(modelCollection),
            IntKeyConverter
        )
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
