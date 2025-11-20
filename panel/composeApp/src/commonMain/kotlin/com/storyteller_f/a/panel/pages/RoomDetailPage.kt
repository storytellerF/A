package com.storyteller_f.a.panel.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.FilePresent
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.core.components.CustomBottomNav
import com.storyteller_f.a.app.core.components.NavRoute
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.pagingItems
import com.storyteller_f.a.panel.LocalPanelNav
import com.storyteller_f.a.panel.common.createPanelRoomFilesViewModel
import com.storyteller_f.a.panel.common.createPanelRoomMembersViewModel
import com.storyteller_f.a.panel.common.createPanelRoomViewModel
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailPage(id: PrimaryKey) {
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { RoomTopBar(id) }, bottomBar = {
        val navRoutes = listOf(
            NavRoute("/info", Icons.Default.People, "Info"),
            NavRoute("/logs", Icons.AutoMirrored.Filled.Article, "Logs"),
        )
        CustomBottomNav(navRoutes[pagerState.currentPage].path, navRoutes) { path ->
            scope.launch {
                pagerState.animateScrollToPage(navRoutes.indexOfFirst { it.path == path })
            }
        }
    }) { paddingValues ->
        val direction = LocalLayoutDirection.current
        Column(
            Modifier.padding(
                top = paddingValues.calculateTopPadding(),
                start = paddingValues.calculateStartPadding(direction),
                end = paddingValues.calculateEndPadding(direction)
            )
        ) {
            HorizontalPager(pagerState) { pageIndex ->
                when (pageIndex) {
                    0 -> RoomInfoTabs(id)
                    else -> RoomLogsTab()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomTopBar(id: PrimaryKey) {
    val vm = createPanelRoomViewModel(id)
    val info by vm.handler.data.collectAsState(null)
    val title = listOf("Room Detail", info?.name ?: "", info?.aid ?: "").filter { it.isNotBlank() }
        .joinToString(" • ")
    val nav = LocalPanelNav.current
    TopAppBar(
        title = { Text(title.ifBlank { "Room Detail • $id" }) },
        navigationIcon = {
            IconButton(onClick = { nav.open() }) {
                Icon(Icons.Default.Menu, null)
            }
        }
    )
}

@Composable
private fun RoomInfoTabs(id: PrimaryKey) {
    val tabs = listOf("Basic info", "Members", "Files")
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
        HorizontalPager(pagerState, modifier = Modifier.weight(1f)) { pageIndex ->
            when (pageIndex) {
                0 -> RoomBasicInfoSection(id)
                1 -> RoomMembersTab(id)
                else -> RoomFilesTab(id)
            }
        }
    }
}

@Composable
private fun RoomBasicInfoSection(id: PrimaryKey) {
    val vm = createPanelRoomViewModel(id)
    StateView(vm.handler, modifier = Modifier.fillMaxSize()) { info ->
        Column(Modifier.padding(16.dp)) {
            Text(info.name)
            val creator = info.creator.toString()
            val isPrivate = info.isPrivate.toString()
            Text(listOf(creator, isPrivate).filter { it.isNotEmpty() }.joinToString(" • "))
        }
    }
}

@Composable
private fun RoomLogsTab() {
    Column(Modifier.padding(16.dp)) { Text("None") }
}

@Composable
private fun RoomMembersTab(id: PrimaryKey) {
    val nav = LocalPanelNav.current
    val vm = createPanelRoomMembersViewModel(id)
    StateView(vm, modifier = Modifier.fillMaxSize()) { items ->
        LazyColumn {
            pagingItems(items, key = { it.userInfo.id }) {
                val m = items[it]
                if (m != null) {
                    ListItem(
                        modifier = Modifier.clickable { nav.gotoUserDetail(m.userInfo.id) },
                        headlineContent = { Text(m.userInfo.nickname) },
                        supportingContent = {
                            val joined = m.joinedTime.toString()
                            val status = m.status.name
                            Text(listOf(joined, status).joinToString(" • "))
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun RoomFilesTab(id: PrimaryKey) {
    val nav = LocalPanelNav.current
    val vm = createPanelRoomFilesViewModel(id)
    StateView(vm, modifier = Modifier.fillMaxSize()) { items ->
        LazyColumn {
            pagingItems(items, key = { it.id }) {
                val info = items[it]
                if (info != null) {
                    ListItem(
                        modifier = Modifier.clickable { nav.gotoFileDetail(info.id) },
                        headlineContent = { Text(info.name) },
                        supportingContent = {
                            val ct = info.contentType
                            val s = listOf(ct).filter { it.isNotEmpty() }.joinToString(" • ")
                            Text(s)
                        },
                        leadingContent = { Icon(Icons.Default.FilePresent, null) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
