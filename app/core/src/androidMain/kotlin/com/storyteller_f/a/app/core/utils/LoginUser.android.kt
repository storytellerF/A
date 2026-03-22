package com.storyteller_f.a.app.core.utils

import android.content.Context
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import com.russhwolf.settings.serialization.decodeValueOrNull
import com.russhwolf.settings.serialization.encodeValue
import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.client.core.RawUserPassInfo
import com.storyteller_f.a.client.core.UserPass
import com.storyteller_f.shared.CryptoJvm
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.getAppContextRefValue
import com.storyteller_f.shared.model.AlgoType
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

actual fun buildSessionHistoryFactory(settings: Settings): SessionHistoryManager {
    if (runCatching {
            Cipher.getInstance("ECIES", "AndroidKeyStore")
        }.isSuccess && runCatching {
            Signature.getInstance("SHA256withECDSA")
        }.isSuccess) {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            return AndroidKeyStoreSessionHistoryManager(settings)
        } catch (e: Exception) {
            Napier.e(e) {
                "AndroidKeyStoreLoginHistoryManager failed"
            }
        }
    }
    return DefaultSessionHistoryManager(settings)
}

data class AndroidKeyStoreUserPass(private val alias: String) : UserPass {
    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun signature(data: String): Result<String> {
        return runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            withContext(Dispatchers.IO) {
                keyStore.load(null)
            }

            // 获取私钥
            val privateKey = keyStore.getKey(alias, null) as PrivateKey

            // 初始化签名对象
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(privateKey)
            signature.update(data.encodeToByteArray())

            signature.sign().toHexString()
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun verify(signature: String, data: String): Result<Boolean> {
        return runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            withContext(Dispatchers.IO) {
                keyStore.load(null)
            }

            // 获取公钥
            val publicKey = keyStore.getCertificate(alias).publicKey

            // 初始化签名验证对象
            val signature1 = Signature.getInstance("SHA256withECDSA")
            signature1.initVerify(publicKey)
            signature1.update(data.encodeToByteArray())
            signature1.verify(signature.hexToByteArray())
        }
    }

    override suspend fun decrypt(encrypted: ByteArray, encryptedAesKey: ByteArray): Result<String> {
        return runCatching {
            // 获取 Android Keystore 实例
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            withContext(Dispatchers.IO) {
                keyStore.load(null)
            }

            // 获取私钥
            val privateKeyEntry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
            val privateKey: PrivateKey = privateKeyEntry.privateKey

            // 使用 Keystore 中的私钥解密 AES 密钥
            val rsaCipher = Cipher.getInstance("ECIES", "AndroidKeyStore")
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey)
            val aesKeyBytes = rsaCipher.doFinal(encryptedAesKey)

            // 将解密后的 AES 密钥转换为 SecretKey 对象
            val aesKey = SecretKeySpec(aesKeyBytes, "AES")

            // 初始化 AES 解密
            val aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "AndroidKeyStore")
            val iv = ByteArray(aesCipher.blockSize) // 初始向量（IV）
            val ivSpec = IvParameterSpec(iv)
            aesCipher.init(Cipher.DECRYPT_MODE, aesKey, ivSpec)

            // 解密加密数据
            val decryptedData = aesCipher.doFinal(encrypted)

            // 转换为字符串并返回
            String(decryptedData)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun address(): Result<String> {
        // 获取 Android Keystore 实例
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        withContext(Dispatchers.IO) {
            keyStore.load(null)
        }

        val derPublicKeyStr = keyStore.getCertificate("default").publicKey.encoded.toHexString()
        println("public $derPublicKeyStr")
        return getAlgo().calcAddress(derPublicKeyStr)
    }

    override suspend fun decryptChildAccount(
        encryptedPrivateKey: String,
        encryptedAesKey: String,
        childAlgoType: AlgoType,
        encryptedEncryptionPrivateKey: String?
    ): Result<Pair<String, String?>> {
        TODO("Not yet implemented")
    }

    override suspend fun encryptChildAccount(childAlgoType: AlgoType): Result<CustomApi.Accounts.ChildAccounts.AddChildAccountRequest> {
        TODO("Not yet implemented")
    }
}

@Suppress("SameParameterValue")
class AndroidKeyStoreSessionHistoryManager(val settings: Settings) : SessionHistoryManager {
    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun getSavedSession(): SavedSession {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val sessionHistory = settings.decodeValueOrNull(SessionHistory.serializer(), "session_history")
        val list = keyStore.aliases().toList()
        return SavedSession(list, sessionHistory)
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override suspend fun addSession(userPassInfo: RawUserPassInfo): UserPass {
        val current = "default"
        importEcdsaPrivateKey(current, userPassInfo.authKey.derPrivateKey)
        settings.encodeValue(SessionHistory.serializer(), "session_history", SessionHistory(current))
        return AndroidKeyStoreUserPass(current)
    }

    override fun buildSession(alias: String): UserPass {
        return AndroidKeyStoreUserPass(alias)
    }

    private suspend fun importEcdsaPrivateKey(alias: String, derPrivateKey: String) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        withContext(Dispatchers.IO) {
            keyStore.load(null)
        }
        if (keyStore.containsAlias(alias)) {
            return
        }
        // 解码 Base64 编码的私钥
        val privateKeyBytes = derPrivateKey.encodeToByteArray()

        // 创建 PKCS8EncodedKeySpec
        val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)

        // 获取 ECDSA KeyFactory
        val keyFactory = KeyFactory.getInstance("EC")

        // 生成 PrivateKey 对象
        val privateKey = keyFactory.generatePrivate(keySpec)

        // 定义密钥保护参数
        val protectionParams = KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()

        val cert = CryptoJvm.generateCert(derPrivateKey).getOrThrow()
        // 将私钥导入 Keystore
        val privateKeyEntry = KeyStore.PrivateKeyEntry(privateKey, arrayOf(cert))
        keyStore.setEntry(alias, privateKeyEntry, protectionParams)

        println("ECDSA private key imported successfully with alias: $alias")
    }

    override fun removeSession(alias: String) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.deleteEntry(alias)
    }

    override fun exitSession(alias: String) = Unit

    override fun logSession(alias: String) = Unit
}

actual fun createSettings(name: String): Settings {
    val context = getAppContextRefValue()!!
    return SharedPreferencesSettings(context.getSharedPreferences(name, Context.MODE_PRIVATE))
}
