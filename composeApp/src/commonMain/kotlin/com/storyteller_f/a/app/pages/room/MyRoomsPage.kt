package com.storyteller_f.a.app.pages.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.common.StateView
import com.storyteller_f.a.app.compontents.CommunityIcon
import com.storyteller_f.a.app.compontents.globalLoader
import com.storyteller_f.a.app.compontents.rememberCommonDialogController
import com.storyteller_f.a.app.model.createCommunityViewModel
import com.storyteller_f.a.app.model.createJoinedRoomsViewModel
import com.storyteller_f.a.app.utils.safeFirstUnicode
import com.storyteller_f.shared.model.RoomInfo

@Composable
fun MyRoomsPage() {
    val viewModel = createJoinedRoomsViewModel()
    val items = viewModel.flow.collectAsLazyPagingItems()
    RoomList(items)
}

@Composable
fun RoomList(
    items: LazyPagingItems<RoomInfo>,
    onClick: ((RoomInfo) -> Unit)? = null
) {
    StateView(items) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                count = items.itemCount,
                key = items.itemKey {
                    it.id.toString()
                },
            ) { index ->
                RoomCell(items[index], false, onClick)
            }
        }
    }
}

@Composable
fun RoomCell(
    roomInfo: RoomInfo?,
    customBackground: Boolean = false,
    onClick: ((RoomInfo) -> Unit)? = null,
) {
    val appNav = LocalAppNav.current
    var showDialog by remember {
        mutableStateOf(false)
    }
    Row(
        modifier = when {
            customBackground -> Modifier

            else -> {
                val shape = RoundedCornerShape(10.dp)
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer, shape)
                    .clip(shape)
                    .clickable {
                        roomInfo?.let { onClick ?: appNav.gotoRoom(it.id, false) }
                    }
                    .padding(10.dp)
            }
        }
    ) {
        val commonDialogController = rememberCommonDialogController()
        val shown by commonDialogController.show
        RoomIcon(roomInfo, showDialog = shown, updateShowDialog = commonDialogController::update)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(roomInfo?.name.orEmpty(), color = MaterialTheme.colorScheme.onSecondaryContainer)
        }

        val communityId = roomInfo?.communityId
        if (communityId != null) {
            val model = createCommunityViewModel(communityId)
            val communityInfo by model.handler.data.collectAsState()
            var showCommunityDialog by remember {
                mutableStateOf(false)
            }
            Spacer(modifier = Modifier.weight(1f))
            CommunityIcon(communityInfo, showDialog = showCommunityDialog) {
                showCommunityDialog = it
            }
        }
    }
    if (roomInfo != null) {
        RoomDialog(showDialog, roomInfo) {
            showDialog = false
        }
    }
}

@Composable
fun RoomIcon(
    roomInfo: RoomInfo?,
    showDialog: Boolean,
    size: Dp = 50.dp,
    enableClick: Boolean = false,
    updateShowDialog: (Boolean) -> Unit,
) {
    val iconUrl = roomInfo?.icon?.url
    val radius = 8.dp
    val shape = RoundedCornerShape(radius)
    if (iconUrl != null) {
        AsyncImage(
            globalLoader(iconUrl),
            contentDescription = "${roomInfo.name}'s icon",
            modifier = Modifier.size(size).clip(shape).clickable(enableClick) {
                updateShowDialog(true)
            }
        )
    } else {
        Box(
            modifier = Modifier.size(size)
                .background(MaterialTheme.colorScheme.tertiaryContainer, shape)
                .clip(shape)
                .clickable(enableClick) {
                    updateShowDialog(true)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(roomInfo?.name?.safeFirstUnicode()?.toString() ?: "")
        }
    }
    roomInfo?.id?.let {
        RoomDialog(showDialog, roomInfo) {
            updateShowDialog(false)
        }
    }
}
