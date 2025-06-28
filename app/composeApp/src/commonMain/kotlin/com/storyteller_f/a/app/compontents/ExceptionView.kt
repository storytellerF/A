package com.storyteller_f.a.app.compontents

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import com.storyteller_f.a.client.core.ServerErrorException
import io.ktor.client.network.sockets.*

@Composable
fun ExceptionView(throwable: Throwable, modifier: Modifier = Modifier) {
    when (throwable) {
        is ServerErrorException -> {
            if (throwable.text.isNotBlank() && throwable.isHtmlContent()) {
                val state = rememberRichTextState()

                LaunchedEffect(throwable.message) {
                    state.setHtml(throwable.text)
                }

                RichText(state = state, modifier = modifier)
            } else {
                Text("${throwable.status} ${throwable.text}", modifier)
            }
        }

        is SocketTimeoutException, is ConnectTimeoutException -> {
            Text("Timeout", modifier)
        }

        else -> {
            Text((throwable.message ?: throwable::class.simpleName ?: throwable.toString()), modifier)
        }
    }
}
