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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.storyteller_f.a.app.compose_app.LocalAppNav
import com.storyteller_f.a.app.compose_app.LocalMainSessionManager
import com.storyteller_f.a.app.compose_app.Res
import com.storyteller_f.a.app.compose_app.common.createWorldViewModel
import com.storyteller_f.a.app.compose_app.components.ButtonNav
import com.storyteller_f.a.app.compose_app.components.TopicList
import com.storyteller_f.a.app.compose_app.design_spec
import com.storyteller_f.a.app.compose_app.pages.community.MyCommunitiesPage
import com.storyteller_f.a.app.compose_app.pages.room.MyRoomsPage
import com.storyteller_f.a.app.compose_app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.compose_app.pages.search.SearchScope
import com.storyteller_f.a.app.core.compontents.CenterBox
import com.storyteller_f.a.app.core.compontents.SignInButton
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalFoundationApi::class)
fun HomePage() {
    val size = calculateWindowSizeClass()
    val homeNavRoutes = listOf(
        NavRoute("/world", Icons.Default.Public, "world"),
        NavRoute("/communities", Icons.Default.Diversity3, "communities"),
        NavRoute("/rooms", Icons.Default.ChatBubble, "rooms"),
    )
    val modifier = Modifier.testTag("home")
    when (size.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            CompatHomePage(homeNavRoutes, modifier)
        }

        else -> {
            ExpandHomePage(modifier, homeNavRoutes)
        }
    }
}

@Composable
private fun ExpandHomePage(
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
private fun CompatHomePage(
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

class NavRoute(val path: String, val icon: ImageVector, val label: String)

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
fun CustomRailNav(
    currentEntry: String?,
    navRoutes: List<NavRoute>,
    navigate: (String) -> Unit = {}
) {
    NavigationRail(modifier = Modifier.padding(horizontal = 8.dp)) {
        navRoutes.forEach {
            NavigationRailItem(currentEntry == it.path, {
                navigate(it.path)
            }, icon = {
                Icon(imageVector = it.icon, contentDescription = it.label)
            }, label = {
                Text(it.label)
            })
        }
    }
}

@Composable
fun CustomBottomNav(
    path: String,
    navRoutes: List<NavRoute>,
    navigate: (String) -> Unit = { }
) {
    NavigationBar {
        navRoutes.forEach {
            NavigationBarItem(path == it.path, {
                navigate(it.path)
            }, {
                Icon(imageVector = it.icon, it.label)
            }, label = {
                Text(it.label)
            }, modifier = Modifier.testTag(it.label))
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
    val session = LocalMainSessionManager.current
    val appNav = LocalAppNav.current
    val user by session.isAlreadySignIn.collectAsState()
    if (user) {
        content()
    } else {
        CenterBox {
            SignInButton {
                appNav.gotoLogin()
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
            val appNav = LocalAppNav.current
            Column {
                ButtonNav(
                    Icons.Default.DesignServices,
                    stringResource(Res.string.design_spec)
                ) {
                    uriHandler.openUri("https://storytellerf.github.io/aspec/")
                }
                ButtonNav(
                    Icons.Default.Code,
                    "Open Source Libraries"
                ) {
                    dismiss()
                    appNav.gotoAbout()
                }
                ButtonNav(
                    Icons.Default.Download,
                    "Download latest app"
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
