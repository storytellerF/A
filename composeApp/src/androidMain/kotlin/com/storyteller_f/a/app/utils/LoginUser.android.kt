package com.storyteller_f.a.app.utils

import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.serialization.decodeValueOrNull
import com.russhwolf.settings.serialization.encodeValue
import com.storyteller_f.a.client_lib.LoginUser
import com.storyteller_f.a.client_lib.LoginUserSession
import com.storyteller_f.shared.CryptoJvm
import com.storyteller_f.shared.calcAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

actual fun buildLoginUserSessionFactory(): LoginUserSessionManager {
    if (runCatching {
            Cipher.getInstance("ECIES", "AndroidKeyStore")
        }.isSuccess && runCatching {
            Signature.getInstance("SHA256withECDSA")
        }.isSuccess) {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            return AndroidKeyStoreLoginUserSessionManager()
        } catch (_: Exception) {
        }
    }
    return DefaultLoginUserSessionManager()
}

class AndroidKeyStoreLoginUserSession(private val alias: String) : LoginUserSession {
    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun signature(data: String): String {
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

        return signature.sign().toHexString()
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun verify(signature: String, data: String): Result<Boolean> {
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
        return signature1.verify(signature.hexToByteArray())
    }

    override suspend fun decrypt(encrypted: ByteArray, encryptedAesKey: ByteArray): String {
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
        return String(decryptedData)
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun address(): String {
        // 获取 Android Keystore 实例
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        withContext(Dispatchers.IO) {
            keyStore.load(null)
        }

        val derPublicKeyStr = keyStore.getCertificate("default").publicKey.encoded.toHexString()
        println("public $derPublicKeyStr")
        return calcAddress(derPublicKeyStr)
    }
}

@Suppress("SameParameterValue")
class AndroidKeyStoreLoginUserSessionManager : LoginUserSessionManager {
    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun savedSession(): SavedSession {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val loginHistory = defaultSettings.decodeValueOrNull(LoginHistory.serializer(), "login_history")
        val list = keyStore.aliases().toList()
        return SavedSession(list, loginHistory?.last, loginHistory?.current)
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
    override fun addSession(session: LoginUser): LoginUserSession {
        val current = "default"
        importEcdsaPrivateKey(current, session.privateKey)
        defaultSettings.encodeValue(LoginHistory.serializer(), "login_history", LoginHistory(current, current))
        return AndroidKeyStoreLoginUserSession(current)
    }

    override fun buildSession(alias: String): LoginUserSession {
        return AndroidKeyStoreLoginUserSession(alias)
    }

    private fun importEcdsaPrivateKey(alias: String, pemPrivateKey: String) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (keyStore.containsAlias(alias)) {
            return
        }
        // 移除 PEM 格式的头部和尾部
        val privateKeyPem = pemPrivateKey
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "") // 移除所有空白字符

        // 解码 Base64 编码的私钥
        val privateKeyBytes = Base64.getDecoder().decode(privateKeyPem)

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

        val cert = CryptoJvm.generateCert(pemPrivateKey)
        // 将私钥导入 Keystore
        val privateKeyEntry = KeyStore.PrivateKeyEntry(privateKey, arrayOf(cert))
        keyStore.setEntry(alias, privateKeyEntry, protectionParams)

        println("ECDSA private key imported successfully with alias: $alias")
    }

    override fun removeSession(session: String) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.deleteEntry(session)
    }
}
