


package com.storyteller_f.a.panel.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.ui.platform.LocalLayoutDirection
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.pagingItems
import com.storyteller_f.a.panel.LocalPanelNav
import com.storyteller_f.a.panel.Res
import com.storyteller_f.a.panel.all_titles
import com.storyteller_f.a.panel.common.AllTitlesViewModel
import com.storyteller_f.a.panel.common.createPanelAllTitlesViewModel
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTitlesPage() {
    val viewModel = createPanelAllTitlesViewModel()
    AllTitlesPageInternal(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTitlesPageInternal(viewModel: AllTitlesViewModel) {
    val panelNav = LocalPanelNav.current
    Scaffold(
        topBar = { TopAppBar(
            title = { Text(stringResource(Res.string.all_titles)) },
            navigationIcon = { IconButton({ panelNav.open() }) { Icon(Icons.Default.Menu, null) } }
        ) }
    ) {
        val direction = LocalLayoutDirection.current
        Box(
            Modifier.padding(
                top = it.calculateTopPadding(),
                start = it.calculateStartPadding(direction),
                end = it.calculateEndPadding(direction)
            )
        ) {
            StateView(viewModel) { items ->
                LazyColumn {
                    pagingItems(items, key = { it.id }) { index ->
                        val info = items[index]
                        if (info != null) {
                            val type = info.type.name
                            val scope = info.scopeType.name
                            val creator = info.creator.toString()
                            val receiver = info.receiver.toString()
                            ListItem(
                                modifier = Modifier.clickable { panelNav.gotoTitleDetail(info.id) },
                                headlineContent = { Text(info.name) },
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
