package com.storyteller_f.a.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.common.CenterBox
import com.storyteller_f.a.app.model.createUploadViewModel
import com.storyteller_f.a.client_lib.LoadingState
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.a.client_lib.SimpleLoadingHandler
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.UserInfo
import io.ktor.http.*

interface ClientFile {
    val name: String
    val contentType: ContentType
    val size: Long
    val id: String

    fun readAll(): ByteArray?
}

class UploadSession(val name: String, val list: List<ClientFile>) {
    override fun equals(other: Any?): Boolean {
        return (other as? UploadSession)?.name == name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}

class Uploader(val session: MutableState<UploadSession?>)

@Composable
fun Upload(uploader: Uploader) {
    val httpUrl = AppConfig.SERVER_URL
    val wsServerUrl = AppConfig.WS_SERVER_URL
    CommonEntry(httpUrl, wsServerUrl, { null }, { null }) {
        val my by LoginViewModel.user.collectAsState()
        val session by uploader.session
        session?.let { UploadInternal(my, it) }
    }
}

@Composable
fun UploadInternal(my: UserInfo?, session: UploadSession) {
    if (my != null) {
        val viewModel = createUploadViewModel(my.id, session)
        Scaffold { paddingValues ->
            Column(modifier = Modifier.padding(top = paddingValues.calculateTopPadding())) {
                LazyColumn(contentPadding = PaddingValues(20.dp)) {
                    items(viewModel.handlers) {
                        UploadItem(it)
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
fun UploadItem(p: Pair<SimpleLoadingHandler<MediaInfo>, ClientFile>) {
    val (handler, file) = p
    val data by handler.data.collectAsState()
    val state by handler.state.collectAsState()
    UploadItem(file, data, state) {
        handler.refresh()
    }
}

@Composable
private fun UploadItem(
    file: ClientFile,
    data: MediaInfo?,
    state: LoadingState?,
    refresh: () -> Unit
) {
    Row(modifier = Modifier.padding(20.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name)
            data?.let {
                Text(it.item.name)
            }
        }

        when (state) {
            LoadingState.Done -> Icon(Icons.Default.Done, "done")
            is LoadingState.Error -> {
                IconButton({
                    globalDialogState.showError(state.e)
                }) {
                    Icon(Icons.Default.Error, "error")
                }
                IconButton({
                    refresh()
                }) {
                    Icon(Icons.Default.Refresh, "retry")
                }
            }

            LoadingState.Loading, null -> CircularProgressIndicator(Modifier.size(30.dp))
        }
    }
}
