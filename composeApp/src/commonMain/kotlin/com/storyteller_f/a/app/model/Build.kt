package com.storyteller_f.a.app.model

import androidx.compose.runtime.Composable
import com.storyteller_f.a.app.common.viewModel
import com.storyteller_f.a.app.pages.search.SearchScope
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.type.DEFAULT_PRIMARY_KEY
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey

@Composable
fun createSearchCommunitiesViewModel(
    finalOption: JoinStatusSearch,
    query: String
) = viewModel(
    keys = listOf("search-community", finalOption.name, query)
) {
    CommunitiesViewModel(finalOption, query, client = it)
}

@Composable
fun createJoinedCommunitiesViewModel() = viewModel(keys = listOf("joined-communities")) {
    CommunitiesViewModel(JoinStatusSearch.JOINED, "", client = it)
}

@Composable
fun createTargetUserJoinedCommunitiesViewModel(
    target: PrimaryKey,
    word: String
) = viewModel(keys = listOf("communities", target, word)) {
    CommunitiesViewModel(JoinStatusSearch.JOINED, word, target, it)
}

@Composable
fun createCommunityViewModel(communityId: PrimaryKey) =
    viewModel(keys = listOf("community", communityId)) {
        CommunityViewModel(communityId, it)
    }

@Composable
fun createCommunityViewModel(communityAid: String) = viewModel(keys = listOf("community", communityAid)) {
    CommunityViewModel(communityAid, it)
}

@Composable
fun createCommunityRoomsViewModel(communityId: PrimaryKey) =
    viewModel(keys = listOf("community-rooms", communityId)) {
        RoomsViewModel(JoinStatusSearch.UNSPECIFIED, "", communityId, it)
    }

@Composable
fun createJoinedRoomsViewModel() = viewModel {
    RoomsViewModel(JoinStatusSearch.JOINED, "", client = it)
}

@Composable
fun createRoomViewModel(roomId: PrimaryKey) =
    viewModel(keys = listOf("room", roomId)) {
        RoomViewModel(roomId, it)
    }

@Composable
fun createRoomSearchInCommunityViewModel(
    scope: SearchScope.CommunityRoom,
    current: String
) = viewModel(keys = listOf("rooms", scope.communityId, current)) {
    RoomsViewModel(JoinStatusSearch.UNSPECIFIED, current, scope.communityId, it)
}

@Composable
fun createRoomSearchViewModel(
    finalOption: JoinStatusSearch,
    current: String
) = viewModel(keys = listOf("my-rooms", finalOption.name, current)) {
    RoomsViewModel(finalOption, current, client = it)
}

@Composable
fun createRoomKeysViewModel(
    roomId: PrimaryKey,
    roomInfo: RoomInfo
) = viewModel(keys = listOf("room-keys", roomId)) {
    RoomKeysViewModel(roomId, roomInfo.isPrivate, it)
}

@Composable
fun createRoomViewModel(roomAid: String) = viewModel(keys = listOf("room", roomAid)) {
    RoomViewModel(roomAid, it)
}

@Composable
fun createRoomTopicsViewModel(roomId: PrimaryKey) =
    viewModel(keys = listOf("room-topics", roomId)) {
        TopicsViewModel(roomId, ObjectType.ROOM, it)
    }

@Composable
fun createCommunityTopicsViewModel(communityId: PrimaryKey): TopicsViewModel {
    return viewModel<TopicsViewModel>(
        keys = listOf("community-topics", communityId)
    ) {
        TopicsViewModel(communityId, ObjectType.COMMUNITY, it)
    }
}

@Composable
fun createUserTopicsViewModel(
    uid: PrimaryKey
) = viewModel(keys = listOf("user-topics", uid)) {
    TopicsViewModel(uid, ObjectType.USER, it)
}

@Composable
fun createTopicSearchViewModel(current: String) = viewModel(keys = listOf("topic", current)) {
    TopicSearchViewModel(current.split(" "), null, null, it)
}

@Composable
fun createTopicSearchInTopicViewModel(
    scope: SearchScope.TopicTopic,
    current: String
) = viewModel(keys = listOf("topic", scope.topicId, current)) {
    TopicSearchViewModel(current.split(" "), scope.topicId, ObjectType.TOPIC, it)
}

@Composable
fun createTopicSearchInUserViewModel(
    scope: SearchScope.UserTopic,
    current: String
) = viewModel(keys = listOf("topic", scope.userId, current)) {
    TopicSearchViewModel(current.split(" "), scope.userId, ObjectType.USER, it)
}

@Composable
fun createMemberSearchViewModel(current: String) = viewModel(keys = listOf("members", current)) {
    MemberViewModel(0, current, ObjectType.USER, it)
}

@Composable
fun createTopicSearchInCommunityViewModel(
    scope: SearchScope.CommunityTopic,
    current: String
) = viewModel(keys = listOf("topic", scope.communityId, current)) {
    TopicSearchViewModel(current.split(" "), scope.communityId, ObjectType.COMMUNITY, it)
}

@Composable
fun createTopicViewModel(topicId: PrimaryKey) =
    viewModel(keys = listOf("topic", topicId)) {
        TopicViewModel(topicId, it)
    }

@Composable
fun createTopicsInTopicViewModel(topicId: PrimaryKey) =
    viewModel(keys = listOf("topic-topics", topicId)) {
        TopicsViewModel(topicId, ObjectType.TOPIC, it)
    }

@Composable
fun createTopicViewModel(topicAid: String) = viewModel(keys = listOf("topic", topicAid)) {
    TopicViewModel(topicAid, it)
}

@Composable
fun createTopicSearchInRoomViewModel(
    scope: SearchScope.RoomTopic,
    current: String
) = viewModel(keys = listOf("topic", scope.roomId, current)) {
    TopicSearchViewModel(current.split(" "), scope.roomId, ObjectType.ROOM, it)
}

@Composable
fun createSearchMemberInRoomViewModel(
    scope: SearchScope.RoomMember,
    current: String
) = viewModel(keys = listOf("members", scope.roomId, current)) {
    MemberViewModel(scope.roomId, current, ObjectType.ROOM, it)
}

@Composable
fun createMemberSearchInCommunityViewModel(
    scope: SearchScope.CommunityMember,
    current: String
) = viewModel(keys = listOf("members", scope.communityId, current)) {
    MemberViewModel(scope.communityId, current, ObjectType.COMMUNITY, it)
}

@Composable
fun createMediaListViewModel(
    privateRoomId: PrimaryKey?,
    uid: PrimaryKey
) = viewModel(keys = listOf("media", uid, privateRoomId)) {
    if (privateRoomId != null) {
        MediaListViewModel(privateRoomId, ObjectType.ROOM, it)
    } else {
        MediaListViewModel(uid, ObjectType.USER, it)
    }
}

@Composable
fun createMemberViewModel(
    objectId: PrimaryKey,
    objectType: ObjectType
) = viewModel(keys = listOf("members", objectId)) {
    MemberViewModel(objectId, "", objectType, it)
}

@Composable
fun createUserViewModel(userAid: String) = viewModel(keys = listOf("user", userAid)) {
    UserViewModel(userAid, it)
}

@Composable
fun createUserViewModel(userId: PrimaryKey) =
    viewModel(keys = listOf("user", userId)) {
        UserViewModel(userId, it)
    }

@Composable
fun createWorldViewModel() = viewModel(keys = listOf("world")) {
    TopicsViewModel(DEFAULT_PRIMARY_KEY, null, it)
}

@Composable
fun createReactionsViewModel(objectId: PrimaryKey) =
    viewModel(keys = listOf("reactions", objectId)) {
        ReactionsViewModel(objectId, it)
    }
