package com.storyteller_f.a.panel.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Group
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
import com.storyteller_f.a.panel.common.createPanelCommunityViewModel
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.launch

@Composable
fun CommunityDetailPage(id: PrimaryKey) {
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    Scaffold(bottomBar = {
        val navRoutes = listOf(
            NavRoute("/info", Icons.Default.Group, "Info"),
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
                    0 -> CommunityInfoTabs(id)
                    else -> CommunityLogsTab()
                }
            }
        }
    }
}

@Composable
private fun CommunityInfoTabs(id: PrimaryKey) {
    val tabs = listOf(
        "Basic info",
    )
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
        HorizontalPager(pagerState) { _ ->
            CommunityBasicInfoSection(id)
        }
    }
}

@Composable
private fun CommunityBasicInfoSection(id: PrimaryKey) {
    val vm = createPanelCommunityViewModel(id)
    StateView(vm.handler) { info ->
        Column(Modifier.padding(16.dp)) {
            Text(info.name)
            val aidText = info.aid
            if (aidText.isNotEmpty()) {
                Text(aidText)
            }
            val owner = info.owner.toString()
            val members = info.memberCount.toString()
            val policy = info.memberPolicy.name
            Text(listOf(owner, members, policy).filter { it.isNotEmpty() }.joinToString(" • "))
        }
    }
}

@Composable
private fun CommunityLogsTab() {
    LazyColumn {
        items(1) {
            ListItem(headlineContent = { Text("None") })
            HorizontalDivider()
        }
    }
}
