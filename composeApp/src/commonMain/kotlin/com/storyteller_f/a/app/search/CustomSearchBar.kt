package com.storyteller_f.a.app.search

import a.composeapp.generated.resources.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paging.compose.collectAsLazyPagingItems
import com.storyteller_f.a.app.common.viewModel
import com.storyteller_f.a.app.community.CommunityList
import com.storyteller_f.a.app.compontents.UserIcon
import com.storyteller_f.a.app.model.*
import com.storyteller_f.a.app.room.RoomList
import com.storyteller_f.a.app.user.MemberList
import com.storyteller_f.a.app.world.TopicList
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

sealed interface SearchScope {
    data object World : SearchScope
    data object MyCommunity : SearchScope
    data object MyRoom : SearchScope
    data class CommunityTopic(val communityId: PrimaryKey) : SearchScope
    data class CommunityRoom(val communityId: PrimaryKey) : SearchScope
    data class RoomTopic(val roomId: PrimaryKey) : SearchScope
    data class TopicTopic(val topicId: PrimaryKey) : SearchScope
    data class CommunityMember(val communityId: PrimaryKey) : SearchScope
    data class RoomMember(val roomId: PrimaryKey) : SearchScope
    data object Member : SearchScope
    data class UserTopic(val userId: PrimaryKey) : SearchScope
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSearchBar(scope: SearchScope, leadingIcon: @Composable () -> Unit) {
    var query by remember {
        mutableStateOf("")
    }
    var searchQuery by remember {
        mutableStateOf("")
    }
    var active by remember {
        mutableStateOf(false)
    }
    val onActiveChange = { newValue: Boolean ->
        active = newValue
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = {
                        query = it
                    },
                    onSearch = {
                        searchQuery = it
                    },
                    expanded = active,
                    onExpandedChange = onActiveChange,
                    leadingIcon = {
                        leadingIcon()
                    },
                    trailingIcon = {
                        val userInfo by LoginViewModel.user.collectAsState()
                        UserIcon(userInfo, size = 40.dp)
                    },
                    placeholder = {
                        SearchPlaceholder(scope)
                    },
                )
            },
            expanded = active,
            onExpandedChange = onActiveChange,
            modifier = Modifier.align(Alignment.Center),
            content = {
                SearchContent(scope, searchQuery)
            },
        )
    }
}

@Composable
private fun SearchPlaceholder(scope: SearchScope) {
    Text(
        stringResource(
            when (scope) {
                SearchScope.World -> Res.string.input_search_topics_and_users
                SearchScope.MyCommunity -> Res.string.input_search_community
                SearchScope.MyRoom -> Res.string.input_search_topics
                is SearchScope.CommunityTopic -> Res.string.input_search_topics
                is SearchScope.CommunityRoom -> Res.string.input_search_room
                is SearchScope.RoomTopic -> Res.string.input_search_topics
                is SearchScope.TopicTopic -> Res.string.input_search_topics
                is SearchScope.CommunityMember -> Res.string.input_search_members
                is SearchScope.RoomMember -> Res.string.input_search_members
                SearchScope.Member -> Res.string.input_search_members
                is SearchScope.UserTopic -> Res.string.input_search_topics
            }
        )
    )
}

@Composable
private fun SearchContent(
    scope: SearchScope,
    searchQuery: String
) {
    val current = searchQuery.trim()
    when (scope) {
        SearchScope.World -> WorldSearchContent(current)

        SearchScope.MyCommunity -> MyCommunitySearchContent(current)

        SearchScope.MyRoom -> MyRoomSearchContent(current)

        is SearchScope.CommunityTopic -> CommunityTopicSearchContent(current, scope)

        is SearchScope.CommunityRoom -> CommunityRoomSearchContent(current, scope)

        is SearchScope.RoomTopic -> RoomTopicSearchContent(current, scope)

        is SearchScope.TopicTopic -> TopicTopicSearchContent(current, scope)

        is SearchScope.CommunityMember -> CommunityMemberSearchContent(current, scope)

        is SearchScope.RoomMember -> RoomMemberSearchContent(current, scope)

        SearchScope.Member -> MemberSearchContent(current)
        is SearchScope.UserTopic -> UserTopicSearchContent(current, scope)
    }
}

@Composable
private fun MemberSearchContent(current: String) {
    if (current.isNotBlank()) {
        val viewModel = createMemberSearchViewModel(current)
        val items = viewModel.flow.collectAsLazyPagingItems()
        MemberList(items)
    }
}

@Composable
private fun RoomMemberSearchContent(current: String, scope: SearchScope.RoomMember) {
    if (current.isNotBlank()) {
        val viewModel = createSearchMemberInRoomViewModel(scope, current)
        val items = viewModel.flow.collectAsLazyPagingItems()
        MemberList(items)
    }
}

@Composable
private fun CommunityMemberSearchContent(current: String, scope: SearchScope.CommunityMember) {
    if (current.isNotBlank()) {
        val viewModel = createMemberSearchInCommunityViewModel(scope, current)
        val items = viewModel.flow.collectAsLazyPagingItems()
        MemberList(items)
    }
}

@Composable
private fun TopicTopicSearchContent(current: String, scope: SearchScope.TopicTopic) {
    if (current.isNotBlank()) {
        val viewModel = createTopicSearchInTopicViewModel(scope, current)
        val topics = viewModel.flow.collectAsLazyPagingItems()
        TopicList(topics)
    }
}

@Composable
private fun UserTopicSearchContent(current: String, scope: SearchScope.UserTopic) {
    if (current.isNotBlank()) {
        val viewModel = createTopicSearchInUserViewModel(scope, current)
        val topics = viewModel.flow.collectAsLazyPagingItems()
        TopicList(topics)
    }
}

@Composable
private fun RoomTopicSearchContent(current: String, scope: SearchScope.RoomTopic) {
    if (current.isNotBlank()) {
        val viewModel = createTopicSearchInRoomViewModel(scope, current)
        val topics = viewModel.flow.collectAsLazyPagingItems()
        TopicList(topics)
    }
}

@Composable
private fun CommunityRoomSearchContent(current: String, scope: SearchScope.CommunityRoom) {
    if (current.isNotBlank()) {
        val viewModel = createRoomSearchInCommunityViewModel(scope, current)
        val items = viewModel.flow.collectAsLazyPagingItems()
        RoomList(items)
    }
}

@Composable
private fun CommunityTopicSearchContent(current: String, scope: SearchScope.CommunityTopic) {
    if (current.isNotBlank()) {
        val viewModel = createTopicSearchInCommunityViewModel(scope, current)
        val topics = viewModel.flow.collectAsLazyPagingItems()
        TopicList(topics)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MyRoomSearchContent(current: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        var currentOption by remember {
            mutableStateOf(JoinStatusSearch.JOINED)
        }

        val isAlreadySignUp by LoginViewModel.isAlreadySignUp.collectAsState(false)
        val finalOption = if (isAlreadySignUp) currentOption else JoinStatusSearch.UNSPECIFIED
        if (isAlreadySignUp) {
            val options = listOf(JoinStatusSearch.JOINED, JoinStatusSearch.NOT_JOINED, JoinStatusSearch.UNSPECIFIED)
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                SingleChoiceSegmentedButtonRow {
                    options.forEachIndexed { i, e ->
                        SegmentedButton(currentOption == e, {
                            currentOption = e
                        }, shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size)) {
                            Text(e.name.lowercase().replace("_", " "))
                        }
                    }
                }
            }
        }
        if (current.isNotBlank()) {
            val viewModel = createRoomSearchViewModel(finalOption, current)
            val items = viewModel.flow.collectAsLazyPagingItems()
            RoomList(items)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MyCommunitySearchContent(query: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        var currentOption by remember {
            mutableStateOf(JoinStatusSearch.JOINED)
        }
        val isAlreadySignUp by LoginViewModel.isAlreadySignUp.collectAsState(false)
        val finalOption = if (isAlreadySignUp) currentOption else JoinStatusSearch.UNSPECIFIED
        if (isAlreadySignUp) {
            val options = listOf(JoinStatusSearch.JOINED, JoinStatusSearch.NOT_JOINED, JoinStatusSearch.UNSPECIFIED)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                SingleChoiceSegmentedButtonRow {
                    options.forEachIndexed { i, e ->
                        SegmentedButton(currentOption == e, {
                            currentOption = e
                        }, shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size)) {
                            Text(e.name.lowercase().replace("_", " "))
                        }
                    }
                }
            }
        }
        if (query.isNotBlank()) {
            val viewModel = createSearchCommunitiesViewModel(finalOption, query)
            val items = viewModel.flow.collectAsLazyPagingItems()
            CommunityList(items)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WorldSearchContent(current: String) {
    if (current.isNotBlank()) {
        val pagerState = rememberPagerState {
            2
        }
        var selected by remember {
            mutableIntStateOf(0)
        }
        val tabs = listOf("Topics", "Users")
        val coroutineScope = rememberCoroutineScope()
        PrimaryTabRow(selected) {
            tabs.forEachIndexed { i, e ->
                Tab(selected = selected == i, onClick = {
                    selected = i
                    coroutineScope.launch {
                        pagerState.scrollToPage(i)
                    }
                }) {
                    Text(text = e, modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
        HorizontalPager(pagerState) {
            if (it == 0) {
                val viewModel = createTopicSearchViewModel(current)
                val topics = viewModel.flow.collectAsLazyPagingItems()
                TopicList(topics)
            } else {
                val viewModel = createMemberSearchViewModel(current)
                val items = viewModel.flow.collectAsLazyPagingItems()
                MemberList(items)
            }
        }
    }
}
