package com.storyteller_f.a.panel.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.storyteller_f.a.app.core.components.CustomBottomNav
import com.storyteller_f.a.app.core.components.NavRoute
import com.storyteller_f.a.app.core.components.PdfView
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.globalLoader
import com.storyteller_f.a.panel.LocalPanelNav
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
        Column(Modifier.padding(paddingValues)) {
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
    val nav = LocalPanelNav.current
    TopAppBar(
        title = { Text(title.ifBlank { "File Detail • $id" }) },
        navigationIcon = {
            IconButton(onClick = { nav.open() }) {
                Icon(Icons.Default.Menu, null)
            }
        }
    )
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
    val nav = LocalPanelNav.current
    StateView(vm.handler, modifier = Modifier.fillMaxSize()) { info ->
        Column(Modifier.padding(16.dp)) {
            Text(info.name)
            Text(info.contentType)
            Box(modifier = Modifier.heightIn(max = 200.dp)) {
                when {
                    info.contentType.startsWith("image") -> {
                        CoilZoomAsyncImage(
                            model = globalLoader(info.url),
                            contentDescription = "preview image",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    info.contentType == com.storyteller_f.shared.model.FileInfo.PDF_CONTENT_TYPE -> {
                        PdfView(info.url, Modifier.fillMaxSize())
                    }
                    info.contentType.startsWith("video") || info.contentType.startsWith("audio") -> {
                        Text("该文件可全屏预览播放")
                    }
                }
            }
            Button(onClick = {
                nav.gotoFilePreview(info.id, info.url, info.contentType, info.name)
            }) {
                Text("全屏预览")
            }
        }
    }
}

@Composable
private fun FileLogsTab() {
    Column(Modifier.padding(16.dp)) { Text("None") }
}
