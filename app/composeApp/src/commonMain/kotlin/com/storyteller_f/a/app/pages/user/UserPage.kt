package com.storyteller_f.a.app.pages.user

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.Scaffold
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.storyteller_f.a.app.LocalUserInfo
import com.storyteller_f.a.app.Res
import com.storyteller_f.a.app.common.IdUserViewModel
import com.storyteller_f.a.app.common.createTargetUserJoinedCommunitiesViewModel
import com.storyteller_f.a.app.common.createUserTitlesViewModel
import com.storyteller_f.a.app.common.createUserTopicsViewModel
import com.storyteller_f.a.app.common.createUserViewModel
import com.storyteller_f.a.app.communities_title
import com.storyteller_f.a.app.core.components.CustomBottomNav
import com.storyteller_f.a.app.core.components.CustomRailNav
import com.storyteller_f.a.app.core.components.NavRoute
import com.storyteller_f.a.app.pages.community.CommunityList
import com.storyteller_f.a.app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.pages.search.SearchScope
import com.storyteller_f.a.app.pages.title.TitleList
import com.storyteller_f.a.app.pages.topic.UserTopicList
import com.storyteller_f.a.app.rooms
import com.storyteller_f.a.app.titles
import com.storyteller_f.a.app.topics
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
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

@Serializable
sealed interface UserRoute : NavKey {
    @Serializable
    data object Topics : UserRoute {
        override fun toString(): String = "/topics"
    }

    @Serializable
    data object Communities : UserRoute {
        override fun toString(): String = "/communities"
    }

    @Serializable
    data object Titles : UserRoute {
        override fun toString(): String = "/titles"
    }
}

@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
private fun UserNonCompatInternal(uid: PrimaryKey, user: UserInfo?) {
    val navRoutes =
        listOf(
            NavRoute("/topics", Icons.Default.Topic, stringResource(Res.string.topics)),
            NavRoute("/communities", Icons.Default.ChatBubble, stringResource(Res.string.communities_title)),
            NavRoute("/titles", Icons.Default.Badge, stringResource(Res.string.titles))
        )
    val config = remember {
        SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(UserRoute.Topics::class)
                    subclass(UserRoute.Communities::class)
                    subclass(UserRoute.Titles::class)
                }
            }
        }
    }
    val backStack = rememberNavBackStack(config, UserRoute.Topics)
    val current = backStack.last()
    Scaffold(modifier = Modifier.testTag("user-page")) {
        Row {
            val currentEntry = current.toString()
            UserRailNav(backStack, current, navRoutes)
            UserNonCompatContent(uid, user, currentEntry, backStack)
        }
    }
}

@Composable
private fun UserRailNav(
    backStack: NavBackStack<NavKey>,
    current: NavKey,
    navRoutes: List<NavRoute>,
) {
    CustomRailNav(current.toString(), navRoutes) { path ->
        val target = when (path) {
            "/communities" -> UserRoute.Communities
            "/titles" -> UserRoute.Titles
            else -> UserRoute.Topics
        }
        if (backStack.last() != target) {
            val i = backStack.indexOf(target)
            if (i >= 0) {
                repeat(backStack.size - i - 1) {
                    backStack.removeLastOrNull()
                }
            } else {
                backStack.add(target)
            }
        }
    }
}

@Composable
private fun UserNonCompatContent(
    uid: PrimaryKey,
    user: UserInfo?,
    currentEntry: String,
    backStack: NavBackStack<NavKey>,
) {
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
        NavDisplay(
            backStack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = entryProvider {
                entry<UserRoute.Topics> {
                    val topicsViewModel = createUserTopicsViewModel(uid)
                    UserTopicList(topicsViewModel)
                }
                entry<UserRoute.Communities> {
                    val communitiesViewModel = createTargetUserJoinedCommunitiesViewModel(uid)
                    CommunityList(communitiesViewModel)
                }
                entry<UserRoute.Titles> {
                    val titlesViewModel = createUserTitlesViewModel(uid, TitleSearchType.RECEIVER)
                    TitleList(titlesViewModel)
                }
            }
        )
    }
}

@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
private fun UserCompatInternal(
    uid: PrimaryKey,
    user: UserInfo?,
    my: UserInfo?,
    pagerState: PagerState,
) {
    Scaffold(bottomBar = {
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
                        val titlesViewModel = createUserTitlesViewModel(uid, TitleSearchType.RECEIVER)
                        TitleList(titlesViewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserPageBottomNavBar(pagerState: PagerState) {
    val scope = rememberCoroutineScope()
    val navRoutes = listOf(
        NavRoute("/topics", Icons.Default.Topic, stringResource(Res.string.topics)),
        NavRoute("/communities", Icons.Default.Diversity3, stringResource(Res.string.rooms)),
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
