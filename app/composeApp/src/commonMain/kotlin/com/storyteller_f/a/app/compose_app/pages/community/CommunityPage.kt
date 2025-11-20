package com.storyteller_f.a.app.compose_app.pages.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddHome
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.storyteller_f.a.app.compose_app.AppGlobalDialogController
import com.storyteller_f.a.app.compose_app.LocalAppNavFactory
import com.storyteller_f.a.app.compose_app.LocalClientFileProvider
import com.storyteller_f.a.app.compose_app.LocalGlobalDialog
import com.storyteller_f.a.app.compose_app.LocalUserInfo
import com.storyteller_f.a.app.compose_app.Res
import com.storyteller_f.a.app.compose_app.add
import com.storyteller_f.a.app.compose_app.all_members
import com.storyteller_f.a.app.compose_app.common.AppNavFactory
import com.storyteller_f.a.app.compose_app.common.CommunityScreen
import com.storyteller_f.a.app.compose_app.common.CommunityViewModel
import com.storyteller_f.a.app.compose_app.common.OnCommunityExited
import com.storyteller_f.a.app.compose_app.common.OnCommunityJoined
import com.storyteller_f.a.app.compose_app.common.createCommunityRoomsViewModel
import com.storyteller_f.a.app.compose_app.common.createCommunityTopicsViewModel
import com.storyteller_f.a.app.compose_app.common.createCommunityViewModel
import com.storyteller_f.a.app.compose_app.common.getDownloadViewModel
import com.storyteller_f.a.app.compose_app.common.hasRouteFlow
import com.storyteller_f.a.app.compose_app.components.FontView
import com.storyteller_f.a.app.compose_app.components.TopicList
import com.storyteller_f.a.app.compose_app.exit_community
import com.storyteller_f.a.app.compose_app.join_community
import com.storyteller_f.a.app.compose_app.join_community_prompt
import com.storyteller_f.a.app.compose_app.pages.room.RoomList
import com.storyteller_f.a.app.compose_app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.compose_app.pages.search.SearchScope
import com.storyteller_f.a.app.compose_app.pages.topic.TopicComposeData
import com.storyteller_f.a.app.compose_app.pages.user.ButtonBadgeSuffix
import com.storyteller_f.a.app.compose_app.permission_denied
import com.storyteller_f.a.app.compose_app.rooms
import com.storyteller_f.a.app.compose_app.topics
import com.storyteller_f.a.app.compose_app.ui.theme.AppTheme
import com.storyteller_f.a.app.core.components.ButtonNav
import com.storyteller_f.a.app.core.components.CommunityIcon
import com.storyteller_f.a.app.core.components.CustomAlertDialog
import com.storyteller_f.a.app.core.components.CustomAlertDialogController
import com.storyteller_f.a.app.core.components.CustomBottomNav
import com.storyteller_f.a.app.core.components.CustomRailNav
import com.storyteller_f.a.app.core.components.DialogContainer
import com.storyteller_f.a.app.core.components.IconRes
import com.storyteller_f.a.app.core.components.NavRoute
import com.storyteller_f.a.app.core.components.emitEvent
import com.storyteller_f.a.app.core.components.request
import com.storyteller_f.a.client.core.exitCommunity
import com.storyteller_f.a.client.core.joinCommunity
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
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
    val fileInfo = community?.font
    val downloadViewModel = getDownloadViewModel(fileInfo?.id)
    val provider = LocalClientFileProvider.current
    LaunchedEffect(fileInfo) {
        if (fileInfo != null) {
            provider.getDownloader()?.download(fileInfo)
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
    var showDialog by remember {
        mutableStateOf(false)
    }
    Scaffold(floatingActionButton = {
        CommunityFloatingButton(community, communityId) {
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
    communityId: PrimaryKey,
    onClickOk: () -> Unit,
) {
    val appNavFactory = LocalAppNavFactory.current
    val alertDialogState = remember {
        CustomAlertDialogController()
    }
    val title = stringResource(Res.string.permission_denied)
    val message = stringResource(Res.string.join_community_prompt)
    FloatingActionButton(onClick = {
        if (community?.isJoined == true) {
            appNavFactory.newAppNav().gotoTopicCompose(
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
private fun CommunityMenus(
    communityId: PrimaryKey,
    communityInfo: CommunityInfo,
    dismiss: () -> Unit,
) {
    val appNavFactory = LocalAppNavFactory.current
    val globalDialogController = LocalGlobalDialog.current
    Column {
        ButtonNav(
            IconRes.Vector(Icons.Default.CardMembership),
            stringResource(Res.string.all_members),
            {
                ButtonBadgeSuffix(communityInfo.memberCount)
            }
        ) {
            dismiss()
            appNavFactory.newAppNav().gotoMemberPage(communityId, ObjectType.COMMUNITY)
        }
        val appNavFactory = LocalAppNavFactory.current
        val isCommunityPage by appNavFactory.hasRouteFlow<CommunityScreen>()
        if (isCommunityPage) {
            CommunityMemberStatusButton(
                communityInfo,
                globalDialogController,
                communityId
            )
            CommunityCreateButton(dismiss, appNavFactory, communityId)
            CommunityAdminButtons(communityInfo, dismiss, appNavFactory, communityId)
        }
    }
}

@Composable
private fun CommunityAdminButtons(
    communityInfo: CommunityInfo,
    dismiss: () -> Unit,
    appNavFactory: AppNavFactory,
    communityId: PrimaryKey
) {
    val myInfo = LocalUserInfo.current
    if (myInfo?.id == communityInfo.owner) {
        ButtonNav(Icons.Default.Folder, "Files") {
            dismiss()
            appNavFactory.newAppNav().gotoFileExplorer(communityId ob ObjectType.COMMUNITY)
        }

        ButtonNav(Icons.Default.Title, "Add Title") {
            dismiss()
            appNavFactory.newAppNav().gotoTitleCompose()
        }

        ButtonNav(Icons.Default.Settings, "Settings") {
            dismiss()
            appNavFactory.newAppNav().gotoSettingPage(communityId, ObjectType.COMMUNITY)
        }
    }
}

@Composable
private fun CommunityCreateButton(
    dismiss: () -> Unit,
    appNavFactory: AppNavFactory,
    communityId: PrimaryKey
) {
    ButtonNav(Icons.Default.Add, "Add") {
        dismiss()
        appNavFactory.newAppNav().gotoTopicCompose(
            TopicComposeData.Community(
                communityId,
                communityId ob ObjectType.COMMUNITY
            )
        )
    }
}

@Composable
private fun CommunityMemberStatusButton(
    communityInfo: CommunityInfo,
    globalDialogController: AppGlobalDialogController,
    communityId: PrimaryKey
) {
    val scope = rememberCoroutineScope()
    if (communityInfo.isJoined) {
        ButtonNav(Icons.Default.Close, stringResource(Res.string.exit_community)) {
            scope.launch {
                globalDialogController.useResult {
                    request {
                        exitCommunity(communityId)
                    }
                }.onSuccess { info ->
                    globalDialogController.emitEvent(OnCommunityExited(info))
                }
            }
        }
    } else {
        ButtonNav(Icons.Default.AddHome, stringResource(Res.string.join_community)) {
            scope.launch {
                globalDialogController.useResult {
                    request {
                        joinCommunity(communityId)
                    }
                }.onSuccess { info ->
                    globalDialogController.emitEvent(OnCommunityJoined(info))
                }
            }
        }
    }
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
