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
import androidx.paging.ExperimentalPagingApi
import app.cash.paging.compose.collectAsLazyPagingItems
import com.storyteller_f.a.app.CustomBottomNav
import com.storyteller_f.a.app.NavRoute
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.compontents.*
import com.storyteller_f.a.app.room.RoomList
import com.storyteller_f.a.app.search.CustomSearchBar
import com.storyteller_f.a.app.world.TopicList
import com.storyteller_f.a.client_lib.getCommunityInfo
import com.storyteller_f.a.client_lib.getCommunityRooms
import com.storyteller_f.a.client_lib.getCommunityTopics
import com.storyteller_f.a.client_lib.joinCommunity
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.OKey
import com.storyteller_f.shared.type.ObjectType
import kotlinx.coroutines.launch
import moe.tlaster.precompose.viewmodel.viewModel
import moe.tlaster.precompose.viewmodel.viewModelScope
import org.jetbrains.compose.resources.stringResource

data class OnCommunityJoined(val communityId: OKey)

class CommunityViewModel(private val communityId: OKey) : SimpleViewModel<CommunityInfo>() {
    init {
        load()
        viewModelScope.launch {
            for (i in bus) {
                if (i is OnCommunityJoined) {
                    if (i.communityId == communityId)
                        handler.refresh()
                }
            }
        }
    }

    override suspend fun loadInternal() {
        handler.request {
            serviceCatching {
                client.getCommunityInfo(communityId)
            }
        }
    }


}

@OptIn(ExperimentalPagingApi::class)
class CommunityTopicsViewModel(private val communityId: OKey) : PagingViewModel<OKey, TopicInfo>({
    SimplePagingSource {
        serviceCatching {
            client.getCommunityTopics(communityId, 10)
        }.map {
            APagingData(it.data, it.pagination?.nextPageToken?.toULongOrNull())
        }

    }
})


@OptIn(ExperimentalPagingApi::class)
class CommunityRoomsViewModel(private val communityId: OKey) : PagingViewModel<Int, RoomInfo>({
    SimplePagingSource {
        serviceCatching {
            client.getCommunityRooms(communityId)
        }.map {
            APagingData(it.data, null)
        }

    }
})


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommunityPage(
    communityId: OKey,
    onClickAddTopic: () -> Unit,
    onClick: (OKey, ObjectType) -> Unit
) {
    val model = viewModel(CommunityViewModel::class, keys = listOf("community", communityId)) {
        CommunityViewModel(communityId)
    }
    val community by model.handler.data.collectAsState()
    val eventState = rememberEventState()
    val navs = communityNavRoutes()
    val pagerState = rememberPagerState {
        2
    }
    val scope = rememberCoroutineScope()
    Scaffold(floatingActionButton = {
        FloatingActionButton(onClick = {
            if (community?.isJoined == true) {
                onClickAddTopic()
            } else {
                eventState.showMessage("Not joined!")
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
    }) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).consumeWindowInsets(WindowInsets.statusBars),
        ) {
            CustomSearchBar {
                CommunityIcon(community, 40.dp)
            }

            CommunityPageInternal(pagerState, communityId, onClick)

        }
    }

}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CommunityPageInternal(
    pagerState: PagerState,
    communityId: OKey,
    onClick: (OKey, ObjectType) -> Unit
) {
    HorizontalPager(pagerState) {
        when (it) {
            0 -> {
                val viewModel = viewModel(
                    CommunityTopicsViewModel::class,
                    keys = listOf("community-topics", communityId)
                ) {
                    CommunityTopicsViewModel(communityId)
                }
                val items = viewModel.flow.collectAsLazyPagingItems()
                TopicList(items, onClick)
            }

            else -> {
                val viewModel =
                    viewModel(CommunityRoomsViewModel::class, keys = listOf("community-rooms", communityId)) {
                        CommunityRoomsViewModel(communityId)
                    }
                val items = viewModel.flow.collectAsLazyPagingItems()
                RoomList(items) { roomId ->
                    onClick(roomId, ObjectType.ROOM)
                }
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
    if (communityInfo != null && showDialog)
        BasicAlertDialog(
            {
                dismiss()
            },
        ) {
            CommunityDialogInternal(communityInfo)
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
            val eventState = rememberEventState()
            if (communityInfo.isJoined)
                ButtonNav(Icons.Default.Close, "Exit Community")
            else
                ButtonNav(Icons.Default.AddHome, "Join Community") {
                    scope.launch {
                        eventState.use {
                            client.joinCommunity(communityId)
                            bus.send(OnCommunityJoined(communityId))
                        }
                    }
                }
            EventDialog(eventState)
        }
    }
}
