package com.storyteller_f.a.app.compose_app.pages.room

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
import com.storyteller_f.a.app.compose_app.LocalAppNavFactory
import com.storyteller_f.a.app.compose_app.common.RoomViewModel
import com.storyteller_f.a.app.compose_app.common.createRoomViewModel
import com.storyteller_f.a.app.core.components.RefCellStateView
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.type.PrimaryKey

@Composable
fun RoomRefCell(roomId: PrimaryKey, onClick: ((RoomInfo) -> Unit)? = null) {
    val viewModel = createRoomViewModel(roomId)
    RoomRefCellInternal(viewModel, onClick)
}

@Composable
fun RoomRefCell(roomAid: String, onClick: ((RoomInfo) -> Unit)? = null) {
    val viewModel = createRoomViewModel(roomAid)
    RoomRefCellInternal(viewModel, onClick)
}

@Composable
private fun RoomRefCellInternal(
    viewModel: RoomViewModel,
    onClick: ((RoomInfo) -> Unit)? = null
) {
    val roomInfo by viewModel.handler.data.collectAsState()
    val appNavFactory = LocalAppNavFactory.current
    val shape = RoundedCornerShape(10.dp)
    RefCellStateView(
        viewModel.handler,
        modifier = Modifier
            .fillMaxWidth()
            .height(65.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, shape)
            .clip(shape)
            .clickable {
                roomInfo?.let {
                    onClick?.invoke(it) ?: appNavFactory.newAppNav().gotoRoom(it.id, false)
                }
            }
            .padding(10.dp)
    ) {
        UnboundedRoomCell(it)
    }
}
