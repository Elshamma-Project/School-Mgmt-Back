package com.kobe.smels.service.user

import com.kobe.smels.configuration.SecurityProperties
import com.kobe.smels.dto.user.AuthTokensResponse
import com.kobe.smels.dto.user.ChangePasswordRequest
import com.kobe.smels.dto.user.LoginRequest
import com.kobe.smels.dto.user.SessionResponse
import com.kobe.smels.dto.user.UserAccountResponse
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.exception.UnauthorizedException
import com.kobe.smels.model.user.AccountStatus
import com.kobe.smels.model.user.UserAccount
import com.kobe.smels.repository.user.UserAccountRepository
import com.kobe.smels.shared.security.CurrentUser
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class AuthService(
    private val userAccountRepository: UserAccountRepository,
    private val userSessionService: UserSessionService,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
    private val securityProperties: SecurityProperties
) {
    fun login(
        request: LoginRequest,
        userAgent: String?,
        ipAddress: String?
    ): Mono<AuthTokensResponse> {
        return findAccountForLogin(request.username.trim(), request.schoolId)
            .flatMap { account -> authenticate(account, request.password) }
            .flatMap { account ->
                userSessionService.openSession(
                    account = account,
                    userAgent = userAgent,
                    ipAddress = ipAddress,
                    deviceLabel = request.deviceLabel
                ).map { account to it }
            }
            .map { (account, issued) -> toTokensResponse(account, issued) }
    }

    fun refresh(rawRefreshToken: String): Mono<AuthTokensResponse> {
        return userSessionService.findActiveByRefreshToken(rawRefreshToken)
            .flatMap { session ->
                userAccountRepository.findById(session.accountId)
                    .switchIfEmpty(Mono.error(UnauthorizedException("ACCOUNT_NOT_FOUND", "Account not found")))
                    .flatMap { account ->
                        assertAccountUsable(account)
                            .then(userSessionService.rotateRefreshToken(session))
                            .map { account to it }
                    }
            }
            .map { (account, issued) -> toTokensResponse(account, issued) }
    }

    fun logout(currentUser: CurrentUser): Mono<Void> {
        return userSessionService.requireActiveSession(currentUser.sessionId, currentUser.accountId)
            .flatMap { userSessionService.revoke(it, "LOGOUT") }
            .then()
    }

    fun logoutAll(currentUser: CurrentUser): Mono<Long> {
        return userSessionService.revokeAllForAccount(currentUser.accountId, "LOGOUT_ALL")
    }

    fun me(currentUser: CurrentUser): Mono<UserAccountResponse> {
        return userAccountRepository.findById(currentUser.accountId)
            .switchIfEmpty(Mono.error(UnauthorizedException("ACCOUNT_NOT_FOUND", "Account not found")))
            .map { UserAccountMapper.toResponse(it) }
    }

    fun listSessions(currentUser: CurrentUser): Flux<SessionResponse> {
        return userSessionService.listActiveSessions(currentUser.accountId)
            .map { session ->
                SessionResponse(
                    id = session.id!!,
                    deviceLabel = session.deviceLabel,
                    userAgent = session.userAgent,
                    ipAddress = session.ipAddress,
                    createdAt = session.createdAtSession,
                    lastSeenAt = session.lastSeenAt,
                    expiresAt = session.expiresAt,
                    current = session.id == currentUser.sessionId
                )
            }
    }

    fun revokeSession(currentUser: CurrentUser, sessionId: String): Mono<Void> {
        return userSessionService.revokeByIdForAccount(sessionId, currentUser.accountId).then()
    }

    fun changePassword(currentUser: CurrentUser, request: ChangePasswordRequest): Mono<UserAccountResponse> {
        if (request.newPassword != request.confirmPassword) {
            return Mono.error(BusinessRuleException("PASSWORD_MISMATCH", "New password confirmation does not match"))
        }
        if (request.newPassword.length < 8) {
            return Mono.error(BusinessRuleException("PASSWORD_TOO_SHORT", "Password must be at least 8 characters"))
        }
        return userAccountRepository.findById(currentUser.accountId)
            .switchIfEmpty(Mono.error(UnauthorizedException("ACCOUNT_NOT_FOUND", "Account not found")))
            .flatMap { account ->
                val hash = account.passwordHash
                    ?: return@flatMap Mono.error(ForbiddenException("PASSWORD_NOT_SET", "Password is not set"))
                if (!passwordEncoder.matches(request.currentPassword, hash)) {
                    return@flatMap Mono.error(UnauthorizedException("BAD_CREDENTIALS", "Current password is incorrect"))
                }
                account.passwordHash = passwordEncoder.encode(request.newPassword)
                account.mustChangePassword = false
                userAccountRepository.save(account)
            }
            .flatMap { saved ->
                // Invalide les autres sessions après changement de mot de passe.
                userSessionService.listActiveSessions(saved.id!!)
                    .filter { it.id != currentUser.sessionId }
                    .flatMap { userSessionService.revoke(it, "PASSWORD_CHANGED") }
                    .then(Mono.just(UserAccountMapper.toResponse(saved)))
            }
    }

    private fun findAccountForLogin(username: String, schoolId: String?): Mono<UserAccount> {
        val lookup = if (schoolId.isNullOrBlank()) {
            userAccountRepository.findBySchoolIdIsNullAndUsername(username)
                .switchIfEmpty(userAccountRepository.findByUsername(username))
        } else {
            userAccountRepository.findBySchoolIdAndUsername(schoolId, username)
        }
        return lookup.switchIfEmpty(
            Mono.error(UnauthorizedException("BAD_CREDENTIALS", "Invalid username or password"))
        )
    }

    private fun authenticate(account: UserAccount, rawPassword: String): Mono<UserAccount> {
        return assertAccountUsable(account)
            .then(Mono.defer {
                val hash = account.passwordHash
                if (hash == null || !passwordEncoder.matches(rawPassword, hash)) {
                    registerFailedLogin(account)
                } else {
                    account.failedLoginCount = 0
                    account.lockedUntil = null
                    account.lastLoginAt = Instant.now()
                    if (account.status == AccountStatus.LOCKED) {
                        account.status = AccountStatus.ACTIVE
                    }
                    userAccountRepository.save(account)
                }
            })
    }

    private fun assertAccountUsable(account: UserAccount): Mono<Void> {
        val now = Instant.now()
        if (account.lockedUntil != null && account.lockedUntil!!.isAfter(now)) {
            return Mono.error(
                ForbiddenException(
                    "ACCOUNT_LOCKED",
                    "Account is temporarily locked due to failed login attempts"
                )
            )
        }
        return when (account.status) {
            AccountStatus.DISABLED ->
                Mono.error(ForbiddenException("ACCOUNT_DISABLED", "Account is disabled"))
            AccountStatus.PENDING ->
                Mono.error(ForbiddenException("ACCOUNT_PENDING", "Account is not activated"))
            AccountStatus.LOCKED -> {
                if (account.lockedUntil == null || !account.lockedUntil!!.isAfter(now)) {
                    Mono.empty()
                } else {
                    Mono.error(ForbiddenException("ACCOUNT_LOCKED", "Account is locked"))
                }
            }
            AccountStatus.ACTIVE -> Mono.empty()
        }
    }

    private fun registerFailedLogin(account: UserAccount): Mono<UserAccount> {
        account.failedLoginCount += 1
        val max = securityProperties.lockout.maxFailedAttempts
        if (account.failedLoginCount >= max) {
            account.status = AccountStatus.LOCKED
            account.lockedUntil = Instant.now()
                .plus(securityProperties.lockout.lockDurationMinutes, ChronoUnit.MINUTES)
            account.failedLoginCount = 0
        }
        return userAccountRepository.save(account)
            .then(
                Mono.error(
                    UnauthorizedException("BAD_CREDENTIALS", "Invalid username or password")
                )
            )
    }

    private fun toTokensResponse(
        account: UserAccount,
        issued: UserSessionService.IssuedSession
    ): AuthTokensResponse {
        val access = jwtService.createAccessToken(account, issued.session.id!!)
        return AuthTokensResponse(
            accessToken = access,
            refreshToken = issued.rawRefreshToken,
            tokenType = "Bearer",
            expiresInMs = jwtService.accessTokenValidityMs(),
            sessionId = issued.session.id!!,
            mustChangePassword = account.mustChangePassword,
            user = UserAccountMapper.toResponse(account)
        )
    }
}
