package com.storyteller_f.a.app.user

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.cash.paging.compose.collectAsLazyPagingItems
import com.storyteller_f.a.app.compontents.UserIcon
import com.storyteller_f.a.app.model.createCommunityTopicsViewModel
import com.storyteller_f.a.app.model.createTopicSearchInUserViewModel
import com.storyteller_f.a.app.model.createUserViewModel
import com.storyteller_f.a.app.search.CustomSearchBar
import com.storyteller_f.a.app.search.SearchScope
import com.storyteller_f.a.app.world.TopicList
import com.storyteller_f.shared.type.PrimaryKey

@Composable
fun UserPage(uid: PrimaryKey) {
    val userViewModel = createUserViewModel(uid)
    val user by userViewModel.handler.data.collectAsState()
    Surface {
        Column {
            CustomSearchBar(SearchScope.UserTopic(uid)) {
                UserIcon(user)
            }
            val topicsViewModel = createTopicSearchInUserViewModel(SearchScope.UserTopic(uid), "")
            val pagingItems = topicsViewModel.flow.collectAsLazyPagingItems()
            TopicList(pagingItems)
        }
    }
}
