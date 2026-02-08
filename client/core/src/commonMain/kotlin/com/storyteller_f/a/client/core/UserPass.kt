package com.storyteller_f.a.client.core

import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.shared.decryptDataByAES
import com.storyteller_f.shared.decryptMessage
import com.storyteller_f.shared.encryptDataByAES
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.model.AlgoType
import kotlinx.serialization.Serializable
import kotlin.io.encoding.ExperimentalEncodingApi

interface UserPass {
    suspend fun signature(data: String): Result<String>

    suspend fun verify(signature: String, data: String): Result<Boolean>

    suspend fun decrypt(encrypted: ByteArray, encryptedAesKey: ByteArray): Result<String>

    suspend fun address(): Result<String>

    suspend fun decryptChildAccount(
        encryptedPrivateKey: String,
        encryptedAesKey: String,
        childAlgoType: AlgoType
    ): Result<String>

    suspend fun encryptChildAccount(): Result<CustomApi.Accounts.ChildAccounts.AddChildAccountRequest>
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

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun decryptChildAccount(
        encryptedPrivateKey: String,
        encryptedAesKey: String,
        childAlgoType: AlgoType
    ): Result<String> {
        // First decrypt the AES key using user's private key
        val (epk, algo) = if (rawUSerPass.algo == AlgoType.DILITHIUM) {
            rawUSerPass.encryptionDerPrivateKey ?: return Result.failure(Exception("encryption private key is null"))
            rawUSerPass.encryptionDerPrivateKey to getAlgo(rawUSerPass.algo)
        } else {
            rawUSerPass.derPrivateKey to getAlgo(rawUSerPass.algo)
        }

        val aesKey = algo.kemDecrypt(epk, encryptedAesKey.hexToByteArray())
        return aesKey.map {
            return decryptDataByAES(encryptedPrivateKey.hexToByteArray(), it)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun encryptChildAccount(): Result<CustomApi.Accounts.ChildAccounts.AddChildAccountRequest> {
        val algoType = rawUSerPass.algo
        // Generate new key pair for child account
        val algo = getAlgo(algoType)
        val (pemPrivateKey, pemPublicKey) = algo.generatePemKeyPair().getOrThrow()
        val derPrivateKey = algo.getDerPrivateKey(pemPrivateKey).getOrThrow()
        val derPublicKey = algo.getDerPublicKeyFromPem(pemPublicKey).getOrThrow()

        // Encrypt the child's private key with AES
        val (encryptedPrivateKey, aesKey) = encryptDataByAES(derPrivateKey).getOrThrow()

        // Get user's public key for encryption
        val userPublicKey = if (rawUSerPass.algo == AlgoType.DILITHIUM) {
            rawUSerPass.encryptionDerPublicKey ?: return Result.failure(Exception("encryption public key is null"))
            rawUSerPass.encryptionDerPublicKey
        } else {
            rawUSerPass.derPublicKey
        }

        // Encrypt AES key with user's public key (hybrid encryption)
        val userAlgo = getAlgo(rawUSerPass.algo)
        val encryptedAesKey = userAlgo.kemEncrypt(userPublicKey, aesKey)

        return encryptedAesKey.map {
            CustomApi.Accounts.ChildAccounts.AddChildAccountRequest(
                encryptedPrivateKey = encryptedPrivateKey.toHexString(),
                encryptedAesKey = it.toHexString(),
                derPublicKey = derPublicKey,
                algoType = algoType
            )
        }
    }
}
