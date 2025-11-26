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
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.storyteller_f.a.app.LocalAppNavFactory
import com.storyteller_f.a.app.LocalUiViewModel
import com.storyteller_f.a.app.Res
import com.storyteller_f.a.app.common.createWorldViewModel
import com.storyteller_f.a.app.communities
import com.storyteller_f.a.app.design_spec
import com.storyteller_f.a.app.download_latest_app
import com.storyteller_f.a.app.open_source_libraries
import com.storyteller_f.a.app.pages.community.MyCommunitiesPage
import com.storyteller_f.a.app.pages.room.MyRoomsPage
import com.storyteller_f.a.app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.pages.search.SearchScope
import com.storyteller_f.a.app.pages.topic.TopicList
import com.storyteller_f.a.app.rooms
import com.storyteller_f.a.app.world
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
    Scaffold(modifier = modifier) {
        Row(Modifier) {
            val navigator = rememberNavController()
            val currentEntry by navigator.currentBackStackEntryFlow.collectAsState(null)
            CustomRailNav(currentEntry?.destination?.route, homeNavRoutes) {
                navigator.navigate(
                    it,
                    NavOptions.Builder().setLaunchSingleTop(true).build()
                )
            }
            HomeNavHost(navigator, modifier = Modifier.weight(1f))
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
