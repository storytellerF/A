package com.storyteller_f.a.app.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.common.RefCellStateView
import com.storyteller_f.a.app.model.RoomViewModel
import com.storyteller_f.a.app.model.createRoomViewModel
import com.storyteller_f.shared.type.PrimaryKey

@Composable
fun RoomRefCell(roomId: PrimaryKey) {
    val viewModel = createRoomViewModel(roomId)
    RoomRefCellInternal(viewModel)
}

@Composable
fun RoomRefCell(roomAid: String) {
    val viewModel = createRoomViewModel(roomAid)

    RoomRefCellInternal(viewModel)
}

@Composable
private fun RoomRefCellInternal(viewModel: RoomViewModel) {
    val roomInfo by viewModel.handler.data.collectAsState()
    val appNav = LocalAppNav.current
    val shape = RoundedCornerShape(10.dp)
    RefCellStateView(
        viewModel.handler,
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, shape)
            .clip(shape)
            .clickable {
                roomInfo?.let {
                    appNav.gotoRoom(it.id, false)
                }
            }
            .padding(10.dp)
    ) {
        RoomCell(it, true)
    }
}
