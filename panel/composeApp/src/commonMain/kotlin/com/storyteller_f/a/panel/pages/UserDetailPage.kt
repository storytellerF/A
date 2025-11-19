package com.storyteller_f.a.panel.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.core.components.CustomBottomNav
import com.storyteller_f.a.app.core.components.NavRoute
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.pagingItems
import com.storyteller_f.a.panel.LocalPanelNav
import com.storyteller_f.a.panel.Res
import com.storyteller_f.a.panel.address_label
import com.storyteller_f.a.panel.aid_label
import com.storyteller_f.a.panel.common.createPanelJoinedCommunitiesViewModel
import com.storyteller_f.a.panel.common.createPanelJoinedRoomsViewModel
import com.storyteller_f.a.panel.common.createPanelUserOverviewViewModel
import com.storyteller_f.a.panel.log_supporting
import com.storyteller_f.a.panel.nickname_label
import com.storyteller_f.a.panel.none
import com.storyteller_f.a.panel.tab_basic_info
import com.storyteller_f.a.panel.tab_created_files
import com.storyteller_f.a.panel.tab_joined_communities
import com.storyteller_f.a.panel.tab_joined_rooms
import com.storyteller_f.a.panel.tab_received_titles
import com.storyteller_f.a.panel.user_info
import com.storyteller_f.a.panel.user_logs
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.ObjectType
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun UserDetailPage(uid: PrimaryKey) {
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    Scaffold(bottomBar = {
        val navRoutes = listOf(
            NavRoute("/info", Icons.Default.People, stringResource(Res.string.user_info)),
            NavRoute("/logs", Icons.AutoMirrored.Filled.Article, stringResource(Res.string.user_logs)),
        )
        CustomBottomNav(navRoutes[pagerState.currentPage].path, navRoutes) { path ->
            scope.launch {
                pagerState.animateScrollToPage(navRoutes.indexOfFirst { it.path == path })
            }
        }
    }) { paddingValues ->
        Column(Modifier.padding(top = paddingValues.calculateTopPadding())) {
            HorizontalPager(pagerState) { pageIndex ->
                when (pageIndex) {
                    0 -> UserInfoTabs(uid)
                    else -> UserLogsTab(uid)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserInfoTabs(uid: PrimaryKey) {
    val tabs = listOf(
        stringResource(Res.string.tab_basic_info),
        stringResource(Res.string.tab_joined_communities),
        stringResource(Res.string.tab_joined_rooms),
        stringResource(Res.string.tab_received_titles),
        stringResource(Res.string.tab_created_files)
    )
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    Column {
        PrimaryScrollableTabRow(pagerState.currentPage) {
            tabs.forEachIndexed { i, label ->
                Tab(selected = pagerState.currentPage == i, onClick = {
                    scope.launch { pagerState.scrollToPage(i) }
                }) {
                    Text(label, modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
        HorizontalPager(pagerState, modifier = Modifier.weight(1f)) { index ->
            when (index) {
                0 -> UserBasicInfoSectionVM(uid)
                1 -> UserJoinedCommunitiesSection(uid)
                2 -> UserJoinedRoomsSection(uid)
                3 -> UserReceivedTitlesSection(uid)
                else -> UserCreatedFilesSection(uid)
            }
        }
    }
}

@Composable
private fun UserJoinedCommunitiesSection(uid: PrimaryKey) {
    val vm = createPanelJoinedCommunitiesViewModel(uid)
    StateView(vm, modifier = Modifier.fillMaxSize()) { items ->
        LazyColumn {
            pagingItems(items, key = { it.id }) { index ->
                val info = items[index]
                if (info != null) {
                    val panelNav = LocalPanelNav.current
                    ListItem(
                        headlineContent = { Text(info.name) },
                        overlineContent = { Text(info.aid) },
                        modifier = Modifier.clickable { panelNav.gotoCommunityDetail(info.id) }
                    )
                    HorizontalDivider()
                } else {
                    ListItem(headlineContent = { Text("") })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun UserJoinedRoomsSection(uid: PrimaryKey) {
    val vm = createPanelJoinedRoomsViewModel(uid)
    StateView(vm, modifier = Modifier.fillMaxSize()) { items ->
        LazyColumn {
            pagingItems(items, key = { it.id }) { index ->
                val info = items[index]
                if (info != null) {
                    val panelNav = LocalPanelNav.current
                    ListItem(
                        headlineContent = { Text(info.name) },
                        supportingContent = {
                            val creator = info.creator.toString()
                            Text(listOf(creator).filter { it.isNotEmpty() }.joinToString(" • "))
                        },
                        modifier = Modifier.clickable { panelNav.gotoRoomDetail(info.id) }
                    )
                    HorizontalDivider()
                } else {
                    ListItem(headlineContent = { Text("") })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun UserReceivedTitlesSection(uid: PrimaryKey) {
    val vm = com.storyteller_f.a.panel.common.createPanelReceivedTitlesViewModel(uid)
    StateView(vm, modifier = Modifier.fillMaxSize()) { items ->
        LazyColumn {
            pagingItems(items, key = { it.id }) { index ->
                val info = items[index]
                if (info != null) {
                    val panelNav = LocalPanelNav.current
                    ListItem(
                        headlineContent = { Text(info.name) },
                        supportingContent = { Text(info.type.name) },
                        modifier = Modifier.clickable { panelNav.gotoTitleDetail(info.id) }
                    )
                    HorizontalDivider()
                } else {
                    ListItem(headlineContent = { Text("") })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun UserCreatedFilesSection(uid: PrimaryKey) {
    val vm = com.storyteller_f.a.panel.common.createPanelUserFilesViewModel(uid)
    StateView(vm, modifier = Modifier.fillMaxSize()) { items ->
        LazyColumn {
            pagingItems(items, key = { it.id }) { index ->
                val info = items[index]
                if (info != null) {
                    val panelNav = LocalPanelNav.current
                    ListItem(
                        headlineContent = { Text(info.name) },
                        supportingContent = { Text(info.contentType) },
                        modifier = Modifier.clickable { panelNav.gotoFileDetail(info.id) }
                    )
                    HorizontalDivider()
                } else {
                    ListItem(headlineContent = { Text("") })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun UserLogsTab(uid: PrimaryKey) {
    val vm = com.storyteller_f.a.panel.common.createPanelUserLogsViewModel(uid)
    StateView(vm, modifier = Modifier.fillMaxSize()) { items ->
        LazyColumn {
            pagingItems(items, key = { it.id }) { index ->
                val info = items[index]
                if (info != null) {
                    val panelNav = LocalPanelNav.current
                    ListItem(
                        headlineContent = { Text(info.type.name) },
                        supportingContent = { Text(
                            stringResource(
                                Res.string.log_supporting,
                                info.objectType,
                                info.objectId.toString(),
                                info.createdTime.toString()
                            )
                        ) },
                        modifier = Modifier.clickable {
                            when (info.objectType) {
                                ObjectType.USER -> panelNav.gotoUserDetail(info.objectId)
                                ObjectType.COMMUNITY -> panelNav.gotoCommunityDetail(info.objectId)
                                ObjectType.ROOM -> panelNav.gotoRoomDetail(info.objectId)
                                ObjectType.TOPIC -> panelNav.gotoTopicDetail(info.objectId)
                                ObjectType.TITLE -> panelNav.gotoTitleDetail(info.objectId)
                                ObjectType.FILE -> panelNav.gotoFileDetail(info.objectId)
                                else -> {}
                            }
                        }
                    )
                    HorizontalDivider()
                } else {
                    ListItem(headlineContent = { Text("") })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun UserBasicInfoSectionVM(uid: PrimaryKey) {
    val vm = createPanelUserOverviewViewModel(uid)
    StateView(vm.handler, modifier = Modifier.fillMaxSize()) { overview ->
        Column(Modifier.padding(16.dp)) {
            val u = overview.userInfo
            Text(stringResource(Res.string.nickname_label, u.nickname))
            Text(stringResource(Res.string.address_label, u.address))
            val aidText = u.aid ?: stringResource(Res.string.none)
            Text(stringResource(Res.string.aid_label, aidText))
            Text("favoriteCount: ${overview.favoriteCount}")
            Text("subscriptionCount: ${overview.subscriptionCount}")
            Text("acg: ${overview.acg}")
            Text("childAccountCount: ${overview.childAccountCount}")
        }
    }
}
