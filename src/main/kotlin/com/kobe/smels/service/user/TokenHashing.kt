package com.kobe.smels.service.user

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object TokenHashing {
    private val secureRandom = SecureRandom()

    fun newRefreshToken(): String {
        val bytes = ByteArray(48)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
