package com.storyteller_f.a.app.pages.room

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
import com.storyteller_f.a.api.NewRoom
import com.storyteller_f.a.app.LocalAppNavFactory
import com.storyteller_f.a.app.LocalGlobalDialog
import com.storyteller_f.a.app.Res
import com.storyteller_f.a.app.aid
import com.storyteller_f.a.app.common.OnRoomCreated
import com.storyteller_f.a.app.name
import com.storyteller_f.a.app.pages.title.CommonComposePage
import com.storyteller_f.a.app.core.components.emitEvent
import com.storyteller_f.a.app.core.components.request
import com.storyteller_f.a.client.core.createRoom
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomComposePage() {
    var name by remember {
        mutableStateOf("")
    }
    var aid by remember {
        mutableStateOf("")
    }
    val appNavFactory = LocalAppNavFactory.current
    val scope = rememberCoroutineScope()
    val globalDialogController = LocalGlobalDialog.current
    CommonComposePage({
        scope.launch {
            globalDialogController.useResult {
                request {
                    createRoom(NewRoom(name, aid))
                }
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
                Text(stringResource(Res.string.name))
            })
            OutlinedTextField(aid, onValueChange = {
                aid = it
            }, label = {
                Text(stringResource(Res.string.aid))
            })
        }
    }
}
