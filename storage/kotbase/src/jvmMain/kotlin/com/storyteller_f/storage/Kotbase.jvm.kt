package com.storyteller_f.storage

import kotbase.CouchbaseLite

actual fun loadKotbaseIfNeed() {
    CouchbaseLite.init()
}