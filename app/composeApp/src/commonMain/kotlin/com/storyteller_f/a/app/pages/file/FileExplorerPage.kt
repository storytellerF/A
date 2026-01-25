package com.storyteller_f.a.app.pages.file

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotStarted
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.paging.compose.collectAsLazyPagingItems
import com.storyteller_f.a.app.LocalAppNavFactory
import com.storyteller_f.a.app.LocalClientFileProvider
import com.storyteller_f.a.app.LocalGlobalDialog
import com.storyteller_f.a.app.LocalUiViewModel
import com.storyteller_f.a.app.LocalUserInfo
import com.storyteller_f.a.app.common.DownloadViewModel
import com.storyteller_f.a.app.common.UploadDetailViewModel
import com.storyteller_f.a.app.common.createMediaListViewModel
import com.storyteller_f.a.app.common.createUploadViewModel
import com.storyteller_f.a.app.common.getDownloadListViewModel
import com.storyteller_f.a.app.common.getDownloadViewModel
import com.storyteller_f.a.app.common.getQuotaViewModel
import com.storyteller_f.a.app.common.getUploadViewModel
import com.storyteller_f.a.app.core.CoreStrings
import com.storyteller_f.a.app.core.components.BaseSheet
import com.storyteller_f.a.app.core.components.CustomBottomNav
import com.storyteller_f.a.app.core.components.CustomRailNav
import com.storyteller_f.a.app.core.components.FileIcon
import com.storyteller_f.a.app.core.components.NavRoute
import com.storyteller_f.a.app.core.components.SheetContainer
import com.storyteller_f.a.app.core.components.SimpleMessageWithButton
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.bottomAppending
import com.storyteller_f.a.app.core.components.catchingResult
import com.storyteller_f.a.app.core.components.pagingItems
import com.storyteller_f.a.app.core.components.request
import com.storyteller_f.a.app.core.components.topPrepend
import com.storyteller_f.a.app.pages.search.CustomSearchBar
import com.storyteller_f.a.app.pages.search.SearchScope
import com.storyteller_f.a.app.pages.topic.PlatformClientFile
import com.storyteller_f.a.app.ui.theme.AppTheme
import com.storyteller_f.a.app.utils.ClientFile
import com.storyteller_f.a.client.core.UploadData
import com.storyteller_f.a.client.core.abortChunkUpload
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.QuotaInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.now
import com.storyteller_f.storage.DownloadInfo
import com.storyteller_f.storage.DownloadStatus
import com.storyteller_f.storage.UploadCollection
import com.storyteller_f.storage.UploadInfo
import com.storyteller_f.storage.UploadStatus
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.windedge.table.DataTable
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import nl.jacobras.humanreadable.HumanReadable
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter
import org.jetbrains.compose.ui.tooling.preview.PreviewParameterProvider
import kotlin.math.pow
import kotlin.math.round

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun FileExplorerPage(mediaTarget: ObjectTuple) {
    val size = calculateWindowSizeClass()
    when (size.widthSizeClass) {
        WindowWidthSizeClass.Compact -> FileExplorerCompatPageInternal(mediaTarget)
        else -> FileExplorerNonCompatPageInternal(mediaTarget)
    }
}

@Composable
private fun fileNavRoutes(mediaTarget: ObjectTuple): List<NavRoute> {
    val list = mutableListOf(
        NavRoute("/uploaded", Icons.Filled.Folder, "Uploaded"),
        NavRoute("/upload-record", Icons.Filled.CloudUpload, "Upload")
    )
    if (mediaTarget.objectType == ObjectType.USER) {
        list.add(NavRoute("/download-record", Icons.Filled.Download, "Download"))
    }
    return list
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileExplorerCompatPageInternal(mediaTarget: ObjectTuple) {
    val navRoutes = fileNavRoutes(mediaTarget)
    val pagerState = rememberPagerState { navRoutes.size }
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
    }) {
        Column {
            CustomSearchBar(
                scope = SearchScope.UploadedFiles(mediaTarget.objectId, mediaTarget.objectType),
                leadingIcon = {}
            )
            FileExplorerPager(pagerState, mediaTarget, PaddingValues(0.dp))
        }
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
            0 -> UploadedPage(mediaTarget)
            1 -> UploadRecordPage(mediaTarget)
            else -> DownloadRecordPage()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileExplorerNonCompatPageInternal(mediaTarget: ObjectTuple) {
    val navRoutes = fileNavRoutes(mediaTarget)
    val navigator = rememberNavController()
    val current by navigator.currentBackStackEntryFlow.collectAsState(null)
    var showQuota by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    Scaffold(floatingActionButton = {
        when (current?.destination?.route) {
            "/uploaded" -> {
                FileQuotaActionButton({ showQuota = true })
            }

            "/upload-record" -> {
                UploadFileActionButton()
            }
        }
    }) {
        Row {
            CustomRailNav(current?.destination?.route, navRoutes) {
                navigator.navigate(it, NavOptions.Builder().setLaunchSingleTop(true).build())
            }
            Column(modifier = Modifier.weight(1f)) {
                CustomSearchBar(
                    scope = SearchScope.UploadedFiles(mediaTarget.objectId, mediaTarget.objectType),
                    leadingIcon = {}
                )
                NavHost(navigator, "/uploaded") {
                    composable("/uploaded") {
                        UploadedPage(mediaTarget)
                    }
                    composable("/upload-record") {
                        UploadRecordPage(mediaTarget)
                    }
                    composable("/download-record") {
                        DownloadRecordPage()
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
    val globalDialogController = LocalGlobalDialog.current
    val scope = rememberCoroutineScope()
    FloatingActionButton({
        globalDialogController.catchingResult(scope) {
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
private fun UploadedPage(mediaTarget: ObjectTuple) {
    val vm = createMediaListViewModel(mediaTarget)
    val appNavFactory = LocalAppNavFactory.current
    Column(modifier = Modifier.fillMaxSize()) {
        StateView(vm, modifier = Modifier.fillMaxSize()) { pagingItems ->
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
                val rec = q.extensions?.uploadRecord
                if (rec != null) {
                    Spacer(modifier = Modifier.padding(8.dp))
                    Text("上传记录: ${rec.name}")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = {
                                (rec.progress.toFloat() / rec.total.coerceAtLeast(1)).coerceIn(
                                    0f,
                                    1f
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                        val uploadPercent = if (rec.total > 0) (rec.progress * 100f / rec.total) else 0f
                        Text("${uploadPercent.toInt()}%")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(HumanReadable.fileSize(rec.progress))
                        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                        Text("/ ${HumanReadable.fileSize(rec.total)}")
                        Spacer(modifier = Modifier.weight(1f))
                        Text(rec.status.name)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("分片大小: ${HumanReadable.fileSize(rec.chunkSize)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadRecordPage(mediaTarget: ObjectTuple) {
    val viewModel = createUploadViewModel(mediaTarget.objectId)
    StateView(viewModel, modifier = Modifier.fillMaxSize()) { items ->
        LazyColumn(
            contentPadding = PaddingValues(20.dp)
        ) {
            pagingItems(items, key = { it.id }) {
                val file = items[it]
                UploadItem(file)
                if (it != items.itemSnapshotList.size - 1) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadRecordPage() {
    val vm = getDownloadListViewModel()
    val items = vm.flow.collectAsLazyPagingItems()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp)
    ) {
        pagingItems(items, key = { it.fileInfo.id }) {
            DownloadRecordItem(items[it])
            if (it != items.itemSnapshotList.size - 1) {
                HorizontalDivider()
            }
        }
    }
}

class DownloadRecordItemPreviewProvider : PreviewParameterProvider<DownloadInfo?> {
    override val values: Sequence<DownloadInfo?>
        get() = sequenceOf(
            DownloadInfo(
                id = 1000L,
                fileInfo = FileInfo(
                    id = 1L,
                    url = "https://example.com/files/sample1.png",
                    fullName = "images/sample1.png",
                    contentType = "image/png",
                    size = 5_000_000,
                    name = "sample1.png",
                    owner = 1001L,
                    ownerType = ObjectType.USER,
                    lastModified = now(),
                    dimension = null
                ),
                status = DownloadStatus.DOWNLOADING,
                message = "正在下载…",
                path = "/downloads/sample1.png",
                progress = 2_500_000,
                total = 5_000_000
            ),
            DownloadInfo(
                id = 2000L,
                fileInfo = FileInfo(
                    id = 2L,
                    url = "https://example.com/files/sample2.mp4",
                    fullName = "videos/sample2.mp4",
                    contentType = "video/mp4",
                    size = 120_000_000,
                    name = "sample2.mp4",
                    owner = 1002L,
                    ownerType = ObjectType.USER,
                    lastModified = now(),
                    dimension = null
                ),
                status = DownloadStatus.DOWNLOADED,
                message = "下载完成",
                path = "/downloads/sample2.mp4",
                progress = 120_000_000,
                total = 120_000_000
            ),
            DownloadInfo(
                id = 3000L,
                fileInfo = FileInfo(
                    id = 3L,
                    url = "https://example.com/files/sample3.pdf",
                    fullName = "docs/sample3.pdf",
                    contentType = FileInfo.PDF_CONTENT_TYPE,
                    size = 2_000_000,
                    name = "sample3.pdf",
                    owner = 1003L,
                    ownerType = ObjectType.USER,
                    lastModified = now(),
                    dimension = null
                ),
                status = DownloadStatus.DOWNLOAD_FAILED,
                message = "网络错误，稍后重试",
                path = "/downloads/sample3.pdf",
                progress = 500_000,
                total = 2_000_000
            ),
            DownloadInfo(
                id = 4000L,
                fileInfo = FileInfo(
                    id = 4L,
                    url = "https://example.com/files/sample4.m3u8",
                    fullName = "streams/sample4.m3u8",
                    contentType = FileInfo.M3U8_MIMETYPE,
                    size = 50_000_000,
                    name = "sample4.m3u8",
                    owner = 1004L,
                    ownerType = ObjectType.USER,
                    lastModified = now(),
                    dimension = null
                ),
                status = DownloadStatus.PROCESSED,
                message = "已处理",
                path = "/downloads/sample4.m3u8",
                progress = 50_000_000,
                total = 50_000_000
            ),
            DownloadInfo(
                id = 5000L,
                fileInfo = FileInfo(
                    id = 5L,
                    url = "https://example.com/files/sample5.jpg",
                    fullName = "images/sample5.jpg",
                    contentType = "image/jpeg",
                    size = 3_500_000,
                    name = "sample5.jpg",
                    owner = 1005L,
                    ownerType = ObjectType.USER,
                    lastModified = now(),
                    dimension = null
                ),
                status = DownloadStatus.PROCESS_FAILED,
                message = "处理失败：文件损坏",
                path = "/downloads/sample5.jpg",
                progress = 3_500_000,
                total = 3_500_000
            )
        )
}

@Preview
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadRecordItem(@PreviewParameter(DownloadRecordItemPreviewProvider::class) d: DownloadInfo?) {
    d ?: return
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                showSheet = true
            }
            .padding(vertical = 8.dp),
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
                    getDownloadProgress(d)
                }, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                FixedProgress(d.getPercent())
            }
        }
        DownloadStatusButton(d)
    }
    DownloadInfoPage(showSheet, sheetState, d.fileInfo.id) {
        showSheet = false
    }
}

private fun getDownloadProgress(d: DownloadInfo): Float =
    (d.progress.toFloat() / (if (d.total == 0L) 1 else d.total)).coerceIn(
        0f,
        1f
    )

@Composable
private fun DownloadStatusButton(d: DownloadInfo?) {
    d ?: return
    val fileProvider = LocalClientFileProvider.current
    val globalDialogController = LocalGlobalDialog.current
    val scope = rememberCoroutineScope()
    // 根据下载状态显示操作按钮
    when (d.status) {
        DownloadStatus.DOWNLOADING -> {
            IconButton(onClick = {
                globalDialogController.catchingResult(scope) {
                    fileProvider.getDownloader()?.pause(d.fileInfo.id)
                }
            }) {
                Icon(Icons.Default.Pause, contentDescription = "pause")
            }
        }

        DownloadStatus.PAUSED,
        DownloadStatus.DOWNLOAD_FAILED,
        DownloadStatus.PROCESS_FAILED,
        DownloadStatus.DOWNLOADED -> {
            IconButton(onClick = {
                globalDialogController.catchingResult(scope) {
                    fileProvider.getDownloader()?.resume(d.fileInfo.id)
                }
            }) {
                Icon(Icons.Default.PlayCircle, contentDescription = "resume")
            }
        }

        DownloadStatus.PROCESSED -> {
            IconButton(onClick = { }) {
                Icon(Icons.Default.Done, contentDescription = "done")
            }
        }

        DownloadStatus.NOT_DOWNLOADED -> {
            IconButton({
                globalDialogController.catchingResult(scope) {
                    fileProvider.getDownloader()?.download(d.fileInfo)
                }
            }) {
                Icon(Icons.Default.NotStarted, "start")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadInfoPage(
    showSheet: Boolean,
    sheetState: SheetState,
    id: PrimaryKey,
    hideSheet: () -> Unit,
) {
    if (LocalInspectionMode.current) return
    val downloadViewModel = getDownloadViewModel(id)
    BaseSheet(showSheet, sheetState, hideSheet) {
        SheetContainer {
            Column(
                modifier = Modifier.heightIn(200.dp, 600.dp).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DownloadInfoPageInternal(downloadViewModel)
            }
        }
    }
}

@Composable
private fun DownloadInfoPageInternal(downloadViewModel: DownloadViewModel) {
    val downloadInfo by downloadViewModel.data.collectAsState(null)
    DownloadInfoTitle(downloadInfo)
    downloadInfo?.let {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LinearProgressIndicator(progress = {
                it.progress.toFloat() / it.total
            })
            FixedProgress(it.getPercent())
        }
    }
    DownloadInfoTable(downloadInfo)
}

fun DownloadInfo.getPercent(): String = "${(progress.toFloat() * 100 / total).roundToDecimalPlaces(2)} %"

@Composable
private fun DownloadInfoTitle(data: DownloadInfo?) {
    val it = data?.fileInfo
    rememberCoroutineScope()
    LocalGlobalDialog.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        LocalClientFileProvider.current
        Text(it?.name ?: "-", modifier = Modifier.weight(1f))
        DownloadStatusButton(data)
    }
}

@Composable
fun DownloadInfoTable(
    downloadInfo: DownloadInfo?
) {
    val fileInfo = downloadInfo?.fileInfo
    val tableData = remember(downloadInfo, fileInfo) {
        buildMap {
            put("Path", downloadInfo?.path)
            put("Size", fileInfo?.size?.let { HumanReadable.fileSize(it) })
            put("Status", downloadInfo?.status?.name)
            if (downloadInfo?.status == DownloadStatus.DOWNLOAD_FAILED) {
                put("Error", downloadInfo.message)
            }
            put("Message", downloadInfo?.message)
            put("Url", fileInfo?.url)
        }
    }
    DataTable(
        {
            headerBackground {
                Box(modifier = Modifier.background(color = MaterialTheme.colorScheme.primaryContainer))
            }
            column { Text("Key", color = MaterialTheme.colorScheme.onPrimaryContainer) }
            column { Text("Value", color = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
    ) {
        tableData.forEach { (key, value) ->
            row(modifier = Modifier) {
                cell {
                    Text(key)
                }
                cell {
                    value?.let {
                        SimpleMessageWithButton(it, key)
                    }
                }
            }
        }
    }
}

fun Float.roundToDecimalPlaces(decimals: Int): Float {
    val multiplier = 10.0f.pow(decimals)
    return round(this * multiplier) / multiplier
}

class UploadItemPreviewProvider : PreviewParameterProvider<UploadInfo> {
    override val values: Sequence<UploadInfo>
        get() = sequenceOf(
            UploadInfo.EMPTY.copy(
                id = 1,
                pathHash = "test",
                path = "text/plain",
                progress = 100,
                status = UploadStatus.SUCCESS,
                message = "message",
                name = "name",
                contentType = "image/png",
            ),
            UploadInfo.EMPTY.copy(
                id = 2,
                pathHash = "test",
                path = "text/plain",
                progress = 100,
                chunkProgress = 50,
                status = UploadStatus.FAILED,
                message = "message",
                name = "name",
                contentType = "image/png",
            ),
            UploadInfo.EMPTY.copy(
                id = 3,
                pathHash = "test",
                path = "text/plain",
                progress = 100,
                chunkProgress = 100,
                status = UploadStatus.NOT_UPLOADING,
                message = "message",
                name = "name",
                contentType = "image/png",
            ),
            UploadInfo.EMPTY.copy(
                id = 4,
                pathHash = "test",
                path = "text/plain",
                progress = 100,
                status = UploadStatus.PAUSED,
                message = "message",
                name = "name",
                contentType = "image/png",
            ),
            UploadInfo.EMPTY.copy(
                id = 5,
                pathHash = "test",
                path = "text/plain",
                progress = 100,
                status = UploadStatus.UPLOADING,
                message = "message",
                name = "name",
                contentType = "image/png",
            ),
        )
}

@Preview
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun UploadItem(@PreviewParameter(UploadItemPreviewProvider::class) uploadInfo: UploadInfo?) {
    uploadInfo ?: return
    var showSheet by remember { mutableStateOf(false) }
    var showDropdown by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    Row(
        modifier = Modifier
            .clickable { showSheet = true }
            .combinedClickable(
                onClick = { showSheet = true },
                onLongClick = { showDropdown = true }
            )
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        UploadIcon(contentType = uploadInfo.contentType)

        Column(modifier = Modifier.weight(1f)) {
            Text(uploadInfo.name)
            Row {
                Text(HumanReadable.fileSize(uploadInfo.total))
                Spacer(modifier = Modifier.weight(1f))
                Text(uploadInfo.status.name)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { uploadInfo.progress.toFloat() / uploadInfo.total },
                    modifier = Modifier.weight(1f)
                )
                Text(uploadInfo.getPercent())
            }
        }

        UploadStatusButton(uploadInfo)
    }

    DropdownMenu(
        expanded = showDropdown,
        onDismissRequest = { showDropdown = false }
    ) {
        if (uploadInfo.recordId != null) {
            CancelUploadButton(uploadInfo) { showDropdown = false }
        }
    }

    UploadInfoPage(showSheet, sheetState, uploadInfo.pathHash) {
        showSheet = false
    }
}

@Composable
fun CancelUploadButton(uploadInfo: UploadInfo, updateDropdown: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val globalDialogController = LocalGlobalDialog.current
    val current = LocalUiViewModel.current
    val state by current.instance.collectAsState()
    val modelStorage by state.database.collectAsState()
    val myUid = state.sessionManager.model.uid ?: return
    DropdownMenuItem(
        text = { Text("Abort Upload") },
        onClick = {
            updateDropdown(false)
            globalDialogController.catchingResult(scope) {
                uploadInfo.recordId?.let {
                    request {
                        abortChunkUpload(it)
                    }.getOrThrow()
                }
                modelStorage.upload.delete(UploadCollection(myUid), uploadInfo.pathHash)
            }
        },
        leadingIcon = {
            Icon(Icons.Default.Stop, contentDescription = CoreStrings.cancel())
        }
    )
}

@Composable
private fun UploadStatusButton(file: UploadInfo?) {
    file ?: return
    val provider = LocalClientFileProvider.current
    val scope = rememberCoroutineScope()
    val globalDialogController = LocalGlobalDialog.current
    when (file.status) {
        UploadStatus.SUCCESS -> {
            Icon(Icons.Default.Done, "done")
        }

        UploadStatus.FAILED -> {
            IconButton({
                globalDialogController.catchingResult(scope) {
                    provider.getUploader()?.resume(file.pathHash)
                }
            }) {
                Icon(Icons.Default.Refresh, "retry")
            }
        }

        UploadStatus.PAUSED, UploadStatus.NOT_UPLOADING -> {
            IconButton({
                globalDialogController.catchingResult(scope) {
                    provider.getUploader()?.resume(file.pathHash)
                }
            }) {
                Icon(Icons.Default.PlayCircle, "resume")
            }
        }

        UploadStatus.UPLOADING -> {
            IconButton({
                globalDialogController.catchingResult(scope) {
                    provider.getUploader()?.pause(file.pathHash)
                }
            }) {
                Icon(Icons.Default.Pause, "pause")
            }
        }
    }
}

fun ClientFile.getUploadDataFromClipFile() = UploadData(
    size,
    name,
    contentType
) {
    source()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadInfoPage(
    showSheet: Boolean,
    sheetState: SheetState,
    pathHash: String,
    hideSheet: () -> Unit,
) {
    if (LocalInspectionMode.current) return
    val my = LocalUserInfo.current ?: return
    val uploadViewModel = getUploadViewModel(pathHash, my.id)
    BaseSheet(showSheet, sheetState, hideSheet) {
        SheetContainer {
            Column(
                modifier = Modifier.heightIn(200.dp, 600.dp).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                UploadInfoPageInternal(uploadViewModel)
            }
        }
    }
}

@Composable
private fun UploadInfoPageInternal(uploadViewModel: UploadDetailViewModel) {
    val data by uploadViewModel.data.collectAsState(null)
    UploadInfoTitle(data)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LinearProgressIndicator(progress = {
            getUploadProgress(data)
        })
        Text(data.getPercent())
    }
    UploadInfoTable(data)
}

private fun getUploadProgress(info: UploadInfo?): Float {
    info ?: return 0f
    return (info.progress.toFloat() / (if (info.total == 0L) 1 else info.total)).coerceIn(
        0f,
        1f
    )
}

@Composable
private fun UploadInfoTitle(data: UploadInfo?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(data?.name ?: "-", modifier = Modifier.weight(1f))
        UploadStatusButton(data)
    }
}

@Composable
private fun UploadInfoTable(uploadInfo: UploadInfo?) {
    val tableData = remember(uploadInfo) {
        buildMap {
            put("Path", uploadInfo?.path)
            put("Hash", uploadInfo?.pathHash)
            put("Size", uploadInfo?.total?.let { HumanReadable.fileSize(it) })
            put("Status", uploadInfo?.status?.name)
            put("Message", uploadInfo?.message)
            put("Name", uploadInfo?.name)
            put("ContentType", uploadInfo?.contentType)
        }
    }
    DataTable(
        {
            headerBackground {
                Box(modifier = Modifier.background(color = MaterialTheme.colorScheme.primaryContainer))
            }
            column { Text("Key", color = MaterialTheme.colorScheme.onPrimaryContainer) }
            column { Text("Value", color = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
    ) {
        tableData.forEach { (key, value) ->
            row(modifier = Modifier) {
                cell {
                    Text(key)
                }
                cell {
                    value?.let {
                        SimpleMessageWithButton(it, key)
                    }
                }
            }
        }
    }
}

fun UploadInfo?.getPercent(): String {
    this ?: return "-"
    return "${
        (progress.toFloat() * 100 / (if (total == 0L) 1 else total)).roundToDecimalPlaces(
            2
        )
    } %"
}

class ProgressPreviewProvider : PreviewParameterProvider<String> {
    override val values: Sequence<String>
        get() = sequenceOf("0 %", "50 %", "89.99 %", "100.0 %")
}

@Preview
@Composable
fun FixedProgress(@PreviewParameter(ProgressPreviewProvider::class) percent: String) {
    AppTheme {
        Text(percent, modifier = Modifier.width(60.dp))
    }
}
