package com.storyteller_f.a.app.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.ExperimentalPagingApi
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import app.cash.paging.compose.itemContentType
import app.cash.paging.compose.itemKey
import com.storyteller_f.a.app.common.PagingViewModel
import com.storyteller_f.a.app.common.RegularPagingSource
import com.storyteller_f.a.app.common.StateView
import com.storyteller_f.a.app.common.viewModel
import com.storyteller_f.a.app.search.CustomSearchBar
import com.storyteller_f.a.app.search.SearchScope
import com.storyteller_f.a.client_lib.searchAllMembers
import com.storyteller_f.a.client_lib.searchCommunityMembers
import com.storyteller_f.a.client_lib.searchRoomMembers
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey

@Composable
fun MemberPage(objectId: PrimaryKey, objectType: ObjectType) {
    val viewModel = viewModel(keys = listOf("members", objectId)) {
        MemberViewModel(objectId, "", objectType)
    }
    val items = viewModel.flow.collectAsLazyPagingItems()

    Scaffold {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CustomSearchBar(
                when (objectType) {
                    ObjectType.COMMUNITY -> SearchScope.CommunityMember(objectId)
                    else -> SearchScope.RoomMember(objectId)
                }
            ) {
            }
            MemberList(items)
        }
    }
}

@Composable
fun MemberList(items: LazyPagingItems<UserInfo>) {
    StateView(items) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                count = items.itemCount,
                key = items.itemKey(),
                contentType = items.itemContentType()
            ) { index ->
                UserCell(items[index], true)
                Spacer(modifier = Modifier.height(20.dp))
                if (index != items.itemCount - 1) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalPagingApi::class)
class MemberViewModel(private val objectId: PrimaryKey, private val word: String, private val objectType: ObjectType) :
    PagingViewModel<PrimaryKey, UserInfo>({
        RegularPagingSource {
            when (objectType) {
                ObjectType.COMMUNITY -> searchCommunityMembers(objectId, it, 10, word)
                ObjectType.ROOM -> searchRoomMembers(objectId, it, 10, word)
                else -> searchAllMembers(it, 10, word)
            }.getOrThrow()
        }
    })
