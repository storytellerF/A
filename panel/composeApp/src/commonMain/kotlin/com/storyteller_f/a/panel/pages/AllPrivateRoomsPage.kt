

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
import com.storyteller_f.a.panel.Res
import com.storyteller_f.a.panel.all_private_rooms
import com.storyteller_f.a.panel.common.AllPrivateRoomsViewModel
import com.storyteller_f.a.panel.common.createPanelAllPrivateRoomsViewModel
import org.jetbrains.compose.resources.stringResource

@Composable
fun AllPrivateRoomsPage() {
    val viewModel = createPanelAllPrivateRoomsViewModel()
    AllPrivateRoomsPageInternal(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllPrivateRoomsPageInternal(viewModel: AllPrivateRoomsViewModel) {
    val panelNav = LocalPanelNav.current
    Scaffold(
        topBar = { TopAppBar(
            title = { Text(stringResource(Res.string.all_private_rooms)) },
            navigationIcon = { IconButton({ panelNav.open() }) { Icon(Icons.Default.Menu, null) } }
        ) }
    ) { paddingValues ->
        Box(Modifier.padding(top = paddingValues.calculateTopPadding())) {
            StateView(viewModel) { items ->
                LazyColumn {
                    pagingItems(items, key = { it.id }) { index ->
                        val info = items[index]
                        if (info != null) {
                            val members = info.memberCount.toString()
                            val aid = info.aid
                            ListItem(
                                headlineContent = { Text(info.name) },
                                overlineContent = { Text(aid) },
                                supportingContent = {
                                    Text(
                                        listOf(members, "私有").filter { it.isNotEmpty() }.joinToString(" • ")
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
