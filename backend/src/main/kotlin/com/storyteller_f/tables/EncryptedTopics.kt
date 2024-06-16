package com.storyteller_f.tables

import com.storyteller_f.shared.type.OKey
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table

object EncryptedTopics : Table() {
    val topicId = ulong("topic_id")
    val content = blob("content")

    override val primaryKey = PrimaryKey(topicId)
}

class EncryptedTopic(val topicId: OKey, val content: ByteArray) {
    companion object {
        fun wrapRow(row: ResultRow): EncryptedTopic {
            return EncryptedTopic(row[EncryptedTopics.topicId], row[EncryptedTopics.content].bytes)
        }
    }
}

object EncryptedTopicKeys : Table() {
    val topicId = ulong("topic_id")
    val uid = ulong("uid")
    val encryptedAes = blob("encrypted_aes")
}

class EncryptedTopicKey(val topicId: OKey, val uid: OKey, val encryptedAes: ByteArray) {
    companion object {
        fun wrapRow(row: ResultRow): EncryptedTopicKey {
            return EncryptedTopicKey(
                row[EncryptedTopicKeys.topicId],
                row[EncryptedTopicKeys.uid],
                row[EncryptedTopicKeys.encryptedAes].bytes
            )
        }
    }
}
