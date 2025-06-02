package com.storyteller_f.storage

import android.content.Context
import kotbase.CouchbaseLite

actual fun loadKotbaseIfNeed() = Unit

fun loadKotbaseIfNeed(context: Context) {
    CouchbaseLite.init(context, true)
}
