package com.storyteller_f.a.app.community

import a.composeapp.generated.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import app.cash.paging.compose.collectAsLazyPagingItems
import com.storyteller_f.a.app.*
import com.storyteller_f.a.app.compontents.*
import com.storyteller_f.a.app.model.*
import com.storyteller_f.a.app.room.RoomList
import com.storyteller_f.a.app.search.CustomSearchBar
import com.storyteller_f.a.app.search.SearchScope
import com.storyteller_f.a.app.world.TopicList
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.client.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun CommunityPage(
    communityId: PrimaryKey,
    showDialog: Boolean
) {
    val size = calculateWindowSizeClass()
    when (size.widthSizeClass) {
        WindowWidthSizeClass.Compact -> CommunityCompatPageInternal(communityId, showDialog)
        else -> CommunityNonCompatPageInternal(communityId, showDialog)
    }
}

private fun buildSearchScope(
    pagerState: PagerState,
    communityId: PrimaryKey
) = when (pagerState.currentPage) {
    0 -> SearchScope.CommunityTopic(communityId)
    else -> SearchScope.CommunityRoom(communityId)
}

@Composable
private fun CommunityNonCompatPageInternal(
    communityId: PrimaryKey,
    needShowDialog: Boolean,
) {
    val model = createCommunityViewModel(communityId)
    val community by model.handler.data.collectAsState()
    val dialogShown by model.dialog.shownDialog.collectAsState()
    val pagerState = rememberPagerState {
        2
    }
    val searchScope = buildSearchScope(pagerState, communityId)
    val navs = communityNavRoutes()
    val scope = rememberCoroutineScope()

    Scaffold { paddingValues ->
        Row(modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())) {
            CustomRailNav(navs[pagerState.currentPage].path, navs) { path ->
                scope.launch {
                    pagerState.animateScrollToPage(navs.indexOfFirst { route ->
                        route.path == path
                    })
                }
            }
            Column(
                modifier = Modifier,
            ) {
                CustomSearchBar(searchScope) {
                    var showDialog by remember {
                        mutableStateOf(false)
                    }
                    LaunchedEffect(needShowDialog, dialogShown) {
                        if (needShowDialog && !dialogShown) {
                            model.dialog.markDialogShown()
                            showDialog = true
                        }
                    }
                    CommunityIcon(community, 40.dp, showDialog) {
                        showDialog = it
                    }
                }

                CommunityPageInternal(pagerState, communityId)
            }
        }
    }
}

@Composable
private fun CommunityCompatPageInternal(
    communityId: PrimaryKey,
    needShowDialog: Boolean,
) {
    val model = createCommunityViewModel(communityId)
    val community by model.handler.data.collectAsState()
    val dialogShown by model.dialog.shownDialog.collectAsState()
    val pagerState = rememberPagerState {
        2
    }
    val searchScope = buildSearchScope(pagerState, communityId)
    val navs = communityNavRoutes()
    val appNav = LocalAppNav.current
    var showDialog by remember {
        mutableStateOf(false)
    }
    Scaffold(floatingActionButton = {
        CommunityFloatingButton(community, appNav, communityId) {
            showDialog = true
        }
    }, bottomBar = {
        CommunityBottomNav(navs, pagerState)
    }) { paddingValues ->
        Column(
            modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding()),
        ) {
            CustomSearchBar(searchScope) {
                LaunchedEffect(needShowDialog) {
                    if (needShowDialog && !dialogShown) {
                        model.dialog.markDialogShown()
                        showDialog = true
                    }
                }
                CommunityIcon(community, 40.dp, showDialog) {
                    showDialog = it
                }
            }

            CommunityPageInternal(pagerState, communityId)
        }
    }
}

@Composable
private fun CommunityFloatingButton(
    community: CommunityInfo?,
    appNav: AppNav,
    communityId: PrimaryKey,
    onClickOk: () -> Unit
) {
    val alertDialogState = remember {
        CustomAlertDialogController()
    }
    val title = stringResource(Res.string.permission_denied)
    val message = stringResource(Res.string.join_community_prompt)
    FloatingActionButton(onClick = {
        if (community?.isJoined == true) {
            appNav.gotoTopicCompose(ObjectType.COMMUNITY, communityId, false, null)
        } else {
            alertDialogState.showMessage(title, message)
        }
    }) {
        Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add))
    }
    CustomAlertDialog(alertDialogState, {
        alertDialogState.close()
    }, onClickOk)
}

@Composable
private fun CommunityBottomNav(
    navs: List<NavRoute>,
    pagerState: PagerState
) {
    val scope = rememberCoroutineScope()
    CustomBottomNav(navs[pagerState.currentPage].path, navs) { path ->
        scope.launch {
            pagerState.animateScrollToPage(navs.indexOfFirst {
                it.path == path
            })
        }
    }
}

@Composable
private fun CommunityPageInternal(
    pagerState: PagerState,
    communityId: PrimaryKey
) {
    HorizontalPager(pagerState) {
        when (it) {
            0 -> {
                val viewModel = createCommunityTopicsViewModel(communityId)
                val items = viewModel.flow.collectAsLazyPagingItems()
                TopicList(items)
            }

            else -> {
                val viewModel = createCommunityRoomsViewModel(communityId)
                val items = viewModel.flow.collectAsLazyPagingItems()
                RoomList(items)
            }
        }
    }
}

@Composable
fun communityNavRoutes(): List<NavRoute> {
    val navs = listOf(
        NavRoute("/topics", Icons.Default.Topic, stringResource(Res.string.topics)),
        NavRoute("/rooms", Icons.Default.ChatBubble, stringResource(Res.string.rooms))
    )
    return navs
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityDialog(
    communityInfo: CommunityInfo?,
    showDialog: Boolean,
    dismiss: () -> Unit
) {
    if (communityInfo != null && showDialog) {
        BasicAlertDialog(
            {
                dismiss()
            },
        ) {
            CommunityDialogInternal(communityInfo, dismiss)
        }
    }
}

@Composable
fun CommunityDialogInternal(communityInfo: CommunityInfo, dismiss: () -> Unit) {
    val nav = LocalAppNav.current
    val communityId = communityInfo.id
    DialogContainer {
        Row(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceDim, RoundedCornerShape(8.dp))
                .padding(8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CommunityIcon(communityInfo, 50.dp, showDialog = false) {}
            Column {
                Text(communityInfo.name)
            }
        }
        Column {
            ButtonNav(Icons.Default.CardMembership, stringResource(Res.string.all_members)) {
                dismiss()
                nav.gotoMemberPage(communityId, ObjectType.COMMUNITY)
            }
            if (nav.currentDestination?.destination?.hasRoute(CommunityScreen::class) == true) {
                val scope = rememberCoroutineScope()
                if (communityInfo.isJoined) {
                    ButtonNav(Icons.Default.Close, stringResource(Res.string.exit_community)) {
                        scope.launch {
                            globalDialogState.use {
                                val info = client.exitCommunity(communityId).getOrThrow()
                                bus.emit(OnCommunityExited(communityId, info))
                            }
                        }
                    }
                } else {
                    ButtonNav(Icons.Default.AddHome, stringResource(Res.string.join_community)) {
                        scope.launch {
                            globalDialogState.use {
                                val info = client.joinCommunity(communityId).getOrThrow()
                                bus.emit(OnCommunityJoined(communityId, info))
                            }
                        }
                    }
                }
                ButtonNav(Icons.Default.Add, "Add") {
                    dismiss()
                    nav.gotoTopicCompose(ObjectType.COMMUNITY, communityId, true, null)
                }
            }
        }
    }
}
