package com.storyteller_f.a.app.compose_app.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storyteller_f.a.app.compose_app.LocalDatabase
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.pages.search.SearchScope
import com.storyteller_f.a.client.core.UserSessionManager
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
): CommunitiesViewModel {
    return customViewModel(
        keys = listOf("search-community", finalOption.name, query)
    ) { client, databaseSource ->
        CommunitiesViewModel(client, databaseSource, finalOption, query)
    }
}

@Composable
fun createJoinedCommunitiesViewModel(): CommunitiesViewModel {
    return customViewModel(
        keys = listOf("joined-communities")
    ) { client, databaseSource ->
        CommunitiesViewModel(client, databaseSource, JoinStatusSearch.JOINED)
    }
}

@Composable
fun createTargetUserJoinedCommunitiesViewModel(
    target: PrimaryKey,
    word: String = "",
): CommunitiesViewModel {
    return customViewModel(
        keys = listOf(
            "communities",
            target,
            word
        )
    ) { client, databaseSource ->
        CommunitiesViewModel(client, databaseSource, JoinStatusSearch.JOINED, word, target)
    }
}

@Composable
fun createCommunityViewModel(communityId: PrimaryKey): IdCommunityViewModel {
    return customViewModel(
        keys = listOf(
            "community",
            communityId
        )
    ) { client, databaseSource ->
        IdCommunityViewModel(client, databaseSource, communityId)
    }
}

@Composable
fun createCommunityViewModel(communityAid: String) =
    customViewModel(
        keys = listOf(
            "community",
            communityAid
        )
    ) { client, databaseSource ->
        AidCommunityViewModel(client, databaseSource, communityAid)
    }

@Composable
fun createCommunityRoomsViewModel(communityId: PrimaryKey) =
    customViewModel(
        keys = listOf(
            "community-rooms",
            communityId
        )
    ) { client, databaseSource ->
        RoomsViewModel(
            client,
            databaseSource,
            JoinStatusSearch.UNSPECIFIED,
            community = communityId
        )
    }

@Composable
fun createJoinedRoomsViewModel() =
    customViewModel { client, databaseSource ->
        RoomsViewModel(client, databaseSource, JoinStatusSearch.JOINED)
    }

@Composable
fun createRoomViewModel(roomId: PrimaryKey) =
    customViewModel(
        keys = listOf(
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
    keys = listOf(
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
) = customViewModel(
    keys = listOf(
        "my-rooms",
        finalOption.name,
        current
    )
) { client, databaseSource ->
    RoomsViewModel(client, databaseSource, finalOption, current)
}

@Composable
fun createRoomKeysViewModel(
    roomId: PrimaryKey,
    roomInfo: RoomInfo,
) = customViewModel(
    keys = listOf(
        "room-keys",
        roomId
    )
) { client, databaseSource ->
    RoomKeysViewModel(client, roomId, roomInfo.isPrivate)
}

@Composable
fun createRoomViewModel(roomAid: String) =
    customViewModel(
        keys = listOf(
            "room",
            roomAid
        )
    ) { client, databaseSource ->
        AidRoomViewModel(client, databaseSource, roomAid)
    }

@Composable
fun createRoomTopicsViewModel(roomId: PrimaryKey): TopicsViewModel {
    return customViewModel(
        keys = listOf(
            "room-topics",
            roomId
        )
    ) { client, databaseSource ->
        TopicsViewModel(client, databaseSource, roomId, ObjectType.ROOM)
    }
}

@Composable
fun createCommunityTopicsViewModel(communityId: PrimaryKey): TopicsViewModel {
    return customViewModel<TopicsViewModel>(
        keys = listOf("community-topics", communityId)
    ) { client, databaseSource ->
        TopicsViewModel(client, databaseSource, communityId, ObjectType.COMMUNITY)
    }
}

@Composable
fun createUserTopicsViewModel(
    uid: PrimaryKey,
): TopicsViewModel {
    return customViewModel(
        keys = listOf(
            "user-topics",
            uid
        )
    ) { client, databaseSource ->
        TopicsViewModel(client, databaseSource, uid, ObjectType.USER)
    }
}

@Composable
fun createTopicSearchViewModel(current: String) =
    customViewModel(
        keys = listOf(
            "topic",
            current
        )
    ) { client, databaseSource ->
        TopicSearchViewModel(client, databaseSource, current.split(" "), null, null)
    }

@Composable
fun createTopicSearchInTopicViewModel(
    scope: SearchScope.TopicTopic,
    current: String,
) = customViewModel(
    keys = listOf(
        "topic",
        scope.topicId,
        current
    )
) { client, databaseSource ->
    TopicSearchViewModel(
        client,
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
    keys = listOf(
        "topic",
        scope.userId,
        current
    )
) { client, databaseSource ->
    TopicSearchViewModel(client, databaseSource, current.split(" "), scope.userId, ObjectType.USER)
}

@Composable
fun createMemberSearchViewModel(current: String) =
    customViewModel(
        keys = listOf(
            "members",
            current
        )
    ) { client, databaseSource ->
        MemberViewModel(client, databaseSource, 0, current, ObjectType.USER)
    }

@Composable
fun createTopicSearchInCommunityViewModel(
    scope: SearchScope.CommunityTopic,
    current: String,
) = customViewModel(
    keys = listOf(
        "topic",
        scope.communityId,
        current
    )
) { client, databaseSource ->
    TopicSearchViewModel(
        client,
        databaseSource,
        current.split(" "),
        scope.communityId,
        ObjectType.COMMUNITY
    )
}

@Composable
fun createTopicViewModel(topicId: PrimaryKey) =
    customViewModel(
        keys = listOf(
            "topic",
            topicId
        )
    ) { client, databaseSource ->
        IdTopicViewModel(client, databaseSource, topicId)
    }

@Composable
fun createTopicsInTopicViewModel(topicId: PrimaryKey): TopicsViewModel {
    return customViewModel(
        keys = listOf(
            "topic-topics",
            topicId
        )
    ) { client, databaseSource ->
        TopicsViewModel(client, databaseSource, topicId, ObjectType.TOPIC)
    }
}

@Composable
fun createTopicViewModel(topicAid: String) =
    customViewModel(
        keys = listOf(
            "topic",
            topicAid
        )
    ) { client, databaseSource ->
        AidTopicViewModel(client, databaseSource, topicAid)
    }

@Composable
fun createTopicSearchInRoomViewModel(
    scope: SearchScope.RoomTopic,
    current: String,
) = customViewModel(
    keys = listOf(
        "topic",
        scope.roomId,
        current
    )
) { client, databaseSource ->
    TopicSearchViewModel(client, databaseSource, current.split(" "), scope.roomId, ObjectType.ROOM)
}

@Composable
fun createSearchMemberInRoomViewModel(
    scope: SearchScope.RoomMember,
    current: String,
) = customViewModel(
    keys = listOf(
        "members",
        scope.roomId,
        current
    )
) { client, databaseSource ->
    MemberViewModel(client, databaseSource, scope.roomId, current, ObjectType.ROOM)
}

@Composable
fun createMemberSearchInCommunityViewModel(
    scope: SearchScope.CommunityMember,
    current: String,
) = customViewModel(
    keys = listOf(
        "members",
        scope.communityId,
        current
    )
) { client, databaseSource ->
    MemberViewModel(client, databaseSource, scope.communityId, current, ObjectType.COMMUNITY)
}

@Composable
fun createMediaListViewModel(
    objectTuple: ObjectTuple,
) = customViewModel(
    keys = listOf(
        "media",
        objectTuple.objectId
    )
) { client, databaseSource ->
    MediaListViewModel(client, databaseSource, objectTuple.objectId, objectTuple.objectType)
}

@Composable
fun createMemberViewModel(
    objectId: PrimaryKey,
    objectType: ObjectType,
) = customViewModel(
    keys = listOf(
        "members",
        objectId
    )
) { client, databaseSource ->
    MemberViewModel(client, databaseSource, objectId, "", objectType)
}

@Composable
fun createUserViewModel(userAid: String) =
    customViewModel(
        keys = listOf(
            "user",
            userAid
        )
    ) { client, databaseSource ->
        AidUserViewModel(client, databaseSource, userAid)
    }

@Composable
fun createUserViewModel(userId: PrimaryKey) =
    customViewModel(
        keys = listOf(
            "user",
            userId
        )
    ) { client, databaseSource ->
        IdUserViewModel(client, databaseSource, userId)
    }

@Composable
fun createWorldViewModel(): WorldViewModel {
    return customViewModel(
        keys = listOf("world")
    ) { client, databaseSource ->
        WorldViewModel(client, databaseSource)
    }
}

@Composable
fun createReactionsViewModel(objectId: PrimaryKey): ReactionsViewModel {
    return customViewModel(
        keys = listOf(
            "reactions",
            objectId
        )
    ) { client, databaseSource ->
        ReactionsViewModel(client, objectId, databaseSource)
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
    keys = listOf(
        "user-titles",
        uid
    )
) { client, databaseSource ->
    TitlesViewModel(client, databaseSource, uid, searchType, status, type, scopeId)
}

@Composable
fun createUploadViewModel(myUid: PrimaryKey) =
    customViewModel(
        keys = listOf(
            "upload",
        )
    ) { client, model ->
        UploadViewModel(myUid, model)
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
) { sessionManager, storageSource ->
    DownloadViewModel(storageSource, fileId)
}

@Composable
fun getChildAccountsViewModel(): ChildAccountsViewModel = customViewModel(
    listOf("child-accounts")
) { sessionManager, storageSource ->
    ChildAccountsViewModel(storageSource, sessionManager)
}

@Composable
inline fun <reified VM : ViewModel> customViewModel(
    keys: List<Comparable<*>?>? = null,
    crossinline factory: (UserSessionManager, ModelStorage) -> VM
): VM {
    val sessionManager = LocalSessionManager.current
    val databaseSource = LocalDatabase.current
    SideEffect {
        Napier.i {
            "viewModel ${VM::class.simpleName}$keys composable"
        }
    }
    val address by sessionManager.address.collectAsState()
    return viewModel(key = "$address:${keys?.joinToString()}") {
        Napier.i {
            "viewModel ${VM::class.simpleName}$keys build"
        }
        factory(sessionManager, databaseSource)
    }
}
