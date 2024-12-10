package com.storyteller_f.a.app.community

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.add
import a.composeapp.generated.resources.all_members
import a.composeapp.generated.resources.exit_community
import a.composeapp.generated.resources.join_community
import a.composeapp.generated.resources.join_community_prompt
import a.composeapp.generated.resources.permission_denied
import a.composeapp.generated.resources.rooms
import a.composeapp.generated.resources.topics
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import app.cash.paging.compose.collectAsLazyPagingItems
import com.storyteller_f.a.app.AppNav
import com.storyteller_f.a.app.CommunityScreen
import com.storyteller_f.a.app.CustomBottomNav
import com.storyteller_f.a.app.CustomRailNav
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.NavRoute
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.compontents.*
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.room.DialogSaveState
import com.storyteller_f.a.app.room.RoomList
import com.storyteller_f.a.app.room.RoomsViewModel
import com.storyteller_f.a.app.room.TopicsViewModel
import com.storyteller_f.a.app.search.CustomSearchBar
import com.storyteller_f.a.app.search.SearchScope
import com.storyteller_f.a.app.world.TopicList
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.client.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

data class OnCommunityJoined(val communityId: PrimaryKey, val newInfo: CommunityInfo)
data class OnCommunityExited(val communityId: PrimaryKey, val newInfo: CommunityInfo)

class CommunityViewModel(private val requestInfo: suspend HttpClient.() -> CommunityInfo) :
    SimpleViewModel<CommunityInfo>() {
    val dialog = DialogSaveState()

    constructor(communityId: PrimaryKey) : this({
        getCommunityInfo(communityId, LoginViewModel.isAlreadySignUp)
    })

    constructor(communityAid: String) : this({
        getCommunityInfoByAid(communityAid, LoginViewModel.isAlreadySignUp)
    })

    init {
        load()
        viewModelScope.launch {
            bus.collect { i ->
                val id = handler.data.value?.id
                when (i) {
                    is OnCommunityJoined -> {
                        if (i.communityId == id) {
                            update(i.newInfo)
                        }
                    }

                    is OnCommunityExited -> {
                        if (i.communityId == id) {
                            update(i.newInfo)
                        }
                    }
                }
            }
        }
    }

    override suspend fun loadInternal() {
        handler.request {
            serviceCatching {
                requestInfo(client)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3WindowSizeClassApi::class)
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
    val model = viewModel(CommunityViewModel::class, keys = listOf("community", communityId)) {
        CommunityViewModel(communityId)
    }
    val community by model.handler.data.collectAsState()
    val dialogShown by model.dialog.shownDialog.collectAsState()
    val pagerState = rememberPagerState {
        2
    }
    val searchScope = buildSearchScope(pagerState, communityId)
    val navs = communityNavRoutes()
    val scope = rememberCoroutineScope()

    Scaffold {
        Row(modifier = Modifier.padding(bottom = it.calculateBottomPadding())) {
            CustomRailNav(navs[pagerState.currentPage].path, navs) { path ->
                scope.launch {
                    pagerState.animateScrollToPage(navs.indexOfFirst {
                        it.path == path
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
    val model = viewModel(CommunityViewModel::class, keys = listOf("community", communityId)) {
        CommunityViewModel(communityId)
    }
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
    }) {
        Column(
            modifier = Modifier.padding(bottom = it.calculateBottomPadding()),
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
            appNav.gotoTopicCompose(ObjectType.COMMUNITY, communityId)
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
@OptIn(ExperimentalFoundationApi::class)
private fun CommunityPageInternal(
    pagerState: PagerState,
    communityId: PrimaryKey
) {
    HorizontalPager(pagerState) {
        when (it) {
            0 -> {
                val viewModel = viewModel(
                    TopicsViewModel::class,
                    keys = listOf("community-topics", communityId)
                ) {
                    TopicsViewModel(communityId, ObjectType.COMMUNITY)
                }
                val items = viewModel.flow.collectAsLazyPagingItems()
                TopicList(items)
            }

            else -> {
                val viewModel =
                    viewModel(RoomsViewModel::class, keys = listOf("community-rooms", communityId)) {
                        RoomsViewModel(JoinStatusSearch.UNSPECIFIED, "", communityId)
                    }
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
            CommunityIcon(communityInfo, 50.dp, showDialog = false, {})
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
                                val info = client.exitCommunity(communityId)
                                bus.emit(OnCommunityExited(communityId, info))
                            }
                        }
                    }
                } else {
                    ButtonNav(Icons.Default.AddHome, stringResource(Res.string.join_community)) {
                        scope.launch {
                            globalDialogState.use {
                                val info = client.joinCommunity(communityId)
                                bus.emit(OnCommunityJoined(communityId, info))
                            }
                        }
                    }
                }
            }
        }
    }
}
