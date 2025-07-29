package com.storyteller_f.a.app.compose_app.pages.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddHome
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.paging.compose.collectAsLazyPagingItems
import com.storyteller_f.a.app.compose_app.AppNav
import com.storyteller_f.a.app.compose_app.CommunityScreen
import com.storyteller_f.a.app.compose_app.LocalAppNav
import com.storyteller_f.a.app.compose_app.LocalDownloadViewModel
import com.storyteller_f.a.app.compose_app.LocalGlobalDialog
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.Res
import com.storyteller_f.a.app.compose_app.add
import com.storyteller_f.a.app.compose_app.all_members
import com.storyteller_f.a.app.compose_app.bus
import com.storyteller_f.a.app.compose_app.common.CachedLoadingHandler
import com.storyteller_f.a.app.compose_app.compontents.BaseSheet
import com.storyteller_f.a.app.compose_app.compontents.ButtonNav
import com.storyteller_f.a.app.compose_app.compontents.CommunityIcon
import com.storyteller_f.a.app.compose_app.compontents.CustomAlertDialog
import com.storyteller_f.a.app.compose_app.compontents.CustomAlertDialogController
import com.storyteller_f.a.app.compose_app.compontents.DialogContainer
import com.storyteller_f.a.app.compose_app.compontents.SheetContainer
import com.storyteller_f.a.app.compose_app.compontents.TopicList
import com.storyteller_f.a.app.compose_app.exit_community
import com.storyteller_f.a.app.compose_app.hasRouteFlow
import com.storyteller_f.a.app.compose_app.join_community
import com.storyteller_f.a.app.compose_app.join_community_prompt
import com.storyteller_f.a.app.compose_app.model.CommunityViewModel
import com.storyteller_f.a.app.compose_app.model.OnCommunityExited
import com.storyteller_f.a.app.compose_app.model.OnCommunityJoined
import com.storyteller_f.a.app.compose_app.model.createCommunityRoomsViewModel
import com.storyteller_f.a.app.compose_app.model.createCommunityTopicsViewModel
import com.storyteller_f.a.app.compose_app.model.createCommunityViewModel
import com.storyteller_f.a.app.compose_app.pages.CustomBottomNav
import com.storyteller_f.a.app.compose_app.pages.CustomRailNav
import com.storyteller_f.a.app.compose_app.pages.NavRoute
import com.storyteller_f.a.app.compose_app.pages.room.RoomList
import com.storyteller_f.a.app.compose_app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.compose_app.pages.search.SearchScope
import com.storyteller_f.a.app.compose_app.permission_denied
import com.storyteller_f.a.app.compose_app.rooms
import com.storyteller_f.a.app.compose_app.topics
import com.storyteller_f.a.app.compose_app.ui.MaterialSymbolsOutlined
import com.storyteller_f.a.app.compose_app.ui.theme.AppTheme
import com.storyteller_f.a.app.compose_app.utils.loadFontFromLocal
import com.storyteller_f.a.client.core.LoadingState
import com.storyteller_f.a.client.core.exitCommunity
import com.storyteller_f.a.client.core.joinCommunity
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.storage.DownloadInfo
import com.storyteller_f.storage.DownloadStatus
import dev.tclement.fonticons.FontIcon
import io.github.aakira.napier.Napier
import io.github.windedge.table.DataTable
import kotlinx.coroutines.launch
import nl.jacobras.humanreadable.HumanReadable
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
            WindowWidthSizeClass.Compact -> CommunityCompatPageInternal(
                communityId,
                showDialog,
                model
            )

            else -> CommunityNonCompatPageInternal(communityId, showDialog, model)
        }
    }
}

@Composable
fun getCommunityFont(communityId: PrimaryKey): Typography {
    val model = createCommunityViewModel(communityId)
    val community by model.handler.data.collectAsState()
    val fontFamily = community?.font?.let { fontId ->
        val downloadViewModel = LocalDownloadViewModel.current
        val loadingHandler by produceState<CachedLoadingHandler<DownloadInfo>?>(
            null,
            fontId
        ) {
            value = downloadViewModel.download(fontId)
        }
        loadingHandler?.let { handler ->
            val state by handler.state.collectAsState()
            val data by handler.data.collectAsState()
            Napier.i {
                "CommunityPage state:$state data:$data"
            }
            if (state is LoadingState.Done && data?.status == DownloadStatus.DOWNLOADED) {
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
        bodyMedium = typography.bodyMedium.copy(
            fontFamily = fontFamily ?: typography.bodyMedium.fontFamily
        ),
        bodySmall = typography.bodySmall.copy(
            fontFamily = fontFamily ?: typography.bodySmall.fontFamily
        )
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
    "/topics" -> SearchScope.CommunityTopic(
        communityId
    )

    else -> SearchScope.CommunityRoom(communityId)
}

@Composable
private fun CommunityNonCompatPageInternal(
    communityId: PrimaryKey,
    needShowDialog: Boolean,
    model: CommunityViewModel,
) {
    val community by model.handler.data.collectAsState()
    val dialogShown by model.dialog.dialogShown.collectAsState()
    val navRoutes = communityNavRoutes()
    val navigator = rememberNavController()
    val current by navigator.currentBackStackEntryFlow.collectAsState(null)
    val searchScope = buildSearchScope(current?.destination?.route, communityId)
    Scaffold { paddingValues ->
        Row(modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())) {
            CustomRailNav(current?.destination?.route, navRoutes) {
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
                    CommunityIcon(
                        community,
                        showDialog
                    ) {
                        showDialog = it
                    }
                }

                NavHost(navigator, "/topics") {
                    composable("/topics") {
                        val viewModel =
                            createCommunityTopicsViewModel(
                                communityId
                            )
                        val items = viewModel.flow.collectAsLazyPagingItems()
                        TopicList(items)
                    }
                    composable("/rooms") {
                        val viewModel =
                            createCommunityRoomsViewModel(
                                communityId
                            )
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
    val dialogShown by model.dialog.dialogShown.collectAsState()
    val pagerState = rememberPagerState {
        2
    }
    val searchScope = buildSearchScope(pagerState, communityId)
    val navRoutes = communityNavRoutes()
    val appNav = LocalAppNav.current
    var showDialog by remember {
        mutableStateOf(false)
    }
    Scaffold(floatingActionButton = {
        CommunityFloatingButton(community, appNav, communityId) {
            showDialog = true
        }
    }, bottomBar = {
        CommunityBottomNav(navRoutes, pagerState)
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
                CommunityIcon(
                    community,
                    showDialog
                ) {
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
    navRoutes: List<NavRoute>,
    pagerState: PagerState,
) {
    val scope = rememberCoroutineScope()
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

@Composable
private fun CommunityPageInternal(
    pagerState: PagerState,
    communityId: PrimaryKey,
) {
    HorizontalPager(pagerState) {
        when (it) {
            0 -> {
                val viewModel =
                    createCommunityTopicsViewModel(
                        communityId
                    )
                val items = viewModel.flow.collectAsLazyPagingItems()
                TopicList(items)
            }

            else -> {
                val viewModel =
                    createCommunityRoomsViewModel(
                        communityId
                    )
                val items = viewModel.flow.collectAsLazyPagingItems()
                RoomList(items)
            }
        }
    }
}

@Composable
fun communityNavRoutes(): List<NavRoute> {
    return listOf(
        NavRoute(
            "/topics",
            Icons.Default.Topic,
            stringResource(Res.string.topics)
        ),
        NavRoute(
            "/rooms",
            Icons.Default.ChatBubble,
            stringResource(Res.string.rooms)
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityDialogInternal(communityInfo: CommunityInfo, dismiss: () -> Unit) {
    val communityId = communityInfo.id
    DialogContainer {
        Row(
            modifier = Modifier.background(
                MaterialTheme.colorScheme.surfaceDim,
                RoundedCornerShape(8.dp)
            )
                .padding(8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CommunityIcon(
                communityInfo,
                showDialog = false,
                50.dp,
                setClickEvent = false
            ) {}
            Column {
                Text(communityInfo.name)
            }
        }
        communityInfo.font?.let {
            FontView(it)
        }
        CommunityMenus(communityId, communityInfo, dismiss)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FontView(info: MediaInfo) {
    val downloadViewModel =
        LocalDownloadViewModel.current
    val loadingHandler by produceState<CachedLoadingHandler<DownloadInfo>?>(
        null,
        info
    ) {
        value = downloadViewModel.download(info)
    }
    var showSheet by remember {
        mutableStateOf(false)
    }
    val sheetState = rememberModalBottomSheetState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
            showSheet = true
        }.padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        FontIcon(MaterialSymbolsOutlined.FontDownload, "font")
        val scrollState = rememberScrollState()
        Text(info.name, modifier = Modifier.weight(1f).horizontalScroll(scrollState))
        loadingHandler?.let { handler ->
            val state by handler.state.collectAsState()
            val data by handler.data.collectAsState()
            DownloadStatusView(state, data)
        }
    }
    DownloadInfoPage(info, showSheet, sheetState) {
        showSheet = false
    }
}

@Composable
private fun DownloadStatusView(
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
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )

                DownloadStatus.DOWNLOADED -> FontIcon(MaterialSymbolsOutlined.DownloadDone, "download done")
                DownloadStatus.FAILED -> FontIcon(MaterialSymbolsOutlined.Error, "error")
            }
        }

        state is LoadingState.Error -> Text(state.e.localizedMessage?.take(10) ?: "!")
        state == null || state is LoadingState.Loading -> CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
        )

        else -> FontIcon(MaterialSymbolsOutlined.Info, "warning")
    }
}

@Composable
private fun CommunityMenus(
    communityId: PrimaryKey,
    communityInfo: CommunityInfo,
    dismiss: () -> Unit,
) {
    val nav = LocalAppNav.current
    val sessionViewModel = LocalSessionManager.current
    val globalDialogController = LocalGlobalDialog.current
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
                        globalDialogController.use {
                            val info = sessionViewModel.exitCommunity(communityId).getOrThrow()
                            bus.emit(OnCommunityExited(info))
                        }
                    }
                }
            } else {
                ButtonNav(Icons.Default.AddHome, stringResource(Res.string.join_community)) {
                    scope.launch {
                        globalDialogController.use {
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
            val userSessionManager = LocalSessionManager.current
            val myInfo by userSessionManager.sessionModel.userHandler.data.collectAsState()
            val my = myInfo
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadInfoPage(
    it: MediaInfo,
    showSheet: Boolean,
    sheetState: SheetState,
    hideSheet: () -> Unit,
) {
    val downloadViewModel =
        LocalDownloadViewModel.current
    val loadingHandler by produceState<CachedLoadingHandler<DownloadInfo>?>(
        null,
        it
    ) {
        value = downloadViewModel.download(it)
    }
    loadingHandler?.let { handler ->
        BaseSheet(showSheet, sheetState, hideSheet) {
            SheetContainer {
                Column(
                    modifier = Modifier.heightIn(200.dp, 600.dp).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val data by handler.data.collectAsState()

                    DownloadInfoTitle(it, data, handler)

                    DownloadInfoTable(data, it)
                }
            }
        }
    }
}

@Composable
private fun DownloadInfoTitle(
    it: MediaInfo,
    data: DownloadInfo?,
    handler: CachedLoadingHandler<DownloadInfo>
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(it.name, modifier = Modifier.weight(1f))
        if (data?.status == DownloadStatus.FAILED) {
            Button({
                handler.refresh()
            }) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun DownloadInfoTable(
    downloadInfo: DownloadInfo?,
    mediaInfo: MediaInfo
) {
    val tableData = remember(downloadInfo, mediaInfo) {
        buildMap {
            put("Path", downloadInfo?.path)
            put("Size", HumanReadable.fileSize(mediaInfo.size))
            put("Status", downloadInfo?.status?.name)
            if (downloadInfo?.status == DownloadStatus.FAILED) {
                put("Error", downloadInfo.message)
            }
            put("Url", mediaInfo.url)
        }
    }
    DataTable(
        {
            headerBackground {
                Box(modifier = Modifier.background(color = Color.LightGray))
            }
            column { Text("Key") }
            column { Text("Value") }
        }
    ) {
        tableData.forEach { (key, value) ->
            row(modifier = Modifier) {
                cell {
                    Text(key)
                }
                cell {
                    value?.let {
                        val scrollState = rememberScrollState()
                        Text(it, maxLines = 1, modifier = Modifier.horizontalScroll(scrollState))
                    }
                }
            }
        }
    }
}
