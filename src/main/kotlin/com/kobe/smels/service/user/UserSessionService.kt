package com.kobe.smels.service.user

import com.kobe.smels.configuration.SecurityProperties
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.exception.UnauthorizedException
import com.kobe.smels.model.user.SessionStatus
import com.kobe.smels.model.user.UserAccount
import com.kobe.smels.model.user.UserSession
import com.kobe.smels.repository.user.UserSessionRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class UserSessionService(
    private val userSessionRepository: UserSessionRepository,
    private val securityProperties: SecurityProperties
) {
    data class IssuedSession(
        val session: UserSession,
        val rawRefreshToken: String
    )

    /**
     * Crée une session ACTIVE. Si le compte a déjà 2 sessions actives,
     * révoque la plus ancienne (lastSeenAt croissant) avant d'en créer une nouvelle.
     */
    fun openSession(
        account: UserAccount,
        userAgent: String?,
        ipAddress: String?,
        deviceLabel: String?
    ): Mono<IssuedSession> {
        val accountId = account.id
            ?: return Mono.error(IllegalStateException("Account id required"))

        return enforceMaxSessions(accountId)
            .then(Mono.fromCallable {
                val rawRefresh = TokenHashing.newRefreshToken()
                val now = Instant.now()
                val session = UserSession(
                    accountId = accountId,
                    schoolId = account.schoolId,
                    refreshTokenHash = TokenHashing.sha256(rawRefresh),
                    status = SessionStatus.ACTIVE,
                    userAgent = userAgent?.take(512),
                    ipAddress = ipAddress?.take(128),
                    deviceLabel = deviceLabel?.take(128),
                    createdAtSession = now,
                    lastSeenAt = now,
                    expiresAt = now.plusMillis(securityProperties.jwt.refreshTokenValidityMs)
                )
                IssuedSession(session, rawRefresh)
            })
            .flatMap { issued ->
                userSessionRepository.save(issued.session).map { saved ->
                    IssuedSession(saved, issued.rawRefreshToken)
                }
            }
    }

    fun requireActiveSession(sessionId: String, accountId: String): Mono<UserSession> {
        return userSessionRepository.findById(sessionId)
            .switchIfEmpty(Mono.error(UnauthorizedException("SESSION_INVALID", "Session is not active")))
            .flatMap { session ->
                when {
                    session.accountId != accountId ->
                        Mono.error(UnauthorizedException("SESSION_INVALID", "Session is not active"))
                    session.status != SessionStatus.ACTIVE ->
                        Mono.error(UnauthorizedException("SESSION_REVOKED", "Session has been revoked"))
                    session.expiresAt.isBefore(Instant.now()) ->
                        revoke(session, "EXPIRED").then(
                            Mono.error(UnauthorizedException("SESSION_EXPIRED", "Session has expired"))
                        )
                    else -> {
                        session.lastSeenAt = Instant.now()
                        userSessionRepository.save(session)
                    }
                }
            }
    }

    fun findActiveByRefreshToken(rawRefreshToken: String): Mono<UserSession> {
        val hash = TokenHashing.sha256(rawRefreshToken)
        return userSessionRepository.findByRefreshTokenHashAndStatus(hash, SessionStatus.ACTIVE)
            .switchIfEmpty(Mono.error(UnauthorizedException("INVALID_REFRESH", "Refresh token is invalid")))
            .flatMap { session ->
                if (session.expiresAt.isBefore(Instant.now())) {
                    revoke(session, "EXPIRED").then(
                        Mono.error(UnauthorizedException("SESSION_EXPIRED", "Session has expired"))
                    )
                } else {
                    Mono.just(session)
                }
            }
    }

    fun rotateRefreshToken(session: UserSession): Mono<IssuedSession> {
        val rawRefresh = TokenHashing.newRefreshToken()
        session.refreshTokenHash = TokenHashing.sha256(rawRefresh)
        session.lastSeenAt = Instant.now()
        session.expiresAt = Instant.now().plusMillis(securityProperties.jwt.refreshTokenValidityMs)
        return userSessionRepository.save(session).map { IssuedSession(it, rawRefresh) }
    }

    fun revoke(session: UserSession, reason: String): Mono<UserSession> {
        if (session.status != SessionStatus.ACTIVE) {
            return Mono.just(session)
        }
        session.status = if (reason == "EXPIRED") SessionStatus.EXPIRED else SessionStatus.REVOKED
        session.revokedAt = Instant.now()
        session.revokedReason = reason
        return userSessionRepository.save(session)
    }

    fun revokeByIdForAccount(sessionId: String, accountId: String): Mono<UserSession> {
        return userSessionRepository.findByIdAndAccountId(sessionId, accountId)
            .switchIfEmpty(Mono.error(NotFoundException("SESSION_NOT_FOUND", "Session not found")))
            .flatMap { revoke(it, "USER_REVOKED") }
    }

    fun revokeAllForAccount(accountId: String, reason: String = "LOGOUT_ALL"): Mono<Long> {
        return userSessionRepository.findByAccountIdAndStatus(accountId, SessionStatus.ACTIVE)
            .flatMap { revoke(it, reason) }
            .count()
    }

    fun listActiveSessions(accountId: String): Flux<UserSession> {
        return userSessionRepository.findByAccountIdAndStatusOrderByLastSeenAtAsc(
            accountId,
            SessionStatus.ACTIVE
        )
    }

    private fun enforceMaxSessions(accountId: String): Mono<Void> {
        return userSessionRepository
            .findByAccountIdAndStatusOrderByLastSeenAtAsc(accountId, SessionStatus.ACTIVE)
            .collectList()
            .flatMap { active ->
                val overflow = active.size - UserSession.MAX_CONCURRENT_SESSIONS + 1
                if (overflow <= 0) {
                    Mono.empty()
                } else {
                    Flux.fromIterable(active.take(overflow))
                        .flatMap { revoke(it, "MAX_SESSIONS_EXCEEDED") }
                        .then()
                }
            }
    }

    fun assertCanAccessAccount(requesterAccountId: String, targetAccountId: String): Mono<Void> {
        return if (requesterAccountId == targetAccountId) {
            Mono.empty()
        } else {
            Mono.error(ForbiddenException("FORBIDDEN", "Cannot manage another account's sessions"))
        }
    }
}
