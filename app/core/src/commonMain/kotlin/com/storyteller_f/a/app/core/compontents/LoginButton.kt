package com.storyteller_f.a.app.core.compontents

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.core.Res
import com.storyteller_f.a.app.core.sign_in
import org.jetbrains.compose.resources.stringResource

@Composable
fun LoginButton(onClick: () -> Unit = {}) {
    val signIn = stringResource(Res.string.sign_in)
    Button({
        onClick()
    }) {
        Icon(
            Icons.AutoMirrored.Default.Login,
            signIn
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(signIn)
    }
}
