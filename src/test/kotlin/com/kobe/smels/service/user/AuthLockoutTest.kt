package com.kobe.smels.service.user

import com.kobe.smels.configuration.SecurityProperties
import com.kobe.smels.dto.user.LoginRequest
import com.kobe.smels.exception.UnauthorizedException
import com.kobe.smels.model.user.AccountStatus
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.model.user.UserAccount
import com.kobe.smels.repository.user.UserAccountRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthLockoutTest {
    private val userAccountRepository: UserAccountRepository = mock()
    private val userSessionService: UserSessionService = mock()
    private val jwtService: JwtService = mock()
    private val passwordEncoder: PasswordEncoder = mock()
    private val securityProperties = SecurityProperties(
        lockout = SecurityProperties.Lockout(maxFailedAttempts = 3, lockDurationMinutes = 15)
    )

    private val service = AuthService(
        userAccountRepository,
        userSessionService,
        jwtService,
        passwordEncoder,
        securityProperties
    )

    @Test
    fun `failed logins lock account after max attempts`() {
        val account = UserAccount(
            id = "acc-1",
            schoolId = "school-1",
            username = "teacher@school.test",
            passwordHash = "hash",
            roles = setOf(Role.TEACHER),
            profileType = ProfileType.STAFF,
            profileId = "staff-t",
            status = AccountStatus.ACTIVE,
            failedLoginCount = 2
        )
        whenever(userAccountRepository.findBySchoolIdAndUsername("school-1", "teacher@school.test"))
            .thenReturn(Mono.just(account))
        whenever(userAccountRepository.findBySchoolIdIsNullAndUsername("teacher@school.test"))
            .thenReturn(Mono.empty())
        whenever(passwordEncoder.matches("wrong", "hash")).thenReturn(false)
        whenever(userAccountRepository.save(any())).thenAnswer { invocation ->
            Mono.just(invocation.getArgument(0))
        }

        StepVerifier.create(
            service.login(
                LoginRequest(email = "teacher@school.test", password = "wrong", schoolId = "school-1"),
                userAgent = null,
                ipAddress = "127.0.0.1"
            )
        )
            .expectError(UnauthorizedException::class.java)
            .verify()

        val captor = argumentCaptor<UserAccount>()
        verify(userAccountRepository, times(1)).save(captor.capture())
        val saved = captor.firstValue
        assertEquals(AccountStatus.LOCKED, saved.status)
        assertNotNull(saved.lockedUntil)
        assertTrue(saved.lockedUntil!!.isAfter(java.time.Instant.now().minusSeconds(1)))
        assertEquals(0, saved.failedLoginCount)
    }
}
