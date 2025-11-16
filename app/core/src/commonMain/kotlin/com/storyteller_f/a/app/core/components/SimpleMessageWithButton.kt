package com.storyteller_f.a.app.core.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun SimpleMessageWithButton(string: String, key: String) {
    val alterDialogController = rememberAlertDialogController()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(string, maxLines = 1, modifier = Modifier.weight(1f))
        IconButton({
            alterDialogController.showMessage(key, string)
        }) {
            Icon(Icons.Default.Fullscreen, "fullscreen")
        }
    }
    CustomAlertDialog(alterDialogController, {
        alterDialogController.close()
    }) {
    }
}
