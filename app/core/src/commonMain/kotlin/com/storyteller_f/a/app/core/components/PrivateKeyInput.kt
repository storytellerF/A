package com.storyteller_f.a.app.core.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.core.Res
import com.storyteller_f.a.app.core.cancel
import com.storyteller_f.a.app.core.confirm
import com.storyteller_f.a.app.core.copied
import com.storyteller_f.a.app.core.copy
import com.storyteller_f.a.app.core.edit_private_key
import com.storyteller_f.a.app.core.generate
import com.storyteller_f.a.app.core.private_key
import com.storyteller_f.shared.getAlgo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateKeyInput(privateKey: String, address: String?, update: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.padding(top = 10.dp).fillMaxWidth().semantics {
            contentDescription = "Private Key Input"
        }.testTag("privateKeyInput"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shape = RoundedCornerShape(10.dp)
        Text(
            address ?: "",
            modifier = Modifier.weight(1f)
                .border(1.dp, MaterialTheme.colorScheme.primaryContainer, shape)
                .padding(8.dp)
        )
        IconButton({
            showDialog = true
        }) {
            Icon(Icons.Default.Edit, stringResource(Res.string.edit_private_key))
        }
    }

    if (showDialog) {
        PrivateKeyDialog(privateKey, { showDialog = false }) {
            update(it)
            showDialog = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateKeyDialog(privateKey: String, onDismissRequest: () -> Unit, onConfirm: (String) -> Unit) {
    var currentKey by remember { mutableStateOf(privateKey) }
    val clipboard = LocalClipboard.current
    val toast = LocalToaster.current
    val scope = rememberCoroutineScope()

    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        DialogContainer {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(Res.string.edit_private_key), style = MaterialTheme.typography.titleMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = {
                        scope.launch {
                            getAlgo().generatePemKeyPair().onSuccess {
                                currentKey = it.first
                            }
                        }
                    }) {
                        Icon(Icons.Default.Casino, stringResource(Res.string.generate))
                    }

                    IconButton(onClick = {
                        scope.launch {
                            clipboard.setText(currentKey)
                            toast.showMessage(getString(Res.string.copied))
                        }
                    }) {
                        Icon(Icons.Default.ContentCopy, stringResource(Res.string.copy))
                    }
                }

                OutlinedTextField(
                    value = currentKey,
                    onValueChange = { currentKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.private_key)) },
                    minLines = 3,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton({
                        onDismissRequest()
                    }) {
                        Text(stringResource(Res.string.cancel))
                    }
                    TextButton(onClick = {
                        onConfirm(currentKey)
                    }) {
                        Text(stringResource(Res.string.confirm))
                    }
                }
            }
        }
    }
}
