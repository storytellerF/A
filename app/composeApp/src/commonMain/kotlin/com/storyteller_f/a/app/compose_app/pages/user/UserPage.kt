package com.storyteller_f.a.app.compose_app.pages.user

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.storyteller_f.a.app.compose_app.LocalAppNavFactory
import com.storyteller_f.a.app.compose_app.LocalUserInfo
import com.storyteller_f.a.app.compose_app.Res
import com.storyteller_f.a.app.compose_app.add_topic
import com.storyteller_f.a.app.compose_app.common.IdUserViewModel
import com.storyteller_f.a.app.compose_app.common.createTargetUserJoinedCommunitiesViewModel
import com.storyteller_f.a.app.compose_app.common.createUserTitlesViewModel
import com.storyteller_f.a.app.compose_app.common.createUserTopicsViewModel
import com.storyteller_f.a.app.compose_app.common.createUserViewModel
import com.storyteller_f.a.app.compose_app.communities_title
import com.storyteller_f.a.app.compose_app.components.UserTopicList
import com.storyteller_f.a.app.compose_app.pages.community.CommunityList
import com.storyteller_f.a.app.compose_app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.compose_app.pages.search.SearchScope
import com.storyteller_f.a.app.compose_app.pages.title.TitleList
import com.storyteller_f.a.app.compose_app.pages.topic.TopicComposeData
import com.storyteller_f.a.app.compose_app.rooms
import com.storyteller_f.a.app.compose_app.titles
import com.storyteller_f.a.app.compose_app.topics
import com.storyteller_f.a.app.core.components.CustomBottomNav
import com.storyteller_f.a.app.core.components.CustomRailNav
import com.storyteller_f.a.app.core.components.NavRoute
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun UserPage(uid: PrimaryKey) {
    val userViewModel = createUserViewModel(uid)
    UserPageInternal(uid, userViewModel)
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun UserPageInternal(
    uid: PrimaryKey,
    userViewModel: IdUserViewModel,
) {
    val user by userViewModel.handler.data.collectAsState()
    val my = LocalUserInfo.current
    val pagerState = rememberPagerState {
        3
    }
    val size = calculateWindowSizeClass()
    when (size.widthSizeClass) {
        WindowWidthSizeClass.Compact -> UserCompatInternal(uid, user, my, pagerState)
        else -> UserNonCompatInternal(uid, user)
    }
}

@Composable
private fun UserNonCompatInternal(uid: PrimaryKey, user: UserInfo?) {
    val navRoutes =
        listOf(
            NavRoute("/topics", Icons.Default.Topic, stringResource(Res.string.topics)),
            NavRoute(
                "/communities",
                Icons.Default.ChatBubble,
                stringResource(Res.string.communities_title)
            ),
            NavRoute("/titles", Icons.Default.Badge, stringResource(Res.string.titles))
        )
    val navigator = rememberNavController()
    val current by navigator.currentBackStackEntryFlow.collectAsState(null)
    Scaffold(modifier = Modifier.testTag("user-page")) {
        Row {
            val currentEntry = current?.destination?.route
            CustomRailNav(currentEntry, navRoutes) {
                navigator.navigate(it, NavOptions.Builder().setLaunchSingleTop(true).build())
            }
            Column {
                val searchScope = when (currentEntry) {
                    "/topics" -> SearchScope.UserTopic(uid)
                    "/titles" -> SearchScope.UserReceivedTitle(uid)
                    else -> SearchScope.UserCommunities(uid)
                }
                val my = LocalUserInfo.current
                CustomSearchBar(searchScope) {
                    if (uid != my?.id) {
                        UserIconWithDialog(user)
                    }
                }

                NavHost(navigator, "/topics") {
                    composable("/topics") {
                        val topicsViewModel = createUserTopicsViewModel(uid)
                        UserTopicList(topicsViewModel)
                    }
                    composable("/communities") {
                        val communitiesViewModel = createTargetUserJoinedCommunitiesViewModel(uid)
                        CommunityList(communitiesViewModel)
                    }
                    composable("/titles") {
                        val titlesViewModel =
                            createUserTitlesViewModel(uid, TitleSearchType.RECEIVER)
                        TitleList(titlesViewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserCompatInternal(
    uid: PrimaryKey,
    user: UserInfo?,
    my: UserInfo?,
    pagerState: PagerState,
) {
    Scaffold(floatingActionButton = {
        UserComposeButton(user, my)
    }, bottomBar = {
        UserPageBottomNavBar(pagerState)
    }, modifier = Modifier.testTag("user-page")) {
        Column {
            CustomSearchBar(SearchScope.UserTopic(uid)) {
                if (uid != my?.id) {
                    UserIconWithDialog(user)
                }
            }
            HorizontalPager(pagerState) { pageIndex ->
                when (pageIndex) {
                    0 -> {
                        val topicsViewModel = createUserTopicsViewModel(uid)
                        UserTopicList(topicsViewModel)
                    }

                    1 -> {
                        val communitiesViewModel = createTargetUserJoinedCommunitiesViewModel(uid)
                        CommunityList(communitiesViewModel)
                    }

                    else -> {
                        val titlesViewModel =
                            createUserTitlesViewModel(uid, TitleSearchType.RECEIVER)
                        TitleList(titlesViewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserComposeButton(
    user: UserInfo?,
    my: UserInfo?,
) {
    val appNavFactory = LocalAppNavFactory.current
    if (user != null && my?.id == user.id) {
        FloatingActionButton({
            appNavFactory.newAppNav().gotoTopicCompose(TopicComposeData.User(user.id, user.tuple()))
        }) {
            Icon(Icons.Default.Add, stringResource(Res.string.add_topic))
        }
    }
}

@Composable
private fun UserPageBottomNavBar(pagerState: PagerState) {
    val scope = rememberCoroutineScope()
    val navRoutes = listOf(
        NavRoute(
            "/topics",
            Icons.Default.Topic,
            stringResource(Res.string.topics)
        ),
        NavRoute(
            "/communities",
            Icons.Default.Diversity3,
            stringResource(Res.string.rooms)
        ),
        NavRoute("/titles", Icons.Default.Badge, stringResource(Res.string.titles))
    )
    CustomBottomNav(
        navRoutes[pagerState.currentPage].path,
        navRoutes
    ) { path ->
        scope.launch {
            pagerState.animateScrollToPage(navRoutes.indexOfFirst {
                it.path == path
            })
        }
    }
}
