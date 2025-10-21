package com.storyteller_f.a.cloud.panel.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.storyteller_f.a.app.core.compontents.StateView
import com.storyteller_f.a.app.core.compontents.pagingItems
import com.storyteller_f.a.cloud.panel.common.AllUsersViewModel
import com.storyteller_f.a.cloud.panel.common.createPanelAllUserViewModel
import com.storyteller_f.a.cloud.panel.components.UserCell

@Composable
fun AllUsersPage() {
    val viewModel = createPanelAllUserViewModel()
    AllUsersPageInternal(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllUsersPageInternal(viewModel: AllUsersViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text("Home")
            })
        }
    ) {
        Box(Modifier.padding(top = it.calculateTopPadding())) {
            StateView(viewModel) { items ->
                LazyColumn {
                    pagingItems(items, key = {
                        it.id
                    }) {
                        UserCell(items.get(it))
                    }
                }
            }
        }
    }
}
