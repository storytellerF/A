package com.storyteller_f.a.client.core

import com.storyteller_f.shared.calcAddress
import com.storyteller_f.shared.decryptMessage
import com.storyteller_f.shared.getDerPrivateKey
import com.storyteller_f.shared.utils.mapResult
import kotlinx.serialization.Serializable

interface UserPass {
    suspend fun signature(data: String): Result<String>

    suspend fun verify(signature: String, data: String): Result<Boolean>

    suspend fun decrypt(encrypted: ByteArray, encryptedAesKey: ByteArray): Result<String>

    suspend fun address(): Result<String>
}

sealed interface ClientSessionState {
    data object None : ClientSessionState
    data class Success(val session: UserPass) : ClientSessionState
}

@Serializable
data class RawUserPassInfo(
    val pemPrivateKey: String,
    val derPublicKey: String,
    val address: String,
)

data class RawUserPass(val rawUSerPass: RawUserPassInfo) : UserPass {
    override suspend fun signature(data: String): Result<String> {
        return com.storyteller_f.shared.signature(rawUSerPass.pemPrivateKey, data)
    }

    override suspend fun verify(signature: String, data: String): Result<Boolean> {
        return com.storyteller_f.shared.verify(rawUSerPass.derPublicKey, signature, data)
    }

    override suspend fun decrypt(encrypted: ByteArray, encryptedAesKey: ByteArray): Result<String> {
        return getDerPrivateKey(rawUSerPass.pemPrivateKey).mapResult { derPrivateKeyStr ->
            decryptMessage(
                derPrivateKeyStr,
                encrypted,
                encryptedAesKey
            )
        }
    }

    override suspend fun address(): Result<String> {
        return calcAddress(rawUSerPass.derPublicKey)
    }
}
