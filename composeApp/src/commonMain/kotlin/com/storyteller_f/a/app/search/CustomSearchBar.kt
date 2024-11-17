package com.storyteller_f.a.app.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paging.compose.collectAsLazyPagingItems
import com.storyteller_f.a.app.LocalAppNav
import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.common.APagingData
import com.storyteller_f.a.app.common.PagingViewModel
import com.storyteller_f.a.app.common.SimplePagingSource
import com.storyteller_f.a.app.common.serviceCatching
import com.storyteller_f.a.app.common.viewModel
import com.storyteller_f.a.app.compontents.UserIcon
import com.storyteller_f.a.app.world.TopicList
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.a.client_lib.searchTopics
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKeyOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSearchBar(leadingIcon: @Composable () -> Unit) {
    val appNav = LocalAppNav.current
    var query by remember {
        mutableStateOf("")
    }
    var searchQuery by remember {
        mutableStateOf("")
    }
    var active by remember {
        mutableStateOf(false)
    }
    val onActiveChange = { newValue: Boolean ->
        active = newValue
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = {
                        query = it
                    },
                    onSearch = {
                        searchQuery = it
                    },
                    expanded = active,
                    onExpandedChange = onActiveChange,
                    leadingIcon = {
                        leadingIcon()
                    },
                    trailingIcon = {
                        val userInfo by LoginViewModel.user.collectAsState<UserInfo?>()
                        UserIcon(userInfo, 40.dp, appNav::gotoLogin)
                    },
                )
            },
            expanded = active,
            onExpandedChange = onActiveChange,
            modifier = Modifier.align(Alignment.Center),
            content = {
                if (searchQuery.isNotBlank()) {
                    val viewModel = viewModel(TopicSearchViewModel::class, keys = listOf("topic", query)) {
                        TopicSearchViewModel(query.split(" "))
                    }
                    val topics = viewModel.flow.collectAsLazyPagingItems()
                    TopicList(topics)
                } else {
                    Box {
                        Text("input word to search topics")
                    }
                }
            },
        )
    }
}

class TopicSearchViewModel(word: List<String>) : PagingViewModel<PrimaryKey, TopicInfo>({
    SimplePagingSource {
        serviceCatching {
            client.searchTopics(it, 10, word)
        }.map {
            APagingData(it.data, it.pagination?.nextPageToken?.toPrimaryKeyOrNull())
        }
    }
})
