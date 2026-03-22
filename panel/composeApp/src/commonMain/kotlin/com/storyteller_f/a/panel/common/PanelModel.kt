package com.storyteller_f.a.panel.common

import androidx.lifecycle.*
import androidx.paging.*
import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.app.core.common.CachedLoadingHandler
import com.storyteller_f.a.app.core.common.PagingViewModel
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
import com.storyteller_f.a.client.core.getCommunityById
import com.storyteller_f.a.client.core.getCommunityMembers
import com.storyteller_f.a.client.core.getFileById
import com.storyteller_f.a.client.core.getFileRefs
import com.storyteller_f.a.client.core.getRoomById
import com.storyteller_f.a.client.core.getRoomFiles
import com.storyteller_f.a.client.core.getRoomMembers
import com.storyteller_f.a.client.core.getTitleById
import com.storyteller_f.a.client.core.getTopicById
import com.storyteller_f.a.client.core.getTopicTopics
import com.storyteller_f.a.client.core.getUserById
import com.storyteller_f.a.client.core.getUserComments
import com.storyteller_f.a.client.core.getUserFavorites
import com.storyteller_f.a.client.core.getUserFiles
import com.storyteller_f.a.client.core.getUserJoinedCommunities
import com.storyteller_f.a.client.core.getUserJoinedRooms
import com.storyteller_f.a.client.core.getUserLogs
import com.storyteller_f.a.client.core.getUserOverview
import com.storyteller_f.a.client.core.getUserReactions
import com.storyteller_f.a.client.core.getUserReceivedTitles
import com.storyteller_f.a.client.core.getUserSubscriptions
import com.storyteller_f.a.client.core.getUserUploadRecords
import com.storyteller_f.a.client.core.overview
import com.storyteller_f.shared.Type2Algo
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.MemberInfo
import com.storyteller_f.shared.model.PanelOverview
import com.storyteller_f.shared.model.ReactionRecordInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.TopicPinSearch
import com.storyteller_f.shared.model.UploadRecordInfo
import com.storyteller_f.shared.model.UserFavoriteInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserLogInfo
import com.storyteller_f.shared.model.UserOverview
import com.storyteller_f.shared.model.UserSubscriptionInfo
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.storage.CommunityCollection
import com.storyteller_f.storage.FileCollection
import com.storyteller_f.storage.ModelStorage
import com.storyteller_f.storage.RoomCollection
import com.storyteller_f.storage.TitleCollection
import com.storyteller_f.storage.TopicCollection
import com.storyteller_f.storage.UserCollection
import com.storyteller_f.storage.UserFavoriteCollection
import com.storyteller_f.storage.UserReactionRecordCollection
import com.storyteller_f.storage.UserSubscriptionCollection
import com.storyteller_f.storage.getName
import com.storyteller_f.storage.wrap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
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
        modelStorage.remoteKey.wrap(modelCollection.getName()),
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
        modelStorage.remoteKey.wrap(modelCollection.getName()),
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
        modelStorage.remoteKey.wrap(modelCollection.getName()),
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
        modelStorage.remoteKey.wrap(modelCollection.getName()),
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
        modelStorage.remoteKey.wrap(modelCollection.getName()),
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
        modelStorage.remoteKey.wrap(modelCollection.getName()),
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
        modelStorage.remoteKey.wrap(modelCollection.getName()),
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
    val algoType = MutableStateFlow(AlgoType.P256)
    val encryptionPrivateKey = MutableStateFlow("")
    val encryptionPublicKey = encryptionPrivateKey.combine(algoType) { pk, algo ->
        if (algo != AlgoType.DILITHIUM || pk.isBlank()) return@combine null
        val encryptionAlgo = getAlgo(algo).encryptionAlgo as? Type2Algo
        encryptionAlgo?.getDerEncryptionPublicKeyFromPemPrivateKey(pk)?.getOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val publicKey = privateKey.combine(algoType) { pk, algo ->
        getAlgo(algo).run {
            getDerPublicKeyFromPrivateKey(pk).getOrNull()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val address = publicKey.combine(algoType) { pub, algo ->
        getAlgo(algo).run {
            pub?.let { derPublicKeyStr -> calcAddress(derPublicKeyStr).getOrNull() }
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

    fun updateEncryptionPrivateKey(privateKey: String) {
        this.encryptionPrivateKey.value = privateKey
    }

    fun updateAlgoType(algoType: AlgoType) {
        this.algoType.value = algoType
        if (algoType != AlgoType.DILITHIUM) {
            encryptionPrivateKey.value = ""
        }
    }

    fun autoGeneratePrivateKey() {
        viewModelScope.launch {
            val algo = getAlgo(algoType.value)
            algo.generatePemKeyPair().onSuccess { (privateKey, _) ->
                updatePrivateKey(privateKey)
            }
            val encryptionAlgo = algo.encryptionAlgo as Type2Algo
            encryptionAlgo.generateEncryptionPemKeyPair().onSuccess { (pemPrivateKey, _) ->
                updateEncryptionPrivateKey(pemPrivateKey)
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
        { modelStorage.user.saveToDefault(it) }
    ) {
        sessionManager.getUserById(id)
    }
}

class IdUserOverviewViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    id: PrimaryKey
) : SimpleViewModel<UserOverview>() {
    override val handler: LoadingHandler<UserOverview> = CachedLoadingHandler(
        modelStorage.userOverview.observeDatum(),
        viewModelScope,
        { modelStorage.userOverview.save(it) }
    ) {
        sessionManager.getUserOverview(id)
    }
}

class IdCommunityViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    id: PrimaryKey
) : SimpleViewModel<CommunityInfo>() {
    override val handler: LoadingHandler<CommunityInfo> = CachedLoadingHandler(
        modelStorage.community.observeDatum(id),
        viewModelScope,
        { modelStorage.community.saveToDefault(it) }
    ) {
        sessionManager.getCommunityById(id)
    }
}

class IdRoomViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    id: PrimaryKey
) : SimpleViewModel<RoomInfo>() {
    override val handler: LoadingHandler<RoomInfo> = CachedLoadingHandler(
        modelStorage.room.observeDatum(id),
        viewModelScope,
        { modelStorage.room.saveToDefault(it) }
    ) {
        sessionManager.getRoomById(id)
    }
}

class IdTopicViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    id: PrimaryKey
) : SimpleViewModel<TopicInfo>() {
    override val handler: LoadingHandler<TopicInfo> = CachedLoadingHandler(
        modelStorage.topic.observeDatum(id),
        viewModelScope,
        { modelStorage.topic.saveToDefault(it) }
    ) {
        sessionManager.getTopicById(id)
    }
}

class IdFileViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    id: PrimaryKey
) : SimpleViewModel<FileInfo>() {
    override val handler: LoadingHandler<FileInfo> = CachedLoadingHandler(
        modelStorage.fileInfo.observeDatum(id),
        viewModelScope,
        { modelStorage.fileInfo.saveToDefault(it) }
    ) {
        sessionManager.getFileById(id)
    }
}

class FileRefsViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    fileId: PrimaryKey
) : PagingViewModel<com.storyteller_f.shared.model.FileRefInfo>() {
    private val modelCollection = com.storyteller_f.storage.FileRefCollection.FileRefs(fileId)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<com.storyteller_f.shared.model.FileRefInfo>> = buildPager(
        modelCollection,
        modelStorage.remoteKey.wrap(modelCollection.getName()),
        modelStorage.fileRef
    ) { key, size ->
        sessionManager.getFileRefs(fileId, PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class IdTitleViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    id: PrimaryKey
) : SimpleViewModel<TitleInfo>() {
    override val handler: LoadingHandler<TitleInfo> = CachedLoadingHandler(
        modelStorage.title.observeDatum(id),
        viewModelScope,
        { modelStorage.title.saveToDefault(it) }
    ) {
        sessionManager.getTitleById(id)
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
        modelStorage.remoteKey.wrap(modelCollection.getName()),
        modelStorage.community
    ) { key, size ->
        sessionManager.getUserJoinedCommunities(
            uid,
            CustomApi.Users.JoinedCommunities.UserCommunitiesQuery(
                com.storyteller_f.shared.model.PosterSearch.UNSPECIFIED,
                key,
                size = size
            )
        )
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
        modelStorage.remoteKey.wrap(modelCollection.getName()),
        modelStorage.room
    ) { key, size ->
        sessionManager.getUserJoinedRooms(uid, PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class UserReceivedTitlesViewModel(
    private val sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    private val uid: PrimaryKey,
) : PagingViewModel<TitleInfo>() {
    private val modelCollection =
        TitleCollection.SearchTitle(uid = uid, searchType = TitleSearchType.RECEIVER)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<TitleInfo>> = buildPager(
        modelCollection,
        modelStorage.remoteKey.wrap(modelCollection.getName()),
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
        modelStorage.remoteKey.wrap(modelCollection.getName()),
        modelStorage.fileInfo
    ) { key, size ->
        sessionManager.getUserFiles(uid, PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class UserLogsViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    uid: PrimaryKey,
) : PagingViewModel<UserLogInfo>() {
    private val modelCollection = com.storyteller_f.storage.UserLogCollection.UserLogs(uid)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<UserLogInfo>> = buildPager(
        modelCollection,
        modelStorage.remoteKey.wrap(modelCollection.getName()),
        modelStorage.userLog
    ) { key, size ->
        sessionManager.getUserLogs(uid, PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class UserUploadRecordsViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    uid: PrimaryKey,
) : PagingViewModel<UploadRecordInfo>() {
    private val modelCollection =
        com.storyteller_f.storage.UploadRecordCollection.UserUploadRecords(uid)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<UploadRecordInfo>> = buildPager(
        modelCollection,
        modelStorage.remoteKey.wrap(modelCollection.getName()),
        modelStorage.uploadRecord
    ) { key, size ->
        sessionManager.getUserUploadRecords(uid, PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class UserReactionsViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    uid: PrimaryKey,
) : PagingViewModel<ReactionRecordInfo>() {
    private val modelCollection = UserReactionRecordCollection.UserReactionRecords(uid)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<ReactionRecordInfo>> = buildPager(
        modelCollection,
        modelStorage.remoteKey.wrap(modelCollection.getName()),
        modelStorage.userReactionRecord
    ) { key, size ->
        sessionManager.getUserReactions(uid, PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class UserCommentsViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    uid: PrimaryKey,
) : PagingViewModel<TopicInfo>() {
    private val modelCollection = TopicCollection.UserComments(uid)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<TopicInfo>> = buildPager(
        modelCollection,
        modelStorage.remoteKey.wrap(modelCollection.getName()),
        modelStorage.topic
    ) { key, size ->
        sessionManager.getUserComments(uid, PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class UserFavoritesViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    uid: PrimaryKey,
) : PagingViewModel<UserFavoriteInfo>() {
    private val modelCollection = UserFavoriteCollection.UserFavorites(uid)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<UserFavoriteInfo>> = buildPager(
        modelCollection,
        modelStorage.remoteKey.wrap(modelCollection.getName()),
        modelStorage.favorite
    ) { key, size ->
        sessionManager.getUserFavorites(uid, PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class UserSubscriptionsViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    uid: PrimaryKey,
) : PagingViewModel<UserSubscriptionInfo>() {
    private val modelCollection = UserSubscriptionCollection.UserSubscriptions(uid)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<UserSubscriptionInfo>> = buildPager(
        modelCollection,
        modelStorage.remoteKey.wrap(modelCollection.getName()),
        modelStorage.subscription
    ) { key, size ->
        sessionManager.getUserSubscriptions(uid, PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class CommunityMembersViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    communityId: PrimaryKey,
) : PagingViewModel<MemberInfo>() {
    private val modelCollection =
        com.storyteller_f.storage.MemberCollection.CommunityMembers(communityId)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<MemberInfo>> = buildPager(
        modelCollection,
        modelStorage.remoteKey.wrap(modelCollection.getName()),
        modelStorage.member
    ) { key, size ->
        sessionManager.getCommunityMembers(communityId, PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class RoomMembersViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    roomId: PrimaryKey,
) : PagingViewModel<MemberInfo>() {
    private val modelCollection = com.storyteller_f.storage.MemberCollection.RoomMembers(roomId)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<MemberInfo>> = buildPager(
        modelCollection,
        modelStorage.remoteKey.wrap(modelCollection.getName()),
        modelStorage.member
    ) { key, size ->
        sessionManager.getRoomMembers(roomId, PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class RoomFilesViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    roomId: PrimaryKey,
) : PagingViewModel<FileInfo>() {
    private val modelCollection = FileCollection.FileList(roomId)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<FileInfo>> = buildPager(
        modelCollection,
        modelStorage.remoteKey.wrap(modelCollection.getName()),
        modelStorage.fileInfo
    ) { key, size ->
        sessionManager.getRoomFiles(roomId, PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class TopicTopicsViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
    topicId: PrimaryKey,
) : PagingViewModel<TopicInfo>() {
    private val modelCollection = TopicCollection.ChildTopicList(topicId)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<TopicInfo>> = buildPager(
        modelCollection,
        modelStorage.remoteKey.wrap(modelCollection.getName()),
        modelStorage.topic
    ) { key, size ->
        sessionManager.getTopicTopics(
            topicId,
            TopicPinSearch.UNSPECIFIED,
            PaginationQuery(key, size = size)
        )
    }.flow.cachedIn(viewModelScope)
}
