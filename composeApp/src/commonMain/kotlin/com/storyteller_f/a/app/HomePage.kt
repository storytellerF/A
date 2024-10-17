package com.storyteller_f.a.app

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.storyteller_f.a.app.common.CenterBox
import com.storyteller_f.a.app.community.MyCommunitiesPage
import com.storyteller_f.a.app.compontents.ButtonNav
import com.storyteller_f.a.app.compontents.EventDialog
import com.storyteller_f.a.app.compontents.rememberEventState
import com.storyteller_f.a.app.room.MyRoomsPage
import com.storyteller_f.a.app.search.CustomSearchBar
import com.storyteller_f.a.app.world.WorldPage
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.OKey
import com.storyteller_f.shared.type.ObjectType
import kotlinx.coroutines.launch
import moe.tlaster.precompose.navigation.*
import moe.tlaster.precompose.navigation.transition.NavTransition

@Composable
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalFoundationApi::class)
fun HomePage(appNav: AppNav, onClick: (OKey, ObjectType) -> Unit = { _, _ -> }) {
    val size = calculateWindowSizeClass()
    val messageState = rememberEventState()
    val homeNavs = listOf(
        NavRoute("/world", Icons.Default.Public, "world"),
        NavRoute("/communities", Icons.Default.Diversity3, "communities"),
        NavRoute("/rooms", Icons.Default.ChatBubble, "rooms"),
    )

    Surface {
        when (size.widthSizeClass) {
            WindowWidthSizeClass.Compact -> {
                Column(
                    Modifier.fillMaxWidth().statusBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CustomSearchBar {
                        ProjectIcon()
                    }
                    val pagerState = rememberPagerState {
                        3
                    }
                    HomePager(Modifier.weight(1f), appNav, pagerState, onClick)
                    val scope = rememberCoroutineScope()
                    CustomBottomNav(homeNavs[pagerState.currentPage].path, homeNavs) { path ->
                        scope.launch {
                            pagerState.animateScrollToPage(homeNavs.indexOfFirst {
                                it.path == path
                            })
                        }
                    }
                }
            }

            else -> {
                Row(Modifier.statusBarsPadding()) {
                    val navigator = rememberNavigator()
                    val currentEntry by navigator.currentEntry.collectAsState(null)
                    CustomRailNav(currentEntry, homeNavs) {
                        navigator.navigate(it, NavOptions(launchSingleTop = true))
                    }
                    HomeContent(navigator, modifier = Modifier.weight(1f), appNav, onClick)
                }
            }
        }
    }
    EventDialog(messageState)
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
            ProjectDialogInternal()
        }
    }
}

@Composable
fun CustomRailNav(
    currentEntry: BackStackEntry?,
    navRoutes: List<NavRoute>,
    navigate: (String) -> Unit = {}
) {
    NavigationRail {
        navRoutes.forEach {
            NavigationRailItem(currentEntry?.path == it.path, {
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
private fun HomeContent(
    navigator: Navigator,
    modifier: Modifier,
    appNav: AppNav,
    onClick: (OKey, ObjectType) -> Unit
) {
    Column(modifier = modifier) {
        NavHost(navigator, initialRoute = "/world", modifier = modifier, navTransition = remember {
            NavTransition(
                createTransition = fadeIn() + slideInVertically {
                    it / 4
                },
                destroyTransition = fadeOut(),
                pauseTransition = fadeOut(),
                resumeTransition = fadeIn() + slideInVertically {
                    it / 4
                }
            )
        }) {
            scene("/world") {
                WorldPage(onClick)
            }
            scene("/communities") {
                UserHost(appNav::gotoLogin) {
                    MyCommunitiesPage(appNav::gotoCommunity)
                }
            }
            scene("/rooms") {
                UserHost(appNav::gotoLogin) {
                    MyRoomsPage(appNav::gotoRoom)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomePager(
    modifier: Modifier,
    appNav: AppNav,
    pagerState: PagerState,
    onClick: (OKey, ObjectType) -> Unit
) {
    HorizontalPager(pagerState, modifier) {
        when (it) {
            0 -> WorldPage(onClick)
            1 -> UserHost(appNav::gotoLogin) {
                MyCommunitiesPage(appNav::gotoCommunity)
            }

            else -> UserHost(appNav::gotoLogin) {
                MyRoomsPage(appNav::gotoRoom)
            }
        }
    }
}

@Composable
private fun UserHost(onClickLogin: () -> Unit, content: @Composable (UserInfo) -> Unit) {
    val user by LoginViewModel.user.collectAsState()
    val localUser = user
    if (localUser != null) {
        content(localUser)
    } else {
        CenterBox {
            Button({
                onClickLogin()
            }) {
                Text("Login")
            }
        }
    }
}

@Composable
private fun ProjectDialogInternal() {
    Surface(shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                AsyncImage(
                    BuildKonfig.GITHUB_CARD_LINK,
                    contentDescription = "github card",
                    modifier = Modifier.fillMaxWidth().aspectRatio(899f / 296)
                )
            }
            Column {
                val uriHandler = LocalUriHandler.current
                ButtonNav(Icons.Default.DesignServices, "设计文档") {
                    uriHandler.openUri("https://storytellerf.github.io/aspec/")
                }
            }
        }
    }
}
