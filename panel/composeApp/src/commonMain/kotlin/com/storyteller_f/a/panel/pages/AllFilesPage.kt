


package com.storyteller_f.a.panel.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.pagingItems
import com.storyteller_f.a.panel.LocalPanelNav
import com.storyteller_f.a.panel.Res
import com.storyteller_f.a.panel.all_files
import com.storyteller_f.a.panel.common.AllFilesViewModel
import com.storyteller_f.a.panel.common.createPanelAllFilesViewModel
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllFilesPage() {
    val viewModel = createPanelAllFilesViewModel()
    AllFilesPageInternal(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllFilesPageInternal(viewModel: AllFilesViewModel) {
    val panelNav = LocalPanelNav.current
    Scaffold(
        topBar = { TopAppBar(
            title = { Text(stringResource(Res.string.all_files)) },
            navigationIcon = { IconButton({ panelNav.open() }) { Icon(Icons.Default.Menu, null) } }
        ) }
    ) { paddingValues ->
        Box(Modifier.padding(top = paddingValues.calculateTopPadding())) {
            StateView(viewModel) { items ->
                LazyColumn {
                    pagingItems(items, key = { it.id }) { index ->
                        val info = items[index]
                        if (info != null) {
                            val name = info.name
                            val type = info.contentType
                            val size = info.size.toString()
                            val dim = info.dimension?.let { "${it.width}x${it.height}" } ?: ""
                            ListItem(
                                modifier = Modifier.clickable { panelNav.gotoFileDetail(info.id) },
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
