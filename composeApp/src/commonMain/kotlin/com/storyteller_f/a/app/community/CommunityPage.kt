package com.storyteller_f.a.app.community

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.add
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import app.cash.paging.compose.collectAsLazyPagingItems
import com.storyteller_f.a.app.CustomBottomNav
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.NavRoute
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.compontents.*
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.room.RoomList
import com.storyteller_f.a.app.room.TopicsViewModel
import com.storyteller_f.a.app.search.CustomSearchBar
import com.storyteller_f.a.app.world.TopicList
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKeyOrNull
import io.ktor.client.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

data class OnCommunityJoined(val communityId: PrimaryKey)

class CommunityViewModel(private val requestInfo: suspend HttpClient.() -> CommunityInfo) :
    SimpleViewModel<CommunityInfo>() {
    constructor(communityId: PrimaryKey) : this({
        getCommunityInfo(communityId)
    })

    constructor(communityAid: String) : this({
        getCommunityInfoByAid(communityAid)
    })

    init {
        load()
        viewModelScope.launch {
            for (i in bus) {
                if (i is OnCommunityJoined) {
                    if (i.communityId == handler.data.value?.id) {
                        handler.refresh()
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

@OptIn(ExperimentalPagingApi::class)
class CommunityRoomsViewModel(private val communityId: PrimaryKey) : PagingViewModel<PrimaryKey, RoomInfo>({
    SimplePagingSource {
        serviceCatching {
            client.getCommunityRooms(communityId, it, 10)
        }.map {
            APagingData(it.data, it.pagination?.nextPageToken?.toPrimaryKeyOrNull())
        }
    }
})

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommunityPage(
    communityId: PrimaryKey
) {
    val appNav = LocalAppNav.current
    val model = viewModel(CommunityViewModel::class, keys = listOf("community", communityId)) {
        CommunityViewModel(communityId)
    }
    val community by model.handler.data.collectAsState()
    val navs = communityNavRoutes()
    val pagerState = rememberPagerState {
        2
    }
    val scope = rememberCoroutineScope()
    Scaffold(floatingActionButton = {
        FloatingActionButton(onClick = {
            if (community?.isJoined == true) {
                appNav.gotoTopicCompose(ObjectType.COMMUNITY, communityId)
            } else {
                globalDialogState.showMessage("Not joined!")
            }
        }) {
            Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add))
        }
    }, bottomBar = {
        CustomBottomNav(navs[pagerState.currentPage].path, navs) { path ->
            scope.launch {
                pagerState.animateScrollToPage(navs.indexOfFirst {
                    it.path == path
                })
            }
        }
    }) {
        Column(
            modifier = Modifier.padding(bottom = it.calculateBottomPadding()),
        ) {
            CustomSearchBar {
                CommunityIcon(community, 40.dp)
            }

            CommunityPageInternal(pagerState, communityId)
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
                    viewModel(CommunityRoomsViewModel::class, keys = listOf("community-rooms", communityId)) {
                        CommunityRoomsViewModel(communityId)
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
fun CommunityDialog(communityInfo: CommunityInfo?, showDialog: Boolean, dismiss: () -> Unit) {
    if (communityInfo != null && showDialog) {
        BasicAlertDialog(
            {
                dismiss()
            },
        ) {
            CommunityDialogInternal(communityInfo)
        }
    }
}

@Composable
fun CommunityDialogInternal(communityInfo: CommunityInfo) {
    val communityId = communityInfo.id
    DialogContainer {
        Row(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceDim, RoundedCornerShape(8.dp))
                .padding(8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CommunityIcon(communityInfo, 50.dp)
            Column {
                Text(communityInfo.name)
            }
        }
        Column {
            val scope = rememberCoroutineScope()
            if (communityInfo.isJoined) {
                ButtonNav(Icons.Default.Close, "Exit Community")
            } else {
                ButtonNav(Icons.Default.AddHome, "Join Community") {
                    scope.launch {
                        globalDialogState.use {
                            client.joinCommunity(communityId)
                            bus.send(OnCommunityJoined(communityId))
                        }
                    }
                }
            }
        }
    }
}
