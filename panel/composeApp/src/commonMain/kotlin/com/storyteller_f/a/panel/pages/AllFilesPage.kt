


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
import com.storyteller_f.a.panel.common.AllFilesViewModel
import com.storyteller_f.a.panel.common.createPanelAllFilesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllFilesPage() {
    val viewModel = createPanelAllFilesViewModel()
    AllFilesPageInternal(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllFilesPageInternal(viewModel: AllFilesViewModel) {
    Scaffold(topBar = { TopAppBar(title = { Text("All files") }) }) {
        Box(Modifier.padding(top = it.calculateTopPadding())) {
            StateView(viewModel) { items ->
                LazyColumn {
                    pagingItems(items, key = { it.id }) { index ->
                        val info = items[index]
                        if (info != null) {
                            val name = info.name ?: info.fullName ?: ""
                            val type = info.contentType ?: ""
                            val size = info.size?.toString() ?: ""
                            val dim = info.dimension?.let { "${it.width}x${it.height}" } ?: ""
                            ListItem(
                                headlineContent = { Text(name) },
                                overlineContent = { Text(type) },
                                supportingContent = {
                                    Text(
                                        listOf(size, dim).filter { it.isNotEmpty() }.joinToString(" • ")
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
