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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.client.compose_core.components.StateView
import com.storyteller_f.a.client.compose_core.components.pagingItems
import com.storyteller_f.a.client.compose_core.components.safeArea
import com.storyteller_f.a.panel.LocalPanelNav
import com.storyteller_f.a.panel.Res
import com.storyteller_f.a.panel.common.createPanelTaskRecordsViewModel
import com.storyteller_f.a.panel.common.createTaskRecordSummariesViewModel
import com.storyteller_f.a.panel.worker_records
import com.storyteller_f.shared.model.TaskFailureType
import com.storyteller_f.shared.model.TaskRecordInfo
import com.storyteller_f.shared.model.TaskRecordStatus
import com.storyteller_f.shared.model.TaskRecordSummary
import com.storyteller_f.shared.model.TaskRecordType
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskRecordsPage() {
    val panelNav = LocalPanelNav.current
    val viewModel = createTaskRecordSummariesViewModel()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.worker_records)) },
                navigationIcon = {
                    IconButton({ panelNav.open() }) {
                        Icon(Icons.Default.Menu, null)
                    }
                },
            )
        },
    ) { paddingValues ->
        val direction = LocalLayoutDirection.current
        StateView(viewModel.handler, Modifier.safeArea(paddingValues, direction).fillMaxSize()) { summaries ->
            LazyColumn {
                items(summaries, key = { it.type }) { summary ->
                    TaskRecordSummaryItem(summary) {
                        panelNav.gotoTaskRecordDetail(summary.type)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskRecordDetailPage(type: TaskRecordType) {
    val panelNav = LocalPanelNav.current
    val summariesViewModel = createTaskRecordSummariesViewModel()
    var status by rememberSaveable { mutableStateOf<TaskRecordStatus?>(null) }
    var failureType by rememberSaveable { mutableStateOf<TaskFailureType?>(null) }
    val recordsViewModel = createPanelTaskRecordsViewModel(type, status, failureType)
    val retryRequestedIds by recordsViewModel.retryRequestedIds.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(type.name) },
                navigationIcon = {
                    IconButton({ panelNav.open() }) {
                        Icon(Icons.Default.Menu, null)
                    }
                },
            )
        },
    ) { paddingValues ->
        val direction = LocalLayoutDirection.current
        Column(Modifier.safeArea(paddingValues, direction).fillMaxSize()) {
            StateView(summariesViewModel.handler) { summaries ->
                summaries.firstOrNull { it.type == type }?.let { summary ->
                    TaskRecordOverview(summary)
                }
            }
            TaskRecordStatusFilter(status) { status = it }
            if (status == TaskRecordStatus.FAILURE || failureType != null) {
                TaskFailureTypeFilter(failureType) { failureType = it }
            }
            StateView(recordsViewModel, modifier = Modifier.weight(1f)) { records ->
                LazyColumn {
                    pagingItems(records, key = { it.id }) { index ->
                        records[index]?.let { record ->
                            TaskRecordHistoryItem(
                                record,
                                record.retryRequested || record.id in retryRequestedIds,
                                recordsViewModel::markForRetry,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRecordSummaryItem(summary: TaskRecordSummary, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(summary.type.name) },
        supportingContent = {
            Text(
                "Success: ${summary.successCount} | Failure: ${summary.failureCount} | Retry: ${summary.retryRequestedCount}",
            )
        },
    )
    HorizontalDivider()
}

@Composable
private fun TaskRecordOverview(summary: TaskRecordSummary) {
    Text(
        "Success: ${summary.successCount} | Failure: ${summary.failureCount} | Retry requested: ${summary.retryRequestedCount}",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun TaskRecordStatusFilter(
    selectedStatus: TaskRecordStatus?,
    onSelectedStatusChanged: (TaskRecordStatus?) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        TaskRecordFilterChip("ALL", selectedStatus == null) { onSelectedStatusChanged(null) }
        TaskRecordStatus.entries.forEach { status ->
            TaskRecordFilterChip(status.name, selectedStatus == status) { onSelectedStatusChanged(status) }
        }
    }
}

@Composable
private fun TaskFailureTypeFilter(
    selectedFailureType: TaskFailureType?,
    onSelectedFailureTypeChanged: (TaskFailureType?) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        TaskRecordFilterChip("ALL FAILURES", selectedFailureType == null) {
            onSelectedFailureTypeChanged(null)
        }
        TaskFailureType.entries.forEach { failureType ->
            TaskRecordFilterChip(failureType.name, selectedFailureType == failureType) {
                onSelectedFailureTypeChanged(failureType)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskRecordFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.padding(end = 8.dp)) {
        FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
    }
}

@Composable
private fun TaskRecordHistoryItem(
    record: TaskRecordInfo,
    retryRequested: Boolean,
    onMarkForRetry: (Long) -> Unit,
) {
    ListItem(
        headlineContent = { Text("${record.status}: ${record.processedId}") },
        supportingContent = {
            Column {
                Text("Record: ${record.id} | Time: ${record.createdTime}")
                record.failureType?.let { Text("Failure type: $it") }
                record.failureReason?.let { reason ->
                    Text(reason, modifier = Modifier.widthIn(max = 720.dp))
                }
            }
        },
        trailingContent = {
            if (record.status == TaskRecordStatus.FAILURE) {
                Button(
                    enabled = !retryRequested,
                    onClick = { onMarkForRetry(record.id) },
                ) {
                    Text(if (retryRequested) "Retry requested" else "Retry")
                }
            }
        },
    )
    HorizontalDivider()
}
