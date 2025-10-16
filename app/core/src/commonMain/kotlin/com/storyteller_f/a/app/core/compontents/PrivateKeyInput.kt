package com.storyteller_f.a.app.core.compontents

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun PrivateKeyInput(privateKey: String, update: (String) -> Unit, startSign: () -> Unit) {
    MeasureTextLineCount(privateKey, LocalTextStyle.current, 32.dp) { lineCount, _ ->
        OutlinedTextField(
            privateKey,
            onValueChange = {
                update(it)
            },
            modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
            maxLines = max(lineCount, 2),
            minLines = max(lineCount, 2),
            label = {
                Text("Private Key")
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                startSign()
            })
        )
    }
}
