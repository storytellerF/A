package com.storyteller_f.shared.utils

import java.security.MessageDigest

fun md5(input: String): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(input.toByteArray()) // 计算 MD5
    return digest.joinToString("") { "%02x".format(it) } // 转换为十六进制字符串
}
