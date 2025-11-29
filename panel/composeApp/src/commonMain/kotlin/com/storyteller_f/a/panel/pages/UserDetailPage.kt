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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.core.components.CommunityIcon
import com.storyteller_f.a.app.core.components.CustomBottomNav
import com.storyteller_f.a.app.core.components.FileIcon
import com.storyteller_f.a.app.core.components.NavRoute
import com.storyteller_f.a.app.core.components.RoomIcon
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.pagingItems
import com.storyteller_f.a.panel.LocalPanelNav
import com.storyteller_f.a.panel.Res
import com.storyteller_f.a.panel.common.createPanelJoinedCommunitiesViewModel
import com.storyteller_f.a.panel.common.createPanelJoinedRoomsViewModel
import com.storyteller_f.a.panel.common.createPanelUserCommentsViewModel
import com.storyteller_f.a.panel.common.createPanelUserLogsViewModel
import com.storyteller_f.a.panel.common.createPanelUserOverviewViewModel
import com.storyteller_f.a.panel.common.createPanelUserReactionsViewModel
import com.storyteller_f.a.panel.common.createPanelUserUploadRecordsViewModel
import com.storyteller_f.a.panel.common.createPanelUserViewModel
import com.storyteller_f.a.panel.components.InfoTable
import com.storyteller_f.a.panel.components.TopicCell
import com.storyteller_f.a.panel.file_progress
import com.storyteller_f.a.panel.log_supporting
import com.storyteller_f.a.panel.tab_basic_info
import com.storyteller_f.a.panel.tab_created_files
import com.storyteller_f.a.panel.tab_joined_communities
import com.storyteller_f.a.panel.tab_joined_rooms
import com.storyteller_f.a.panel.tab_received_titles
import com.storyteller_f.a.panel.tab_user_comments
import com.storyteller_f.a.panel.tab_user_reactions
import com.storyteller_f.a.panel.upload_records
import com.storyteller_f.a.panel.user_detail_title
import com.storyteller_f.a.panel.user_detail_title_with_info
import com.storyteller_f.a.panel.user_info
import com.storyteller_f.a.panel.user_logs
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.launch
import nl.jacobras.humanreadable.HumanReadable
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailPage(uid: PrimaryKey) {
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { UserTopBar(uid) }, bottomBar = {
        val navRoutes = listOf(
            NavRoute("/info", Icons.Default.People, stringResource(Res.string.user_info)),
            NavRoute(
                "/logs",
                Icons.AutoMirrored.Filled.Article,
                stringResource(Res.string.user_logs)
            ),
        )
        CustomBottomNav(navRoutes[pagerState.currentPage].path, navRoutes) { path ->
            scope.launch {
                pagerState.animateScrollToPage(navRoutes.indexOfFirst { it.path == path })
            }
        }
    }) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
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
private fun UserTopBar(uid: PrimaryKey) {
    val vm = createPanelUserViewModel(uid)
    val info by vm.handler.data.collectAsState(null)
    val nickname = info?.nickname
    val aid = info?.aid
    val title = if (nickname != null && aid != null) {
        stringResource(Res.string.user_detail_title_with_info, nickname, aid)
    } else {
        stringResource(Res.string.user_detail_title)
    }
    val nav = LocalPanelNav.current
    TopAppBar(
        title = {
            Text(title)
        },
        navigationIcon = {
            IconButton(onClick = { nav.open() }) {
                Icon(Icons.Default.Menu, null)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserInfoTabs(uid: PrimaryKey) {
    val tabs = listOf(
        stringResource(Res.string.tab_basic_info),
        stringResource(Res.string.tab_joined_communities),
        stringResource(Res.string.tab_joined_rooms),
        stringResource(Res.string.tab_received_titles),
        stringResource(Res.string.tab_created_files),
        stringResource(Res.string.upload_records),
        stringResource(Res.string.tab_user_reactions),
        stringResource(Res.string.tab_user_comments)
    )
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    Column {
        PrimaryScrollableTabRow(pagerState.currentPage) {
            tabs.forEachIndexed { i, label ->
                Tab(selected = pagerState.currentPage == i, onClick = {
                    scope.launch { pagerState.scrollToPage(i) }
                }) {
                    Text(label, modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))
                }
            }
        }
        HorizontalPager(pagerState, modifier = Modifier.weight(1f)) { index ->
            when (index) {
                0 -> UserBasicInfoSection(uid)
                1 -> UserJoinedCommunitiesSection(uid)
                2 -> UserJoinedRoomsSection(uid)
                3 -> UserReceivedTitlesSection(uid)
                4 -> UserCreatedFilesSection(uid)
                5 -> UserUploadRecordsSection(uid)
                6 -> UserReactionsSection(uid)
                else -> UserCommentsSection(uid)
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
                        leadingContent = {
                            CommunityIcon(info, 40.dp, false) {}
                        },
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
                        leadingContent = {
                            RoomIcon(info, 40.dp, false) {}
                        },
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
                        supportingContent = { Text(HumanReadable.fileSize(info.size)) },
                        leadingContent = { FileIcon(info) },
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
private fun UserUploadRecordsSection(uid: PrimaryKey) {
    val vm = createPanelUserUploadRecordsViewModel(uid)
    StateView(vm, modifier = Modifier.fillMaxSize()) { items ->
        LazyColumn {
            pagingItems(items, key = { it.id }) { index ->
                val info = items[index]
                if (info != null) {
                    ListItem(
                        headlineContent = { Text(info.name) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    Res.string.file_progress,
                                    info.status,
                                    HumanReadable.fileSize(info.progress),
                                    HumanReadable.fileSize(info.total)
                                )
                            )
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
private fun UserLogsTab(uid: PrimaryKey) {
    val vm = createPanelUserLogsViewModel(uid)
    StateView(vm, modifier = Modifier.fillMaxSize()) { items ->
        LazyColumn {
            pagingItems(items, key = { it.id }) { index ->
                val info = items[index]
                if (info != null) {
                    val panelNav = LocalPanelNav.current
                    ListItem(
                        headlineContent = { Text(info.type.name) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    Res.string.log_supporting,
                                    info.objectType,
                                    info.objectId.toString(),
                                    info.createdTime.toString()
                                )
                            )
                        },
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
private fun UserBasicInfoSection(uid: PrimaryKey) {
    val vm = createPanelUserOverviewViewModel(uid)
    StateView(vm.handler, modifier = Modifier.fillMaxSize()) { overview ->
        val items = buildList {
            add("id" to overview.userInfo.id.toString())
            add("nickname" to overview.userInfo.nickname)
            add("address" to overview.userInfo.address)
            add("aid" to overview.userInfo.aid.toString())
            add("favoriteCount" to overview.favoriteCount.toString())
            add("subscriptionCount" to overview.subscriptionCount.toString())
            add("acg" to overview.acg.toString())
            add("reactionRecordCount" to overview.reactionRecordCount.toString())
            add("commentCount" to overview.commentCount.toString())
            add("childAccountCount" to overview.childAccountCount.toString())
        }
        InfoTable(items, Modifier.padding(16.dp))
    }
}

@Composable
private fun UserReactionsSection(uid: PrimaryKey) {
    val vm = createPanelUserReactionsViewModel(uid)
    StateView(vm, modifier = Modifier.fillMaxSize()) { items ->
        LazyColumn {
            pagingItems(items, key = { it.id }) { index ->
                val info = items[index]
                if (info != null) {
                    val panelNav = LocalPanelNav.current
                    ListItem(
                        headlineContent = { Text("${info.emoji} • Topic ${info.objectId}") },
                        supportingContent = { Text("${info.objectType} • ${info.createdTime}") },
                        modifier = Modifier.clickable { panelNav.gotoTopicDetail(info.objectId) }
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
private fun UserCommentsSection(uid: PrimaryKey) {
    val vm = createPanelUserCommentsViewModel(uid)
    StateView(vm, modifier = Modifier.fillMaxSize()) { items ->
        LazyColumn {
            pagingItems(items, key = { it.id }) { index ->
                val info = items[index]
                if (info != null) {
                    val panelNav = LocalPanelNav.current
                    TopicCell(info, panelNav)
                    HorizontalDivider()
                } else {
                    ListItem(headlineContent = { Text("") })
                    HorizontalDivider()
                }
            }
        }
    }
}
