package com.kobe.smels.service.user

import com.kobe.smels.configuration.SecurityProperties
import com.kobe.smels.model.user.SessionStatus
import com.kobe.smels.model.user.UserSession
import com.kobe.smels.repository.user.UserSessionRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.OptimisticLockingFailureException
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class UserSessionOptimisticLockTest {
    private val userSessionRepository: UserSessionRepository = mock()
    private val securityProperties = SecurityProperties()
    private val service = UserSessionService(userSessionRepository, securityProperties)

    @Test
    fun `requireActiveSession skips save when lastSeen is fresh`() {
        val now = Instant.now()
        val session = session("sess-1", now.minusSeconds(10))
        whenever(userSessionRepository.findById("sess-1")).thenReturn(Mono.just(session))

        StepVerifier.create(service.requireActiveSession("sess-1", "acc-1"))
            .expectNext(session)
            .verifyComplete()

        verify(userSessionRepository, never()).save(any())
    }

    @Test
    fun `requireActiveSession ignores optimistic lock on touch`() {
        val stale = session("sess-1", Instant.now().minusSeconds(120))
        stale.version = 1
        val reloaded = session("sess-1", Instant.now().minusSeconds(5))
        reloaded.version = 2

        whenever(userSessionRepository.findById("sess-1"))
            .thenReturn(Mono.just(stale), Mono.just(reloaded))
        whenever(userSessionRepository.save(any()))
            .thenReturn(Mono.error(OptimisticLockingFailureException("conflict")))

        StepVerifier.create(service.requireActiveSession("sess-1", "acc-1"))
            .expectNext(reloaded)
            .verifyComplete()

        verify(userSessionRepository, times(1)).save(any())
    }

    @Test
    fun `rotateRefreshToken retries after optimistic lock when hash unchanged`() {
        val session = session("sess-1", Instant.now())
        session.version = 1
        session.refreshTokenHash = TokenHashing.sha256("refresh-old")

        val afterTouch = session("sess-1", Instant.now())
        afterTouch.version = 2
        afterTouch.refreshTokenHash = session.refreshTokenHash

        whenever(userSessionRepository.save(any()))
            .thenReturn(Mono.error(OptimisticLockingFailureException("conflict")))
            .thenAnswer { invocation ->
                val s = invocation.getArgument<UserSession>(0)
                s.version = 3
                Mono.just(s)
            }
        whenever(userSessionRepository.findById("sess-1")).thenReturn(Mono.just(afterTouch))

        StepVerifier.create(service.rotateRefreshToken(session))
            .assertNext { issued ->
                assertEquals("sess-1", issued.session.id)
                assertNotEquals(TokenHashing.sha256("refresh-old"), issued.session.refreshTokenHash)
                assertEquals(TokenHashing.sha256(issued.rawRefreshToken), issued.session.refreshTokenHash)
            }
            .verifyComplete()

        verify(userSessionRepository, times(2)).save(any())
    }

    @Test
    fun `rotateRefreshToken fails when another refresh already won`() {
        val session = session("sess-1", Instant.now())
        session.version = 1
        session.refreshTokenHash = TokenHashing.sha256("refresh-old")

        val alreadyRotated = session("sess-1", Instant.now())
        alreadyRotated.version = 2
        alreadyRotated.refreshTokenHash = TokenHashing.sha256("refresh-other")

        whenever(userSessionRepository.save(any()))
            .thenReturn(Mono.error(OptimisticLockingFailureException("conflict")))
        whenever(userSessionRepository.findById("sess-1")).thenReturn(Mono.just(alreadyRotated))

        StepVerifier.create(service.rotateRefreshToken(session))
            .expectErrorMatches { error ->
                error is com.kobe.smels.exception.UnauthorizedException &&
                    error.code == "INVALID_REFRESH"
            }
            .verify()
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
