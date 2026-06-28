package com.storyteller_f.a.app.utils

expect suspend fun saveTextToFile(suggestedName: String, extension: String, extensions: Set<String>, content: String)
