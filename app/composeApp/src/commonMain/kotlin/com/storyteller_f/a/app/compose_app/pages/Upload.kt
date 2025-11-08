package com.storyteller_f.a.app.compose_app.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.storyteller_f.a.app.compose_app.CommonEntry
import com.storyteller_f.a.app.compose_app.LocalUserInfo
import com.storyteller_f.a.app.compose_app.common.createUploadViewModel
import com.storyteller_f.a.app.compose_app.utils.ClientFile
import com.storyteller_f.a.app.core.components.CenterBox
import com.storyteller_f.a.app.core.components.CustomAlertDialog
import com.storyteller_f.a.app.core.components.rememberAlertDialogController
import com.storyteller_f.a.client.core.UploadData
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.storage.UploadInfo
import com.storyteller_f.storage.UploadStatus
import kotlinx.collections.immutable.ImmutableList

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
        val myInfo = LocalUserInfo.current
        UploadInternal(myInfo)
    })
}

@Composable
fun UploadInternal(my: UserInfo?) {
    if (my != null) {
        val viewModel = createUploadViewModel(my.id)
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

fun ClientFile.getUploadDataFromClipFile() = UploadData(
    size,
    name,
    contentType
) {
    source()
}
