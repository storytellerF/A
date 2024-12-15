package com.storyteller_f.a.app.compontents

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import com.storyteller_f.a.client_lib.ServerErrorException
import io.ktor.client.network.sockets.*

@Composable
fun ExceptionView(throwable: Throwable) {
    if (throwable is ServerErrorException) {
        if (throwable.text.isNotBlank() && throwable.text.startsWith("<html")) {
            val state = rememberRichTextState()

            LaunchedEffect(throwable.message) {
                state.setHtml(throwable.text)
            }

            RichText(state = state)
        } else {
            Text("${throwable.status.value} ${throwable.text}")
        }
    } else if (throwable is SocketTimeoutException) {
        Text("Timeout")
    } else {
        Text((throwable.message ?: throwable::class.simpleName ?: throwable.toString()).take(100))
    }
}
