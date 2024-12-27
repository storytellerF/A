package com.storyteller_f.a.app.user

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.cash.paging.compose.collectAsLazyPagingItems
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.compontents.UserIcon
import com.storyteller_f.a.app.model.createTopicSearchInUserViewModel
import com.storyteller_f.a.app.model.createUserViewModel
import com.storyteller_f.a.app.search.CustomSearchBar
import com.storyteller_f.a.app.search.SearchScope
import com.storyteller_f.a.app.world.TopicList
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey

@Composable
fun UserPage(uid: PrimaryKey) {
    val userViewModel = createUserViewModel(uid)
    val user by userViewModel.handler.data.collectAsState()
    val my by LoginViewModel.user.collectAsState()
    UserPageInternal(user, my, uid)
}

@Composable
private fun UserPageInternal(
    user: UserInfo?,
    my: UserInfo?,
    uid: PrimaryKey
) {
    val appNav = LocalAppNav.current
    Scaffold(floatingActionButton = {
        if (user != null && my?.id == user.id) {
            FloatingActionButton({
                appNav.gotoTopicCompose(ObjectType.USER, user.id, false, null)
            }) {
                Icon(Icons.Default.Add, "add topic")
            }
        }
    }) {
        Column(
            modifier = Modifier.padding(bottom = it.calculateBottomPadding()),
        ) {
            CustomSearchBar(SearchScope.UserTopic(uid)) {
                UserIcon(user)
            }
            val topicsViewModel = createTopicSearchInUserViewModel(SearchScope.UserTopic(uid), "")
            val pagingItems = topicsViewModel.flow.collectAsLazyPagingItems()
            TopicList(pagingItems)
        }
    }
}
