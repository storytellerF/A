package com.storyteller_f.a.app.compose_app

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
import com.storyteller_f.a.app.compose_app.compontents.CenterBox
import com.storyteller_f.a.app.compose_app.model.createUploadViewModel
import com.storyteller_f.a.client.core.LoadingHandler
import com.storyteller_f.a.client.core.LoadingState
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.UserInfo
import io.ktor.http.*
import kotlinx.coroutines.launch
import kotlinx.io.Source

@Stable
interface ClientFile {
    val name: String
    val contentType: ContentType
    val size: Long
    val id: String

    fun source(): Source?
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
    CommonEntry(httpUrl, wsServerUrl, {
        val userSessionManager = LocalSessionManager.current
        val myInfo by userSessionManager.sessionModel.userHandler.data.collectAsState()
        val my = myInfo
        val session by uploader.session
        session?.let { UploadInternal(my, it) }
    })
}

@Composable
fun UploadInternal(my: UserInfo?, session: UploadSession) {
    if (my != null) {
        val viewModel =
            createUploadViewModel(my.id, session)
        Scaffold { paddingValues ->
            Column(modifier = Modifier.padding(top = paddingValues.calculateTopPadding())) {
                LazyColumn(contentPadding = PaddingValues(20.dp)) {
                    items(session.list.size) {
                        val file = session.list[it]
                        UploadItem(viewModel.handlers[it], file) {
                            viewModel.retry(it)
                        }
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
fun UploadItem(p: LoadingHandler<MediaInfo>, file: ClientFile, refresh: () -> Unit) {
    val handler = p
    val data by handler.data.collectAsState()
    val state by handler.state.collectAsState()
    UploadItem(file, data, state, refresh)
}

@Composable
private fun UploadItem(
    file: ClientFile,
    data: MediaInfo?,
    state: LoadingState?,
    refresh: () -> Unit
) {
    val globalDialogController = LocalGlobalDialog.current
    val scope = rememberCoroutineScope()
    Row(modifier = Modifier.padding(20.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name)
            data?.let {
                Text(it.fullName)
            }
        }

        when (state) {
            LoadingState.Done -> Icon(Icons.Default.Done, "done")
            is LoadingState.Error -> {
                IconButton({
                    scope.launch {
                        globalDialogController.showErrorMessage(state.e)
                    }
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
