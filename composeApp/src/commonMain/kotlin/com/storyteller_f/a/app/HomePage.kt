package com.storyteller_f.a.app

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.design_spec
import a.composeapp.generated.resources.sign_in
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
import androidx.compose.material.icons.automirrored.filled.Login
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import com.storyteller_f.a.app.common.CenterBox
import com.storyteller_f.a.app.community.MyCommunitiesPage
import com.storyteller_f.a.app.compontents.ButtonNav
import com.storyteller_f.a.app.room.MyRoomsPage
import com.storyteller_f.a.app.search.CustomSearchBar
import com.storyteller_f.a.app.search.SearchScope
import com.storyteller_f.a.app.world.WorldPage
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.shared.model.UserInfo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalFoundationApi::class)
fun HomePage() {
    val size = calculateWindowSizeClass()
    val homeNavs = listOf(
        NavRoute("/world", Icons.Default.Public, "world"),
        NavRoute("/communities", Icons.Default.Diversity3, "communities"),
        NavRoute("/rooms", Icons.Default.ChatBubble, "rooms"),
    )
    when (size.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            val pagerState = rememberPagerState {
                3
            }
            Scaffold(bottomBar = {
                val scope = rememberCoroutineScope()
                CustomBottomNav(homeNavs[pagerState.currentPage].path, homeNavs) { path ->
                    scope.launch {
                        pagerState.animateScrollToPage(homeNavs.indexOfFirst {
                            it.path == path
                        })
                    }
                }
            }) {
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

        else -> {
            Scaffold {
                Row(Modifier) {
                    val navigator = rememberNavController()
                    val currentEntry by navigator.currentBackStackEntryFlow.collectAsState(null)
                    CustomRailNav(currentEntry?.destination?.route, homeNavs) {
                        navigator.navigate(it, NavOptions.Builder().setLaunchSingleTop(true).build())
                    }
                    HomeNavHost(navigator, modifier = Modifier.weight(1f).padding(bottom = it.calculateBottomPadding()))
                }
            }
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
    path: String?,
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
            })
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
private fun UserHost(content: @Composable (UserInfo) -> Unit) {
    val appNav = LocalAppNav.current
    val user by LoginViewModel.user.collectAsState()
    val localUser = user
    if (localUser != null) {
        content(localUser)
    } else {
        CenterBox {
            Button({
                appNav.gotoLogin()
            }) {
                Icon(Icons.AutoMirrored.Default.Login, stringResource(Res.string.sign_in))
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(Res.string.sign_in))
            }
        }
    }
}

@Composable
private fun ProjectDialogInternal(dismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Surface(shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                AsyncImage(
                    BuildKonfig.GITHUB_CARD_LINK,
                    contentDescription = "github card",
                    modifier = Modifier.fillMaxWidth().aspectRatio(899f / 296).clickable {
                        uriHandler.openUri("https://github.com/storytellerF/A")
                    }
                )
            }
            val appNav = LocalAppNav.current
            Column {
                ButtonNav(Icons.Default.DesignServices, stringResource(Res.string.design_spec)) {
                    uriHandler.openUri("https://storytellerf.github.io/aspec/")
                }
                ButtonNav(Icons.Default.Add, "About") {
                    dismiss()
                    appNav.gotoAbout()
                }
            }
        }
    }
}
