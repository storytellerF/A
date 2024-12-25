package com.storyteller_f.a.app.model

import androidx.compose.runtime.Composable
import com.storyteller_f.a.app.common.viewModel
import com.storyteller_f.a.app.search.SearchScope
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.type.DEFAULT_PRIMARY_KEY
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey

@Composable
fun createSearchCommunitiesViewModel(
    finalOption: JoinStatusSearch,
    query: String
) = viewModel(
    keys = listOf("my-community", finalOption.name, query)
) {
    CommunitiesViewModel(finalOption, query)
}

@Composable
fun createJoinedCommunitiesViewModel() = viewModel {
    CommunitiesViewModel(JoinStatusSearch.JOINED, "")
}

@Composable
fun createCommunityViewModel(communityId: PrimaryKey) =
    viewModel(keys = listOf("community", communityId)) {
        CommunityViewModel(communityId)
    }

@Composable
fun createCommunityViewModel(communityAid: String) = viewModel(keys = listOf("community", communityAid)) {
    CommunityViewModel(communityAid)
}

@Composable
fun createCommunityRoomsViewModel(communityId: PrimaryKey) =
    viewModel(keys = listOf("community-rooms", communityId)) {
        RoomsViewModel(JoinStatusSearch.UNSPECIFIED, "", communityId)
    }

@Composable
fun createJoinedRoomsViewModel() = viewModel {
    RoomsViewModel(JoinStatusSearch.JOINED, "")
}

@Composable
fun createRoomViewModel(roomId: PrimaryKey) =
    viewModel(keys = listOf("room", roomId)) {
        RoomViewModel(roomId)
    }

@Composable
fun createRoomSearchInCommunityViewModel(
    scope: SearchScope.CommunityRoom,
    current: String
) = viewModel(keys = listOf("rooms", scope.communityId, current)) {
    RoomsViewModel(JoinStatusSearch.UNSPECIFIED, current, scope.communityId)
}

@Composable
fun createRoomSearchViewModel(
    finalOption: JoinStatusSearch,
    current: String
) = viewModel(keys = listOf("my-rooms", finalOption.name, current)) {
    RoomsViewModel(finalOption, current)
}

@Composable
fun createRoomKeysViewModel(
    roomId: PrimaryKey,
    roomInfo: RoomInfo
) = viewModel(keys = listOf("room-keys", roomId)) {
    RoomKeysViewModel(roomId, roomInfo.isPrivate)
}

@Composable
fun createRoomViewModel(roomAid: String) = viewModel(keys = listOf("room", roomAid)) {
    RoomViewModel(roomAid)
}

@Composable
fun createRoomTopicsViewModel(roomId: PrimaryKey) =
    viewModel(keys = listOf("room-topics", roomId)) {
        TopicsViewModel(roomId, ObjectType.ROOM)
    }

@Composable
fun createCommunityTopicsViewModel(communityId: PrimaryKey): TopicsViewModel {
    return viewModel<TopicsViewModel>(
        keys = listOf("community-topics", communityId)
    ) {
        TopicsViewModel(communityId, ObjectType.COMMUNITY)
    }
}

@Composable
fun createTopicSearchViewModel(current: String) = viewModel(keys = listOf("topic", current)) {
    TopicSearchViewModel(current.split(" "), null, null)
}

@Composable
fun createTopicSearchInTopicViewModel(
    scope: SearchScope.TopicTopic,
    current: String
) = viewModel(keys = listOf("topic", scope.topicId, current)) {
    TopicSearchViewModel(current.split(" "), scope.topicId, ObjectType.TOPIC)
}

@Composable
fun createTopicSearchInUserViewModel(
    scope: SearchScope.UserTopic,
    current: String
) = viewModel(keys = listOf("topic", scope.userId, current)) {
    TopicSearchViewModel(current.split(" "), scope.userId, ObjectType.USER)
}

@Composable
fun createMemberSearchViewModel(current: String) = viewModel(keys = listOf("members", current)) {
    MemberViewModel(0, current, ObjectType.USER)
}

@Composable
fun createTopicSearchInCommunityViewModel(
    scope: SearchScope.CommunityTopic,
    current: String
) = viewModel(keys = listOf("topic", scope.communityId, current)) {
    TopicSearchViewModel(current.split(" "), scope.communityId, ObjectType.COMMUNITY)
}

@Composable
fun createTopicViewModel(topicId: PrimaryKey) =
    viewModel(keys = listOf("topic", topicId)) {
        TopicViewModel(topicId)
    }

@Composable
fun createTopicViewModelFromInfo(topicInfo: TopicInfo) =
    viewModel(keys = listOf("topic", topicInfo.id)) {
        TopicViewModel(topicInfo)
    }

@Composable
fun createTopicsInTopicViewModel(topicId: PrimaryKey) =
    viewModel(keys = listOf("topic-topics", topicId)) {
        TopicsViewModel(topicId, ObjectType.TOPIC)
    }

@Composable
fun createTopicViewModel(topicAid: String) = viewModel(keys = listOf("topic", topicAid)) {
    TopicViewModel(topicAid)
}

@Composable
fun createTopicSearchInRoomViewModel(
    scope: SearchScope.RoomTopic,
    current: String
) = viewModel(keys = listOf("topic", scope.roomId, current)) {
    TopicSearchViewModel(current.split(" "), scope.roomId, ObjectType.ROOM)
}

@Composable
fun createSearchMemberInRoomViewModel(
    scope: SearchScope.RoomMember,
    current: String
) = viewModel(keys = listOf("members", scope.roomId, current)) {
    MemberViewModel(scope.roomId, current, ObjectType.ROOM)
}

@Composable
fun createMemberSearchInCommunityViewModel(
    scope: SearchScope.CommunityMember,
    current: String
) = viewModel(keys = listOf("members", scope.communityId, current)) {
    MemberViewModel(scope.communityId, current, ObjectType.COMMUNITY)
}

@Composable
fun createMediaListViewModel(
    privateRoomId: PrimaryKey?,
    uid: PrimaryKey
) = viewModel(keys = listOf("media", uid, privateRoomId)) {
    if (privateRoomId != null) {
        MediaListViewModel(privateRoomId, ObjectType.ROOM)
    } else {
        MediaListViewModel(uid, ObjectType.USER)
    }
}

@Composable
fun createMemberViewModel(
    objectId: PrimaryKey,
    objectType: ObjectType
) = viewModel(keys = listOf("members", objectId)) {
    MemberViewModel(objectId, "", objectType)
}

@Composable
fun createUserViewModel(userAid: String) = viewModel(keys = listOf("user", userAid)) {
    UserViewModel(userAid)
}

@Composable
fun createUserViewModel(userId: PrimaryKey) =
    viewModel(keys = listOf("user", userId)) {
        UserViewModel(userId)
    }

@Composable
fun createWorldViewModel() = viewModel(keys = listOf("world")) {
    TopicsViewModel(DEFAULT_PRIMARY_KEY, null)
}

@Composable
fun createReactionsViewModel(objectId: PrimaryKey) =
    viewModel(keys = listOf("reactions", objectId)) {
        ReactionsViewModel(objectId)
    }
