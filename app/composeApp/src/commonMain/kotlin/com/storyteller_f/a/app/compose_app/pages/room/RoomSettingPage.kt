package com.storyteller_f.a.app.compose_app.pages.room

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
import com.storyteller_f.a.app.compose_app.LocalGlobalDialog
import com.storyteller_f.a.app.compose_app.LocalSessionManager
import com.storyteller_f.a.app.compose_app.LocalToaster
import com.storyteller_f.a.app.compose_app.bus
import com.storyteller_f.a.app.compose_app.compontents.GlobalDialogController
import com.storyteller_f.a.app.compose_app.compontents.SettingOptionResettableView
import com.storyteller_f.a.app.compose_app.compontents.SettingOptionView
import com.storyteller_f.a.app.compose_app.model.OnRoomUpdated
import com.storyteller_f.a.app.compose_app.model.createRoomViewModel
import com.storyteller_f.a.app.compose_app.pages.user.ObjectSettingDialog
import com.storyteller_f.a.app.compose_app.pages.user.SettingOption
import com.storyteller_f.a.client.core.SessionManager
import com.storyteller_f.a.client.core.updateRoomInfo
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
        val globalDialogController = LocalGlobalDialog.current
        ObjectSettingDialog(
            closeDialog,
            currentOption,
            sheetState,
            {
                scope.launch {
                    globalDialogController.use {
                        val body = UpdateRoomBody(icon = it.id)
                        val newInfo = sessionManager.updateRoomInfo(roomId, body).getOrThrow()
                        bus.emit(
                            OnRoomUpdated(
                                newInfo
                            )
                        )
                    }
                }
            },
            {
                scope.launch {
                    updateRoom(roomId, sessionManager, it, currentOption, globalDialogController, closeDialog)
                }
            }
        )
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
    val globalDialogController = LocalGlobalDialog.current
    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(values)) {
        SettingOptionResettableView(
            "Icon",
            roomInfo.icon != null,
            {
                if (it) {
                    scope.launch {
                        globalDialogController.use {
                            val body = UpdateRoomBody(icon = 0)
                            val newInfo = sessionManager.updateRoomInfo(roomInfo.id, body).getOrThrow()
                            bus.emit(
                                OnRoomUpdated(
                                    newInfo
                                )
                            )
                        }
                    }
                } else {
                    showDialog(
                        SettingOption.Icon(
                            roomInfo.icon?.fullName
                        )
                    )
                }
            },
            {
                RoomIcon(roomInfo, showDialog = false, setClickEvent = false) {}
            }
        )
        SettingOptionView("Name", {
            showDialog(
                SettingOption.Name(roomInfo.name)
            )
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
    roomId: PrimaryKey,
    sessionManager: SessionManager,
    string: String,
    showInputDialog: SettingOption?,
    globalDialogController: GlobalDialogController,
    closeDialog: () -> Unit,
) {
    val body = when (showInputDialog) {
        is SettingOption.Name -> {
            UpdateRoomBody(name = string)
        }

        else -> {
            null
        }
    } ?: return
    globalDialogController.use {
        val newInfo = sessionManager.updateRoomInfo(roomId, body).getOrThrow()
        bus.emit(
            OnRoomUpdated(
                newInfo
            )
        )
        closeDialog()
    }
}
