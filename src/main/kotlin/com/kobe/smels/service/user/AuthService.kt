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
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.user.AccountStatus
import com.kobe.smels.model.user.UserAccount
import com.kobe.smels.model.user.UserAvatar
import com.kobe.smels.repository.user.UserAccountRepository
import com.kobe.smels.repository.user.UserAvatarRepository
import com.kobe.smels.shared.security.CurrentUser
import org.bson.types.Binary
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class AuthService(
    private val userAccountRepository: UserAccountRepository,
    private val userAvatarRepository: UserAvatarRepository,
    private val userSessionService: UserSessionService,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
    private val securityProperties: SecurityProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val AVATAR_MAX_BYTES = 512 * 1024
        private val AVATAR_CONTENT_TYPES = setOf(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/webp"
        )
    }

    fun login(
        request: LoginRequest,
        userAgent: String?,
        ipAddress: String?
    ): Mono<AuthTokensResponse> {
        val email = request.email.trim().lowercase()
        log.info("auth.login start email={} schoolId={} ip={}", email, request.schoolId, ipAddress)
        return findAccountForLogin(email, request.schoolId)
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
            .doOnNext { tokens ->
                log.info(
                    "auth.login ok email={} accountId={} sessionId={} mustChangePassword={}",
                    email,
                    tokens.user.id,
                    tokens.sessionId,
                    tokens.mustChangePassword
                )
            }
            .doOnError { error ->
                log.warn("auth.login failed email={} schoolId={} reason={}", email, request.schoolId, error.message)
            }
    }

    fun refresh(rawRefreshToken: String): Mono<AuthTokensResponse> {
        log.info("auth.refresh start")
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
            .doOnNext { tokens ->
                log.info(
                    "auth.refresh ok accountId={} sessionId={}",
                    tokens.user.id,
                    tokens.sessionId
                )
            }
            .doOnError { error ->
                log.warn("auth.refresh failed reason={}", error.message)
            }
    }

    fun logout(currentUser: CurrentUser): Mono<Void> {
        log.info("auth.logout start user={} sessionId={}", currentUser.username, currentUser.sessionId)
        return userSessionService.requireActiveSession(currentUser.sessionId, currentUser.accountId)
            .flatMap { userSessionService.revoke(it, "LOGOUT") }
            .then()
            .doOnSuccess {
                log.info("auth.logout ok user={} sessionId={}", currentUser.username, currentUser.sessionId)
            }
    }

    /**
     * Révoque toutes les sessions ACTIVE **sauf** la session courante
     * (« déconnecter les autres appareils »).
     */
    fun logoutAll(currentUser: CurrentUser): Mono<Long> {
        log.info("auth.logoutAll start user={} accountId={}", currentUser.username, currentUser.accountId)
        return userSessionService.listActiveSessions(currentUser.accountId)
            .filter { it.id != currentUser.sessionId }
            .flatMap { userSessionService.revoke(it, "LOGOUT_ALL") }
            .count()
            .doOnNext { count ->
                log.info(
                    "auth.logoutAll ok user={} accountId={} revoked={} keptSession={}",
                    currentUser.username,
                    currentUser.accountId,
                    count,
                    currentUser.sessionId
                )
            }
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
                    current = session.id == currentUser.sessionId,
                    status = session.status.name,
                    deviceKind = inferDeviceKind(session.userAgent),
                )
            }
    }

    fun revokeSession(currentUser: CurrentUser, sessionId: String): Mono<Void> {
        return userSessionService.revokeByIdForAccount(sessionId, currentUser.accountId).then()
    }

    /**
     * Upload avatar UI (hors bulletin). Remplace l’éventuel précédent.
     */
    fun uploadAvatar(currentUser: CurrentUser, file: FilePart): Mono<UserAccountResponse> {
        val contentType = file.headers().contentType?.toString()?.substringBefore(";")?.trim()?.lowercase()
            ?: ""
        if (contentType !in AVATAR_CONTENT_TYPES) {
            return Mono.error(
                BusinessRuleException("AVATAR_TYPE", "Avatar must be JPEG, PNG or WebP")
            )
        }
        log.info("auth.uploadAvatar start accountId={}", currentUser.accountId)
        return DataBufferUtils.join(file.content())
            .flatMap { buffer ->
                val size = buffer.readableByteCount()
                if (size <= 0) {
                    DataBufferUtils.release(buffer)
                    return@flatMap Mono.error(BusinessRuleException("AVATAR_EMPTY", "Avatar file is empty"))
                }
                if (size > AVATAR_MAX_BYTES) {
                    DataBufferUtils.release(buffer)
                    return@flatMap Mono.error(
                        BusinessRuleException("AVATAR_TOO_LARGE", "Avatar must be at most 512 KB")
                    )
                }
                val bytes = ByteArray(size)
                buffer.read(bytes)
                DataBufferUtils.release(buffer)
                Mono.just(bytes to contentType)
            }
            .flatMap { (bytes, type) ->
                userAccountRepository.findById(currentUser.accountId)
                    .switchIfEmpty(Mono.error(UnauthorizedException("ACCOUNT_NOT_FOUND", "Account not found")))
                    .flatMap { account ->
                        val previousId = account.avatarFileId
                        val avatar = UserAvatar(
                            id = previousId,
                            accountId = account.id!!,
                            contentType = type,
                            sizeBytes = bytes.size.toLong(),
                            data = Binary(bytes)
                        )
                        userAvatarRepository.save(avatar)
                            .flatMap { saved ->
                                account.avatarFileId = saved.id
                                userAccountRepository.save(account)
                            }
                    }
            }
            .map { UserAccountMapper.toResponse(it) }
            .doOnNext {
                log.info("auth.uploadAvatar ok accountId={}", currentUser.accountId)
            }
    }

    fun getAvatar(currentUser: CurrentUser): Mono<UserAvatar> {
        return userAccountRepository.findById(currentUser.accountId)
            .switchIfEmpty(Mono.error(UnauthorizedException("ACCOUNT_NOT_FOUND", "Account not found")))
            .flatMap { account ->
                val avatarId = account.avatarFileId
                    ?: return@flatMap Mono.error(NotFoundException("AVATAR_NOT_FOUND", "No avatar"))
                userAvatarRepository.findById(avatarId)
                    .switchIfEmpty(
                        userAvatarRepository.findByAccountId(account.id!!)
                            .switchIfEmpty(Mono.error(NotFoundException("AVATAR_NOT_FOUND", "No avatar")))
                    )
            }
    }

    fun deleteAvatar(currentUser: CurrentUser): Mono<UserAccountResponse> {
        log.info("auth.deleteAvatar start accountId={}", currentUser.accountId)
        return userAccountRepository.findById(currentUser.accountId)
            .switchIfEmpty(Mono.error(UnauthorizedException("ACCOUNT_NOT_FOUND", "Account not found")))
            .flatMap { account ->
                val avatarId = account.avatarFileId
                account.avatarFileId = null
                userAccountRepository.save(account)
                    .flatMap { saved ->
                        val cleanup = when {
                            !avatarId.isNullOrBlank() -> userAvatarRepository.deleteById(avatarId)
                            else -> userAvatarRepository.deleteByAccountId(saved.id!!)
                        }
                        cleanup.thenReturn(UserAccountMapper.toResponse(saved))
                    }
            }
            .doOnNext {
                log.info("auth.deleteAvatar ok accountId={}", currentUser.accountId)
            }
    }

    /**
     * Change le mot de passe, révoque les autres sessions, **rotate** le refresh de la session
     * courante et renvoie de nouveaux jetons (claim `mcp` à jour).
     */
    fun changePassword(currentUser: CurrentUser, request: ChangePasswordRequest): Mono<AuthTokensResponse> {
        log.info("auth.changePassword start user={} accountId={}", currentUser.username, currentUser.accountId)
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
                userSessionService.listActiveSessions(saved.id!!)
                    .filter { it.id != currentUser.sessionId }
                    .flatMap { userSessionService.revoke(it, "PASSWORD_CHANGED") }
                    .then(
                        userSessionService.requireActiveSession(currentUser.sessionId, saved.id!!)
                            .flatMap { userSessionService.rotateRefreshToken(it) }
                            .map { issued -> toTokensResponse(saved, issued) }
                    )
            }
            .doOnNext {
                log.info("auth.changePassword ok user={} accountId={}", currentUser.username, currentUser.accountId)
            }
            .doOnError { error ->
                log.warn(
                    "auth.changePassword failed user={} accountId={} reason={}",
                    currentUser.username,
                    currentUser.accountId,
                    error.message
                )
            }
    }

    private fun findAccountForLogin(email: String, schoolId: String?): Mono<UserAccount> {
        // username du compte = e-mail normalisé. Avec schoolId : compte école, sinon plateforme.
        val lookup = if (schoolId.isNullOrBlank()) {
            userAccountRepository.findBySchoolIdIsNullAndUsername(email)
        } else {
            userAccountRepository.findBySchoolIdAndUsername(schoolId, email)
                .switchIfEmpty(userAccountRepository.findBySchoolIdIsNullAndUsername(email))
        }
        return lookup.switchIfEmpty(
            Mono.error(UnauthorizedException("BAD_CREDENTIALS", "Invalid email or password"))
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
        val willLock = account.failedLoginCount >= max
        if (willLock) {
            account.status = AccountStatus.LOCKED
            account.lockedUntil = Instant.now()
                .plus(securityProperties.lockout.lockDurationMinutes, ChronoUnit.MINUTES)
            account.failedLoginCount = 0
            log.warn(
                "auth.login lockout accountId={} username={} schoolId={}",
                account.id,
                account.username,
                account.schoolId
            )
        } else {
            log.warn(
                "auth.login bad credentials accountId={} username={} failedCount={}",
                account.id,
                account.username,
                account.failedLoginCount
            )
        }
        return userAccountRepository.save(account)
            .then(
                Mono.error(
                    UnauthorizedException("BAD_CREDENTIALS", "Invalid email or password")
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

    /** Classification légère pour l’UI profil — pas persistée. */
    private fun inferDeviceKind(userAgent: String?): String {
        if (userAgent.isNullOrBlank()) return "UNKNOWN"
        val ua = userAgent.lowercase()
        return when {
            listOf("ipad", "tablet", "kindle", "silk").any { it in ua } -> "TABLET"
            listOf("mobi", "android", "iphone", "ipod", "windows phone").any { it in ua } -> "MOBILE"
            else -> "DESKTOP"
        }
    }
}
