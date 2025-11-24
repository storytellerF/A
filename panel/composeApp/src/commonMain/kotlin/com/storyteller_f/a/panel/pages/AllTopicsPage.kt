

package com.storyteller_f.a.panel.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.pagingItems
import com.storyteller_f.a.app.core.components.safeArea
import com.storyteller_f.a.panel.LocalPanelNav
import com.storyteller_f.a.panel.Res
import com.storyteller_f.a.panel.all_topics
import com.storyteller_f.a.panel.common.AllTopicsViewModel
import com.storyteller_f.a.panel.common.createPanelAllTopicsViewModel
import com.storyteller_f.a.panel.components.TopicCell
import org.jetbrains.compose.resources.stringResource

@Composable
fun AllTopicsPage() {
    val viewModel = createPanelAllTopicsViewModel()
    AllTopicsPageInternal(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTopicsPageInternal(viewModel: AllTopicsViewModel) {
    val panelNav = LocalPanelNav.current
    Scaffold(
        topBar = { TopAppBar(
            title = { Text(stringResource(Res.string.all_topics)) },
            navigationIcon = { IconButton({ panelNav.open() }) { Icon(Icons.Default.Menu, null) } }
        ) }
    ) {
        val direction = LocalLayoutDirection.current
        Box(Modifier.safeArea(it, direction)) {
            StateView(viewModel) { items ->
                LazyColumn {
                    pagingItems(items, key = { it.id }) { index ->
                        val info = items[index]
                        if (info != null) {
                            val panelNav = LocalPanelNav.current
                            TopicCell(info, panelNav)
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
