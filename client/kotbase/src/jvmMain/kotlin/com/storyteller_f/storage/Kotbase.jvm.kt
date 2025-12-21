package com.storyteller_f.storage

import kotbase.CouchbaseLite

fun loadKotbaseIfNeed() {
    CouchbaseLite.init()
}
