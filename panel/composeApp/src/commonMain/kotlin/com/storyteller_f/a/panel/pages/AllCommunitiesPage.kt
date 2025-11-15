

package com.storyteller_f.a.panel.pages

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
import com.storyteller_f.a.panel.common.AllCommunitiesViewModel
import com.storyteller_f.a.panel.common.createPanelAllCommunitiesViewModel

@Composable
fun AllCommunitiesPage() {
    val viewModel = createPanelAllCommunitiesViewModel()
    AllCommunitiesPageInternal(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllCommunitiesPageInternal(viewModel: AllCommunitiesViewModel) {
    val panelNav = LocalPanelNav.current
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("All communities") }, navigationIcon = {
                IconButton({ panelNav.open() }) { Icon(Icons.Default.Menu, null) }
            })
        }
    ) { paddingValues ->
        Box(Modifier.padding(top = paddingValues.calculateTopPadding())) {
            StateView(viewModel) { items ->
                LazyColumn {
                    pagingItems(items, key = { it.id }) { index ->
                        val info = items[index]
                        if (info != null) {
                            ListItem(
                                headlineContent = { Text(info.name) },
                                overlineContent = { Text(info.aid ?: "") },
                                supportingContent = {
                                    val owner = info.owner?.toString() ?: ""
                                    val members = info.memberCount?.toString() ?: ""
                                    val policy = info.memberPolicy?.name ?: ""
                                    Text(listOf(owner, members, policy).filter { it.isNotEmpty() }.joinToString(" • "))
                                }
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
