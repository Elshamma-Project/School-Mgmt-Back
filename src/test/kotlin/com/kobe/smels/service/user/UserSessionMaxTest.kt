package com.kobe.smels.service.user

import com.kobe.smels.model.user.UserSession
import kotlin.test.Test
import kotlin.test.assertEquals

class UserSessionMaxTest {
    @Test
    fun `max concurrent sessions is two`() {
        assertEquals(2, UserSession.MAX_CONCURRENT_SESSIONS)
    }
}
