/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.app.pages.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.LocalAppNavFactory
import com.storyteller_f.a.app.Res
import com.storyteller_f.a.app.common.createCommunityViewModel
import com.storyteller_f.a.app.common.createJoinedRoomsViewModel
import com.storyteller_f.a.app.pages.community.CommunityIconWithDialog
import com.storyteller_f.a.app.room_community
import com.storyteller_f.a.app.room_members
import com.storyteller_f.a.app.room_no_community
import com.storyteller_f.a.app.room_read_only
import com.storyteller_f.a.app.room_status
import com.storyteller_f.a.app.room_unread
import com.storyteller_f.a.app.rooms
import com.storyteller_f.a.client.compose_core.common.PagingViewModel
import com.storyteller_f.a.client.compose_core.components.LayoutDefaults
import com.storyteller_f.a.client.compose_core.components.RoomIcon
import com.storyteller_f.a.client.compose_core.components.StateView
import com.storyteller_f.a.client.compose_core.components.bottomAppending
import com.storyteller_f.a.client.compose_core.components.pagingItems
import com.storyteller_f.a.client.compose_core.components.rememberCommonDialogController
import com.storyteller_f.a.client.compose_core.components.topPrepend
import com.storyteller_f.a.client.compose_core.utils.appiumSemantics
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.RoomInfo
import org.jetbrains.compose.resources.stringResource

@Composable
fun MyRoomsPage() {
    val viewModel = createJoinedRoomsViewModel()
    RoomList(viewModel)
}

@Composable
fun RoomList(roomsViewModel: PagingViewModel<RoomInfo>, onClick: ((RoomInfo) -> Unit)? = null) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp
        Column(Modifier.fillMaxSize()) {
            if (wide) RoomTableHeader()
            StateView(roomsViewModel, Modifier.weight(1f).fillMaxWidth()) { items ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = LayoutDefaults.contentPadding,
                    verticalArrangement = LayoutDefaults.pagingVerticalArrangement,
                ) {
                    topPrepend(items.loadState)
                    pagingItems(items, { it.id }) { index ->
                        PrimaryRoomCell(items[index], onClick, wide)
                    }
                    bottomAppending(items.loadState)
                }
            }
        }
    }
}

@Preview(widthDp = 300)
@Composable
fun PrimaryRoomCell(
    @PreviewParameter(RoomCellPreviewProvider::class) roomInfo: RoomInfo?,
    onClick: ((RoomInfo) -> Unit)? = null,
    wide: Boolean = false,
) {
    val appNavFactory = LocalAppNavFactory.current
    val openRoom =
        roomInfo?.let { info ->
            {
                onClick?.invoke(info) ?: appNavFactory.newAppNav().gotoRoom(info.id, false)
            }
        }
    val shape = RoundedCornerShape(10.dp)
    RoomCellInternal(
        roomInfo,
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, shape)
            .clip(shape)
            .clickable { openRoom?.invoke() }
            .padding(10.dp)
            .appiumSemantics(
                text = roomInfo?.name,
                onClick = openRoom,
            ),
        wide,
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
        get() =
            sequenceOf(
                RoomInfo.EMPTY.copy(name = "Room Name", latestTopic = 1, lastRead = 0),
            )
}

@Composable
private fun RoomCellInternal(roomInfo: RoomInfo?, modifier: Modifier = Modifier, wide: Boolean = false) {
    val communityInfo =
        roomInfo?.communityId?.let { communityId ->
            val model = createCommunityViewModel(communityId)
            val info by model.handler.data.collectAsState()
            info
        }
    val roomDialog = rememberCommonDialogController()
    val roomShown by roomDialog.shown
    val communityDialog = rememberCommonDialogController()
    val communityShown by communityDialog.shown
    BadgedBox(badge = {
        if (!wide && roomInfo?.hasUnread == true) Badge(containerColor = Color.Red)
    }) {
        RoomRowContent(roomInfo, communityInfo, wide, modifier, {
            RoomIconWithDialog(roomInfo, showDialog = roomShown, updateDialog = roomDialog::update)
        }) {
            CommunityIconWithDialog(communityInfo, communityShown, onClickIcon = communityDialog::update)
        }
    }
}

/** Pure room presentation; callers provide navigation-aware icons outside screenshot fixtures. */
@Composable
fun RoomRowContent(
    roomInfo: RoomInfo?,
    communityInfo: CommunityInfo?,
    wide: Boolean,
    modifier: Modifier = Modifier,
    roomIcon: @Composable () -> Unit,
    communityIcon: @Composable () -> Unit,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        roomIcon()
        Text(
            roomInfo?.name.orEmpty(),
            Modifier.weight(1f).padding(start = 8.dp, end = 12.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (wide) {
            Text(
                communityInfo?.name ?: if (roomInfo?.communityId == null) {
                    stringResource(Res.string.room_no_community)
                } else {
                    "—"
                },
                Modifier.weight(0.8f).padding(end = 12.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(roomInfo?.memberCount?.toString().orEmpty(), Modifier.width(88.dp))
            Text(
                if (roomInfo?.readOnly == true) stringResource(Res.string.room_read_only) else "—",
                Modifier.width(100.dp),
                maxLines = 2,
                style = MaterialTheme.typography.labelMedium,
            )
            Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                if (roomInfo?.hasUnread == true) {
                    Badge { Text(stringResource(Res.string.room_unread)) }
                }
            }
        } else if (roomInfo?.communityId != null) {
            communityIcon()
        }
    }
}

@Composable
private fun RoomTableHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Spacer(Modifier.width(68.dp))
        Text(stringResource(Res.string.rooms), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        Text(
            stringResource(Res.string.room_community),
            Modifier.weight(0.8f),
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            stringResource(Res.string.room_members),
            Modifier.width(88.dp),
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            stringResource(Res.string.room_status),
            Modifier.width(100.dp),
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            stringResource(Res.string.room_unread),
            Modifier.width(64.dp),
            style = MaterialTheme.typography.labelMedium,
        )
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
