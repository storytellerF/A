package com.storyteller_f.a.app.compose_app.pages.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.api.core.NewRoom
import com.storyteller_f.a.app.compose_app.LocalAppNavFactory
import com.storyteller_f.a.app.compose_app.LocalGlobalDialog
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.common.OnRoomCreated
import com.storyteller_f.a.app.compose_app.pages.title.CommonComposePage
import com.storyteller_f.a.client.core.createRoom
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomComposePage() {
    var name by remember {
        mutableStateOf("")
    }
    var aid by remember {
        mutableStateOf("")
    }
    val sessionManager = LocalSessionManager.current
    val appNavFactory = LocalAppNavFactory.current
    val scope = rememberCoroutineScope()
    val globalDialogController = LocalGlobalDialog.current
    CommonComposePage({
        scope.launch {
            globalDialogController.useResult {
                sessionManager.createRoom(NewRoom(name, aid))
            }.onSuccess { roomInfo ->
                globalDialogController.emitEvent(OnRoomCreated(roomInfo))
                appNavFactory.newAppNav().back()
            }
        }
    }) {
        Column(
            modifier = Modifier.width(300.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OutlinedTextField(name, onValueChange = {
                name = it
            }, label = {
                Text("name")
            })
            OutlinedTextField(aid, onValueChange = {
                aid = it
            }, label = {
                Text("aid")
            })
        }
    }
}
