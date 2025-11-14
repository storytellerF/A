


package com.storyteller_f.a.panel.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.pagingItems
import com.storyteller_f.a.panel.common.AllTitlesViewModel
import com.storyteller_f.a.panel.common.createPanelAllTitlesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTitlesPage() {
    val viewModel = createPanelAllTitlesViewModel()
    AllTitlesPageInternal(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTitlesPageInternal(viewModel: AllTitlesViewModel) {
    Scaffold(topBar = { TopAppBar(title = { Text("All titles") }) }) {
        Box(Modifier.padding(top = it.calculateTopPadding())) {
            StateView(viewModel) { items ->
                LazyColumn {
                    pagingItems(items, key = { it.id }) { index ->
                        val info = items[index]
                        if (info != null) {
                            val type = info.type?.name ?: ""
                            val scope = info.scopeType?.name ?: ""
                            val creator = info.creator?.toString() ?: ""
                            val receiver = info.receiver?.toString() ?: ""
                            ListItem(
                                headlineContent = { Text(info.name ?: "") },
                                overlineContent = { Text(type) },
                                supportingContent = {
                                    Text(
                                        listOf(scope, creator, receiver).filter { it.isNotEmpty() }.joinToString(" • ")
                                    )
                                },
                            )
                            HorizontalDivider()
                        } else {
                            ListItem(headlineContent = { Text("") })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
