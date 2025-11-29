package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.types.EncryptedKey
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table

object EncryptedKeys : Table() {
    val topicId = customPrimaryKey("topic_id").index()
    val uid = customPrimaryKey("uid")
    val encryptedAes = blob("encrypted_aes")

    init {
        index("encrypted-keys-main", true, topicId, uid)
    }
}

fun EncryptedKey.Companion.wrapRow(row: ResultRow): EncryptedKey {
    return with(EncryptedKeys) {
        EncryptedKey(row[topicId], row[uid], row[encryptedAes].bytes)
    }
}
