package com.storyteller_f.a.app.core.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import com.storyteller_f.a.app.core.Res
import com.storyteller_f.a.app.core.refresh
import com.storyteller_f.a.client.core.ServerErrorException
import com.storyteller_f.shared.utils.safeMessage
import io.ktor.client.network.sockets.*
import org.jetbrains.compose.resources.stringResource

fun ServerErrorException.isHtmlContent(): Boolean = text.startsWith("<html") || text.startsWith("<!DOCTYPE html")

@OptIn(ExperimentalRichTextApi::class)
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
            Text(throwable.safeMessage(), modifier)
        }
    }
}

@Composable
fun ExceptionCell(
    throwable: Throwable,
    extraRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExceptionView(throwable)
        Button({
            extraRefresh()
        }, modifier = Modifier) {
            Text(stringResource(Res.string.refresh))
        }
    }
}
