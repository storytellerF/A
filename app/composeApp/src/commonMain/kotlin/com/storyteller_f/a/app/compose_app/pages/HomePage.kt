package com.storyteller_f.a.app.compose_app.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.storyteller_f.a.app.compose_app.LocalAppNavFactory
import com.storyteller_f.a.app.compose_app.LocalUiViewModel
import com.storyteller_f.a.app.compose_app.Res
import com.storyteller_f.a.app.compose_app.common.createWorldViewModel
import com.storyteller_f.a.app.compose_app.communities
import com.storyteller_f.a.app.compose_app.components.TopicList
import com.storyteller_f.a.app.compose_app.design_spec
import com.storyteller_f.a.app.compose_app.download_latest_app
import com.storyteller_f.a.app.compose_app.open_source_libraries
import com.storyteller_f.a.app.compose_app.pages.community.MyCommunitiesPage
import com.storyteller_f.a.app.compose_app.pages.room.MyRoomsPage
import com.storyteller_f.a.app.compose_app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.compose_app.pages.search.SearchScope
import com.storyteller_f.a.app.compose_app.rooms
import com.storyteller_f.a.app.compose_app.world
import com.storyteller_f.a.app.core.components.ButtonNav
import com.storyteller_f.a.app.core.components.CenterBox
import com.storyteller_f.a.app.core.components.CustomBottomNav
import com.storyteller_f.a.app.core.components.CustomRailNav
import com.storyteller_f.a.app.core.components.NavRoute
import com.storyteller_f.a.app.core.components.SignInButton
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalFoundationApi::class)
fun HomePage() {
    val size = calculateWindowSizeClass()
    val homeNavRoutes = listOf(
        NavRoute(
            "/world",
            Icons.Default.Public,
            stringResource(Res.string.world)
        ),
        NavRoute(
            "/communities",
            Icons.Default.Diversity3,
            stringResource(Res.string.communities)
        ),
        NavRoute(
            "/rooms",
            Icons.Default.ChatBubble,
            stringResource(Res.string.rooms)
        ),
    )
    val modifier = Modifier.testTag("home")
    when (size.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            HomeCompatPage(homeNavRoutes, modifier)
        }

        else -> {
            HomeNonCompatPage(modifier, homeNavRoutes)
        }
    }
}

@Composable
private fun HomeNonCompatPage(
    modifier: Modifier,
    homeNavRoutes: List<NavRoute>
) {
    Scaffold(modifier = modifier) { paddingValues ->
        Row(Modifier) {
            val navigator = rememberNavController()
            val currentEntry by navigator.currentBackStackEntryFlow.collectAsState(null)
            CustomRailNav(currentEntry?.destination?.route, homeNavRoutes) {
                navigator.navigate(
                    it,
                    NavOptions.Builder().setLaunchSingleTop(true).build()
                )
            }
            HomeNavHost(
                navigator,
                modifier = Modifier.weight(1f)
                    .padding(bottom = paddingValues.calculateBottomPadding())
            )
        }
    }
}

@Composable
private fun HomeCompatPage(
    homeNavRoutes: List<NavRoute>,
    modifier: Modifier
) {
    val pagerState = rememberPagerState {
        3
    }
    Scaffold(bottomBar = {
        val scope = rememberCoroutineScope()
        CustomBottomNav(homeNavRoutes[pagerState.currentPage].path, homeNavRoutes) { path ->
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
            HomePager(
                Modifier.weight(1f).padding(bottom = it.calculateBottomPadding()),
                pagerState
            )
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
private fun HomeNavHost(
    navigator: NavHostController,
    modifier: Modifier
) {
    Column(modifier = modifier) {
        val scope = when (navigator.currentDestination?.route) {
            "/communities" -> SearchScope.MyCommunity
            "/rooms" -> SearchScope.MyRoom
            else -> SearchScope.World
        }
        CustomSearchBar(scope) {
            ProjectIcon()
        }
        Spacer(modifier = Modifier.height(10.dp))
        NavHost(navigator, "/world") {
            composable("/world") {
                WorldPage()
            }
            composable("/communities") {
                UserHost {
                    MyCommunitiesPage()
                }
            }
            composable("/rooms") {
                UserHost {
                    MyRoomsPage()
                }
            }
        }
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
                appNavFactory.newAppNav().gotoLogin()
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
