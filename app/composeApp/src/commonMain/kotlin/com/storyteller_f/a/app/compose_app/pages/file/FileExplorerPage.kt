package com.storyteller_f.a.app.compose_app.pages.file

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.storyteller_f.a.app.compose_app.LocalAppNavFactory
import com.storyteller_f.a.app.compose_app.LocalClientFileProvider
import com.storyteller_f.a.app.compose_app.LocalUserInfo
import com.storyteller_f.a.app.compose_app.common.createMediaListViewModel
import com.storyteller_f.a.app.compose_app.common.createUploadViewModel
import com.storyteller_f.a.app.compose_app.common.getDownloadListViewModel
import com.storyteller_f.a.app.compose_app.common.getQuotaViewModel
import com.storyteller_f.a.app.compose_app.components.BaseSheet
import com.storyteller_f.a.app.compose_app.pages.CustomBottomNav
import com.storyteller_f.a.app.compose_app.pages.CustomRailNav
import com.storyteller_f.a.app.compose_app.pages.NavRoute
import com.storyteller_f.a.app.compose_app.pages.UploadItem
import com.storyteller_f.a.app.compose_app.pages.community.DownloadInfoTable
import com.storyteller_f.a.app.compose_app.pages.community.DownloadStatusViewInternal
import com.storyteller_f.a.app.compose_app.pages.community.getPercent
import com.storyteller_f.a.app.compose_app.pages.topic.FileCell
import com.storyteller_f.a.app.compose_app.pages.topic.FileIcon
import com.storyteller_f.a.app.compose_app.pages.topic.PlatformClientFile
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.bottomAppending
import com.storyteller_f.a.app.core.components.pagingItems
import com.storyteller_f.a.app.core.components.topPrepend
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.QuotaInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.storage.DownloadInfo
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import nl.jacobras.humanreadable.HumanReadable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun FileExplorerPage() {
    val my = LocalUserInfo.current ?: return
    val size = calculateWindowSizeClass()
    val mediaTarget = my.id ob ObjectType.USER
    when (size.widthSizeClass) {
        WindowWidthSizeClass.Compact -> FileExplorerCompatPageInternal(mediaTarget)
        else -> FileExplorerNonCompatPageInternal(mediaTarget)
    }
}

@Composable
private fun fileNavRoutes(): List<NavRoute> {
    return listOf(
        NavRoute(
            "/uploaded",
            Icons.Filled.Folder,
            "Uploaded"
        ),
        NavRoute(
            "/upload-record",
            Icons.Filled.CloudUpload,
            "Upload"
        ),
        NavRoute(
            "/download-record",
            Icons.Filled.Download,
            "Download"
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileExplorerCompatPageInternal(mediaTarget: ObjectTuple) {
    val pagerState = rememberPagerState { 3 }
    val navRoutes = fileNavRoutes()
    var showQuota by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    Scaffold(floatingActionButton = {
        when (pagerState.currentPage) {
            0 -> {
                FloatingActionButton({ showQuota = true }) {
                    Icon(Icons.Filled.Info, "quota info")
                }
            }

            1 -> {
                UploadFileActionButton()
            }
        }
    }, bottomBar = {
        val scope = rememberCoroutineScope()
        CustomBottomNav(navRoutes[pagerState.currentPage].path, navRoutes) { path ->
            scope.launch {
                pagerState.animateScrollToPage(navRoutes.indexOfFirst { it.path == path })
            }
        }
    }) { paddingValues ->
        FileExplorerPager(pagerState, mediaTarget, paddingValues)
    }
    QuotaSheet(showQuota, sheetState, { showQuota = false }, mediaTarget)
}

@Composable
private fun FileExplorerPager(
    pagerState: PagerState,
    mediaTarget: ObjectTuple,
    paddingValues: PaddingValues
) {
    HorizontalPager(
        pagerState,
        modifier = Modifier.padding(top = paddingValues.calculateTopPadding())
    ) { index ->
        when (index) {
            0 -> UploadedTab(mediaTarget = mediaTarget)
            1 -> UploadRecordTab()
            else -> DownloadRecordTab()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileExplorerNonCompatPageInternal(mediaTarget: ObjectTuple) {
    val navRoutes = fileNavRoutes()
    val navigator = rememberNavController()
    val current by navigator.currentBackStackEntryFlow.collectAsState(null)
    var showQuota by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    Scaffold(floatingActionButton = {
        when (current?.destination?.route) {
            "/uploaded" -> {
                FileQuotaActionButton({ -> showQuota = true })
            }

            "/upload-record" -> {
                UploadFileActionButton()
            }
        }
    }) { paddingValues ->
        Row(modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())) {
            CustomRailNav(current?.destination?.route, navRoutes) {
                navigator.navigate(it, NavOptions.Builder().setLaunchSingleTop(true).build())
            }
            Column(modifier = Modifier) {
                NavHost(navigator, "/uploaded") {
                    composable("/uploaded") {
                        UploadedTab(mediaTarget)
                    }
                    composable("/upload-record") {
                        UploadRecordTab()
                    }
                    composable("/download-record") {
                        DownloadRecordTab()
                    }
                }
            }
        }
    }
    QuotaSheet(showQuota, sheetState, { showQuota = false }, mediaTarget)
}

@Composable
private fun FileQuotaActionButton(onClick: () -> Unit) {
    FloatingActionButton(onClick) {
        Icon(Icons.Filled.Info, "quota info")
    }
}

@Composable
private fun UploadFileActionButton() {
    val fileProvider = LocalClientFileProvider.current
    val scope = rememberCoroutineScope()
    FloatingActionButton({
        scope.launch {
            val f = FileKit.openFilePicker()
            if (f != null) {
                fileProvider.getUploader()?.upload(persistentListOf(PlatformClientFile(f)))
            }
        }
    }) {
        Icon(Icons.Filled.CloudUpload, "upload file")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadedTab(mediaTarget: ObjectTuple) {
    val vm = createMediaListViewModel(mediaTarget)
    val appNavFactory = LocalAppNavFactory.current
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        StateView(vm) { pagingItems ->
            LazyColumn(contentPadding = PaddingValues(10.dp)) {
                topPrepend(pagingItems.loadState)
                pagingItems(pagingItems, key = { it.id }) {
                    val item: FileInfo? = pagingItems[it]
                    FileCell(item) { items ->
                        val first = items.firstOrNull()
                        if (first != null) {
                            appNavFactory.newAppNav().gotoMedia(first)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                }
                bottomAppending(pagingItems.loadState)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuotaSheet(
    showSheet: Boolean,
    sheetState: SheetState,
    hideSheet: () -> Unit,
    mediaTarget: ObjectTuple
) {
    BaseSheet(showSheet, sheetState, hideSheet) {
        val quotaVm = getQuotaViewModel(mediaTarget)
        StateView(quotaVm.handler) { q: QuotaInfo ->
            Column(modifier = Modifier.padding(20.dp)) {
                val totalText = HumanReadable.fileSize(q.total)
                val usedText = HumanReadable.fileSize(q.used)
                Text("总空间: $totalText")
                Text("已用空间: $usedText")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { q.used.toFloat() / q.total.coerceAtLeast(1) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.padding(8.dp))
                    val percent = if (q.total > 0) (q.used * 100f / q.total) else 0f
                    Text("${percent.toInt()}%")
                }
            }
        }
    }
}

@Composable
private fun UploadRecordTab() {
    val my = LocalUserInfo.current ?: return
    val viewModel = createUploadViewModel(my.id)
    val pagingItems = viewModel.flow.collectAsLazyPagingItems()
    LazyColumn(
        contentPadding = PaddingValues(20.dp)
    ) {
        items(pagingItems.itemSnapshotList.size, key = pagingItems.itemKey { it.id }) {
            val file = pagingItems[it]
            UploadItem(file)
            if (it != pagingItems.itemSnapshotList.size - 1) {
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadRecordTab() {
    val vm = getDownloadListViewModel()
    val items = vm.flow.collectAsLazyPagingItems()
    LazyColumn(
        contentPadding = PaddingValues(20.dp)
    ) {
        pagingItems(items, key = { it.fileInfo.id }) {
            val d: DownloadInfo? = items[it]
            DownloadRecordItem(d)
            if (it != items.itemSnapshotList.size - 1) {
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun UploadIcon(contentType: String) {
    val modifier = Modifier.padding(4.dp)
    when {
        contentType.startsWith("audio") -> Icon(
            Icons.Default.AudioFile,
            contentDescription = "audio"
        )

        contentType.startsWith("video") -> Icon(
            Icons.Default.VideoFile,
            contentDescription = "video"
        )

        else -> Icon(Icons.Default.AttachFile, contentDescription = "file", modifier = modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadRecordItem(d: DownloadInfo?) {
    d ?: return
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable {
                showSheet = true
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FileIcon(d.fileInfo)
        Column(modifier = Modifier.weight(1f)) {
            Text(d.fileInfo.name)
            Row {
                Text(HumanReadable.fileSize(d.fileInfo.size))
                Spacer(modifier = Modifier.weight(1f))
                Text(d.status.name)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(progress = {
                    (d.progress.toFloat() / (if (d.total == 0L) 1 else d.total)).coerceIn(
                        0f,
                        1f
                    )
                }, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Text(d.getPercent())
            }
        }
        DownloadStatusViewInternal(d)
    }
    DownloadRecordSheet(d, showSheet, sheetState) {
        showSheet = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadRecordSheet(
    info: DownloadInfo,
    showSheet: Boolean,
    sheetState: SheetState,
    hideSheet: () -> Unit,
) {
    BaseSheet(showSheet, sheetState, hideSheet) {
        Column(modifier = Modifier.padding(20.dp)) {
            DownloadInfoTable(info, info.fileInfo)
        }
    }
}
