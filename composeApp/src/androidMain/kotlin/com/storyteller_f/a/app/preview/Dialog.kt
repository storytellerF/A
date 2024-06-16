package com.storyteller_f.a.app.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.storyteller_f.a.app.compontents.DialogState
import com.storyteller_f.a.app.compontents.EventAlertDialog

@Preview(showSystemUi = true)
@Composable
private fun PreviewLoading() {
    EventAlertDialog(DialogState.Loading) {

    }
}

@Preview(showSystemUi = true)
@Composable
private fun PreviewError() {
    EventAlertDialog(DialogState.Error(Exception("Error 404"))) {

    }
}
