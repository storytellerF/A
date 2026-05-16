package com.storyteller_f.a.panel.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.core.components.StateView
import com.storyteller_f.a.app.core.components.pagingItems
import com.storyteller_f.a.app.core.components.safeArea
import com.storyteller_f.a.panel.LocalPanelNav
import com.storyteller_f.a.panel.Res
import com.storyteller_f.a.panel.common.createPanelTaskRecordsViewModel
import com.storyteller_f.a.panel.task_record_supporting
import com.storyteller_f.a.panel.worker_records
import com.storyteller_f.shared.model.TaskRecordInfo
import com.storyteller_f.shared.model.TaskRecordType
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskRecordsPage() {
    val panelNav = LocalPanelNav.current
    var type by rememberSaveable { mutableStateOf<TaskRecordType?>(null) }
    val viewModel = createPanelTaskRecordsViewModel(type)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.worker_records)) },
                navigationIcon = {
                    IconButton({ panelNav.open() }) {
                        Icon(Icons.Default.Menu, null)
                    }
                }
            )
        }
    ) { paddingValues ->
        val direction = LocalLayoutDirection.current
        Column(Modifier.safeArea(paddingValues, direction).fillMaxSize()) {
            TaskRecordTypeFilter(type) {
                type = it
            }
            StateView(viewModel, modifier = Modifier.weight(1f)) { items ->
                LazyColumn {
                    pagingItems(items, key = { it.id }) { index ->
                        val info = items[index]
                        if (info != null) {
                            TaskRecordItem(info)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskRecordTypeFilter(
    selectedType: TaskRecordType?,
    onSelectedTypeChanged: (TaskRecordType?) -> Unit
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        TaskRecordTypeChip(
            label = "ALL",
            selected = selectedType == null,
            onClick = { onSelectedTypeChanged(null) }
        )
        TaskRecordType.entries.forEach { type ->
            TaskRecordTypeChip(
                label = type.name,
                selected = selectedType == type,
                onClick = { onSelectedTypeChanged(type) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskRecordTypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.padding(end = 8.dp)) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(label) }
        )
    }
}

@Composable
private fun TaskRecordItem(info: TaskRecordInfo) {
    val nav = LocalPanelNav.current
    ListItem(
        modifier = Modifier.clickable {
            when (info.type) {
                TaskRecordType.INTRO -> nav.gotoUserDetail(info.processedId)
                TaskRecordType.TITLE -> nav.gotoTitleDetail(info.processedId)
                TaskRecordType.SUBSCRIPTION,
                TaskRecordType.TOPIC_ACG -> nav.gotoTopicDetail(info.processedId)
            }
        },
        headlineContent = { Text(info.type.name) },
        supportingContent = {
            Text(
                stringResource(
                    Res.string.task_record_supporting,
                    info.processedId.toString(),
                    info.id.toString(),
                    info.createdTime.toString()
                ),
                modifier = Modifier.widthIn(max = 720.dp)
            )
        }
    )
    HorizontalDivider()
}
