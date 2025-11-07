package com.storyteller_f.a.app.compose_app.pages.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.compose_app.LocalAppNavFactory
import com.storyteller_f.a.app.compose_app.common.IdCommunityViewModel
import com.storyteller_f.a.app.compose_app.common.RoomsViewModel
import com.storyteller_f.a.app.compose_app.common.createCommunityViewModel
import com.storyteller_f.a.app.compose_app.common.createJoinedRoomsViewModel
import com.storyteller_f.a.app.compose_app.components.RoomIcon
import com.storyteller_f.a.app.compose_app.pages.community.CommunityIconWithDialog
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.bottomAppending
import com.storyteller_f.a.app.core.components.pagingItems
import com.storyteller_f.a.app.core.components.rememberCommonDialogController
import com.storyteller_f.a.app.core.components.topPrepend
import com.storyteller_f.shared.model.RoomInfo
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter
import org.jetbrains.compose.ui.tooling.preview.PreviewParameterProvider

@Composable
fun MyRoomsPage() {
    val viewModel = createJoinedRoomsViewModel()
    RoomList(viewModel)
}

@Composable
fun RoomList(
    roomsViewModel: RoomsViewModel,
    onClick: ((RoomInfo) -> Unit)? = null
) {
    StateView(roomsViewModel) { items ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            topPrepend(items.loadState)
            pagingItems(items, {
                it.id
            }) {
                PrimaryRoomCell(items[it], onClick)
            }
            bottomAppending(items.loadState)
        }
    }
}

@Preview(widthDp = 300)
@Composable
fun PrimaryRoomCell(
    @PreviewParameter(RoomCellPreviewProvider::class) roomInfo: RoomInfo?,
    onClick: ((RoomInfo) -> Unit)? = null
) {
    val appNavFactory = LocalAppNavFactory.current
    val shape = RoundedCornerShape(10.dp)
    RoomCellInternal(
        roomInfo,
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, shape)
            .clip(shape)
            .clickable {
                roomInfo?.let { onClick?.invoke(it) ?: appNavFactory.newAppNav().gotoRoom(it.id, false) }
            }
            .padding(10.dp)
    )
}

@Preview(widthDp = 300)
@Composable
fun UnboundedRoomCell(@PreviewParameter(RoomCellPreviewProvider::class) roomInfo: RoomInfo?) {
    val modifier = Modifier.fillMaxWidth()
    RoomCellInternal(roomInfo, modifier)
}

class RoomCellPreviewProvider : PreviewParameterProvider<RoomInfo> {
    override val values: Sequence<RoomInfo>
        get() = sequenceOf(
            RoomInfo.EMPTY.copy(
                name = "Room Name",
                latestTopic = 1,
                lastRead = 0
            )
        )
}

@Composable
private fun RoomCellInternal(
    roomInfo: RoomInfo?,
    modifier: Modifier = Modifier
) {
    BadgedBox(badge = {
        if (roomInfo != null && roomInfo.hasUnread) {
            Badge(containerColor = Color.Red)
        }
    }) {
        Row(modifier = modifier) {
            val dialogController = rememberCommonDialogController()
            val shown by dialogController.shown
            RoomIconWithDialog(
                roomInfo,
                showDialog = shown,
                updateDialog = dialogController::update
            )
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    roomInfo?.name.orEmpty(),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            CommunityIconInRoomCell(roomInfo)
        }
    }
}

@Composable
private fun RowScope.CommunityIconInRoomCell(roomInfo: RoomInfo?) {
    val communityId = roomInfo?.communityId
    if (communityId != null) {
        val model = createCommunityViewModel(communityId)
        CommunityIconInRoomCellInternal(model)
    }
}

@Composable
private fun RowScope.CommunityIconInRoomCellInternal(model: IdCommunityViewModel) {
    val communityInfo by model.handler.data.collectAsState()
    var showCommunityDialog by remember {
        mutableStateOf(false)
    }
    Spacer(modifier = Modifier.weight(1f))
    CommunityIconWithDialog(
        communityInfo,
        showDialog = showCommunityDialog
    ) {
        showCommunityDialog = it
    }
}

@Composable
fun RoomIconWithDialog(
    roomInfo: RoomInfo?,
    showDialog: Boolean,
    width: Dp = 60.dp,
    setClickEvent: Boolean = false,
    updateDialog: (Boolean) -> Unit,
) {
    RoomIcon(roomInfo, width, setClickEvent, updateDialog)
    RoomDialog(showDialog, roomInfo) {
        updateDialog(false)
    }
}
