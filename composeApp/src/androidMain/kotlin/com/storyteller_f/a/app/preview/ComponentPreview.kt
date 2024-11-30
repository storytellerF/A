package com.storyteller_f.a.app.preview

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.storyteller_f.a.app.compontents.ButtonNav

@Preview
@Composable
private fun PreviewButtonNav() {
    ButtonNav(icon = Icons.Default.Settings, title = "Settings")
}
