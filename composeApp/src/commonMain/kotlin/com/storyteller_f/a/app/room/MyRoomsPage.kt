package com.storyteller_f.a.app.room

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
import androidx.paging.ExperimentalPagingApi
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemContentType
import app.cash.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.common.*
import com.storyteller_f.a.app.common.viewModel
import com.storyteller_f.a.app.utils.safeFirstUnicode
import com.storyteller_f.a.client_lib.getJoinedRooms
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKey

@Composable
fun MyRoomsPage() {
    val viewModel = viewModel(MyRoomsViewModel::class) {
        MyRoomsViewModel()
    }
    val items = viewModel.flow.collectAsLazyPagingItems()
    RoomList(items)
}

@OptIn(ExperimentalPagingApi::class)
class MyRoomsViewModel : PagingViewModel<PrimaryKey, RoomInfo>({
    SimplePagingSource {
        serviceCatching {
            client.getJoinedRooms(10, it)
        }.map {
            APagingData(it.data, it.pagination?.nextPageToken?.toPrimaryKey())
        }
    }
})

@Composable
fun RoomList(
    items: LazyPagingItems<RoomInfo>
) {
    StateView(items) {
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                count = items.itemCount,
                key = items.itemKey {
                    it.id.toString()
                },
                contentType = items.itemContentType()
            ) { index ->
                RoomCell(items[index], false)
            }
        }
    }
}

@Composable
fun RoomCell(
    roomInfo: RoomInfo?,
    customBackground: Boolean = false
) {
    val appNav = LocalAppNav.current
    val onClick = appNav::goto
    var showDialog by remember {
        mutableStateOf(false)
    }
    Row(
        modifier = when {
            customBackground -> Modifier

            else -> Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp))
                .clickable {
                    roomInfo?.let { onClick(it.id, ObjectType.ROOM) }
                }
                .padding(10.dp)
        }
    ) {
        RoomIcon(roomInfo)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(roomInfo?.name.orEmpty(), color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
    if (roomInfo != null) {
        RoomDialog(showDialog, roomInfo) {
            showDialog = false
        }
    }
}

@Composable
fun RoomIcon(roomInfo: RoomInfo?, size: Dp = 50.dp) {
    var showDialog by remember {
        mutableStateOf(false)
    }
    val iconUrl = roomInfo?.icon?.url
    val radius = 8.dp
    if (iconUrl != null) {
        AsyncImage(iconUrl, contentDescription = "${roomInfo.name}'s icon", modifier = Modifier.size(size).clickable {
            showDialog = true
        }.clip(RoundedCornerShape(radius)))
    } else {
        Box(
            modifier = Modifier.size(size)
                .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(radius))
                .clickable {
                    showDialog = true
                },
            contentAlignment = Alignment.Center
        ) {
            Text(roomInfo?.name?.safeFirstUnicode()?.toString() ?: "")
        }
    }
    RoomDialog(showDialog, roomInfo) {
        showDialog = false
    }
}
