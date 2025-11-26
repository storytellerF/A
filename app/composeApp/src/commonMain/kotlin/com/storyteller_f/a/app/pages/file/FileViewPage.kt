package com.storyteller_f.a.app.pages.file

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.storyteller_f.a.app.LocalGlobalDialog
import com.storyteller_f.a.app.common.FileViewViewModel
import com.storyteller_f.a.app.common.createFileViewModel
import com.storyteller_f.a.app.core.components.AudioViewFullScreen
import com.storyteller_f.a.app.core.components.BaseSheet
import com.storyteller_f.a.app.core.components.ButtonNav
import com.storyteller_f.a.app.core.components.CenterBox
import com.storyteller_f.a.app.core.components.FileViewData
import com.storyteller_f.a.app.core.components.LocalToaster
import com.storyteller_f.a.app.core.components.PdfView
import com.storyteller_f.a.app.core.components.VideoViewFullScreen
import com.storyteller_f.a.app.core.components.globalLoader
import com.storyteller_f.a.app.core.components.request
import com.storyteller_f.a.app.ui.MaterialSymbolsOutlined
import com.storyteller_f.a.client.core.copy
import com.storyteller_f.shared.model.FileInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewPage(session: FileViewData) {
    when (session) {
        is FileViewData.Regular -> {
            val fileViewModel = createFileViewModel(session.fileId)
            RegularFileView(fileViewModel)
        }

        is FileViewData.Player -> {
            CenterBox {
                val remoteMediaItem = session.remoteMediaItem
                if (remoteMediaItem.contentType.startsWith("video")) {
                    VideoViewFullScreen(remoteMediaItem)
                } else {
                    AudioViewFullScreen(remoteMediaItem)
                }
            }
        }

        is FileViewData.LocalImage -> {
            Box {
                CoilZoomAsyncImage(
                    model = session.url,
                    contentDescription = "view image",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun RegularFileView(fileViewModel: FileViewViewModel) {
    val fileViewInfo1 by fileViewModel.handler.data.collectAsState()
    fileViewInfo1?.let { RegularFileViewInternal(it) }
}

@Composable
private fun RegularFileViewInternal(info: FileInfo) {
    when {
        info.contentType.startsWith("image") -> {
            ZoomImage(info)
        }

        info.contentType == FileInfo.PDF_CONTENT_TYPE -> {
            Column {
                PdfView(info.url, Modifier.weight(1f))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ZoomImage(fileInfo: FileInfo) {
    Box {
        var showSheet by remember {
            mutableStateOf(false)
        }
        val sheetState = rememberModalBottomSheetState()
        CoilZoomAsyncImage(
            model = globalLoader(fileInfo.url),
            contentDescription = "view image",
            modifier = Modifier.fillMaxSize(),
            onLongPress = {
                showSheet = true
            }
        )
        ImageSheet(fileInfo, showSheet, sheetState) {
            showSheet = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSheet(
    fileInfo: FileInfo,
    showSheet: Boolean,
    sheetState: SheetState,
    hideSheet: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val globalDialogController = LocalGlobalDialog.current
    BaseSheet(
        showSheet,
        sheetState,
        hideSheet
    ) {
        Column(modifier = Modifier.height(200.dp).padding(20.dp)) {
            ButtonNav(MaterialSymbolsOutlined.FileCopy, "copy") {
                scope.launch {
                    globalDialogController.useResult {
                        request {
                            copy(fileInfo.id)
                        }
                    }.onSuccess {
                        toaster.showMessage("done")
                    }
                }
            }
        }
    }
}
