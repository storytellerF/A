package com.storyteller_f.a.app.common

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.api.NewTitle
import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.api.SearchQuery
import com.storyteller_f.a.app.CustomUserSessionManager
import com.storyteller_f.a.app.core.common.CachedLoadingHandler
import com.storyteller_f.a.app.core.common.CompatPagingSource
import com.storyteller_f.a.app.core.common.IntKeyConverter
import com.storyteller_f.a.app.core.common.IntermediatePagingSource
import com.storyteller_f.a.app.core.common.PagingViewModel
import com.storyteller_f.a.app.core.common.RegularPagingSource
import com.storyteller_f.a.app.core.common.SectionLoadParams
import com.storyteller_f.a.app.core.common.SectionPagingSource
import com.storyteller_f.a.app.core.common.SimpleViewModel
import com.storyteller_f.a.app.core.common.buildPager
import com.storyteller_f.a.app.core.components.DialogSaveState
import com.storyteller_f.a.app.core.components.generateMathIfNeed
import com.storyteller_f.a.app.core.utils.SavedSession
import com.storyteller_f.a.app.core.utils.loadFontFromLocal
import com.storyteller_f.a.client.core.ClientSessionState
import com.storyteller_f.a.client.core.LoadingHandler
import com.storyteller_f.a.client.core.LoadingState
import com.storyteller_f.a.client.core.SimpleLoadingHandler
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.getChildAccounts
import com.storyteller_f.a.client.core.getComments
import com.storyteller_f.a.client.core.getCommunityInfo
import com.storyteller_f.a.client.core.getCommunityInfoByAid
import com.storyteller_f.a.client.core.getCommunityRooms
import com.storyteller_f.a.client.core.getFavorites
import com.storyteller_f.a.client.core.getFileInfo
import com.storyteller_f.a.client.core.getFileList
import com.storyteller_f.a.client.core.getFileRefs
import com.storyteller_f.a.client.core.getMediaByName
import com.storyteller_f.a.client.core.getQuotaInfo
import com.storyteller_f.a.client.core.getReactionRecords
import com.storyteller_f.a.client.core.getReactions
import com.storyteller_f.a.client.core.getRecommendTopics
import com.storyteller_f.a.client.core.getRoomInfo
import com.storyteller_f.a.client.core.getRoomInfoByAid
import com.storyteller_f.a.client.core.getRoomMembersPublicKeys
import com.storyteller_f.a.client.core.getSubscriptions
import com.storyteller_f.a.client.core.getTopicInfo
import com.storyteller_f.a.client.core.getTopicInfoByAid
import com.storyteller_f.a.client.core.getTopicList
import com.storyteller_f.a.client.core.getUserCommunities
import com.storyteller_f.a.client.core.getUserInfo
import com.storyteller_f.a.client.core.getUserInfoByAid
import com.storyteller_f.a.client.core.getUserJoinedCommunities
import com.storyteller_f.a.client.core.getUserOverview
import com.storyteller_f.a.client.core.getUserRooms
import com.storyteller_f.a.client.core.processEncryptedTopic
import com.storyteller_f.a.client.core.searchAllMembers
import com.storyteller_f.a.client.core.searchCommunity
import com.storyteller_f.a.client.core.searchCommunityMembers
import com.storyteller_f.a.client.core.searchCommunityRooms
import com.storyteller_f.a.client.core.searchCommunityTopics
import com.storyteller_f.a.client.core.searchCurrentUserRooms
import com.storyteller_f.a.client.core.searchFiles
import com.storyteller_f.a.client.core.searchRoomMembers
import com.storyteller_f.a.client.core.searchRoomTopics
import com.storyteller_f.a.client.core.searchUserTopics
import com.storyteller_f.a.client.core.userTitles
import com.storyteller_f.shared.model.ChildAccountInfo
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.FileRefInfo
import com.storyteller_f.shared.model.MemberInfo
import com.storyteller_f.shared.model.PosterSearch
import com.storyteller_f.shared.model.QuotaInfo
import com.storyteller_f.shared.model.QuotaType
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.ReactionRecordInfo
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
import com.storyteller_f.storage.DownloadInfo
import com.storyteller_f.storage.DownloadStatus
import com.storyteller_f.storage.FileCollection
import com.storyteller_f.storage.FileRefCollection
import com.storyteller_f.storage.MemberCollection
import com.storyteller_f.storage.ModelStorage
import com.storyteller_f.storage.ReactionCollection
import com.storyteller_f.storage.RoomCollection
import com.storyteller_f.storage.TitleCollection
import com.storyteller_f.storage.TopicCollection
import com.storyteller_f.storage.UploadCollection
import com.storyteller_f.storage.UploadInfo
import com.storyteller_f.storage.UserCollection
import com.storyteller_f.storage.UserFavoriteStorage
import com.storyteller_f.storage.UserReactionRecordCollection
import com.storyteller_f.storage.UserSubscriptionStorage
import com.storyteller_f.storage.WrappedPagingSource
import com.storyteller_f.storage.getName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
) : CommunityViewModel() {
    override val handler: LoadingHandler<CommunityInfo> =
        CachedLoadingHandler(
            modelStorage.community.observeDatum(communityId),
            viewModelScope,
            { t ->
                modelStorage.community.save(t)
            }
        ) { sessionManager.getCommunityInfo(communityId) }
}

class AidCommunityViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    aid: String,
) :
    CommunityViewModel() {
    override val handler: LoadingHandler<CommunityInfo> =
        CachedLoadingHandler(
            modelStorage.community.observeDatum(aid),
            viewModelScope,
            { t ->
                modelStorage.community.save(t)
            }
        ) { sessionManager.getCommunityInfoByAid(aid) }
}

@OptIn(ExperimentalPagingApi::class)
class CommunitiesViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    joinStatusSearch: JoinStatusSearch,
    word: String,
    target: PrimaryKey? = null,
) : PagingViewModel<CommunityInfo>() {
    private val modelCollection =
        CommunityCollection.SearchCommunity(joinStatusSearch, word, target, PosterSearch.UNSPECIFIED)

    override val flow = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.community,
        RegularPagingSource { key, size ->
            sessionManager.searchCommunity(size, joinStatusSearch, word, target, key, PosterSearch.UNSPECIFIED)
        }
    ).flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class UserJoinedCommunitiesViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    target: PrimaryKey? = null,
) : PagingViewModel<CommunityInfo>() {
    private val modelCollection =
        CommunityCollection.SearchCommunity(JoinStatusSearch.JOINED, "", target, PosterSearch.UNSPECIFIED)

    override val flow = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.community,
        RegularPagingSource { key, size ->
            if (target == null) {
                sessionManager.getUserCommunities(PaginationQuery(key, size = size))
            } else {
                sessionManager.getUserJoinedCommunities(target, PaginationQuery(key, size = size))
            }
        }
    ).flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class UserJoinedCommunitiesWithPosterViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
) : PagingViewModel<CommunityInfo>() {
    private val modelCollection =
        CommunityCollection.SearchCommunity(JoinStatusSearch.JOINED, "", null, PosterSearch.HAS_POSTER)

    override val flow = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.community
    ) { key, size ->
        sessionManager.getUserCommunities(PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class RoomsViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    joinStatusSearch: JoinStatusSearch,
    word: String,
) : PagingViewModel<RoomInfo>() {
    private val modelCollection = RoomCollection.SearchRoom(word, null, joinStatusSearch)

    override val flow: Flow<PagingData<RoomInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.room
    ) { key, size ->
        sessionManager.searchCurrentUserRooms(word, size, key)
    }.flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class UserJoinedRoomsViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
) : PagingViewModel<RoomInfo>() {
    private val modelCollection = RoomCollection.SearchRoom("", null, JoinStatusSearch.JOINED)

    override val flow: Flow<PagingData<RoomInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.room
    ) { key, size ->
        sessionManager.getUserRooms(PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class WorldViewModel(
    val sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    codeTextStyle: TextStyle,
    inlineCodeTextStyle: TextStyle,
    density: Density,
) : PagingViewModel<TopicInfo>() {
    private val modelCollection = TopicCollection.Recommend

    @OptIn(FlowPreview::class)
    override val flow = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.topic,
        IntermediatePagingSource(
            SectionLoadParams::class,
            SectionPagingSource(
                listOf(
                    RegularPagingSource { loadKey, size ->
                        sessionManager.getRecommendTopics(PaginationQuery(loadKey, size = size))
                    }
                )
            )
        )
    ).flow.map { pagingData ->
        pagingData.map {
            generateMathIfNeed(it, codeTextStyle, inlineCodeTextStyle, density)
        }.map {
            extractHeadlineIfPlain(it)
        }
    }.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class TopicsViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    id: PrimaryKey,
    type: ObjectType,
    codeTextStyle: TextStyle,
    inlineCodeTextStyle: TextStyle,
    density: Density,
) : PagingViewModel<TopicInfo>() {
    private val modelCollection = TopicCollection.ChildTopicList(id)

    @OptIn(FlowPreview::class)
    override val flow: Flow<PagingData<TopicInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.topic,
        IntermediatePagingSource(
            SectionLoadParams::class,
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
            )
        )
    ) {
        WrappedPagingSource(
            CompatPagingSource(modelStorage.topic.observeData(modelCollection), IntKeyConverter)
        ) { topicInfos ->
            processEncryptedTopic(topicInfos, sessionManager).map {
                generateMathIfNeed(it, codeTextStyle, inlineCodeTextStyle, density)
            }.map { topicInfo ->
                extractHeadlineIfPlain(topicInfo)
            }
        }
    }.flow.cachedIn(viewModelScope)
}

private fun extractHeadlineIfPlain(it: TopicInfo): TopicInfo {
    val content = it.content
    return if (content is TopicContent.Plain) {
        it.copy(
            content = TopicContent.Extracted(extractMarkdownHeadline(content.plain), content.fileInfos, content.plain)
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
    override val handler: LoadingHandler<RoomInfo> =
        CachedLoadingHandler(
            modelStorage.room.observeDatum(communityId),
            viewModelScope,
            { t ->
                modelStorage.room.save(t)
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
    override val handler: LoadingHandler<RoomInfo> =
        CachedLoadingHandler(
            modelStorage.room.observeDatum(aid),
            viewModelScope,
            { t ->
                modelStorage.room.save(t)
            }
        ) {
            sessionManager.getRoomInfoByAid(aid)
        }
}

@OptIn(ExperimentalPagingApi::class)
class TopicSearchViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    word: String,
    parentId: PrimaryKey?,
    parentType: ObjectType?,
) :
    PagingViewModel<TopicInfo>() {
    private val modelCollection = TopicCollection.SearchTopic(word, parentId)

    override val flow: Flow<PagingData<TopicInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.topic
    ) { key, size ->
        // 内联并确保调用正确的endpoint
        if (parentId != null && parentType != null) {
            when (parentType) {
                ObjectType.COMMUNITY -> sessionManager.searchCommunityTopics(parentId, size, word, key)
                ObjectType.ROOM -> sessionManager.searchRoomTopics(parentId, size, word, key)
                ObjectType.USER -> sessionManager.searchUserTopics(parentId, size, word, key)
                else -> throw IllegalArgumentException("Unsupported parentType: $parentType")
            }
        } else {
            // 如果没有指定父对象，则搜索推荐主题
            sessionManager.getRecommendTopics(PaginationQuery(key, size = size))
        }
    }.flow.cachedIn(viewModelScope)
}

class MediaListViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    objectId: PrimaryKey,
    objectType: ObjectType,
) :
    PagingViewModel<FileInfo>() {
    private val modelCollection = FileCollection.FileList(objectId)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<FileInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.fileInfo
    ) { key, size ->
        sessionManager.getFileList(objectId, objectType, key, size)
    }.flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class FileSearchViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    word: String,
    objectId: PrimaryKey,
    objectType: ObjectType,
) : PagingViewModel<FileInfo>() {
    private val modelCollection = FileCollection.FileSearch(objectId, word)

    override val flow: Flow<PagingData<FileInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.fileInfo
    ) { key, size ->
        sessionManager.searchFiles(
            SearchQuery(
                word = word,
                nextPageToken = key,
                size = size
            ),
            objectId,
            objectType
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
    override val handler: LoadingHandler<UserInfo> =
        CachedLoadingHandler(
            modelStorage.user.observeDatum(id),
            viewModelScope,
            { t ->
                modelStorage.user.save(t)
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
    override val handler: LoadingHandler<UserInfo> =
        CachedLoadingHandler(
            modelStorage.user.observeDatum(aid),
            viewModelScope,
            { t ->
                modelStorage.user.save(t)
            }
        ) {
            sessionManager.getUserInfoByAid(aid)
        }
}

@OptIn(ExperimentalPagingApi::class)
class ContainerMemberViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    objectId: PrimaryKey,
    word: String,
    objectType: ObjectType,
) : PagingViewModel<MemberInfo>() {
    private val modelCollection = when (objectType) {
        ObjectType.COMMUNITY -> MemberCollection.CommunityMembers(objectId, word)
        ObjectType.ROOM -> MemberCollection.RoomMembers(objectId, word)
        else -> throw IllegalArgumentException("Unsupported objectType: $objectType")
    }

    override val flow: Flow<PagingData<MemberInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.member
    ) { key, size ->
        sessionManager.run {
            when (objectType) {
                ObjectType.COMMUNITY -> searchCommunityMembers(objectId, key, size, word)
                ObjectType.ROOM -> searchRoomMembers(objectId, key, size, word)
                else -> throw IllegalArgumentException("Unsupported objectType: $objectType")
            }
        }
    }.flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class UserSearchViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    word: String,
) : PagingViewModel<UserInfo>() {
    private val modelCollection = UserCollection.SearchUser(word)

    override val flow: Flow<PagingData<UserInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.user
    ) { key, size ->
        sessionManager.searchAllMembers(key, size, word)
    }.flow.cachedIn(viewModelScope)
}

class ReactionsViewModel(
    sessionManager: UserSessionManager,
    objectId: PrimaryKey,
    modelStorage: ModelStorage,
) : PagingViewModel<ReactionInfo>() {
    val modelCollection = ReactionCollection.ReactionList(objectId)

    @OptIn(ExperimentalPagingApi::class)
    override val flow = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.reaction
    ) { key, size ->
        sessionManager.getReactions(objectId, size, key)
    }.flow.cachedIn(viewModelScope)
}

abstract class TopicViewModel : SimpleViewModel<TopicInfo>()

class IdTopicViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    topicId: PrimaryKey,
    codeTextStyle: TextStyle,
    inlineCodeTextStyle: TextStyle,
    density: Density
) : TopicViewModel() {
    override val handler: LoadingHandler<TopicInfo> =
        CachedLoadingHandler(
            modelStorage.topic.observeDatum(topicId),
            viewModelScope,
            { t ->
                modelStorage.topic.save(t)
            }
        ) {
            sessionManager.getTopicInfo(topicId).map { topicInfo ->
                processEncryptedTopic(listOf(topicInfo), sessionManager).map {
                    generateMathIfNeed(it, codeTextStyle, inlineCodeTextStyle, density)
                }.first()
            }
        }
}

class AidTopicViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    aid: String,
    codeTextStyle: TextStyle,
    inlineCodeTextStyle: TextStyle,
    density: Density
) : TopicViewModel() {
    override val handler: LoadingHandler<TopicInfo> = CachedLoadingHandler(
        modelStorage.topic.observeDatum(aid),
        viewModelScope,
        { t ->
            modelStorage.topic.save(t)
        }
    ) {
        sessionManager.getTopicInfoByAid(aid).map { topicInfo ->
            processEncryptedTopic(listOf(topicInfo), sessionManager).map {
                generateMathIfNeed(it, codeTextStyle, inlineCodeTextStyle, density)
            }.first()
        }
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
    private val modelCollection = TitleCollection.SearchTitle(uid, searchType, status, type, scopeId)

    override val flow: Flow<PagingData<TitleInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.title
    ) { key, size ->
        sessionManager.userTitles(uid, size, searchType, key, status, type, scopeId)
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
        modelStorage.upload.observeData(UploadCollection(myUid))
    }.flow.cachedIn(viewModelScope)
}

class UploadDetailViewModel(
    modelStorage: ModelStorage,
    pathHash: String,
    myUid: PrimaryKey,
) : ViewModel() {
    val data = modelStorage.upload.observeDatumByHash(UploadCollection(myUid), pathHash)
}

class DownloadListViewModel(
    modelStorage: ModelStorage,
) : PagingViewModel<DownloadInfo>() {
    override val flow = Pager(PagingConfig(10)) {
        modelStorage.download.observeData()
    }.flow.cachedIn(viewModelScope)
}

class QuotaViewModel(
    private val sessionManager: UserSessionManager,
    private val objectTuple: ObjectTuple,
    private val quotaType: QuotaType
) : SimpleViewModel<QuotaInfo>() {
    override val handler: LoadingHandler<QuotaInfo> = SimpleLoadingHandler(viewModelScope) {
        sessionManager.getQuotaInfo(objectTuple, quotaType)
    }
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
    val data = fileId?.let { modelStorage.download.observeDatum(it) } ?: MutableStateFlow(null)

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
    override val flow: Flow<PagingData<ChildAccountInfo>> = buildPager(
        ChildAccountStorage.COLLECTION_NAME,
        modelStorage.remoteKey,
        modelStorage.childAccount
    ) { key, size ->
        sessionManager.getChildAccounts(key, size)
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
    override val flow: Flow<PagingData<UserFavoriteInfo>> = buildPager(
        UserFavoriteStorage.COLLECTION_NAME,
        modelStorage.remoteKey,
        modelStorage.favorite
    ) { key, size ->
        sessionManager.getFavorites(PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class SubscriptionsViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    textStyle: TextStyle,
    inlineCodeTextStyle: TextStyle,
    density: Density,
) : PagingViewModel<UserSubscriptionInfo>() {

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<UserSubscriptionInfo>> = buildPager(
        UserSubscriptionStorage.COLLECTION_NAME,
        modelStorage.remoteKey,
        modelStorage.subscription,
        RegularPagingSource { key, size ->
            sessionManager.getSubscriptions(PaginationQuery(key, size = size))
        }
    ) {
        WrappedPagingSource(
            CompatPagingSource(modelStorage.subscription.observeData(), IntKeyConverter)
        ) { list ->
            list.map { subscriptionInfo ->
                val extensions = subscriptionInfo.extensions
                val topicInfo = extensions?.topicInfo
                if (topicInfo != null) {
                    val newTopic = processEncryptedTopic(listOf(topicInfo), sessionManager).map {
                        generateMathIfNeed(it, textStyle, inlineCodeTextStyle, density)
                    }.first()
                    subscriptionInfo.copy(extensions = extensions.copy(topicInfo = newTopic))
                } else {
                    subscriptionInfo
                }
            }
        }
    }.flow.cachedIn(viewModelScope)
}

class UserReactionRecordsViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage
) : PagingViewModel<ReactionRecordInfo>() {

    val collection = UserReactionRecordCollection.UserReactionRecords(0)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<ReactionRecordInfo>> = buildPager(
        collection,
        collection.getName(),
        modelStorage.remoteKey,
        modelStorage.userReactionRecord,
        RegularPagingSource { key, size ->
            sessionManager.getReactionRecords(PaginationQuery(key, size = size))
        },
    ).flow.cachedIn(viewModelScope)
}

class UserCommentsViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    textStyle: TextStyle,
    inlineCodeTextStyle: TextStyle,
    density: Density,
) : PagingViewModel<TopicInfo>() {

    val collection = TopicCollection.UserComments(0)

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<TopicInfo>> = buildPager(
        collection,
        collection.getName(),
        modelStorage.remoteKey,
        modelStorage.topic,
        RegularPagingSource { key, size ->
            sessionManager.getComments(PaginationQuery(key, size = size))
        }
    ) {
        WrappedPagingSource(
            CompatPagingSource(modelStorage.topic.observeData(collection), IntKeyConverter)
        ) { list ->
            processEncryptedTopic(list, sessionManager).map {
                generateMathIfNeed(it, textStyle, inlineCodeTextStyle, density)
            }
        }
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
        if (sessionManager.model.state.value is ClientSessionState.Success) {
            sessionManager.getUserOverview()
        } else {
            Result.failure(IllegalStateException("not logged in"))
        }
    }
}

@OptIn(ExperimentalPagingApi::class)
class CommunityRoomsViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    communityId: PrimaryKey,
) : PagingViewModel<RoomInfo>() {
    private val modelCollection = RoomCollection.CommunityRooms(communityId)

    override val flow: Flow<PagingData<RoomInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.room
    ) { key, size ->
        sessionManager.getCommunityRooms(
            communityId,
            CustomApi.Communities.Id.Rooms.CommunityRoomQuery(key, size = size)
        )
    }.flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class CommunityRoomSearchViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    communityId: PrimaryKey,
    word: String,
) : PagingViewModel<RoomInfo>() {
    private val modelCollection = RoomCollection.CommunityRoomSearch(communityId, word)

    override val flow: Flow<PagingData<RoomInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.room
    ) { key, size ->
        sessionManager.searchCommunityRooms(
            communityId,
            word,
            JoinStatusSearch.UNSPECIFIED,
            size,
            key
        )
    }.flow.cachedIn(viewModelScope)
}

@OptIn(ExperimentalPagingApi::class)
class FileRefsViewModel(
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage,
    fileId: PrimaryKey
) : PagingViewModel<FileRefInfo>() {
    private val modelCollection = FileRefCollection.FileRefs(fileId)

    override val flow: Flow<PagingData<FileRefInfo>> = buildPager(
        modelCollection,
        modelCollection.getName(),
        modelStorage.remoteKey,
        modelStorage.fileRef
    ) { key, size ->
        sessionManager.getFileRefs(fileId, PaginationQuery(key, size = size))
    }.flow.cachedIn(viewModelScope)
}

class FileViewViewModel(
    fileId: PrimaryKey,
    sessionManager: UserSessionManager,
    modelStorage: ModelStorage
) :
    SimpleViewModel<FileInfo>() {
    override val handler: LoadingHandler<FileInfo> = CachedLoadingHandler(
        modelStorage.fileInfo.observeDatum(fileId),
        viewModelScope,
        {
            modelStorage.fileInfo.save(it)
        }
    ) {
        sessionManager.getFileInfo(fileId)
    }
}

enum class TitleComposeSheetType { NONE, SCOPE, RECEIVER }

class TitleComposeViewModel : ViewModel() {
    val name = MutableStateFlow("")
    val showSheetType = MutableStateFlow(TitleComposeSheetType.NONE)
    val titleScope = MutableStateFlow<ObjectTuple?>(null)
    val titleType = MutableStateFlow(TitleType.REGULAR)
    val receiver = MutableStateFlow<PrimaryKey?>(null)
    val content = MutableStateFlow("")

    fun setName(value: String) {
        name.value = value
    }

    fun setTitleType(value: TitleType) {
        titleType.value = value
    }

    fun setTitleScope(value: ObjectTuple?) {
        titleScope.value = value
    }

    fun clearTitleScope() {
        titleScope.value = null
    }

    fun setReceiver(value: PrimaryKey?) {
        receiver.value = value
    }

    fun setContent(value: String) {
        content.value = value
    }

    fun setShowSheetType(value: TitleComposeSheetType) {
        showSheetType.value = value
    }

    fun clearShowSheetType() {
        showSheetType.value = TitleComposeSheetType.NONE
    }

    // Readability helpers (Flow-based for Compose collectAsState)
    val isSheetVisibleFlow: StateFlow<Boolean> = showSheetType
        .map { it != TitleComposeSheetType.NONE }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val supportedObjectTypesFlow: StateFlow<List<ObjectType>> = showSheetType
        .map {
            if (it == TitleComposeSheetType.SCOPE) {
                listOf(ObjectType.COMMUNITY, ObjectType.ROOM)
            } else {
                listOf(ObjectType.USER)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, listOf(ObjectType.USER))

    fun openScopeSheet() = setShowSheetType(TitleComposeSheetType.SCOPE)
    fun openReceiverSheet() = setShowSheetType(TitleComposeSheetType.RECEIVER)

    fun buildNewTitle(): Result<NewTitle> {
        val t = titleType.value
        val r = receiver.value
        val s = titleScope.value
        return if (r != null && s != null) {
            Result.success(NewTitle(name.value, t, r, s.objectId, s.objectType, content.value))
        } else {
            Result.failure(IllegalStateException("titleType/receiver/titleScope must not be null"))
        }
    }
}
