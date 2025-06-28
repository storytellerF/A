package com.storyteller_f.a.app.pages.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.storyteller_f.a.app.AppNav
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.LocalSessionManager
import com.storyteller_f.a.app.Res
import com.storyteller_f.a.app.compontents.UserIcon
import com.storyteller_f.a.app.input_search_community
import com.storyteller_f.a.app.input_search_members
import com.storyteller_f.a.app.input_search_room
import com.storyteller_f.a.app.input_search_topics
import com.storyteller_f.a.app.input_search_topics_and_users
import com.storyteller_f.a.app.input_search_user_created_titles
import com.storyteller_f.a.app.input_search_user_received_titles
import com.storyteller_f.a.app.model.*
import com.storyteller_f.a.app.pages.community.CommunityList
import com.storyteller_f.a.app.pages.room.RoomList
import com.storyteller_f.a.app.pages.title.ComposeMenu
import com.storyteller_f.a.app.pages.user.MemberList
import com.storyteller_f.a.app.pages.world.TopicList
import com.storyteller_f.a.app.utils.platform
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
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
    data class UserCommunities(val userId: PrimaryKey) : SearchScope
    data class UserReceivedTitle(val userId: PrimaryKey) : SearchScope
    data class UserCreatedTitle(val userId: PrimaryKey) : SearchScope
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSearchBar(scope: SearchScope, leadingIcon: @Composable () -> Unit) {
    val appNav = LocalAppNav.current
    var query by remember {
        mutableStateOf("")
    }
    var searchQuery by remember {
        mutableStateOf("")
    }
    var active by remember {
        mutableStateOf(false)
    }
    var showSheet by remember {
        mutableStateOf(false)
    }
    CustomSearchBarInternal(appNav, scope, query, {
        query = it
    }, searchQuery, {
        searchQuery = it
    }, active, {
        active = it
    }, leadingIcon) {
        showSheet = true
    }
    val sheetState = rememberModalBottomSheetState()
    ComposeMenu(showSheet, sheetState, {
        showSheet = false
    }) {
        showSheet = false
        when (it) {
            ObjectType.COMMUNITY -> appNav.gotoCommunityCompose()
            ObjectType.ROOM -> appNav.gotoRoomCompose()
            ObjectType.TOPIC -> TODO()
            ObjectType.USER -> TODO()
            ObjectType.TITLE -> appNav.gotoTitleCompose()
            ObjectType.MEDIA -> TODO()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CustomSearchBarInternal(
    appNav: AppNav,
    scope: SearchScope,
    query: String,
    updateQuery: (String) -> Unit,
    searchQuery: String,
    updateSearch: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    leadingIcon: @Composable (() -> Unit),
    clickCreate: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = updateQuery,
                    onSearch = updateSearch,
                    expanded = active,
                    onExpandedChange = onActiveChange,
                    leadingIcon = {
                        MergedLeadingIcon(leadingIcon, active, appNav)
                    },
                    trailingIcon = {
                        val userSessionManager = LocalSessionManager.current
                        val myInfo by userSessionManager.sessionModel.userHandler.data.collectAsState()
                        val userInfo = myInfo
                        UserIcon(userInfo, true, onClickCreate = clickCreate)
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
private fun MergedLeadingIcon(
    leadingIcon: @Composable () -> Unit,
    active: Boolean,
    appNav: AppNav
) {
    var active1 = active
    if (platform.hasNativeBack) {
        leadingIcon()
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({
                if (active1) {
                    active1 = false
                } else {
                    appNav.back()
                }
            }) {
                Icon(Icons.AutoMirrored.Default.ArrowBack, "back")
            }
            leadingIcon()
        }
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
                is SearchScope.UserCommunities -> Res.string.input_search_community
                is SearchScope.UserReceivedTitle -> Res.string.input_search_user_received_titles
                is SearchScope.UserCreatedTitle -> Res.string.input_search_user_created_titles
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
        is SearchScope.UserCommunities -> UserCommunitySearchContent(current, scope)
        is SearchScope.UserReceivedTitle -> UserReceivedTitleSearchContent(current, scope)
        is SearchScope.UserCreatedTitle -> UserCreatedTitleSearchContent(current, scope)
    }
}

@Suppress("unused")
@Composable
fun UserCreatedTitleSearchContent(x0: String, x1: SearchScope.UserCreatedTitle) {
    TODO("Not yet implemented")
}

@Suppress("unused")
@Composable
fun UserReceivedTitleSearchContent(x0: String, x1: SearchScope.UserReceivedTitle) {
    TODO("Not yet implemented")
}

@Composable
fun UserCommunitySearchContent(current: String, scope: SearchScope.UserCommunities) {
    if (current.isNotBlank()) {
        val communitiesViewModel = createTargetUserJoinedCommunitiesViewModel(scope.userId, current)
        val pagingItems = communitiesViewModel.flow.collectAsLazyPagingItems()
        CommunityList(pagingItems)
    }
}

@Composable
private fun MemberSearchContent(current: String) {
    if (current.isNotBlank()) {
        val viewModel = createMemberSearchViewModel(current)
        MemberList(viewModel.flow.collectAsLazyPagingItems())
    }
}

@Composable
private fun RoomMemberSearchContent(current: String, scope: SearchScope.RoomMember) {
    if (current.isNotBlank()) {
        val viewModel = createSearchMemberInRoomViewModel(scope, current)
        MemberList(viewModel.flow.collectAsLazyPagingItems())
    }
}

@Composable
private fun CommunityMemberSearchContent(current: String, scope: SearchScope.CommunityMember) {
    if (current.isNotBlank()) {
        val viewModel = createMemberSearchInCommunityViewModel(scope, current)
        MemberList(viewModel.flow.collectAsLazyPagingItems())
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

        val userSessionManager = LocalSessionManager.current
        val isAlreadySignUp by userSessionManager.isAlreadySignUp.collectAsState()
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
        val userSessionManager = LocalSessionManager.current
        val isAlreadySignUp by userSessionManager.isAlreadySignUp.collectAsState()
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
                MemberList(viewModel.flow.collectAsLazyPagingItems())
            }
        }
    }
}
