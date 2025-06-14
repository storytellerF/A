package com.storyteller_f.a.app.compontents

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun CharSequenceText(content: CharSequence) {
    if (content is String) {
        Text(content)
    } else {
        Text(content::class.simpleName.toString())
    }
}
