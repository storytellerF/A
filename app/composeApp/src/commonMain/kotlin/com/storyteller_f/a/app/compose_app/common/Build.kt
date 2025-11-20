package com.storyteller_f.a.app.compose_app.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storyteller_f.a.app.compose_app.CustomUserSessionManager
import com.storyteller_f.a.app.compose_app.LocalUiViewModel
import com.storyteller_f.a.app.compose_app.pages.search.SearchScope
import com.storyteller_f.a.app.core.components.buildByMarkdown
import com.storyteller_f.a.client.room.RoomModelStorage
import com.storyteller_f.shared.model.QuotaType
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.TitleStatus
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.md5
import com.storyteller_f.storage.ModelStorage
import io.github.aakira.napier.Napier

@Composable
fun createSearchCommunitiesViewModel(
    finalOption: JoinStatusSearch,
    query: String,
) = customViewModel(
    listOf("search-community", finalOption.name, query)
) { client, databaseSource ->
    CommunitiesViewModel(client, databaseSource, finalOption, query)
}

@Composable
fun createJoinedCommunitiesViewModel() = customViewModel(
    listOf("joined-communities")
) { client, databaseSource ->
    CommunitiesViewModel(client, databaseSource, JoinStatusSearch.JOINED)
}

@Composable
fun createTargetUserJoinedCommunitiesViewModel(
    target: PrimaryKey,
    word: String = "",
) = customViewModel(
    listOf(
        "communities",
        target,
        word
    )
) { client, databaseSource ->
    CommunitiesViewModel(client, databaseSource, JoinStatusSearch.JOINED, word, target)
}

@Composable
fun createCommunityViewModel(communityId: PrimaryKey) = customViewModel(
    listOf(
        "community",
        communityId
    )
) { client, databaseSource ->
    IdCommunityViewModel(client, databaseSource, communityId)
}

@Composable
fun createCommunityViewModel(communityAid: String) = customViewModel(
    listOf(
        "community",
        communityAid
    )
) { client, databaseSource ->
    AidCommunityViewModel(client, databaseSource, communityAid)
}

@Composable
fun createCommunityRoomsViewModel(communityId: PrimaryKey) = customViewModel(
    listOf(
        "community-rooms",
        communityId
    )
) { client, databaseSource ->
    RoomsViewModel(
        client,
        databaseSource,
        JoinStatusSearch.UNSPECIFIED,
        communityId = communityId
    )
}

@Composable
fun createJoinedRoomsViewModel() = customViewModel { client, databaseSource ->
    RoomsViewModel(client, databaseSource, JoinStatusSearch.JOINED)
}

@Composable
fun createRoomViewModel(roomId: PrimaryKey) = customViewModel(
    listOf(
        "room",
        roomId
    )
) { client, databaseSource ->
    IdRoomViewModel(client, databaseSource, roomId)
}

@Composable
fun createRoomSearchInCommunityViewModel(
    scope: SearchScope.CommunityRoom,
    current: String,
) = customViewModel(
    listOf(
        "rooms",
        scope.communityId,
        current
    )
) { client, databaseSource ->
    RoomsViewModel(client, databaseSource, JoinStatusSearch.UNSPECIFIED, current, scope.communityId)
}

@Composable
fun createRoomSearchViewModel(
    finalOption: JoinStatusSearch,
    current: String,
) = customViewModel(listOf("my-rooms", finalOption.name, current)) { client, databaseSource ->
    RoomsViewModel(client, databaseSource, finalOption, current)
}

@Composable
fun createRoomKeysViewModel(
    roomId: PrimaryKey,
    roomInfo: RoomInfo,
) = customViewModel(
    listOf(
        "room-keys",
        roomId
    )
) { client, _ ->
    RoomKeysViewModel(client, roomId, roomInfo.isPrivate)
}

@Composable
fun createRoomViewModel(roomAid: String) = customViewModel(
    listOf(
        "room",
        roomAid
    )
) { client, databaseSource ->
    AidRoomViewModel(client, databaseSource, roomAid)
}

@Composable
fun createRoomTopicsViewModel(roomId: PrimaryKey): TopicsViewModel {
    return buildByMarkdown { typography, density ->
        customViewModel(
            listOf(
                "room-topics",
                roomId
            )
        ) { sessionManager, databaseSource ->
            TopicsViewModel(
                sessionManager,
                databaseSource,
                roomId,
                ObjectType.ROOM,
                typography.code,
                typography.inlineCode,
                density
            )
        }
    }
}

@Composable
fun createCommunityTopicsViewModel(communityId: PrimaryKey): TopicsViewModel {
    return buildByMarkdown { typography, density ->
        customViewModel(
            listOf(
                "community-topics",
                communityId
            )
        ) { sessionManager, databaseSource ->
            TopicsViewModel(
                sessionManager,
                databaseSource,
                communityId,
                ObjectType.COMMUNITY,
                typography.code,
                typography.inlineCode,
                density
            )
        }
    }
}

@Composable
fun createUserTopicsViewModel(
    uid: PrimaryKey,
): TopicsViewModel {
    return buildByMarkdown { typography, density ->
        customViewModel(
            listOf(
                "user-topics",
                uid
            )
        ) { sessionManager, databaseSource ->
            TopicsViewModel(
                sessionManager,
                databaseSource,
                uid,
                ObjectType.USER,
                typography.code,
                typography.inlineCode,
                density
            )
        }
    }
}

@Composable
fun createTopicSearchViewModel(current: String) = customViewModel(
    listOf(
        "topic",
        current
    )
) { sessionManager, databaseSource ->
    TopicSearchViewModel(sessionManager, databaseSource, current.split(" "), null, null)
}

@Composable
fun createTopicSearchInTopicViewModel(
    scope: SearchScope.TopicTopic,
    current: String,
) = customViewModel(
    listOf(
        "topic",
        scope.topicId,
        current
    )
) { sessionManager, databaseSource ->
    TopicSearchViewModel(
        sessionManager,
        databaseSource,
        current.split(" "),
        scope.topicId,
        ObjectType.TOPIC
    )
}

@Composable
fun createTopicSearchInUserViewModel(
    scope: SearchScope.UserTopic,
    current: String,
) = customViewModel(
    listOf(
        "topic",
        scope.userId,
        current
    )
) { sessionManager, databaseSource ->
    TopicSearchViewModel(
        sessionManager,
        databaseSource,
        current.split(" "),
        scope.userId,
        ObjectType.USER
    )
}

@Composable
fun createMemberSearchViewModel(word: String) = customViewModel(
    listOf("members", word)
) { sessionManager, databaseSource ->
    UserSearchViewModel(sessionManager, databaseSource, word)
}

@Composable
fun createTopicSearchInCommunityViewModel(
    scope: SearchScope.CommunityTopic,
    current: String,
) = customViewModel(
    listOf(
        "topic",
        scope.communityId,
        current
    )
) { sessionManager, databaseSource ->
    TopicSearchViewModel(
        sessionManager,
        databaseSource,
        current.split(" "),
        scope.communityId,
        ObjectType.COMMUNITY
    )
}

@Composable
fun createTopicViewModel(topicId: PrimaryKey): IdTopicViewModel {
    return buildByMarkdown { typography, density ->
        customViewModel(
            listOf(
                "topic",
                topicId
            )
        ) { sessionManager, databaseSource ->
            IdTopicViewModel(
                sessionManager,
                databaseSource,
                topicId,
                typography.code,
                typography.inlineCode,
                density
            )
        }
    }
}

@Composable
fun createTopicsInTopicViewModel(topicId: PrimaryKey): TopicsViewModel {
    return buildByMarkdown { typography, density ->
        customViewModel(
            listOf(
                "topic-topics",
                topicId
            )
        ) { sessionManager, databaseSource ->
            TopicsViewModel(
                sessionManager,
                databaseSource,
                topicId,
                ObjectType.TOPIC,
                typography.code,
                typography.inlineCode,
                density
            )
        }
    }
}

@Composable
fun createTopicViewModel(topicAid: String): AidTopicViewModel {
    return buildByMarkdown { typography, density ->
        customViewModel(
            listOf(
                "topic",
                topicAid
            )
        ) { sessionManager, databaseSource ->
            AidTopicViewModel(
                sessionManager,
                databaseSource,
                topicAid,
                typography.code,
                typography.inlineCode,
                density
            )
        }
    }
}

@Composable
fun createTopicSearchInRoomViewModel(
    scope: SearchScope.RoomTopic,
    current: String,
) = customViewModel(
    listOf(
        "topic",
        scope.roomId,
        current
    )
) { sessionManager, databaseSource ->
    TopicSearchViewModel(
        sessionManager,
        databaseSource,
        current.split(" "),
        scope.roomId,
        ObjectType.ROOM
    )
}

@Composable
fun createSearchMemberInRoomViewModel(
    scope: SearchScope.RoomMember,
    current: String,
) = customViewModel(
    listOf(
        "members",
        scope.roomId,
        current
    )
) { sessionManager, databaseSource ->
    ContainerMemberViewModel(sessionManager, databaseSource, scope.roomId, current, ObjectType.ROOM)
}

@Composable
fun createMemberSearchInCommunityViewModel(
    scope: SearchScope.CommunityMember,
    current: String,
) = customViewModel(
    listOf(
        "members",
        scope.communityId,
        current
    )
) { sessionManager, databaseSource ->
    ContainerMemberViewModel(
        sessionManager,
        databaseSource,
        scope.communityId,
        current,
        ObjectType.COMMUNITY
    )
}

@Composable
fun createMediaListViewModel(
    objectTuple: ObjectTuple,
) = customViewModel(
    listOf(
        "media",
        objectTuple.objectId
    )
) { sessionManager, databaseSource ->
    MediaListViewModel(sessionManager, databaseSource, objectTuple.objectId, objectTuple.objectType)
}

@Composable
fun createMemberViewModel(
    objectId: PrimaryKey,
    objectType: ObjectType,
) = customViewModel(
    listOf(
        "members",
        objectId
    )
) { sessionManager, databaseSource ->
    ContainerMemberViewModel(sessionManager, databaseSource, objectId, "", objectType)
}

@Composable
fun createUserViewModel(userAid: String) =
    customViewModel(
        listOf(
            "user",
            userAid
        )
    ) { sessionManager, databaseSource ->
        AidUserViewModel(sessionManager, databaseSource, userAid)
    }

@Composable
fun createUserViewModel(userId: PrimaryKey) =
    customViewModel(
        listOf(
            "user",
            userId
        )
    ) { sessionManager, databaseSource ->
        IdUserViewModel(sessionManager, databaseSource, userId)
    }

@Composable
fun createWorldViewModel(): WorldViewModel {
    return buildByMarkdown { typography, density ->
        customViewModel(
            listOf("world")
        ) { sessionManager, databaseSource ->
            WorldViewModel(
                sessionManager,
                databaseSource,
                typography.code,
                typography.inlineCode,
                density
            )
        }
    }
}

@Composable
fun createReactionsViewModel(objectId: PrimaryKey): ReactionsViewModel {
    return customViewModel(
        listOf(
            "reactions",
            objectId
        )
    ) { sessionManager, databaseSource ->
        ReactionsViewModel(sessionManager, objectId, databaseSource)
    }
}

@Composable
fun createUserTitlesViewModel(
    uid: PrimaryKey,
    searchType: TitleSearchType,
    status: TitleStatus? = null,
    type: TitleType? = null,
    scopeId: PrimaryKey? = null,
) = customViewModel(
    listOf(
        "user-titles",
        uid
    )
) { sessionManager, databaseSource ->
    TitlesViewModel(sessionManager, databaseSource, uid, searchType, status, type, scopeId)
}

@Composable
fun createUploadViewModel(myUid: PrimaryKey) =
    customViewModel(
        listOf(
            "upload",
        )
    ) { _, model ->
        UploadViewModel(myUid, model)
    }

@Composable
fun getUploadViewModel(pathHash: String, myUid: PrimaryKey): UploadDetailViewModel =
    customViewModel(
        listOf("upload-detail", pathHash)
    ) { _, modelStorage ->
        UploadDetailViewModel(modelStorage, pathHash, myUid)
    }

@Composable
fun getMarkdownMediasViewModel(
    input: String,
    objectTuple: ObjectTuple,
): MarkdownMediasViewModel = customViewModel(
    listOf(
        "content",
        md5(input)
    )
) { sessionManager, _ ->
    MarkdownMediasViewModel(sessionManager, input, objectTuple)
}

@Composable
fun getDownloadViewModel(fileId: PrimaryKey?): DownloadViewModel = customViewModel(
    listOf("download", fileId)
) { _, storageSource ->
    DownloadViewModel(storageSource, fileId)
}

@Composable
fun getDownloadListViewModel(): DownloadListViewModel = customViewModel(
    listOf("download-list")
) { _, storageSource ->
    DownloadListViewModel(storageSource)
}

@Composable
fun getQuotaViewModel(
    objectTuple: ObjectTuple,
    quotaType: QuotaType = QuotaType.FILE
): QuotaViewModel = customViewModel(
    listOf("quota", objectTuple.objectId, quotaType.name)
) { sessionManager, _ ->
    QuotaViewModel(sessionManager, objectTuple, quotaType)
}

@Composable
fun getChildAccountsViewModel(): ChildAccountsViewModel = customMainViewModel(
    listOf("child-accounts")
) { sessionManager, storageSource ->
    ChildAccountsViewModel(storageSource, sessionManager)
}

@Composable
fun getLoginHistoryViewModel() = customViewModel(listOf("login-history")) { sessionManager, _ ->
    SessionHistoryViewModel(sessionManager)
}

@Composable
fun getFavoriteViewModel() = customViewModel(listOf("favorite")) { sessionManager, modelStorage ->
    FavoritesViewModel(sessionManager, modelStorage)
}

@Composable
fun getSubscriptionViewModel(): SubscriptionsViewModel {
    return buildByMarkdown { typography, density ->
        customViewModel(listOf("subscription")) { sessionManager, modelStorage ->
            SubscriptionsViewModel(
                sessionManager,
                modelStorage,
                typography.code,
                typography.inlineCode,
                density
            )
        }
    }
}

@Composable
fun getUserOverviewViewModel() =
    customViewModel(listOf("user-overview")) { sessionManager, modelStorage ->
        UserOverviewViewModel(sessionManager, modelStorage)
    }

@Composable
fun createTitleComposeViewModel(): TitleComposeViewModel =
    customViewModel(listOf("title-compose")) { _, _ ->
        TitleComposeViewModel()
    }

@Composable
fun createFileViewModel(objectId: PrimaryKey) =
    customViewModel(
        listOf(
            "files",
            objectId
        )
    ) { sessionManager, databaseSource ->
        FileViewViewModel(objectId, sessionManager, databaseSource)
    }

@Composable
inline fun <reified VM : ViewModel> customViewModel(
    keys: List<Comparable<*>?>? = null,
    crossinline factory: (CustomUserSessionManager, ModelStorage) -> VM
): VM {
    val uiViewModel = LocalUiViewModel.current
    val instance by uiViewModel.instance.collectAsState()
    val databaseSource by instance.database.collectAsState()
    val sessionManager = instance.sessionManager
    return customViewModel<VM>(keys, sessionManager, factory, databaseSource)
}

@Composable
inline fun <reified VM : ViewModel> customViewModel(
    keys: List<Comparable<*>?>?,
    sessionManager: CustomUserSessionManager,
    crossinline factory: (CustomUserSessionManager, ModelStorage) -> VM,
    databaseSource: RoomModelStorage
): VM {
    val address by sessionManager.address.collectAsState()
    SideEffect {
        Napier.i {
            "viewModel $address ${VM::class.simpleName}$keys composable"
        }
    }
    return viewModel(key = "$address:${keys?.joinToString()}") {
        Napier.i {
            "viewModel $address ${VM::class.simpleName}$keys build"
        }
        factory(sessionManager, databaseSource)
    }
}

@Composable
inline fun <reified VM : ViewModel> customMainViewModel(
    keys: List<Comparable<*>?>? = null,
    crossinline factory: (CustomUserSessionManager, ModelStorage) -> VM
): VM {
    val uiViewModel = LocalUiViewModel.current
    val instance by uiViewModel.instance.collectAsState()
    val databaseSource by instance.database.collectAsState()
    val sessionManager = uiViewModel.mainInstance.sessionManager
    return customViewModel<VM>(keys, sessionManager, factory, databaseSource)
}
