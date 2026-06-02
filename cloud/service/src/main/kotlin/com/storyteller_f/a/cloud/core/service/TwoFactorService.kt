package com.storyteller_f.a.cloud.core.service

import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.types.UserTwoFactor
import com.storyteller_f.a.backend.core.types.toTwoFactorSettingsInfo
import com.storyteller_f.shared.model.RecoveryCodesResponse
import com.storyteller_f.shared.model.TotpSetupInfo
import com.storyteller_f.shared.model.TwoFactorSettingsInfo
import com.storyteller_f.shared.model.TwoFactorType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.now
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val TOTP_ISSUER = "A"
private const val TOTP_DIGITS = 6
private const val TOTP_PERIOD_SECONDS = 30L
private const val TOTP_SECRET_BYTES = 20
private const val RECOVERY_CODE_COUNT = 10
private const val RECOVERY_CODE_BYTES = 8
private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

private val secureRandom = SecureRandom()

suspend fun Backend.getTwoFactorSettings(uid: PrimaryKey): Result<TwoFactorSettingsInfo> {
    return database.user.getUserTwoFactor(uid).map {
        it.toTwoFactorSettingsInfo()
    }
}

suspend fun Backend.setupTotp(uid: PrimaryKey): Result<TotpSetupInfo> = runCatching {
    val secret = generateBase32Secret()
    val recoveryCodes = generateRecoveryCodes()
    val rawUser = database.user.getRawUser(ObjectFetch.IdFetch(uid), uid).getOrThrow()
        ?: throw CustomBadRequestException("user not found")
    val label = rawUser.user.aid ?: rawUser.user.nickname.ifBlank { rawUser.user.address }
    val otpauthUri = buildOtpAuthUri(label, secret)
    database.user.upsertUserTwoFactor(
        UserTwoFactor(
            uid,
            enabled = false,
            type = TwoFactorType.TOTP,
            totpSecret = secret,
            recoveryCodeHashes = recoveryCodes.map(::hashRecoveryCode),
            updatedAt = now(),
        )
    ).getOrThrow()
    TotpSetupInfo(secret, otpauthUri, recoveryCodes)
}

suspend fun Backend.enableTotp(uid: PrimaryKey, code: String): Result<TwoFactorSettingsInfo> = runCatching {
    val current = database.user.getUserTwoFactor(uid).getOrThrow()
        ?: throw CustomBadRequestException("totp setup required")
    if (!verifyTotpCode(current.totpSecret, code)) {
        throw CustomBadRequestException("invalid totp code")
    }
    database.user.upsertUserTwoFactor(current.copy(enabled = true, updatedAt = now())).getOrThrow()
    TwoFactorSettingsInfo(enabled = true, type = TwoFactorType.TOTP)
}

suspend fun Backend.disableTwoFactor(uid: PrimaryKey): Result<TwoFactorSettingsInfo> {
    return database.user.disableUserTwoFactor(uid).map {
        TwoFactorSettingsInfo(enabled = false)
    }
}

suspend fun Backend.generateRecoveryCodes(uid: PrimaryKey): Result<RecoveryCodesResponse> = runCatching {
    database.user.getUserTwoFactor(uid).getOrThrow()
        ?: throw CustomBadRequestException("two factor not configured")
    val recoveryCodes = generateRecoveryCodes()
    database.user.updateRecoveryCodeHashes(uid, recoveryCodes.map(::hashRecoveryCode)).getOrThrow()
    RecoveryCodesResponse(recoveryCodes)
}

suspend fun Backend.isTwoFactorEnabled(uid: PrimaryKey): Result<Boolean> {
    return database.user.getUserTwoFactor(uid).map {
        it?.enabled == true
    }
}

suspend fun Backend.verifyUserTotp(uid: PrimaryKey, code: String): Result<Boolean> {
    return database.user.getUserTwoFactor(uid).map { twoFactor ->
        twoFactor?.enabled == true && verifyTotpCode(twoFactor.totpSecret, code)
    }
}

private fun generateBase32Secret(): String {
    val bytes = ByteArray(TOTP_SECRET_BYTES).apply {
        secureRandom.nextBytes(this)
    }
    return encodeBase32(bytes)
}

private fun generateRecoveryCodes(): List<String> {
    return List(RECOVERY_CODE_COUNT) {
        val bytes = ByteArray(RECOVERY_CODE_BYTES).apply {
            secureRandom.nextBytes(this)
        }
        encodeBase32(bytes).chunked(4).joinToString("-")
    }
}

private fun hashRecoveryCode(code: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(code.encodeToByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun buildOtpAuthUri(label: String, secret: String): String {
    val encodedIssuer = TOTP_ISSUER.urlEncode()
    val encodedLabel = "$TOTP_ISSUER:$label".urlEncode()
    return "otpauth://totp/$encodedLabel?secret=$secret&issuer=$encodedIssuer" +
        "&algorithm=SHA1&digits=$TOTP_DIGITS&period=$TOTP_PERIOD_SECONDS"
}

private fun String.urlEncode(): String {
    return URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")
}

@OptIn(ExperimentalTime::class)
private fun verifyTotpCode(secret: String, code: String): Boolean {
    val normalized = code.filter { it.isDigit() }
    if (normalized.length != TOTP_DIGITS) return false
    val timeStep = Clock.System.now().epochSeconds / TOTP_PERIOD_SECONDS
    return (-1L..1L).any { offset ->
        generateTotpCode(secret, timeStep + offset) == normalized
    }
}

internal fun generateTotpCode(secret: String, timeStep: Long): String {
    val key = decodeBase32(secret)
    val data = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(timeStep).array()
    val hmac = Mac.getInstance("HmacSHA1").run {
        init(SecretKeySpec(key, "HmacSHA1"))
        doFinal(data)
    }
    val offset = hmac.last().toInt() and 0x0f
    val binary = ((hmac[offset].toInt() and 0x7f) shl 24) or
        ((hmac[offset + 1].toInt() and 0xff) shl 16) or
        ((hmac[offset + 2].toInt() and 0xff) shl 8) or
        (hmac[offset + 3].toInt() and 0xff)
    val modulo = 10.0.pow(TOTP_DIGITS).toInt()
    return (binary % modulo).toString().padStart(TOTP_DIGITS, '0')
}

internal fun encodeBase32(bytes: ByteArray): String {
    var buffer = 0
    var bitsLeft = 0
    val result = StringBuilder()
    bytes.forEach { byte ->
        buffer = (buffer shl 8) or (byte.toInt() and 0xff)
        bitsLeft += 8
        while (bitsLeft >= 5) {
            result.append(BASE32_ALPHABET[(buffer shr (bitsLeft - 5)) and 0x1f])
            bitsLeft -= 5
        }
    }
    if (bitsLeft > 0) {
        result.append(BASE32_ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1f])
    }
    return result.toString()
}

private fun decodeBase32(value: String): ByteArray {
    var buffer = 0
    var bitsLeft = 0
    val bytes = mutableListOf<Byte>()
    value.filterNot { it == '=' || it.isWhitespace() }.uppercase().forEach { char ->
        val index = BASE32_ALPHABET.indexOf(char)
        if (index < 0) throw CustomBadRequestException("invalid totp secret")
        buffer = (buffer shl 5) or index
        bitsLeft += 5
        if (bitsLeft >= 8) {
            bytes.add(((buffer shr (bitsLeft - 8)) and 0xff).toByte())
            bitsLeft -= 8
        }
    }
    return bytes.toByteArray()
}
