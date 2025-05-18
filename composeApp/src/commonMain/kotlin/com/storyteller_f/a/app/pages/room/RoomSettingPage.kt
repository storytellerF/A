package com.storyteller_f.a.app.pages.room

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.LocalSessionManager
import com.storyteller_f.a.app.LocalToaster
import com.storyteller_f.a.app.bus
import com.storyteller_f.a.app.compontents.SettingOptionResettableView
import com.storyteller_f.a.app.compontents.SettingOptionView
import com.storyteller_f.a.app.globalDialogState
import com.storyteller_f.a.app.model.OnRoomUpdated
import com.storyteller_f.a.app.model.createRoomViewModel
import com.storyteller_f.a.app.pages.user.ObjectSettingDialog
import com.storyteller_f.a.app.pages.user.SettingOption
import com.storyteller_f.a.client_lib.SessionManager
import com.storyteller_f.a.client_lib.updateRoomInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomSettingPage(roomId: PrimaryKey) {
    var currentOption by remember {
        mutableStateOf<SettingOption?>(null)
    }
    val roomViewModel = createRoomViewModel(roomId)
    val roomInfo by roomViewModel.handler.data.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    val closeDialog = {
        currentOption = null
    }
    Scaffold { padding ->
        roomInfo?.let { it1 ->
            RoomSettingInternal(padding, { option: SettingOption ->
                currentOption = option
            }, it1)
        }
        val sessionManager = LocalSessionManager.current
        val scope = rememberCoroutineScope()
        ObjectSettingDialog(closeDialog, currentOption, sheetState, {
            scope.launch {
                globalDialogState.use {
                    val body = UpdateRoomBody(icon = it.name)
                    val newInfo = sessionManager.updateRoomInfo(roomId, body).getOrThrow()
                    bus.emit(OnRoomUpdated(newInfo))
                }
            }
        }, {
            scope.launch {
                updateRoom(currentOption, sessionManager, it, closeDialog, roomId)
            }
        })
    }
}

@Composable
private fun RoomSettingInternal(
    values: PaddingValues,
    showDialog: (SettingOption) -> Unit,
    roomInfo: RoomInfo,
) {
    val toasterState = LocalToaster.current
    val sessionManager = LocalSessionManager.current
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(values)) {
        SettingOptionResettableView("Icon", roomInfo.icon != null, {
            if (it) {
                scope.launch {
                    globalDialogState.use {
                        val body = UpdateRoomBody(icon = "")
                        val newInfo = sessionManager.updateRoomInfo(roomInfo.id, body).getOrThrow()
                        bus.emit(OnRoomUpdated(newInfo))
                    }
                }
            } else {
                showDialog(SettingOption.Icon(roomInfo.icon?.name))
            }
        }, {
            RoomIcon(roomInfo, showDialog = false, setClickEvent = false) {}
        })
        SettingOptionView("Name", {
            showDialog(SettingOption.Name(roomInfo.name))
        }, {
            Text(roomInfo.name, textDecoration = TextDecoration.Underline)
        })
        val aid = roomInfo.aid
        SettingOptionView("Aid", {
            toasterState.show("forbid", duration = 1.seconds)
        }, {
            Text(aid)
        })
    }
}

private suspend fun updateRoom(
    showInputDialog: SettingOption?,
    sessionManager: SessionManager,
    string: String,
    closeDialog: () -> Unit,
    roomId: PrimaryKey
) {
    val body = when (showInputDialog) {
        is SettingOption.Name -> {
            UpdateRoomBody(name = string)
        }

        else -> {
            null
        }
    } ?: return
    globalDialogState.use {
        val newInfo = sessionManager.updateRoomInfo(roomId, body).getOrThrow()
        bus.emit(OnRoomUpdated(newInfo))
        closeDialog()
    }
}
