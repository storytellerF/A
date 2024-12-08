package com.storyteller_f.a.app.compontents

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import com.storyteller_f.a.client_lib.ServerErrorException

@Composable
fun ExceptionView(throwable: Throwable) {
    if (throwable is ServerErrorException) {
        val state = rememberRichTextState()

        LaunchedEffect(throwable.message) {
            state.setHtml(throwable.text)
        }

        BasicRichTextEditor(state = state, readOnly = true)
    } else {
        Text((throwable.message ?: throwable::class.simpleName ?: throwable.toString()).take(100))
    }
}
