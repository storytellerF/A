package com.storyteller_f.a.app.compose_app.pages.file

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.storyteller_f.a.app.compose_app.FileViewInfo
import com.storyteller_f.a.app.compose_app.LocalGlobalDialog
import com.storyteller_f.a.app.compose_app.components.AudioView
import com.storyteller_f.a.app.core.components.BaseSheet
import com.storyteller_f.a.app.core.components.ButtonNav
import com.storyteller_f.a.app.core.components.PdfView
import com.storyteller_f.a.app.compose_app.components.VideoView
import com.storyteller_f.a.app.compose_app.ui.MaterialSymbolsOutlined
import com.storyteller_f.a.app.core.components.CenterBox
import com.storyteller_f.a.app.core.components.LocalToaster
import com.storyteller_f.a.app.core.components.globalLoader
import com.storyteller_f.a.app.core.components.request
import com.storyteller_f.a.client.core.copy
import com.storyteller_f.shared.model.FileInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewPage(session: FileViewInfo) {
    when (session) {
        is FileViewInfo.Regular -> {
            when {
                session.fileInfo.contentType.startsWith("image") -> {
                    ZoomImage(session)
                }
                session.fileInfo.contentType == FileInfo.PDF_CONTENT_TYPE -> {
                    Column {
                        PdfView(session.fileInfo.url)
                    }
                }
            }
        }

        is FileViewInfo.Player -> {
            CenterBox {
                val remoteMediaItem = session.obj
                if (remoteMediaItem.contentType.startsWith("video")) {
                    VideoView(remoteMediaItem, true)
                } else {
                    AudioView(remoteMediaItem, true)
                }
            }
        }

        is FileViewInfo.LocalImage -> {
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
@OptIn(ExperimentalMaterial3Api::class)
private fun ZoomImage(session: FileViewInfo.Regular) {
    Box {
        var showSheet by remember {
            mutableStateOf(false)
        }
        val sheetState = rememberModalBottomSheetState()
        CoilZoomAsyncImage(
            model = globalLoader(session.fileInfo.url),
            contentDescription = "view image",
            modifier = Modifier.fillMaxSize(),
            onLongPress = {
                showSheet = true
            }
        )
        ImageSheet(session, showSheet, sheetState) {
            showSheet = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSheet(
    session: FileViewInfo.Regular,
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
                            copy(session.fileInfo.id)
                        }
                    }.onSuccess {
                        toaster.showMessage("done")
                    }
                }
            }
        }
    }
}
