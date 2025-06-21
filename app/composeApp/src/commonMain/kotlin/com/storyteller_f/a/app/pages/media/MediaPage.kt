package com.storyteller_f.a.app.pages.media

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
import com.storyteller_f.a.app.LocalSessionManager
import com.storyteller_f.a.app.LocalToaster
import com.storyteller_f.a.app.MediaPlaySession
import com.storyteller_f.a.app.common.CenterBox
import com.storyteller_f.a.app.compontents.AudioView
import com.storyteller_f.a.app.compontents.ButtonNav
import com.storyteller_f.a.app.compontents.VideoView
import com.storyteller_f.a.app.compontents.globalLoader
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.pages.topic.BaseSheet
import com.storyteller_f.a.app.showShortToast
import com.storyteller_f.a.app.ui.MaterialSymbolsOutlined
import com.storyteller_f.a.client_lib.copy
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPage(session: MediaPlaySession) {
    when (session) {
        is MediaPlaySession.Image -> {
            Box {
                var showSheet by remember {
                    mutableStateOf(false)
                }
                val sheetState = rememberModalBottomSheetState()
                CoilZoomAsyncImage(
                    model = globalLoader(session.mediaInfo.url),
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

        is MediaPlaySession.VideoOrAudio -> {
            CenterBox {
                val remoteMediaItem = session.obj
                if (remoteMediaItem.contentType.startsWith("video")) {
                    VideoView(remoteMediaItem, false)
                } else {
                    AudioView(remoteMediaItem, false)
                }
            }
        }

        is MediaPlaySession.LocalImage -> {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSheet(
    session: MediaPlaySession.Image,
    showSheet: Boolean,
    sheetState: SheetState,
    hideSheet: () -> Unit,
) {
    val sessionManager = LocalSessionManager.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    BaseSheet(
        showSheet,
        sheetState,
        hideSheet
    ) {
        Column(modifier = Modifier.height(200.dp).padding(20.dp)) {
            ButtonNav(MaterialSymbolsOutlined.FileCopy, "copy") {
                scope.launch {
                    globalDialogState.use {
                        sessionManager.copy(session.mediaInfo.id).getOrThrow()
                        toaster.showShortToast("done")
                    }
                }
            }
        }
    }
}
