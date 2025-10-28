package com.storyteller_f.a.client.core

import com.storyteller_f.shared.getAlgo
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
    data class Success(val userPass: UserPass) : ClientSessionState
}

@Serializable
data class RawUserPassInfo(
    val pemPrivateKey: String,
    val derPublicKey: String,
    val address: String,
)

data class RawUserPass(val rawUSerPass: RawUserPassInfo) : UserPass {
    override suspend fun signature(data: String): Result<String> {
        return getAlgo().signature(rawUSerPass.pemPrivateKey, data)
    }

    override suspend fun verify(signature: String, data: String): Result<Boolean> {
        return getAlgo().verify(rawUSerPass.derPublicKey, signature, data)
    }

    override suspend fun decrypt(encrypted: ByteArray, encryptedAesKey: ByteArray): Result<String> {
        return getAlgo().run {
            getDerPrivateKey(rawUSerPass.pemPrivateKey).mapResult { derPrivateKeyStr ->
                decryptMessage(derPrivateKeyStr, encrypted, encryptedAesKey)
            }
        }
    }

    override suspend fun address(): Result<String> {
        return getAlgo().calcAddress(rawUSerPass.derPublicKey)
    }
}
