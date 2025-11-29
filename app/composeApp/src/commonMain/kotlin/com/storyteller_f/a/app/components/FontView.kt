package com.storyteller_f.a.app.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.common.DownloadViewModel
import com.storyteller_f.a.app.common.getDownloadViewModel
import com.storyteller_f.a.app.pages.file.DownloadInfoPage
import com.storyteller_f.a.app.ui.MaterialSymbolsOutlined
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.storage.DownloadInfo
import com.storyteller_f.storage.DownloadStatus
import dev.tclement.fonticons.FontIcon

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FontView(info: FileInfo) {
    var showSheet by remember {
        mutableStateOf(false)
    }
    val sheetState = rememberModalBottomSheetState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                showSheet = true
            }
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        FontIcon(MaterialSymbolsOutlined.FontDownload, "font")
        val scrollState = rememberScrollState()
        Text(info.name, modifier = Modifier.weight(1f).horizontalScroll(scrollState))
        val downloadViewModel = getDownloadViewModel(info.id)
        DownloadStatusView(downloadViewModel)
    }
    DownloadInfoPage(showSheet, sheetState, info.id) {
        showSheet = false
    }
}

@Composable
fun DownloadStatusView(downloadViewModel: DownloadViewModel) {
    val data by downloadViewModel.data.collectAsState(null)
    DownloadStatusViewInternal(data)
}

@Composable
fun DownloadStatusViewInternal(data: DownloadInfo?) {
    val downloadStatus = data?.status
    when {
        data == null ||
            downloadStatus == DownloadStatus.NOT_DOWNLOADED ||
            downloadStatus == DownloadStatus.DOWNLOADING -> CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
        )

        downloadStatus == DownloadStatus.DOWNLOADED -> FontIcon(MaterialSymbolsOutlined.DownloadDone, "download done")

        downloadStatus == DownloadStatus.DOWNLOAD_FAILED -> FontIcon(MaterialSymbolsOutlined.Error, "error")
    }
}
