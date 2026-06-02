package com.storyteller_f.a.cloud.core.service

import kotlin.test.Test
import kotlin.test.assertEquals

class TwoFactorServiceTest {
    @Test
    fun `totp matches rfc 6238 sha1 vectors with six digits`() {
        val secret = encodeBase32("12345678901234567890".encodeToByteArray())

        assertEquals("287082", generateTotpCode(secret, 59 / 30))
        assertEquals("081804", generateTotpCode(secret, 1111111109 / 30))
        assertEquals("050471", generateTotpCode(secret, 1111111111 / 30))
        assertEquals("005924", generateTotpCode(secret, 1234567890 / 30))
        assertEquals("279037", generateTotpCode(secret, 2000000000 / 30))
        assertEquals("353130", generateTotpCode(secret, 20000000000 / 30))
    }
}
