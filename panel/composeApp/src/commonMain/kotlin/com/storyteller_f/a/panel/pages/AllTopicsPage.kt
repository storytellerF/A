

package com.storyteller_f.a.panel.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.pagingItems
import com.storyteller_f.a.panel.common.AllTopicsViewModel
import com.storyteller_f.a.panel.common.createPanelAllTopicsViewModel
import com.storyteller_f.a.panel.LocalPanelNav

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
            title = { Text("All topics") },
            navigationIcon = { IconButton({ panelNav.open() }) { Icon(Icons.Default.Menu, null) } }
        ) }
    ) {
        Box(Modifier.padding(top = it.calculateTopPadding())) {
            StateView(viewModel) { items ->
                LazyColumn {
                    pagingItems(items, key = { it.id }) { index ->
                        val info = items[index]
                        if (info != null) {
                            val text = when (val content = info.content) {
                                is com.storyteller_f.shared.model.TopicContent.Plain -> content.plain
                                is com.storyteller_f.shared.model.TopicContent.Extracted -> content.plain
                                else -> ""
                            }
                            val author = info.extension?.authorInfo?.nickname ?: info.author.toString()
                            val room = info.extension?.roomInfo?.name ?: ""
                            val overline = listOf(author, room).filter { it.isNotEmpty() }.joinToString(" @ ")
                            val counts = if (info.commentCount > 0 || info.reactionCount > 0) {
                                "互动 ${info.commentCount}/${info.reactionCount}"
                            } else {
                                ""
                            }
                            val flags = listOfNotNull(
                                if (info.isEncrypted) "加密" else null,
                                if (info.isPin) "置顶" else null
                            ).joinToString(" • ")
                            val supporting = listOf(
                                info.createdTime.toString(),
                                counts,
                                flags
                            ).filter { it.isNotEmpty() }.joinToString(" • ")

                            ListItem(
                                headlineContent = { Text(text) },
                                overlineContent = { Text(overline) },
                                supportingContent = {
                                    Text(supporting)
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
