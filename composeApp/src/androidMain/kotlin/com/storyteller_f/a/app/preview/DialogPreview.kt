package com.storyteller_f.a.app.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.storyteller_f.a.app.compontents.CustomAlertDialogInternal
import com.storyteller_f.a.app.compontents.CustomAlertDialogState
import com.storyteller_f.a.app.compontents.DialogState
import com.storyteller_f.a.app.compontents.GlobalDialogInternal

@Preview(showSystemUi = true)
@Composable
private fun PreviewLoading() {
    GlobalDialogInternal(DialogState.Loading) {
    }
}

@Preview(showSystemUi = true)
@Composable
private fun PreviewError() {
    GlobalDialogInternal(DialogState.Error(Exception("Error 404"))) {
    }
}

@Preview
@Composable
private fun PreviewCustomDialog() {
    CustomAlertDialogInternal({
    }, CustomAlertDialogState(null, "test")) {
    }
}
