package com.storyteller_f.tables

import com.storyteller_f.customPrimaryKey
import com.storyteller_f.shared.type.PrimaryKey
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table

object EncryptedTopics : Table() {
    val topicId = customPrimaryKey("topic_id").index()
    val content = blob("content")

    override val primaryKey = PrimaryKey(topicId)
}

class EncryptedTopic(val topicId: PrimaryKey, val content: ByteArray) {
    companion object {
        fun wrapRow(row: ResultRow): EncryptedTopic {
            return EncryptedTopic(row[EncryptedTopics.topicId], row[EncryptedTopics.content].bytes)
        }
    }
}

object EncryptedTopicKeys : Table() {
    val topicId = customPrimaryKey("topic_id").index()
    val uid = customPrimaryKey("uid")
    val encryptedAes = blob("encrypted_aes")

    init {
        index("encrypted-topic-key-main", true, topicId, uid)
    }
}

class EncryptedTopicKey(val topicId: PrimaryKey, val uid: PrimaryKey, val encryptedAes: ByteArray) {
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
