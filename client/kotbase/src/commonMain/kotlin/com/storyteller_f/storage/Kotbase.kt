package com.storyteller_f.storage

import kotlinx.serialization.json.Json

expect fun createKotbaseStorageSource(scope: String?, json: Json): DocumentSource
