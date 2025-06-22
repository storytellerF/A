package com.storyteller_f.a.app.pages.community

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.cash.paging.compose.collectAsLazyPagingItems
import com.storyteller_f.a.app.*
import com.storyteller_f.a.app.common.CachedLoadingHandler
import com.storyteller_f.a.app.compontents.*
import com.storyteller_f.a.app.model.*
import com.storyteller_f.a.app.pages.room.RoomList
import com.storyteller_f.a.app.pages.room.getCurrentUserInfo
import com.storyteller_f.a.app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.pages.search.SearchScope
import com.storyteller_f.a.app.pages.world.TopicList
import com.storyteller_f.a.app.ui.theme.AppTheme
import com.storyteller_f.a.app.utils.loadFontFromLocal
import com.storyteller_f.a.client_lib.LoadingState
import com.storyteller_f.a.client_lib.exitCommunity
import com.storyteller_f.a.client_lib.joinCommunity
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun CommunityPage(
    communityId: PrimaryKey,
    showDialog: Boolean,
) {
    val size = calculateWindowSizeClass()
    val model = createCommunityViewModel(communityId)
    val typography = getCommunityFont(communityId)

    AppTheme(typography = typography) {
        when (size.widthSizeClass) {
            WindowWidthSizeClass.Compact -> CommunityCompatPageInternal(communityId, showDialog, model)
            else -> CommunityNonCompatPageInternal(communityId, showDialog, model)
        }
    }

}

@Composable
fun getCommunityFont(communityId: PrimaryKey): Typography {
    val model = createCommunityViewModel(communityId)
    val community by model.handler.data.collectAsState()
    val fontFamily = community?.font?.let {
        val downloadViewModel = LocalDownloadViewModel.current
        val loadingHandler by produceState<CachedLoadingHandler<DownloadInfo>?>(null, it) {
            value = downloadViewModel.download(it.id.toString(), it)
        }
        loadingHandler?.let { handler ->
            val state by handler.state.collectAsState()
            val data by handler.data.collectAsState()
            Napier.i {
                "CommunityPage state:$state data:$data"
            }
            if (state is LoadingState.Done) {
                data?.let {
                    loadFontFromLocal(it.path + ".extracted")
                }
            } else {
                null
            }
        }
    }
    val typography = MaterialTheme.typography
    return typography.copy(
        bodyLarge =
            typography.bodyLarge.copy(fontFamily = fontFamily ?: typography.bodyLarge.fontFamily),
        bodyMedium = typography.bodyMedium.copy(fontFamily = fontFamily ?: typography.bodyMedium.fontFamily),
        bodySmall = typography.bodySmall.copy(fontFamily = fontFamily ?: typography.bodySmall.fontFamily),

        )
}

private fun buildSearchScope(
    pagerState: PagerState,
    communityId: PrimaryKey,
) = when (pagerState.currentPage) {
    0 -> SearchScope.CommunityTopic(communityId)
    else -> SearchScope.CommunityRoom(communityId)
}

private fun buildSearchScope(
    currentRoute: String?,
    communityId: PrimaryKey,
) = when (currentRoute) {
    "/topics" -> SearchScope.CommunityTopic(communityId)
    else -> SearchScope.CommunityRoom(communityId)
}

@Composable
private fun CommunityNonCompatPageInternal(
    communityId: PrimaryKey,
    needShowDialog: Boolean,
    model: CommunityViewModel,
) {
    val community by model.handler.data.collectAsState()
    val dialogShown by model.dialog.shownDialog.collectAsState()
    val navs = communityNavRoutes()
    val navigator = rememberNavController()
    val current by navigator.currentBackStackEntryFlow.collectAsState(null)
    val searchScope = buildSearchScope(current?.destination?.route, communityId)
    Scaffold { paddingValues ->
        Row(modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())) {
            CustomRailNav(current?.destination?.route, navs) {
                navigator.navigate(it, NavOptions.Builder().setLaunchSingleTop(true).build())
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
                    CommunityIcon(community, showDialog) {
                        showDialog = it
                    }
                }

                NavHost(navigator, "/topics") {
                    composable("/topics") {
                        val viewModel = createCommunityTopicsViewModel(communityId)
                        val items = viewModel.flow.collectAsLazyPagingItems()
                        TopicList(items)
                    }
                    composable("/rooms") {
                        val viewModel = createCommunityRoomsViewModel(communityId)
                        val items = viewModel.flow.collectAsLazyPagingItems()
                        RoomList(items)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityCompatPageInternal(
    communityId: PrimaryKey,
    needShowDialog: Boolean,
    model: CommunityViewModel,
) {
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
                CommunityIcon(community, showDialog) {
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
    onClickOk: () -> Unit,
) {
    val alertDialogState = remember {
        CustomAlertDialogController()
    }
    val title = stringResource(Res.string.permission_denied)
    val message = stringResource(Res.string.join_community_prompt)
    FloatingActionButton(onClick = {
        if (community?.isJoined == true) {
            appNav.gotoTopicCompose(ObjectType.COMMUNITY, communityId, false, null, communityId)
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
    pagerState: PagerState,
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
    communityId: PrimaryKey,
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
    return listOf(
        NavRoute("/topics", Icons.Default.Topic, stringResource(Res.string.topics)),
        NavRoute("/rooms", Icons.Default.ChatBubble, stringResource(Res.string.rooms))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityDialog(
    communityInfo: CommunityInfo?,
    showDialog: Boolean,
    dismiss: () -> Unit,
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
    val communityId = communityInfo.id
    DialogContainer {
        Row(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceDim, RoundedCornerShape(8.dp))
                .padding(8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CommunityIcon(communityInfo, showDialog = false, 50.dp, setClickEvent = false) {}
            Column {
                Text(communityInfo.name)
            }
        }
        communityInfo.font?.let {
            Column {
                val downloadViewModel = LocalDownloadViewModel.current
                val loadingHandler by produceState<CachedLoadingHandler<DownloadInfo>?>(null, it) {
                    value = downloadViewModel.download(it.id.toString(), it)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    loadingHandler?.let { handler ->
                        val state by handler.state.collectAsState()
                        val data by handler.data.collectAsState()
                        Text(it.name, modifier = Modifier.weight(1f))
                        DownloadStatus(state, data)
                    }
                }
            }
        }
        CommunityMenus(dismiss, communityId, communityInfo)
    }
}

@Composable
private fun DownloadStatus(
    state: LoadingState?,
    data: DownloadInfo?,
) {
    Napier.i {
        "DownloadStatus state:$state data:$data"
    }
    when {
        state is LoadingState.Done && data != null -> {

            when (data.status) {
                DownloadStatus.NOT_DOWNLOADED, DownloadStatus.DOWNLOADING -> CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 2.dp
                )

                DownloadStatus.DOWNLOADED -> Text("✅")
                DownloadStatus.FAILED -> Text(data.message.take(10))
            }
        }

        state is LoadingState.Error -> Text(state.e.localizedMessage?.take(10) ?: "!")
        state == null || state is LoadingState.Loading -> CircularProgressIndicator(
            modifier = Modifier.size(10.dp),
            strokeWidth = 2.dp
        )
    }
}

@Composable
private fun CommunityMenus(
    dismiss: () -> Unit,
    communityId: PrimaryKey,
    communityInfo: CommunityInfo,
) {
    val nav = LocalAppNav.current
    val sessionViewModel = LocalSessionManager.current
    Column {
        ButtonNav(Icons.Default.CardMembership, stringResource(Res.string.all_members)) {
            dismiss()
            nav.gotoMemberPage(communityId, ObjectType.COMMUNITY)
        }
        val appNav = LocalAppNav.current
        val isCommunityPage by appNav.hasRouteFlow<CommunityScreen>().collectAsState(false)
        if (isCommunityPage) {
            val scope = rememberCoroutineScope()
            if (communityInfo.isJoined) {
                ButtonNav(Icons.Default.Close, stringResource(Res.string.exit_community)) {
                    scope.launch {
                        globalDialogState.use {
                            val info = sessionViewModel.exitCommunity(communityId).getOrThrow()
                            bus.emit(OnCommunityExited(info))
                        }
                    }
                }
            } else {
                ButtonNav(Icons.Default.AddHome, stringResource(Res.string.join_community)) {
                    scope.launch {
                        globalDialogState.use {
                            val info = sessionViewModel.joinCommunity(communityId).getOrThrow()
                            bus.emit(OnCommunityJoined(info))
                        }
                    }
                }
            }
            ButtonNav(Icons.Default.Add, "Add") {
                dismiss()
                nav.gotoTopicCompose(ObjectType.COMMUNITY, communityId, true, null, communityId)
            }
            val my = getCurrentUserInfo()
            if (my?.id == communityInfo.owner) {
                ButtonNav(Icons.Default.Title, "Add Title") {
                    dismiss()
                    nav.gotoTitleCompose()
                }

                ButtonNav(Icons.Default.Settings, "Settings") {
                    dismiss()
                    nav.gotoSettingPage(communityId, ObjectType.COMMUNITY)
                }
            }
        }
    }
}
