package com.storyteller_f.a.panel.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.FilePresent
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
import com.storyteller_f.a.panel.common.createPanelFileViewModel
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDetailPage(id: PrimaryKey) {
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { FileTopBar(id) }, bottomBar = {
        val navRoutes = listOf(
            NavRoute("/info", Icons.Default.FilePresent, "Info"),
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
                    0 -> FileInfoTabs(id)
                    else -> FileLogsTab()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileTopBar(id: PrimaryKey) {
    val vm = createPanelFileViewModel(id)
    val info by vm.handler.data.collectAsState(null)
    val title = listOf("File Detail", info?.name ?: "").filter { it.isNotBlank() }.joinToString(" • ")
    TopAppBar(title = { Text(title.ifBlank { "File Detail • $id" }) })
}

@Composable
private fun FileInfoTabs(id: PrimaryKey) {
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
            FileBasicInfoSection(id)
        }
    }
}

@Composable
private fun FileBasicInfoSection(id: PrimaryKey) {
    val vm = createPanelFileViewModel(id)
    StateView(vm.handler, modifier = Modifier.fillMaxSize()) { info ->
        Column(Modifier.padding(16.dp)) {
            Text(info.name)
            Text(info.contentType)
        }
    }
}

@Composable
private fun FileLogsTab() {
    Column(Modifier.padding(16.dp)) { Text("None") }
}
