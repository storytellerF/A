/*
 * This is a private project. All rights reserved.
 */
package com.storytellerf.a.panel.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.client.compose_core.components.safeArea
import com.storyteller_f.a.panel.LocalPanelNav
import com.storyteller_f.a.panel.Res
import com.storyteller_f.a.panel.common.CreateTaskConfigsViewModel
import com.storyteller_f.a.panel.common.TaskConfigEditorState
import com.storyteller_f.a.panel.no_task_configurations
import com.storyteller_f.a.panel.save
import com.storyteller_f.a.panel.task_config_fetch_size
import com.storyteller_f.a.panel.task_config_wait_duration
import com.storyteller_f.a.panel.worker_task_configurations
import org.jetbrains.compose.resources.stringResource

/** Displays persisted worker task configurations and their editable values. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskConfigsPage() {
    val panelNav = LocalPanelNav.current
    val viewModel = CreateTaskConfigsViewModel()
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.worker_task_configurations)) },
                navigationIcon = {
                    IconButton({ panelNav.open() }) {
                        Icon(Icons.Default.Menu, null)
                    }
                },
            )
        },
    ) { paddingValues ->
        val direction = LocalLayoutDirection.current
        Box(Modifier.safeArea(paddingValues, direction).fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                Column(Modifier.fillMaxSize()) {
                    uiState.error?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    if (uiState.configs.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.no_task_configurations),
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(uiState.configs, key = { it.type }) { config ->
                                TaskConfigEditor(
                                    config = config,
                                    onEnabledChange = { viewModel.updateEnabled(config.type, it) },
                                    onFetchSizeChange = { viewModel.updateFetchSize(config.type, it) },
                                    onWaitDurationChange = { viewModel.updateWaitDuration(config.type, it) },
                                    onSave = { viewModel.save(config.type) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskConfigEditor(
    config: TaskConfigEditorState,
    onEnabledChange: (Boolean) -> Unit,
    onFetchSizeChange: (String) -> Unit,
    onWaitDurationChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = config.type.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = config.isEnabled,
                    enabled = !config.isSaving,
                    onCheckedChange = onEnabledChange,
                )
            }
            OutlinedTextField(
                value = config.fetchSize,
                onValueChange = onFetchSizeChange,
                enabled = !config.isSaving,
                label = { Text(stringResource(Res.string.task_config_fetch_size)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = config.waitDurationMillis,
                onValueChange = onWaitDurationChange,
                enabled = !config.isSaving,
                label = { Text(stringResource(Res.string.task_config_wait_duration)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onSave,
                enabled = !config.isSaving,
                modifier = Modifier.align(Alignment.End),
            ) {
                if (config.isSaving) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(Res.string.save))
                }
            }
        }
    }
}
