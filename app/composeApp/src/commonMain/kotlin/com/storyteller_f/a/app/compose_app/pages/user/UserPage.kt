package com.storyteller_f.a.app.compose_app.pages.user

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.storyteller_f.a.app.compose_app.*
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.compontents.TopicList
import com.storyteller_f.a.app.compose_app.compontents.UserIcon
import com.storyteller_f.a.app.compose_app.model.createTargetUserJoinedCommunitiesViewModel
import com.storyteller_f.a.app.compose_app.model.createUserTitlesViewModel
import com.storyteller_f.a.app.compose_app.model.createUserTopicsViewModel
import com.storyteller_f.a.app.compose_app.model.createUserViewModel
import com.storyteller_f.a.app.compose_app.pages.CustomBottomNav
import com.storyteller_f.a.app.compose_app.pages.CustomRailNav
import com.storyteller_f.a.app.compose_app.pages.NavRoute
import com.storyteller_f.a.app.compose_app.pages.community.CommunityList
import com.storyteller_f.a.app.compose_app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.compose_app.pages.search.SearchScope
import com.storyteller_f.a.app.compose_app.pages.title.TitleList
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun UserPage(uid: PrimaryKey) {
    val userViewModel = createUserViewModel(uid)
    val user by userViewModel.handler.data.collectAsState()
    val userSessionManager = LocalSessionManager.current
    val myInfo by userSessionManager.sessionModel.userHandler.data.collectAsState()
    val my = myInfo
    UserPageInternal(user, my, uid)
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun UserPageInternal(
    user: UserInfo?,
    my: UserInfo?,
    uid: PrimaryKey,
) {
    val pagerState = rememberPagerState {
        3
    }
    val appNav = LocalAppNav.current
    val size = calculateWindowSizeClass()
    when (size.widthSizeClass) {
        WindowWidthSizeClass.Compact -> UserCompatInternal(uid, user, my, appNav, pagerState)
        else -> UserNonCompatInternal(uid, user)
    }
}

@Composable
private fun UserNonCompatInternal(uid: PrimaryKey, user: UserInfo?) {
    val navRoutes =
        listOf(
            NavRoute("/topics", Icons.Default.Topic, stringResource(Res.string.topics)),
            NavRoute("/communities", Icons.Default.ChatBubble, "Communities"),
            NavRoute("/titles", Icons.Default.Title, "Titles")
        )
    val navigator = rememberNavController()
    val current by navigator.currentBackStackEntryFlow.collectAsState(null)
    Scaffold {
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
                val userSessionManager = LocalSessionManager.current
                val myInfo by userSessionManager.sessionModel.userHandler.data.collectAsState()
                val my = myInfo
                CustomSearchBar(searchScope) {
                    if (uid != my?.id) {
                        UserIcon(user)
                    }
                }

                NavHost(navigator, "/topics") {
                    composable("/topics") {
                        val topicsViewModel = createUserTopicsViewModel(uid)
                        TopicList(topicsViewModel, showAvatar = false)
                    }
                    composable("/communities") {
                        val communitiesViewModel = createTargetUserJoinedCommunitiesViewModel(uid)
                        CommunityList(communitiesViewModel)
                    }
                    composable("/titles") {
                        val titlesViewModel = createUserTitlesViewModel(uid, TitleSearchType.RECEIVER)
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
    appNav: AppNav,
    pagerState: PagerState,
) {
    Scaffold(floatingActionButton = {
        UserComposeButton(user, my, appNav)
    }, bottomBar = {
        UserPageBottomNavBar(pagerState)
    }) {
        Column(
            modifier = Modifier.padding(bottom = it.calculateBottomPadding()),
        ) {
            CustomSearchBar(
                SearchScope.UserTopic(
                    uid
                )
            ) {
                if (uid != my?.id) {
                    UserIcon(user)
                }
            }
            HorizontalPager(pagerState) { pageIndex ->
                when (pageIndex) {
                    0 -> {
                        val topicsViewModel =
                            createUserTopicsViewModel(uid)
                        TopicList(
                            topicsViewModel,
                            showAvatar = false
                        )
                    }

                    1 -> {
                        val communitiesViewModel =
                            createTargetUserJoinedCommunitiesViewModel(
                                uid
                            )
                        CommunityList(
                            communitiesViewModel
                        )
                    }

                    else -> {
                        val titlesViewModel =
                            createUserTitlesViewModel(
                                uid,
                                TitleSearchType.RECEIVER
                            )
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
    appNav: AppNav,
) {
    if (user != null && my?.id == user.id) {
        FloatingActionButton({
            appNav.gotoTopicCompose(ObjectType.USER, user.id, false, null, null)
        }) {
            Icon(Icons.Default.Add, "add topic")
        }
    }
}

@Composable
private fun UserPageBottomNavBar(pagerState: PagerState) {
    val scope = rememberCoroutineScope()
    val navs = listOf(
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
        NavRoute("/titles", Icons.Default.Title, "titles")
    )
    CustomBottomNav(
        navs[pagerState.currentPage].path,
        navs
    ) { path ->
        scope.launch {
            pagerState.animateScrollToPage(navs.indexOfFirst {
                it.path == path
            })
        }
    }
}
