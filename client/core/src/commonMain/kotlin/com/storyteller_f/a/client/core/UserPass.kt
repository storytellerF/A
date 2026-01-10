package com.storyteller_f.a.client.core

import com.storyteller_f.shared.decryptMessage
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.model.AlgoType
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
    val derPrivateKey: String,
    val derPublicKey: String,
    val address: String,
    val algo: AlgoType = AlgoType.P256,
    val encryptionDerPrivateKey: String? = null,
    val encryptionDerPublicKey: String? = null,
)

data class RawUserPass(val rawUSerPass: RawUserPassInfo) : UserPass {
    override suspend fun signature(data: String): Result<String> {
        return getAlgo(rawUSerPass.algo).signature(rawUSerPass.derPrivateKey, data)
    }

    override suspend fun verify(signature: String, data: String): Result<Boolean> {
        return getAlgo(rawUSerPass.algo).verify(rawUSerPass.derPublicKey, signature, data)
    }

    override suspend fun decrypt(encrypted: ByteArray, encryptedAesKey: ByteArray): Result<String> {
        val (epk, algo) = if (rawUSerPass.algo == AlgoType.DILITHIUM && rawUSerPass.encryptionDerPrivateKey != null) {
            rawUSerPass.encryptionDerPrivateKey to getAlgo(rawUSerPass.algo)
        } else {
            rawUSerPass.derPrivateKey to getAlgo(rawUSerPass.algo)
        }
        return algo.decryptMessage(epk, encrypted, encryptedAesKey)
    }

    override suspend fun address(): Result<String> {
        return getAlgo(rawUSerPass.algo).calcAddress(rawUSerPass.derPublicKey)
    }
}
