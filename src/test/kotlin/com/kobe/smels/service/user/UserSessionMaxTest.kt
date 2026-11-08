package com.kobe.smels.service.user

import com.kobe.smels.configuration.SecurityProperties
import com.kobe.smels.model.user.AccountStatus
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.model.user.SessionStatus
import com.kobe.smels.model.user.UserAccount
import com.kobe.smels.model.user.UserSession
import com.kobe.smels.repository.user.UserSessionRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class UserSessionMaxTest {
    private val userSessionRepository: UserSessionRepository = mock()
    private val securityProperties = SecurityProperties()
    private val service = UserSessionService(userSessionRepository, securityProperties)

    @Test
    fun `max concurrent sessions is two`() {
        assertEquals(2, UserSession.MAX_CONCURRENT_SESSIONS)
    }

    @Test
    fun `opening a third session revokes the oldest`() {
        val oldest = session("sess-old", Instant.parse("2026-01-01T00:00:00Z"))
        val newer = session("sess-new", Instant.parse("2026-01-02T00:00:00Z"))
        whenever(
            userSessionRepository.findByAccountIdAndStatusOrderByLastSeenAtAsc(
                "acc-1",
                SessionStatus.ACTIVE
            )
        ).thenReturn(Flux.just(oldest, newer))
        whenever(userSessionRepository.save(any())).thenAnswer { invocation ->
            val s = invocation.getArgument<UserSession>(0)
            if (s.id == null) {
                s.id = "sess-3"
            }
            Mono.just(s)
        }

        val account = UserAccount(
            id = "acc-1",
            schoolId = "school-1",
            username = "teacher",
            passwordHash = "hash",
            roles = setOf(Role.TEACHER),
            profileType = ProfileType.STAFF,
            profileId = "staff-t",
            status = AccountStatus.ACTIVE
        )

        StepVerifier.create(service.openSession(account, null, null, null))
            .expectNextMatches { it.session.id == "sess-3" && it.rawRefreshToken.isNotBlank() }
            .verifyComplete()

        val captor = argumentCaptor<UserSession>()
        verify(userSessionRepository, times(2)).save(captor.capture())
        val revoked = captor.allValues.first { it.id == "sess-old" }
        assertEquals(SessionStatus.REVOKED, revoked.status)
        assertEquals("MAX_SESSIONS_EXCEEDED", revoked.revokedReason)
    }

    private fun session(id: String, lastSeenAt: Instant) = UserSession(
        id = id,
        accountId = "acc-1",
        schoolId = "school-1",
        refreshTokenHash = "hash-$id",
        status = SessionStatus.ACTIVE,
        createdAtSession = lastSeenAt,
        lastSeenAt = lastSeenAt,
        expiresAt = lastSeenAt.plusSeconds(3600)
    )
}
