package com.kobe.smels.service.user

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TokenHashingTest {
    @Test
    fun `refresh tokens are unique and hashed`() {
        val a = TokenHashing.newRefreshToken()
        val b = TokenHashing.newRefreshToken()
        assertNotEquals(a, b)
        assertTrue(a.length >= 32)
        assertEquals(TokenHashing.sha256(a), TokenHashing.sha256(a))
        assertNotEquals(TokenHashing.sha256(a), TokenHashing.sha256(b))
    }
}
