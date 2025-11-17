package com.storyteller_f.a.panel.pages

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.PrimaryTabRow
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
import com.storyteller_f.a.panel.common.createPanelUserViewModel
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.launch

@Composable
fun UserDetailPage(uid: PrimaryKey) {
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    Scaffold(bottomBar = {
        val navRoutes = listOf(
            NavRoute("/info", Icons.Default.People, "用户信息"),
            NavRoute("/logs", Icons.AutoMirrored.Filled.Article, "用户日志"),
        )
        CustomBottomNav(navRoutes[pagerState.currentPage].path, navRoutes) { path ->
            scope.launch {
                pagerState.animateScrollToPage(navRoutes.indexOfFirst { it.path == path })
            }
        }
    }) { paddingValues ->
        Column(Modifier.padding(bottom = paddingValues.calculateBottomPadding())) {
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
    val tabs = listOf("基本信息", "加入的社区", "加入的聊天室", "收到的头衔", "创建的文件")
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    Column {
        PrimaryTabRow(pagerState.currentPage) {
            tabs.forEachIndexed { i, label ->
                Tab(selected = pagerState.currentPage == i, onClick = {
                    scope.launch { pagerState.scrollToPage(i) }
                }) {
                    Text(label, modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
        HorizontalPager(pagerState) { index ->
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
    val vm = com.storyteller_f.a.panel.common.createPanelJoinedCommunitiesViewModel(uid)
    StateView(vm) { items ->
        LazyColumn {
            pagingItems(items, key = { it.id }) { index ->
                val info = items[index]
                if (info != null) {
                    ListItem(
                        headlineContent = { Text(info.name) },
                        overlineContent = { Text(info.aid) }
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
    val vm = com.storyteller_f.a.panel.common.createPanelJoinedRoomsViewModel(uid)
    StateView(vm) { items ->
        LazyColumn {
            pagingItems(items, key = { it.id }) { index ->
                val info = items[index]
                if (info != null) {
                    ListItem(
                        headlineContent = { Text(info.name) },
                        supportingContent = {
                            val creator = info.creator.toString()
                            Text(listOf(creator).filter { it.isNotEmpty() }.joinToString(" • "))
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
private fun UserReceivedTitlesSection(uid: PrimaryKey) {
    val vm = com.storyteller_f.a.panel.common.createPanelReceivedTitlesViewModel(uid)
    StateView(vm) { items ->
        LazyColumn {
            pagingItems(items, key = { it.id }) { index ->
                val info = items[index]
                if (info != null) {
                    ListItem(
                        headlineContent = { Text(info.name) },
                        supportingContent = { Text(info.type.name) }
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
    StateView(vm) { items ->
        LazyColumn {
            pagingItems(items, key = { it.id }) { index ->
                val info = items[index]
                if (info != null) {
                    ListItem(
                        headlineContent = { Text(info.name) },
                        supportingContent = { Text(info.contentType) }
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
    StateView(vm) { items ->
        LazyColumn {
            pagingItems(items, key = { it.id }) { index ->
                val info = items[index]
                if (info != null) {
                    ListItem(
                        headlineContent = { Text(info.type.name) },
                        supportingContent = { Text(
                            "对象: ${info.objectType}(${info.objectId}) • 时间: ${info.createdTime}"
                        ) }
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
    val vm = createPanelUserViewModel(uid)
    StateView(vm.handler) { u ->
        Column(Modifier.padding(16.dp)) {
            Text("昵称: ${u.nickname}")
            Text("地址: ${u.address}")
            val aidText = u.aid ?: "无"
            Text("AID: $aidText")
        }
    }
}
