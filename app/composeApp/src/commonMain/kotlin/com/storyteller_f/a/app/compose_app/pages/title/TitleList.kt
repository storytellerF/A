package com.storyteller_f.a.app.compose_app.pages.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.itemKey
import com.storyteller_f.a.app.compose_app.common.TitlesViewModel
import com.storyteller_f.a.app.compose_app.pages.community.CommunityIconWithDialog
import com.storyteller_f.a.app.compose_app.pages.room.RoomIcon
import com.storyteller_f.a.app.compose_app.pages.user.UserIconWithDialog
import com.storyteller_f.a.app.core.compontents.StateView
import com.storyteller_f.a.app.core.compontents.bottomAppending
import com.storyteller_f.a.app.core.compontents.topPrepend
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.type.ObjectType

@Composable
fun TitleList(titlesViewModel: TitlesViewModel) {
    StateView(titlesViewModel) { pagingItems ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            topPrepend(pagingItems.loadState)
            items(
                count = pagingItems.itemSnapshotList.size,
                key = pagingItems.itemKey {
                    it.id.toString()
                },
            ) { index ->
                val title = pagingItems[index]
                title?.let {
                    TitleItem(it)
                }
            }
            bottomAppending(pagingItems.loadState)
        }
    }
}

@Composable
fun TitleItem(titleInfo: TitleInfo) {
    Row {
        Text(titleInfo.name)

        titleInfo.extension?.let {
            UserIconWithDialog(it.creatorInfo)
            Text("->")
            UserIconWithDialog(it.receiverInfo)
            when (titleInfo.type) {
                TitleType.REGULAR -> Text("regular")
                TitleType.JOIN -> Text("join")
            }
            Text("in")
            when (titleInfo.scopeType) {
                ObjectType.COMMUNITY -> CommunityIconWithDialog(it.communityInfo, showDialog = false) { }
                ObjectType.ROOM -> RoomIcon(it.roomInfo, showDialog = false) { }
                ObjectType.TOPIC -> {}
                ObjectType.USER -> UserIconWithDialog(it.receiverInfo)
                ObjectType.TITLE -> {}
                ObjectType.File -> {}
                ObjectType.PANEL_ACCOUNT -> {}
            }
        }
    }
}
