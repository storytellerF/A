package com.storyteller_f.a.app.compontents

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun VideoView(modifier: Modifier, url: String, contentType: String)
