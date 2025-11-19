package com.storyteller_f.a.panel.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.storyteller_f.a.app.core.components.CustomBottomNav
import com.storyteller_f.a.app.core.components.NavRoute
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.panel.common.createPanelTopicViewModel
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicDetailPage(id: PrimaryKey) {
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { TopicTopBar(id) }, bottomBar = {
        val navRoutes = listOf(
            NavRoute("/info", Icons.Default.Topic, "Info"),
            NavRoute("/logs", Icons.AutoMirrored.Filled.Article, "Logs"),
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
                    0 -> TopicInfoTabs(id)
                    else -> TopicLogsTab()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicTopBar(id: PrimaryKey) {
    val vm = createPanelTopicViewModel(id)
    val info by vm.handler.data.collectAsState(null)
    val title = listOf("Topic Detail", info?.id?.toString() ?: "").filter { it.isNotBlank() }.joinToString(" • ")
    TopAppBar(title = { Text(title.ifBlank { "Topic Detail • $id" }) })
}

@Composable
private fun TopicInfoTabs(id: PrimaryKey) {
    val tabs = listOf("Basic info")
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
        HorizontalPager(pagerState, modifier = Modifier.weight(1f)) { _ ->
            TopicBasicInfoSection(id)
        }
    }
}

@Composable
private fun TopicBasicInfoSection(id: PrimaryKey) {
    val vm = createPanelTopicViewModel(id)
    StateView(vm.handler, modifier = Modifier.fillMaxSize()) { info ->
        Column(Modifier.padding(16.dp)) {
            Text(info.id.toString())
            Text(info.createdTime.toString())
            Text(info.parentType.name)
        }
    }
}

@Composable
private fun TopicLogsTab() {
    Column(Modifier.padding(16.dp)) { Text("None") }
}
