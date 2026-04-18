package com.storyteller_f.a.app.pages.community

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
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.storyteller_f.a.app.AppGlobalDialogController
import com.storyteller_f.a.app.LocalAppNavFactory
import com.storyteller_f.a.app.LocalClientFileProvider
import com.storyteller_f.a.app.LocalGlobalDialog
import com.storyteller_f.a.app.LocalUserInfo
import com.storyteller_f.a.app.Res
import com.storyteller_f.a.app.add
import com.storyteller_f.a.app.all_members
import com.storyteller_f.a.app.common.AppNavFactory
import com.storyteller_f.a.app.common.CommunityScreen
import com.storyteller_f.a.app.common.CommunityViewModel
import com.storyteller_f.a.app.common.OnCommunityExited
import com.storyteller_f.a.app.common.OnCommunityJoined
import com.storyteller_f.a.app.common.createCommunityRoomsViewModel
import com.storyteller_f.a.app.common.createCommunityTopicsViewModel
import com.storyteller_f.a.app.common.createCommunityViewModel
import com.storyteller_f.a.app.common.getDownloadViewModel
import com.storyteller_f.a.app.common.hasRouteFlow
import com.storyteller_f.a.app.components.FontView
import com.storyteller_f.a.app.core.components.ButtonNav
import com.storyteller_f.a.app.core.components.CommunityIcon
import com.storyteller_f.a.app.core.components.CustomAlertDialog
import com.storyteller_f.a.app.core.components.CustomAlertDialogController
import com.storyteller_f.a.app.core.components.CustomBottomNav
import com.storyteller_f.a.app.core.components.CustomRailNav
import com.storyteller_f.a.app.core.components.DialogContainer
import com.storyteller_f.a.app.core.components.FavoriteButton
import com.storyteller_f.a.app.core.components.IconRes
import com.storyteller_f.a.app.core.components.NavRoute
import com.storyteller_f.a.app.core.components.SubscriptionButton
import com.storyteller_f.a.app.core.components.emitEvent
import com.storyteller_f.a.app.core.components.request
import com.storyteller_f.a.app.exit_community
import com.storyteller_f.a.app.join_community
import com.storyteller_f.a.app.join_community_prompt
import com.storyteller_f.a.app.pages.room.RoomList
import com.storyteller_f.a.app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.pages.search.SearchScope
import com.storyteller_f.a.app.pages.topic.TopicComposeData
import com.storyteller_f.a.app.pages.topic.TopicList
import com.storyteller_f.a.app.pages.user.ButtonBadgeSuffix
import com.storyteller_f.a.app.permission_denied
import com.storyteller_f.a.app.rooms
import com.storyteller_f.a.app.topics
import com.storyteller_f.a.app.ui.theme.AppTheme
import com.storyteller_f.a.client.core.exitCommunity
import com.storyteller_f.a.client.core.joinCommunity
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
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
    val fontFamily by getFontFamily(communityId)
    val typography = MaterialTheme.typography
    return typography.copy(
        bodyLarge = typography.bodyLarge.copy(fontFamily = fontFamily ?: typography.bodyLarge.fontFamily),
        bodyMedium = typography.bodyMedium.copy(fontFamily = fontFamily ?: typography.bodyMedium.fontFamily),
        bodySmall = typography.bodySmall.copy(fontFamily = fontFamily ?: typography.bodySmall.fontFamily)
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

@Serializable
sealed interface CommunityRoute : NavKey {
    @Serializable
    data object Topics : CommunityRoute {
        override fun toString(): String = "/topics"
    }

    @Serializable
    data object Rooms : CommunityRoute {
        override fun toString(): String = "/rooms"
    }
}

private fun buildSearchScope(
    pagerState: PagerState,
    communityId: PrimaryKey,
) = when (pagerState.currentPage) {
    0 -> SearchScope.CommunityTopic(communityId)
    else -> SearchScope.CommunityRoom(communityId)
}

private fun buildSearchScope(
    currentRoute: NavKey?,
    communityId: PrimaryKey,
) = when (currentRoute) {
    CommunityRoute.Topics -> SearchScope.CommunityTopic(communityId)

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
    val config = remember {
        SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(CommunityRoute.Topics::class)
                    subclass(CommunityRoute.Rooms::class)
                }
            }
        }
    }
    val backStack = rememberNavBackStack(config, CommunityRoute.Topics)
    val current = backStack.last()
    val searchScope = buildSearchScope(current, communityId)
    Scaffold { paddingValues ->
        Row(modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())) {
            CommunityRailNav(backStack, current, navRoutes)
            CommunityNonCompatContent(
                communityId,
                needShowDialog,
                dialogShown,
                model,
                community,
                searchScope,
                backStack
            )
        }
    }
}

@Composable
private fun CommunityRailNav(
    backStack: NavBackStack<NavKey>,
    current: NavKey,
    navRoutes: List<NavRoute>,
) {
    CustomRailNav(current.toString(), navRoutes) { path ->
        val target = when (path) {
            "/rooms" -> CommunityRoute.Rooms
            else -> CommunityRoute.Topics
        }
        if (backStack.last() != target) {
            val i = backStack.indexOf(target)
            if (i >= 0) {
                repeat(backStack.size - i - 1) {
                    backStack.removeLastOrNull()
                }
            } else {
                backStack.add(target)
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun CommunityNonCompatContent(
    communityId: PrimaryKey,
    needShowDialog: Boolean,
    dialogShown: Boolean,
    model: CommunityViewModel,
    community: CommunityInfo?,
    searchScope: SearchScope,
    backStack: NavBackStack<NavKey>,
) {
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

        NavDisplay(
            backStack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = entryProvider {
                entry<CommunityRoute.Topics> {
                    val viewModel = createCommunityTopicsViewModel(communityId)
                    TopicList(viewModel)
                }
                entry<CommunityRoute.Rooms> {
                    val viewModel = createCommunityRoomsViewModel(communityId)
                    RoomList(viewModel)
                }
            }
        )
    }
}

@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
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
    }) {
        Column(modifier = Modifier.padding(bottom = it.calculateBottomPadding())) {
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
                TopicComposeData.Community(communityId, communityId ob ObjectType.COMMUNITY)
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
                val viewModel = createCommunityTopicsViewModel(communityId)
                TopicList(viewModel)
            }

            else -> {
                val viewModel = createCommunityRoomsViewModel(communityId)
                RoomList(viewModel)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityDialogInternal(communityInfo: CommunityInfo, dismiss: () -> Unit) {
    val communityId = communityInfo.id
    DialogContainer {
        Row(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceDim, RoundedCornerShape(8.dp))
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
        val isCommunityPage = appNavFactory.hasRouteFlow<CommunityScreen>()
        if (isCommunityPage) {
            CommunityMemberStatusButton(communityInfo, globalDialogController, communityId)
            FavoriteButton(communityInfo.favoriteId, communityInfo.tuple())
            SubscriptionButton(communityInfo.subscriptionId, communityInfo.tuple())
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
            TopicComposeData.Community(communityId, communityId ob ObjectType.COMMUNITY)
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
