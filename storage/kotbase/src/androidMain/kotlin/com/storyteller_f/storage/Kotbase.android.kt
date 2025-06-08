package com.storyteller_f.storage

import android.content.Context
import kotbase.CouchbaseLite

fun loadKotbaseIfNeed(context: Context) {
    CouchbaseLite.init(context, true)
}
