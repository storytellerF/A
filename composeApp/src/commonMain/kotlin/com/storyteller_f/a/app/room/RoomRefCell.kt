package com.storyteller_f.a.app.room

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.storyteller_f.a.app.common.StateView2
import com.storyteller_f.shared.type.OKey
import moe.tlaster.precompose.viewmodel.viewModel

@Composable
fun RoomRefCell(roomId: OKey, onClick: (OKey) -> Unit) {
    val viewModel = viewModel(RoomViewModel::class, keys = listOf("room", roomId)) {
        RoomViewModel(roomId)
    }
    StateView2(viewModel.handler) {
        RoomCell(it, onClick)
    }
}
