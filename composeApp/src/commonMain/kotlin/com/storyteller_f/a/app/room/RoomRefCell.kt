package com.storyteller_f.a.app.room

import androidx.compose.runtime.Composable
import com.storyteller_f.a.app.common.StateView2
import com.storyteller_f.shared.type.PrimaryKey
import moe.tlaster.precompose.viewmodel.viewModel

@Composable
fun RoomRefCell(roomId: PrimaryKey, onClick: (PrimaryKey) -> Unit) {
    val viewModel = viewModel(RoomViewModel::class, keys = listOf("room", roomId)) {
        RoomViewModel(roomId)
    }
    StateView2(viewModel.handler) {
        RoomCell(it, onClick)
    }
}
