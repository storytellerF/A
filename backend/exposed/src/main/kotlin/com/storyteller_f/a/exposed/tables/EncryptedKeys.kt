package com.storyteller_f.a.exposed.tables

import com.storyteller_f.a.exposed.customPrimaryKey
import com.storyteller_f.shared.type.PrimaryKey
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table

object EncryptedKeys : Table() {
    val topicId = customPrimaryKey("topic_id").index()
    val uid = customPrimaryKey("uid")
    val encryptedAes = blob("encrypted_aes")

    init {
        index("encrypted-keys-main", true, topicId, uid)
    }
}

class EncryptedKey(val topicId: PrimaryKey, val uid: PrimaryKey, val encryptedAes: ByteArray) {
    companion object {
        fun wrapRow(row: ResultRow): EncryptedKey {
            return with(EncryptedKeys) {
                EncryptedKey(
                    row[topicId],
                    row[uid],
                    row[encryptedAes].bytes
                )
            }
        }
    }
}
