package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.type.PrimaryKey

class EncryptedKey(val topicId: PrimaryKey, val uid: PrimaryKey, val encryptedAes: ByteArray) {
    companion object
}
