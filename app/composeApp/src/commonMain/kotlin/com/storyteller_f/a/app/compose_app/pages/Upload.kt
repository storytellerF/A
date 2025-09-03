package com.storyteller_f.a.app.compose_app.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.storyteller_f.a.app.compose_app.CommonEntry
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.compontents.CenterBox
import com.storyteller_f.a.app.compose_app.compontents.CustomAlertDialog
import com.storyteller_f.a.app.compose_app.compontents.rememberAlertDialogController
import com.storyteller_f.a.app.compose_app.model.createUploadViewModel
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.storage.UploadInfo
import com.storyteller_f.storage.UploadStatus
import io.ktor.http.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.io.Source

@Stable
interface ClientFile {
    val name: String
    val contentType: ContentType
    val size: Long
    val path: String

    fun source(): Source
}

@Stable
class UploadSession(val name: String, val list: ImmutableList<ClientFile>) {
    override fun equals(other: Any?): Boolean {
        return (other as? UploadSession)?.name == name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}

@Composable
fun UploadPage() {
    CommonEntry({
        val userSessionManager =
            LocalSessionManager.current
        val myInfo by userSessionManager.sessionModel.userHandler.data.collectAsState()
        UploadInternal(myInfo)
    })
}

@Composable
fun UploadInternal(my: UserInfo?) {
    if (my != null) {
        val viewModel =
            createUploadViewModel(my.id)
        val pagingItems = viewModel.flow.collectAsLazyPagingItems()
        Scaffold { paddingValues ->
            Column(modifier = Modifier.padding(top = paddingValues.calculateTopPadding())) {
                LazyColumn(contentPadding = PaddingValues(20.dp)) {
                    items(pagingItems.itemSnapshotList.size, key = pagingItems.itemKey {
                        it.id
                    }) {
                        val file = pagingItems[it]
                        UploadItem(file)
                    }
                }
            }
        }
    } else {
        CenterBox {
            Text("Not login")
        }
    }
}

@Composable
private fun UploadItem(
    file: UploadInfo?,
) {
    file ?: return
    val globalDialogController = rememberAlertDialogController()
    Row(modifier = Modifier.padding(20.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(file.path)
            Text(file.message)
            LinearProgressIndicator(
                progress = { file.progress.toFloat() / file.total },
            )
        }

        when (file.status) {
            UploadStatus.SUCCESS -> Icon(Icons.Default.Done, "done")
            UploadStatus.FAILED -> {
                IconButton({
                }) {
                    Icon(Icons.Default.Error, "error")
                }
                IconButton({
                }) {
                    Icon(Icons.Default.Refresh, "retry")
                }
            }

            else -> CircularProgressIndicator(Modifier.size(30.dp))
        }
    }
    CustomAlertDialog(globalDialogController, {
        globalDialogController.close()
    }) {
    }
}
