package com.storyteller_f.shared

import android.annotation.SuppressLint
import org.bouncycastle.jcajce.provider.digest.Keccak
import org.bouncycastle.jce.interfaces.ECPrivateKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECPrivateKeySpec
import org.bouncycastle.jce.spec.ECPublicKeySpec
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.util.encoders.Hex
import java.io.IOException
import java.io.StringReader
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Security
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

fun addProvider() {
    Security.removeProvider("BC")
    Security.addProvider(BouncyCastleProvider())
//    Security.addProvider(BouncyCastleJsseProvider())
//    Security.addProvider(BouncyCastlePQCProvider())
//    Security.getProviders().forEach {
//        println(it.name)
//        it.services.forEach {
//            println("service ${it.type} ${it.algorithm} ${it.className}")
//        }
//    }
}

@Throws(IOException::class)
fun readPrivateKeyFromPEMString(pemString: String): PrivateKey {
    val stringReader = StringReader(pemString)
    val pemParser = PEMParser(stringReader)
    val converter: JcaPEMKeyConverter = JcaPEMKeyConverter().setProvider("BC")
    val any: Any = pemParser.readObject()
    pemParser.close()

    return when (any) {
        is org.bouncycastle.openssl.PEMKeyPair -> converter.getPrivateKey(any.privateKeyInfo)

        is org.bouncycastle.asn1.pkcs.PrivateKeyInfo -> converter.getPrivateKey(any)

        else -> throw IllegalArgumentException("Unsupported PEM object: " + any.javaClass)
    }
}

// 从私钥中生成公钥
@Throws(Exception::class)
fun getPublicKeyFromPrivateKey(privateKey: PrivateKey): PublicKey {
    val keyFactory = KeyFactory.getInstance("ECDSA", "BC")
    val ecSpec = (keyFactory.getKeySpec(
        privateKey,
        ECPrivateKeySpec::class.java
    ) as ECPrivateKeySpec).params

    val ecPrivateKeySpec = ECPrivateKeySpec((privateKey as ECPrivateKey).d, ecSpec)
    val publicKeySpec = ECPublicKeySpec(ecSpec.g.multiply(ecPrivateKeySpec.d), ecSpec)

    return keyFactory.generatePublic(publicKeySpec)
}

@OptIn(ExperimentalStdlibApi::class)
actual suspend fun getDerPublicKeyFromPrivateKey(pemPrivateKeyStr: String): String {
    val privateKey = readPrivateKeyFromPEMString(pemPrivateKeyStr)
    return getPublicKeyFromPrivateKey(privateKey).encoded.toHexString()
}

actual suspend fun calcAddress(derPublicKeyStr: String): String {
    val decode = Hex.decode(derPublicKeyStr)
    val digest256 = Keccak.Digest256()
    val digest = digest256.digest(decode).takeLast(20).toByteArray()
    return Hex.toHexString(digest)
}

@SuppressLint("DeprecatedProvider")
actual suspend fun encrypt(data: String): Pair<ByteArray, ByteArray> {
    // 生成AES密钥
    val keyGen = KeyGenerator.getInstance("AES", "BC")
    keyGen.init(256) // 使用256位密钥
    val aesKey = keyGen.generateKey()

    // 初始化Cipher进行AES加密
    val aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "BC")
    val iv = ByteArray(aesCipher.getBlockSize())
    val ivSpec = IvParameterSpec(iv)

    // 待加密的数据
    val plaintextBytes = data.toByteArray()

    // 使用AES密钥加密数据
    aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, ivSpec)
    return aesCipher.doFinal(plaintextBytes) to aesKey.encoded
}

@SuppressLint("DeprecatedProvider")
@OptIn(ExperimentalStdlibApi::class)
actual suspend fun decrypt(derPrivateKeyStr: String, encrypted: ByteArray, encryptedAesKey: ByteArray): String {
    val privateKeyBytes = derPrivateKeyStr.hexToByteArray()

    // 将字节数组转换为私钥对象
    val privateKeySpec = PKCS8EncodedKeySpec(privateKeyBytes)
    val keyFactory = KeyFactory.getInstance("EC", "BC")
    val privateKey = keyFactory.generatePrivate(privateKeySpec)

    val rsaCipher = Cipher.getInstance("ECIES", "BC")
    rsaCipher.init(Cipher.DECRYPT_MODE, privateKey)
    val aesKeyBytes = rsaCipher.doFinal(encryptedAesKey)

    // 将解密后的AES密钥转换为SecretKey对象
    val aesKey = SecretKeySpec(aesKeyBytes, "AES")

    // 初始化Cipher进行AES解密
    val aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "BC")
    val iv = ByteArray(aesCipher.blockSize)
    val ivSpec = IvParameterSpec(iv)
    aesCipher.init(Cipher.DECRYPT_MODE, aesKey, ivSpec)

    // 使用AES密钥解密数据
    return String(aesCipher.doFinal(encrypted))
}

@SuppressLint("DeprecatedProvider")
@OptIn(ExperimentalStdlibApi::class)
actual suspend fun encryptAesKey(derPublicKeyStr: String, aesKey: ByteArray): ByteArray {
    val publicKeyBytes = derPublicKeyStr.hexToByteArray()

    val keySpec = X509EncodedKeySpec(publicKeyBytes)
    val keyFactory = KeyFactory.getInstance("EC", "BC")
    val publicKey = keyFactory.generatePublic(keySpec)

    val rsaCipher = Cipher.getInstance("ECIES", "BC")
    rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey)
    return rsaCipher.doFinal(aesKey)
}
