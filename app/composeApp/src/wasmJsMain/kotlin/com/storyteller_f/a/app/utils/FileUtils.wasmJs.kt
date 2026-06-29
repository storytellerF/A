package com.storyteller_f.a.app.utils

actual suspend fun saveTextToFile(
    suggestedName: String,
    extension: String,
    extensions: Set<String>,
    content: String,
) = Unit
