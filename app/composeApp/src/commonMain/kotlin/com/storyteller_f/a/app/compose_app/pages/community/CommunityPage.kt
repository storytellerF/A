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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.storyteller_f.a.app.compose_app.LocalAppNav
import com.storyteller_f.a.app.compose_app.LocalClientFileProvider
import com.storyteller_f.a.app.compose_app.LocalGlobalDialog
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.Res
import com.storyteller_f.a.app.compose_app.add
import com.storyteller_f.a.app.compose_app.all_members
import com.storyteller_f.a.app.compose_app.common.AppNav
import com.storyteller_f.a.app.compose_app.common.CommunityScreen
import com.storyteller_f.a.app.compose_app.common.CommunityViewModel
import com.storyteller_f.a.app.compose_app.common.DownloadViewModel
import com.storyteller_f.a.app.compose_app.common.OnCommunityExited
import com.storyteller_f.a.app.compose_app.common.TopicComposeData
import com.storyteller_f.a.app.compose_app.common.createCommunityRoomsViewModel
import com.storyteller_f.a.app.compose_app.common.createCommunityTopicsViewModel
import com.storyteller_f.a.app.compose_app.common.createCommunityViewModel
import com.storyteller_f.a.app.compose_app.common.getDownloadViewModel
import com.storyteller_f.a.app.compose_app.common.hasRouteFlow
import com.storyteller_f.a.app.compose_app.components.BaseSheet
import com.storyteller_f.a.app.compose_app.components.ButtonNav
import com.storyteller_f.a.app.compose_app.components.CommunityIcon
import com.storyteller_f.a.app.compose_app.components.CustomAlertDialog
import com.storyteller_f.a.app.compose_app.components.CustomAlertDialogController
import com.storyteller_f.a.app.compose_app.components.DialogContainer
import com.storyteller_f.a.app.compose_app.components.SheetContainer
import com.storyteller_f.a.app.compose_app.components.TopicList
import com.storyteller_f.a.app.compose_app.exit_community
import com.storyteller_f.a.app.compose_app.join_community
import com.storyteller_f.a.app.compose_app.join_community_prompt
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
import com.storyteller_f.a.client.core.exitCommunity
import com.storyteller_f.a.client.core.joinCommunity
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.storage.DownloadInfo
import com.storyteller_f.storage.DownloadStatus
import dev.tclement.fonticons.FontIcon
import io.github.windedge.table.DataTable
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import nl.jacobras.humanreadable.HumanReadable
import org.jetbrains.compose.resources.stringResource
import kotlin.math.pow
import kotlin.math.round

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
    val fontFamily by getFontFamily(communityId)
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

@Composable
fun getFontFamily(communityId: PrimaryKey): State<FontFamily?> {
    val model = createCommunityViewModel(communityId)
    val community by model.handler.data.collectAsState()
    val downloadViewModel = getDownloadViewModel(community?.font?.id)
    val provider = LocalClientFileProvider.current
    SideEffect {
        val font = community?.font
        if (font != null) {
            val path = Path(SystemTemporaryDirectory, "downloads", font.id.toString(), font.name)
            provider.getDownloader()?.download(font, path)
        }
    }
    return downloadViewModel.fontFamily.collectAsState()
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
                    CommunityIconWithDialog(
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
                        TopicList(viewModel)
                    }
                    composable("/rooms") {
                        val viewModel =
                            createCommunityRoomsViewModel(
                                communityId
                            )
                        RoomList(viewModel)
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
                CommunityIconWithDialog(
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
            appNav.gotoTopicCompose(
                TopicComposeData.Community(
                    communityId,
                    communityId ob ObjectType.COMMUNITY
                )
            )
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
                TopicList(viewModel)
            }

            else -> {
                val viewModel =
                    createCommunityRoomsViewModel(
                        communityId
                    )
                RoomList(viewModel)
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
            CommunityIconWithDialog(
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
private fun FontView(info: FileInfo) {
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
        val downloadViewModel = getDownloadViewModel(info.id)
        DownloadStatusView(downloadViewModel)
    }
    DownloadInfoPage(info, showSheet, sheetState) {
        showSheet = false
    }
}

@Composable
private fun DownloadStatusView(downloadViewModel: DownloadViewModel) {
    val data by downloadViewModel.data.collectAsState(null)
    val downloadStatus = data?.status
    when {
        data == null ||
            downloadStatus == DownloadStatus.NOT_DOWNLOADED ||
            downloadStatus == DownloadStatus.DOWNLOADING -> CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
        )

        downloadStatus == DownloadStatus.DOWNLOADED -> FontIcon(
            MaterialSymbolsOutlined.DownloadDone,
            "download done"
        )

        downloadStatus == DownloadStatus.DOWNLOAD_FAILED -> FontIcon(
            MaterialSymbolsOutlined.Error,
            "error"
        )
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
                        globalDialogController.useResult {
                            sessionViewModel.exitCommunity(communityId)
                        }.onSuccess { info ->
                            globalDialogController.emitEvent(OnCommunityExited(info))
                        }
                    }
                }
            } else {
                ButtonNav(Icons.Default.AddHome, stringResource(Res.string.join_community)) {
                    scope.launch {
                        globalDialogController.useResult {
                            sessionViewModel.joinCommunity(communityId)
                        }.onSuccess { info ->
                            globalDialogController.emitEvent(OnCommunityExited(info))
                        }
                    }
                }
            }
            ButtonNav(Icons.Default.Add, "Add") {
                dismiss()
                nav.gotoTopicCompose(
                    TopicComposeData.Community(
                        communityId,
                        communityId ob ObjectType.COMMUNITY
                    )
                )
            }
            val userSessionManager = LocalSessionManager.current
            val myInfo by userSessionManager.model.userHandler.data.collectAsState()
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
    fileInfo: FileInfo,
    showSheet: Boolean,
    sheetState: SheetState,
    hideSheet: () -> Unit,
) {
    BaseSheet(showSheet, sheetState, hideSheet) {
        SheetContainer {
            Column(
                modifier = Modifier.heightIn(200.dp, 600.dp).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val downloadViewModel = getDownloadViewModel(fileInfo.id)
                DownloadInfoPageInternal(downloadViewModel, fileInfo)
            }
        }
    }
}

@Composable
private fun DownloadInfoPageInternal(
    downloadViewModel: DownloadViewModel,
    fileInfo: FileInfo
) {
    val downloadInfo by downloadViewModel.data.collectAsState(null)
    DownloadInfoTitle(fileInfo, downloadInfo)
    downloadInfo?.let {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LinearProgressIndicator(progress = {
                it.progress.toFloat() / it.total
            })
            Text("${(it.progress.toFloat() * 100 / it.total).roundToDecimalPlaces(2)} %")
        }
    }
    DownloadInfoTable(downloadInfo, fileInfo)
}

@Composable
private fun DownloadInfoTitle(
    it: FileInfo,
    data: DownloadInfo?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        val provider = LocalClientFileProvider.current
        Text(it.name, modifier = Modifier.weight(1f))
        if (data != null && data.status != DownloadStatus.PROCESSED) {
            Button({
                provider.getDownloader()?.resume(it)
            }) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun DownloadInfoTable(
    downloadInfo: DownloadInfo?,
    fileInfo: FileInfo
) {
    val tableData = remember(downloadInfo, fileInfo) {
        buildMap {
            put("Path", downloadInfo?.path)
            put("Size", HumanReadable.fileSize(fileInfo.size))
            put("Status", downloadInfo?.status?.name)
            if (downloadInfo?.status == DownloadStatus.DOWNLOAD_FAILED) {
                put("Error", downloadInfo.message)
            }
            put("Message", downloadInfo?.message)
            put("Url", fileInfo.url)
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

fun Float.roundToDecimalPlaces(decimals: Int): Float {
    val multiplier = 10.0f.pow(decimals)
    return round(this * multiplier) / multiplier
}

@Composable
fun CommunityIconWithDialog(
    communityInfo: CommunityInfo?,
    showDialog: Boolean,
    iconSize: Dp = 40.dp,
    setClickEvent: Boolean = true,
    onClickIcon: (Boolean) -> Unit,
) {
    CommunityIcon(communityInfo, iconSize, setClickEvent, onClickIcon)
    CommunityDialog(communityInfo, showDialog) {
        onClickIcon(false)
    }
}
