package com.kobe.smels.service.user

import com.kobe.smels.configuration.SecurityProperties
import com.kobe.smels.model.user.AccountStatus
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.model.user.UserAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JwtServiceTest {

    private val jwtService = JwtService(
        SecurityProperties(
            jwt = SecurityProperties.Jwt(
                secret = "unit-test-secret-key-at-least-32-chars!!",
                accessTokenValidityMs = 60_000,
                refreshTokenValidityMs = 120_000
            )
        )
    )

    @Test
    fun `access token round-trip preserves claims`() {
        val account = UserAccount(
            id = "acc-1",
            schoolId = "school-1",
            username = "teacher1",
            passwordHash = "hash",
            roles = setOf(Role.TEACHER, Role.ADMINISTRATION),
            profileType = ProfileType.STAFF,
            profileId = "staff-1",
            status = AccountStatus.ACTIVE,
            mustChangePassword = false
        )

        val token = jwtService.createAccessToken(account, "session-9")
        val user = jwtService.parseAccessToken(token)

        assertEquals("acc-1", user.accountId)
        assertEquals("session-9", user.sessionId)
        assertEquals("teacher1", user.username)
        assertEquals("school-1", user.schoolId)
        assertEquals(setOf(Role.TEACHER, Role.ADMINISTRATION), user.roles)
        assertEquals(ProfileType.STAFF, user.profileType)
        assertEquals("staff-1", user.profileId)
        assertTrue(!user.mustChangePassword)
    }
}
