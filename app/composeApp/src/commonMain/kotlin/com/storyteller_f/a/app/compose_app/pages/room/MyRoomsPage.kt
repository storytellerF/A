package com.storyteller_f.a.app.compose_app.pages.room

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
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.storyteller_f.a.app.compose_app.LocalAppNav
import com.storyteller_f.a.app.core.compontents.CommonImage
import com.storyteller_f.a.app.compose_app.compontents.CommunityIcon
import com.storyteller_f.a.app.core.compontents.StateView
import com.storyteller_f.a.app.core.compontents.bottomAppending
import com.storyteller_f.a.app.compose_app.compontents.rememberCommonDialogController
import com.storyteller_f.a.app.core.compontents.topPrepend
import com.storyteller_f.a.app.compose_app.model.RoomsViewModel
import com.storyteller_f.a.app.compose_app.model.createCommunityViewModel
import com.storyteller_f.a.app.compose_app.model.createJoinedRoomsViewModel
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.utils.safeFirstUnicode

@Composable
fun MyRoomsPage() {
    val viewModel = createJoinedRoomsViewModel()
    val items = viewModel.flow.collectAsLazyPagingItems()
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
            items(
                count = items.itemSnapshotList.size,
                key = items.itemKey {
                    it.id.toString()
                },
            ) { index ->
                RoomCell(items[index], false, onClick)
            }
            bottomAppending(items.loadState)
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
        val commonDialogController =
            rememberCommonDialogController()
        val shown by commonDialogController.shown
        RoomIcon(roomInfo, showDialog = shown, updateDialog = commonDialogController::update)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(roomInfo?.name.orEmpty(), color = MaterialTheme.colorScheme.onSecondaryContainer)
        }

        val communityId = roomInfo?.communityId
        if (communityId != null) {
            val model =
                createCommunityViewModel(communityId)
            val communityInfo by model.handler.data.collectAsState()
            var showCommunityDialog by remember {
                mutableStateOf(false)
            }
            Spacer(modifier = Modifier.weight(1f))
            CommunityIcon(
                communityInfo,
                showDialog = showCommunityDialog
            ) {
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
    setClickEvent: Boolean = false,
    updateDialog: (Boolean) -> Unit,
) {
    val iconUrl = roomInfo?.icon?.url
    val radius = 8.dp
    val shape = RoundedCornerShape(radius)
    if (iconUrl != null) {
        CommonImage(
            iconUrl,
            contentDescription = "${roomInfo.name}'s icon",
            modifier = Modifier.size(size).clip(shape).let {
                if (setClickEvent) {
                    it.clickable {
                        updateDialog(true)
                    }
                } else {
                    it
                }
            }
        )
    } else {
        Box(
            modifier = Modifier.size(size)
                .background(MaterialTheme.colorScheme.tertiaryContainer, shape)
                .clip(shape)
                .let {
                    if (setClickEvent) {
                        it.clickable {
                            updateDialog(true)
                        }
                    } else {
                        it
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(roomInfo?.name?.let { safeFirstUnicode(it) } ?: "")
        }
    }
    RoomDialog(showDialog, roomInfo) {
        updateDialog(false)
    }
}
