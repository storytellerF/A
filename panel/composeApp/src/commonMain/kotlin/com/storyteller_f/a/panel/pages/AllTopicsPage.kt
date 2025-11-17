

package com.storyteller_f.a.panel.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.pagingItems
import com.storyteller_f.a.panel.LocalPanelNav
import com.storyteller_f.a.panel.Res
import com.storyteller_f.a.panel.all_topics
import com.storyteller_f.a.panel.common.AllTopicsViewModel
import com.storyteller_f.a.panel.common.createPanelAllTopicsViewModel
import com.storyteller_f.a.panel.encrypted
import com.storyteller_f.a.panel.interaction
import com.storyteller_f.a.panel.pinned
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
                                stringResource(Res.string.interaction, info.commentCount, info.reactionCount)
                            } else {
                                ""
                            }
                            val flags = listOfNotNull(
                                if (info.isEncrypted) stringResource(Res.string.encrypted) else null,
                                if (info.isPin) stringResource(Res.string.pinned) else null
                            ).joinToString(" • ")
                            val supporting = listOf(
                                info.createdTime.toString(),
                                counts,
                                flags
                            ).filter { it.isNotEmpty() }.joinToString(" • ")

                            ListItem(
                                modifier = Modifier.clickable { panelNav.gotoTopicDetail(info.id) },
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
