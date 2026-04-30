package com.storyteller_f.a.app.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.storyteller_f.a.app.LocalAppNavFactory
import com.storyteller_f.a.app.LocalUiViewModel
import com.storyteller_f.a.app.Res
import com.storyteller_f.a.app.common.createWorldViewModel
import com.storyteller_f.a.app.common.getUnreadRoomsStateViewModel
import com.storyteller_f.a.app.communities
import com.storyteller_f.a.app.core.components.ButtonNav
import com.storyteller_f.a.app.core.components.CenterBox
import com.storyteller_f.a.app.core.components.CustomBottomNav
import com.storyteller_f.a.app.core.components.CustomRailNav
import com.storyteller_f.a.app.core.components.NavRoute
import com.storyteller_f.a.app.core.components.SignInButton
import com.storyteller_f.a.app.design_spec
import com.storyteller_f.a.app.download_latest_app
import com.storyteller_f.a.app.home_start_destination_communities
import com.storyteller_f.a.app.home_start_destination_rooms
import com.storyteller_f.a.app.home_start_destination_world
import com.storyteller_f.a.app.open_source_libraries
import com.storyteller_f.a.app.pages.community.MyCommunitiesPage
import com.storyteller_f.a.app.pages.room.MyRoomsPage
import com.storyteller_f.a.app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.pages.search.SearchScope
import com.storyteller_f.a.app.pages.topic.TopicList
import com.storyteller_f.a.app.rooms
import com.storyteller_f.a.app.world
import com.strabled.composepreferences.getPreference
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jetbrains.compose.resources.stringResource

@Composable
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalFoundationApi::class)
fun HomePage() {
    val size = calculateWindowSizeClass()
    val homeStartDestinationFlow: StateFlow<String> by getPreference(HOME_START_DESTINATION_PREFERENCE_KEY)
    val homeStartDestination by homeStartDestinationFlow.collectAsState()
    val defaultHomeRoute = homeRouteFromPreference(homeStartDestination)
    val defaultHomePage = homePageFromPreference(homeStartDestination)
    val homeNavRoutes = listOf(
        NavRoute(HOME_START_DESTINATION_WORLD, Icons.Default.Public, stringResource(Res.string.world)),
        NavRoute(HOME_START_DESTINATION_COMMUNITIES, Icons.Default.Diversity3, stringResource(Res.string.communities)),
        NavRoute(HOME_START_DESTINATION_ROOMS, Icons.Default.ChatBubble, stringResource(Res.string.rooms)),
    )
    val modifier = Modifier.testTag("home")
    when (size.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            HomeCompatPage(homeNavRoutes, modifier, defaultHomePage)
        }

        else -> {
            HomeNonCompatPage(modifier, homeNavRoutes, defaultHomeRoute)
        }
    }
}

@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
private fun HomeNonCompatPage(
    modifier: Modifier,
    homeNavRoutes: List<NavRoute>,
    defaultHomeRoute: HomeRoute,
) {
    val unreadRoomsViewModel = getUnreadRoomsStateViewModel()
    val hasUnread by unreadRoomsViewModel.handler.data.collectAsState()
    Scaffold(modifier = modifier) {
        Row(Modifier) {
            val config = remember {
                SavedStateConfiguration {
                    serializersModule = SerializersModule {
                        polymorphic(NavKey::class) {
                            subclass(HomeRoute.World::class)
                            subclass(HomeRoute.Communities::class)
                            subclass(HomeRoute.Rooms::class)
                        }
                    }
                }
            }
            val backStack = rememberNavBackStack(config, defaultHomeRoute)
            val currentEntry = backStack.last()
            CustomRailNav(
                currentEntry = currentEntry.toString(),
                navRoutes = homeNavRoutes,
                unreadRoomsBadge = hasUnread ?: false
            ) { path ->
                val targetRoute = when (path) {
                    HOME_START_DESTINATION_COMMUNITIES -> HomeRoute.Communities
                    HOME_START_DESTINATION_ROOMS -> HomeRoute.Rooms
                    else -> HomeRoute.World
                }
                if (backStack.last() != targetRoute) {
                    val i = backStack.indexOf(targetRoute)
                    if (i >= 0) {
                        repeat(backStack.size - i - 1) {
                            backStack.removeLastOrNull()
                        }
                    } else {
                        backStack.add(targetRoute)
                    }
                }
            }
            HomeNavDisplay(backStack, modifier = Modifier.weight(1f))
        }
    }
}

@Serializable
sealed interface HomeRoute : NavKey {
    @Serializable
    data object World : HomeRoute {
        override fun toString(): String = HOME_START_DESTINATION_WORLD
    }

    @Serializable
    data object Communities : HomeRoute {
        override fun toString(): String = HOME_START_DESTINATION_COMMUNITIES
    }

    @Serializable
    data object Rooms : HomeRoute {
        override fun toString(): String = HOME_START_DESTINATION_ROOMS
    }
}

internal const val HOME_START_DESTINATION_PREFERENCE_KEY = "home_start_destination"
internal const val HOME_START_DESTINATION_WORLD = "/world"
internal const val HOME_START_DESTINATION_COMMUNITIES = "/communities"
internal const val HOME_START_DESTINATION_ROOMS = "/rooms"

internal fun homeRouteFromPreference(value: String?): HomeRoute = when (value) {
    HOME_START_DESTINATION_COMMUNITIES -> HomeRoute.Communities
    HOME_START_DESTINATION_ROOMS -> HomeRoute.Rooms
    else -> HomeRoute.World
}

internal fun homePageFromPreference(value: String?): Int = when (value) {
    HOME_START_DESTINATION_COMMUNITIES -> 1
    HOME_START_DESTINATION_ROOMS -> 2
    else -> 0
}

@Composable
internal fun homeStartDestinationLabel(value: String?): String = when (value) {
    HOME_START_DESTINATION_COMMUNITIES -> stringResource(Res.string.home_start_destination_communities)
    HOME_START_DESTINATION_ROOMS -> stringResource(Res.string.home_start_destination_rooms)
    else -> stringResource(Res.string.home_start_destination_world)
}

@Composable
private fun HomeCompatPage(
    homeNavRoutes: List<NavRoute>,
    modifier: Modifier,
    defaultHomePage: Int,
) {
    val pagerState = rememberPagerState(initialPage = defaultHomePage) {
        3
    }
    val unreadRoomsViewModel = getUnreadRoomsStateViewModel()
    val hasUnread by unreadRoomsViewModel.handler.data.collectAsState()
    Scaffold(bottomBar = {
        val scope = rememberCoroutineScope()
        CustomBottomNav(
            path = homeNavRoutes[pagerState.currentPage].path,
            navRoutes = homeNavRoutes,
            unreadRoomsBadge = hasUnread ?: false
        ) { path ->
            scope.launch {
                pagerState.animateScrollToPage(homeNavRoutes.indexOfFirst {
                    it.path == path
                })
            }
        }
    }, modifier = modifier) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val scope = when (pagerState.currentPage) {
                1 -> SearchScope.MyCommunity
                2 -> SearchScope.MyRoom
                else -> SearchScope.World
            }
            CustomSearchBar(scope) {
                ProjectIcon()
            }
            Spacer(modifier = Modifier.height(10.dp))
            HomePager(Modifier.weight(1f).padding(bottom = it.calculateBottomPadding()), pagerState)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ProjectIcon() {
    var showDialog by remember {
        mutableStateOf(false)
    }
    Box(
        modifier = Modifier.size(40.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .clip(CircleShape)
            .clickable {
                showDialog = true
            },
        contentAlignment = Alignment.Center
    ) {
        Text("A", color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
    if (showDialog) {
        BasicAlertDialog({
            showDialog = false
        }) {
            ProjectDialogInternal {
                showDialog = false
            }
        }
    }
}

@Composable
private fun HomeNavDisplay(
    backStack: NavBackStack<NavKey>,
    modifier: Modifier
) {
    Column(modifier = modifier) {
        val current = backStack.last()
        val scope = when (current) {
            HomeRoute.Communities -> SearchScope.MyCommunity
            HomeRoute.Rooms -> SearchScope.MyRoom
            else -> SearchScope.World
        }
        CustomSearchBar(scope) {
            ProjectIcon()
        }
        Spacer(modifier = Modifier.height(10.dp))
        NavDisplay(
            backStack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = entryProvider {
                entry<HomeRoute.World> {
                    WorldPage()
                }
                entry<HomeRoute.Communities> {
                    UserHost {
                        MyCommunitiesPage()
                    }
                }
                entry<HomeRoute.Rooms> {
                    UserHost {
                        MyRoomsPage()
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomePager(
    modifier: Modifier,
    pagerState: PagerState
) {
    HorizontalPager(pagerState, modifier) {
        when (it) {
            0 -> WorldPage()
            1 -> UserHost {
                MyCommunitiesPage()
            }

            else -> UserHost {
                MyRoomsPage()
            }
        }
    }
}

@Composable
private fun UserHost(content: @Composable () -> Unit) {
    val uiViewModel = LocalUiViewModel.current
    val session = uiViewModel.mainInstance.sessionManager
    val appNavFactory = LocalAppNavFactory.current
    val user by session.isAlreadySignIn.collectAsState()
    if (user) {
        content()
    } else {
        CenterBox {
            SignInButton {
                appNavFactory.newAppNav().gotoSignIn()
            }
        }
    }
}

@Composable
private fun ProjectDialogInternal(dismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Surface(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val appNavFactory = LocalAppNavFactory.current
            Column {
                ButtonNav(
                    Icons.Default.DesignServices,
                    stringResource(Res.string.design_spec)
                ) {
                    uriHandler.openUri("https://storytellerf.github.io/aspec/")
                }
                ButtonNav(
                    Icons.Default.Code,
                    stringResource(Res.string.open_source_libraries)
                ) {
                    dismiss()
                    appNavFactory.newAppNav().gotoAbout()
                }
                ButtonNav(
                    Icons.Default.Download,
                    stringResource(Res.string.download_latest_app)
                ) {
                    dismiss()
                    uriHandler.openUri(
                        "https://nightly.link/storytellerF/A/workflows/alpha/alpha/Signed%20A%20Bundle.zip"
                    )
                }
            }
        }
    }
}

@Composable
fun WorldPage() {
    val viewModel = createWorldViewModel()
    TopicList(viewModel)
}
