package com.storyteller_f.a.app.model

import androidx.compose.runtime.Composable
import com.storyteller_f.a.app.UploadSession
import com.storyteller_f.a.app.common.viewModel
import com.storyteller_f.a.app.pages.search.SearchScope
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.TitleSearchType
import com.storyteller_f.shared.type.*

@Composable
fun createSearchCommunitiesViewModel(
    finalOption: JoinStatusSearch,
    query: String
) = viewModel(
    keys = listOf("search-community", finalOption.name, query)
) { client, databaseSource ->
    CommunitiesViewModel(client, databaseSource = databaseSource, finalOption, query)
}

@Composable
fun createJoinedCommunitiesViewModel() = viewModel(keys = listOf("joined-communities")) { client, databaseSource ->
    CommunitiesViewModel(client, databaseSource = databaseSource, JoinStatusSearch.JOINED, "")
}

@Composable
fun createTargetUserJoinedCommunitiesViewModel(
    target: PrimaryKey,
    word: String = ""
) = viewModel(keys = listOf("communities", target, word)) { client, databaseSource ->
    CommunitiesViewModel(client, databaseSource, JoinStatusSearch.JOINED, word, target)
}

@Composable
fun createCommunityViewModel(communityId: PrimaryKey) =
    viewModel(keys = listOf("community", communityId)) { client, databaseSource ->
        IdCommunityViewModel(client, databaseSource, communityId)
    }

@Composable
fun createCommunityViewModel(communityAid: String) =
    viewModel(keys = listOf("community", communityAid)) { client, databaseSource ->
        AidCommunityViewModel(client, databaseSource, communityAid)
    }

@Composable
fun createCommunityRoomsViewModel(communityId: PrimaryKey) =
    viewModel(keys = listOf("community-rooms", communityId)) { client, databaseSource ->
        RoomsViewModel(client, databaseSource, JoinStatusSearch.UNSPECIFIED, "", communityId)
    }

@Composable
fun createJoinedRoomsViewModel() = viewModel { client, databaseSource ->
    RoomsViewModel(client, databaseSource, JoinStatusSearch.JOINED, "")
}

@Composable
fun createRoomViewModel(roomId: PrimaryKey) =
    viewModel(keys = listOf("room", roomId)) { client, databaseSource ->
        IdRoomViewModel(client, databaseSource, roomId)
    }

@Composable
fun createRoomSearchInCommunityViewModel(
    scope: SearchScope.CommunityRoom,
    current: String
) = viewModel(keys = listOf("rooms", scope.communityId, current)) { client, databaseSource ->
    RoomsViewModel(client, databaseSource, JoinStatusSearch.UNSPECIFIED, current, scope.communityId)
}

@Composable
fun createRoomSearchViewModel(
    finalOption: JoinStatusSearch,
    current: String
) = viewModel(keys = listOf("my-rooms", finalOption.name, current)) { client, databaseSource ->
    RoomsViewModel(client, databaseSource, finalOption, current)
}

@Composable
fun createRoomKeysViewModel(
    roomId: PrimaryKey,
    roomInfo: RoomInfo
) = viewModel(keys = listOf("room-keys", roomId)) { client, databaseSource ->
    RoomKeysViewModel(client, roomId, roomInfo.isPrivate)
}

@Composable
fun createRoomViewModel(roomAid: String) = viewModel(keys = listOf("room", roomAid)) { client, databaseSource ->
    AidRoomViewModel(client, databaseSource, roomAid)
}

@Composable
fun createRoomTopicsViewModel(roomId: PrimaryKey) =
    viewModel(keys = listOf("room-topics", roomId)) { client, databaseSource ->
        TopicsViewModel(client, databaseSource, roomId, ObjectType.ROOM)
    }

@Composable
fun createCommunityTopicsViewModel(communityId: PrimaryKey): TopicsViewModel {
    return viewModel<TopicsViewModel>(
        keys = listOf("community-topics", communityId)
    ) { client, databaseSource ->
        TopicsViewModel(client, databaseSource, communityId, ObjectType.COMMUNITY)
    }
}

@Composable
fun createUserTopicsViewModel(
    uid: PrimaryKey
) = viewModel(keys = listOf("user-topics", uid)) { client, databaseSource ->
    TopicsViewModel(client, databaseSource, uid, ObjectType.USER)
}

@Composable
fun createTopicSearchViewModel(current: String) = viewModel(keys = listOf("topic", current)) { client, databaseSource ->
    TopicSearchViewModel(client, databaseSource, current.split(" "), null, null)
}

@Composable
fun createTopicSearchInTopicViewModel(
    scope: SearchScope.TopicTopic,
    current: String
) = viewModel(keys = listOf("topic", scope.topicId, current)) { client, databaseSource ->
    TopicSearchViewModel(client, databaseSource, current.split(" "), scope.topicId, ObjectType.TOPIC)
}

@Composable
fun createTopicSearchInUserViewModel(
    scope: SearchScope.UserTopic,
    current: String
) = viewModel(keys = listOf("topic", scope.userId, current)) { client, databaseSource ->
    TopicSearchViewModel(client, databaseSource, current.split(" "), scope.userId, ObjectType.USER)
}

@Composable
fun createMemberSearchViewModel(current: String) =
    viewModel(keys = listOf("members", current)) { client, databaseSource ->
        MemberViewModel(client, databaseSource, 0, current, ObjectType.USER)
    }

@Composable
fun createTopicSearchInCommunityViewModel(
    scope: SearchScope.CommunityTopic,
    current: String
) = viewModel(keys = listOf("topic", scope.communityId, current)) { client, databaseSource ->
    TopicSearchViewModel(client, databaseSource, current.split(" "), scope.communityId, ObjectType.COMMUNITY)
}

@Composable
fun createTopicViewModel(topicId: PrimaryKey) =
    viewModel(keys = listOf("topic", topicId)) { client, databaseSource ->
        IdTopicViewModel(client, databaseSource, topicId)
    }

@Composable
fun createTopicsInTopicViewModel(topicId: PrimaryKey) =
    viewModel(keys = listOf("topic-topics", topicId)) { client, databaseSource ->
        TopicsViewModel(client, databaseSource, topicId, ObjectType.TOPIC)
    }

@Composable
fun createTopicViewModel(topicAid: String) = viewModel(keys = listOf("topic", topicAid)) { client, databaseSource ->
    AidTopicViewModel(client, databaseSource, topicAid)
}

@Composable
fun createTopicSearchInRoomViewModel(
    scope: SearchScope.RoomTopic,
    current: String
) = viewModel(keys = listOf("topic", scope.roomId, current)) { client, databaseSource ->
    TopicSearchViewModel(client, databaseSource, current.split(" "), scope.roomId, ObjectType.ROOM)
}

@Composable
fun createSearchMemberInRoomViewModel(
    scope: SearchScope.RoomMember,
    current: String
) = viewModel(keys = listOf("members", scope.roomId, current)) { client, databaseSource ->
    MemberViewModel(client, databaseSource, scope.roomId, current, ObjectType.ROOM)
}

@Composable
fun createMemberSearchInCommunityViewModel(
    scope: SearchScope.CommunityMember,
    current: String
) = viewModel(keys = listOf("members", scope.communityId, current)) { client, databaseSource ->
    MemberViewModel(client, databaseSource, scope.communityId, current, ObjectType.COMMUNITY)
}

@Composable
fun createMediaListViewModel(
    objectTuple: ObjectTuple
) = viewModel(keys = listOf("media", objectTuple.objectId)) { client, databaseSource ->
    MediaListViewModel(client, databaseSource, objectTuple.objectId, objectTuple.objectType)
}

@Composable
fun createAllMediaListViewModel(
    objectTuple: ObjectTuple
) = viewModel(keys = listOf("all-media", objectTuple.objectId)) { client, _ ->
    AllMediaListViewModel(client, objectTuple.objectId, objectTuple.objectType)
}

@Composable
fun createMemberViewModel(
    objectId: PrimaryKey,
    objectType: ObjectType
) = viewModel(keys = listOf("members", objectId)) { client, databaseSource ->
    MemberViewModel(client, databaseSource, objectId, "", objectType)
}

@Composable
fun createUserViewModel(userAid: String) = viewModel(keys = listOf("user", userAid)) { client, databaseSource ->
    AidUserViewModel(client, databaseSource, userAid)
}

@Composable
fun createUserViewModel(userId: PrimaryKey) = viewModel(keys = listOf("user", userId)) { client, databaseSource ->
    IdUserViewModel(client, databaseSource, userId)
}

@Composable
fun createWorldViewModel() = viewModel(keys = listOf("world")) { client, databaseSource ->
    TopicsViewModel(client, databaseSource, DEFAULT_PRIMARY_KEY, null)
}

@Composable
fun createReactionsViewModel(objectId: PrimaryKey) =
    viewModel(keys = listOf("reactions", objectId)) { client, databaseSource ->
        ReactionsViewModel(client, objectId)
    }

@Composable
fun createUserTitlesViewModel(
    uid: PrimaryKey,
    searchType: TitleSearchType,
    status: TitleStatus? = null,
    type: TitleType? = null,
    scopeId: PrimaryKey? = null
) = viewModel(keys = listOf("user-titles", uid)) { client, databaseSource ->
    TitlesViewModel(client, databaseSource, uid, searchType, status, type, scopeId)
}

@Composable
fun createUploadViewModel(myUid: PrimaryKey, uploadSession: UploadSession) =
    viewModel(keys = listOf("upload", uploadSession.name)) { client, _ ->
        UploadViewModel(client, uploadSession, myUid)
    }
