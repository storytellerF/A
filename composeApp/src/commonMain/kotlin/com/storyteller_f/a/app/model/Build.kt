package com.storyteller_f.a.app.model

import androidx.compose.runtime.Composable
import com.storyteller_f.a.app.UploadSession
import com.storyteller_f.a.app.common.viewModel
import com.storyteller_f.a.app.pages.search.SearchScope
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.*

@Composable
fun createSearchCommunitiesViewModel(
    finalOption: JoinStatusSearch, query: String
) = viewModel(
    keys = listOf("search-community", finalOption.name, query)
) { client, databaseSource, address ->
    CommunitiesViewModel(client, databaseSource = databaseSource, finalOption, query, scopeName = address)
}

@Composable
fun createJoinedCommunitiesViewModel() =
    viewModel(keys = listOf("joined-communities")) { client, databaseSource, address ->
        CommunitiesViewModel(client, databaseSource = databaseSource, JoinStatusSearch.JOINED, "", scopeName = address)
    }

@Composable
fun createTargetUserJoinedCommunitiesViewModel(
    target: PrimaryKey, word: String = ""
) = viewModel(keys = listOf("communities", target, word)) { client, databaseSource, address ->
    CommunitiesViewModel(client, databaseSource, JoinStatusSearch.JOINED, word, target, scopeName = address)
}

@Composable
fun createCommunityViewModel(communityId: PrimaryKey): IdCommunityViewModel {
    return viewModel(keys = listOf("community", communityId)) { client, databaseSource, address ->
        IdCommunityViewModel(client, databaseSource, communityId, address)
    }
}

@Composable
fun createCommunityViewModel(communityAid: String) =
    viewModel(keys = listOf("community", communityAid)) { client, databaseSource, address ->
        AidCommunityViewModel(client, databaseSource, communityAid, address)
    }

@Composable
fun createCommunityRoomsViewModel(communityId: PrimaryKey) =
    viewModel(keys = listOf("community-rooms", communityId)) { client, databaseSource, address ->
        RoomsViewModel(client, databaseSource, JoinStatusSearch.UNSPECIFIED, "", communityId, address)
    }

@Composable
fun createJoinedRoomsViewModel() = viewModel { client, databaseSource, address ->
    RoomsViewModel(client, databaseSource, JoinStatusSearch.JOINED, "", scopeName = address)
}

@Composable
fun createRoomViewModel(roomId: PrimaryKey) =
    viewModel(keys = listOf("room", roomId)) { client, databaseSource, address ->
        IdRoomViewModel(client, databaseSource, roomId, address)
    }

@Composable
fun createRoomSearchInCommunityViewModel(
    scope: SearchScope.CommunityRoom, current: String
) = viewModel(keys = listOf("rooms", scope.communityId, current)) { client, databaseSource, address ->
    RoomsViewModel(client, databaseSource, JoinStatusSearch.UNSPECIFIED, current, scope.communityId, address)
}

@Composable
fun createRoomSearchViewModel(
    finalOption: JoinStatusSearch, current: String
) = viewModel(keys = listOf("my-rooms", finalOption.name, current)) { client, databaseSource, address ->
    RoomsViewModel(client, databaseSource, finalOption, current, scopeName = address)
}

@Composable
fun createRoomKeysViewModel(
    roomId: PrimaryKey, roomInfo: RoomInfo
) = viewModel(keys = listOf("room-keys", roomId)) { client, databaseSource, address ->
    RoomKeysViewModel(client, roomId, roomInfo.isPrivate)
}

@Composable
fun createRoomViewModel(roomAid: String) =
    viewModel(keys = listOf("room", roomAid)) { client, databaseSource, address ->
        AidRoomViewModel(client, databaseSource, roomAid, address)
    }

@Composable
fun createRoomTopicsViewModel(roomId: PrimaryKey) =
    viewModel(keys = listOf("room-topics", roomId)) { client, databaseSource, address ->
        TopicsViewModel(client, databaseSource, roomId, address, ObjectType.ROOM)
    }

@Composable
fun createCommunityTopicsViewModel(communityId: PrimaryKey): TopicsViewModel {
    return viewModel<TopicsViewModel>(
        keys = listOf("community-topics", communityId)
    ) { client, databaseSource, address ->
        TopicsViewModel(client, databaseSource, communityId, address, ObjectType.COMMUNITY)
    }
}

@Composable
fun createUserTopicsViewModel(
    uid: PrimaryKey
) = viewModel(keys = listOf("user-topics", uid)) { client, databaseSource, address ->
    TopicsViewModel(client, databaseSource, uid, address, ObjectType.USER)
}

@Composable
fun createTopicSearchViewModel(current: String) =
    viewModel(keys = listOf("topic", current)) { client, databaseSource, address ->
        TopicSearchViewModel(client, databaseSource, current.split(" "), null, null, address)
    }

@Composable
fun createTopicSearchInTopicViewModel(
    scope: SearchScope.TopicTopic, current: String
) = viewModel(keys = listOf("topic", scope.topicId, current)) { client, databaseSource, address ->
    TopicSearchViewModel(client, databaseSource, current.split(" "), scope.topicId, ObjectType.TOPIC, address)
}

@Composable
fun createTopicSearchInUserViewModel(
    scope: SearchScope.UserTopic, current: String
) = viewModel(keys = listOf("topic", scope.userId, current)) { client, databaseSource, address ->
    TopicSearchViewModel(client, databaseSource, current.split(" "), scope.userId, ObjectType.USER, address)
}

@Composable
fun createMemberSearchViewModel(current: String) =
    viewModel(keys = listOf("members", current)) { client, databaseSource, address ->
        MemberViewModel(client, databaseSource, 0, current, ObjectType.USER, address)
    }

@Composable
fun createTopicSearchInCommunityViewModel(
    scope: SearchScope.CommunityTopic, current: String
) = viewModel(keys = listOf("topic", scope.communityId, current)) { client, databaseSource, address ->
    TopicSearchViewModel(client, databaseSource, current.split(" "), scope.communityId, ObjectType.COMMUNITY, address)
}

@Composable
fun createTopicViewModel(topicId: PrimaryKey) =
    viewModel(keys = listOf("topic", topicId)) { client, databaseSource, address ->
        IdTopicViewModel(client, databaseSource, topicId, address)
    }

@Composable
fun createTopicsInTopicViewModel(topicId: PrimaryKey) =
    viewModel(keys = listOf("topic-topics", topicId)) { client, databaseSource, address ->
        TopicsViewModel(client, databaseSource, topicId, address, ObjectType.TOPIC)
    }

@Composable
fun createTopicViewModel(topicAid: String) =
    viewModel(keys = listOf("topic", topicAid)) { client, databaseSource, address ->
        AidTopicViewModel(client, databaseSource, topicAid, address)
    }

@Composable
fun createTopicSearchInRoomViewModel(
    scope: SearchScope.RoomTopic, current: String
) = viewModel(keys = listOf("topic", scope.roomId, current)) { client, databaseSource, address ->
    TopicSearchViewModel(client, databaseSource, current.split(" "), scope.roomId, ObjectType.ROOM, address)
}

@Composable
fun createSearchMemberInRoomViewModel(
    scope: SearchScope.RoomMember, current: String
) = viewModel(keys = listOf("members", scope.roomId, current)) { client, databaseSource, address ->
    MemberViewModel(client, databaseSource, scope.roomId, current, ObjectType.ROOM, address)
}

@Composable
fun createMemberSearchInCommunityViewModel(
    scope: SearchScope.CommunityMember, current: String
) = viewModel(keys = listOf("members", scope.communityId, current)) { client, databaseSource, address ->
    MemberViewModel(client, databaseSource, scope.communityId, current, ObjectType.COMMUNITY, address)
}

@Composable
fun createMediaListViewModel(
    objectTuple: ObjectTuple
) = viewModel(keys = listOf("media", objectTuple.objectId)) { client, databaseSource, address ->
    MediaListViewModel(client, databaseSource, objectTuple.objectId, objectTuple.objectType, address)
}

@Composable
fun createAllMediaListViewModel(
    objectTuple: ObjectTuple
) = viewModel(keys = listOf("all-media", objectTuple.objectId)) { client, _, _ ->
    AllMediaListViewModel(client, objectTuple.objectId, objectTuple.objectType)
}

@Composable
fun createMemberViewModel(
    objectId: PrimaryKey, objectType: ObjectType
) = viewModel(keys = listOf("members", objectId)) { client, databaseSource, address ->
    MemberViewModel(client, databaseSource, objectId, "", objectType, address)
}

@Composable
fun createUserViewModel(userAid: String) =
    viewModel(keys = listOf("user", userAid)) { client, databaseSource, address ->
        AidUserViewModel(client, databaseSource, userAid, address)
    }

@Composable
fun createUserViewModel(userId: PrimaryKey) =
    viewModel(keys = listOf("user", userId)) { client, databaseSource, address ->
        IdUserViewModel(client, databaseSource, userId, address)
    }

@Composable
fun createWorldViewModel() = viewModel(keys = listOf("world")) { client, databaseSource, _ ->
    TopicsViewModel(client, databaseSource, DEFAULT_PRIMARY_KEY, null)
}

@Composable
fun createReactionsViewModel(objectId: PrimaryKey) =
    viewModel(keys = listOf("reactions", objectId)) { client, databaseSource, _ ->
        ReactionsViewModel(client, objectId)
    }

@Composable
fun createUserTitlesViewModel(
    uid: PrimaryKey,
    searchType: TitleSearchType,
    status: TitleStatus? = null,
    type: TitleType? = null,
    scopeId: PrimaryKey? = null
) = viewModel(keys = listOf("user-titles", uid)) { client, databaseSource, address ->
    TitlesViewModel(client, databaseSource, uid, searchType, status, type, scopeId, address)
}

@Composable
fun createUploadViewModel(myUid: PrimaryKey, uploadSession: UploadSession) =
    viewModel(keys = listOf("upload", uploadSession.name)) { client, _, _ ->
        UploadViewModel(client, uploadSession, myUid)
    }
