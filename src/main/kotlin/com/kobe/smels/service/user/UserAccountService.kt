package com.kobe.smels.service.user

import com.kobe.smels.dto.user.CreateUserAccountRequest
import com.kobe.smels.dto.user.UserAccountResponse
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.exception.ConflictException
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.user.AccountStatus
import com.kobe.smels.model.user.Role
import com.kobe.smels.model.user.UserAccount
import com.kobe.smels.repository.user.UserAccountRepository
import com.kobe.smels.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

/**
 * Gestion des comptes. Contrôles de rôle dans la couche service (docs/05).
 */
@Service
class UserAccountService(
    private val userAccountRepository: UserAccountRepository,
    private val passwordEncoder: PasswordEncoder,
    private val userSessionService: UserSessionService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun create(currentUser: CurrentUser, request: CreateUserAccountRequest): Mono<UserAccountResponse> {
        val username = request.username.trim()
        log.info(
            "user.create start by={} targetUsername={} schoolId={} roles={} profileType={}",
            currentUser.username,
            username,
            request.schoolId,
            request.roles.joinToString(",") { it.name },
            request.profileType
        )
        return Mono.fromCallable { validateCreateAuthorization(currentUser, request) }
            .then(ensureUsernameAvailable(request.schoolId, username))
            .then(
                Mono.fromCallable {
                    UserAccount(
                        schoolId = if (request.roles.contains(Role.SUPER_ADMIN)) null else request.schoolId,
                        username = username,
                        passwordHash = passwordEncoder.encode(request.password),
                        roles = request.roles,
                        profileType = request.profileType,
                        profileId = request.profileId,
                        status = request.status,
                        mustChangePassword = request.mustChangePassword
                    )
                }
            )
            .flatMap { userAccountRepository.save(it) }
            .map { UserAccountMapper.toResponse(it) }
            .doOnNext { created ->
                log.info(
                    "user.create ok by={} accountId={} username={} schoolId={}",
                    currentUser.username,
                    created.id,
                    created.username,
                    created.schoolId
                )
            }
            .doOnError { error ->
                log.warn(
                    "user.create failed by={} targetUsername={} reason={}",
                    currentUser.username,
                    username,
                    error.message
                )
            }
    }

    fun getById(currentUser: CurrentUser, id: String): Mono<UserAccountResponse> {
        return userAccountRepository.findById(id)
            .switchIfEmpty(Mono.error(NotFoundException("USER_NOT_FOUND", "User account not found")))
            .flatMap { account ->
                if (!canRead(currentUser, account)) {
                    Mono.error(ForbiddenException("FORBIDDEN", "Cannot read this account"))
                } else {
                    Mono.just(UserAccountMapper.toResponse(account))
                }
            }
    }

    fun disable(currentUser: CurrentUser, id: String): Mono<UserAccountResponse> {
        log.info("user.disable start by={} accountId={}", currentUser.username, id)
        return userAccountRepository.findById(id)
            .switchIfEmpty(Mono.error(NotFoundException("USER_NOT_FOUND", "User account not found")))
            .flatMap { account ->
                if (!canManage(currentUser, account)) {
                    return@flatMap Mono.error(ForbiddenException("FORBIDDEN", "Cannot manage this account"))
                }
                account.status = AccountStatus.DISABLED
                userAccountRepository.save(account)
                    .flatMap { saved ->
                        userSessionService.revokeAllForAccount(saved.id!!, "ACCOUNT_DISABLED")
                            .thenReturn(UserAccountMapper.toResponse(saved))
                    }
            }
            .doOnNext {
                log.info("user.disable ok by={} accountId={}", currentUser.username, id)
            }
            .doOnError { error ->
                log.warn("user.disable failed by={} accountId={} reason={}", currentUser.username, id, error.message)
            }
    }

    fun resetPassword(currentUser: CurrentUser, id: String, temporaryPassword: String): Mono<UserAccountResponse> {
        log.info("user.resetPassword start by={} accountId={}", currentUser.username, id)
        if (temporaryPassword.length < 8) {
            return Mono.error(BusinessRuleException("PASSWORD_TOO_SHORT", "Password must be at least 8 characters"))
        }
        return userAccountRepository.findById(id)
            .switchIfEmpty(Mono.error(NotFoundException("USER_NOT_FOUND", "User account not found")))
            .flatMap { account ->
                if (!canManage(currentUser, account)) {
                    return@flatMap Mono.error(ForbiddenException("FORBIDDEN", "Cannot manage this account"))
                }
                account.passwordHash = passwordEncoder.encode(temporaryPassword)
                account.mustChangePassword = true
                account.failedLoginCount = 0
                account.lockedUntil = null
                if (account.status == AccountStatus.LOCKED) {
                    account.status = AccountStatus.ACTIVE
                }
                userAccountRepository.save(account)
                    .flatMap { saved ->
                        userSessionService.revokeAllForAccount(saved.id!!, "PASSWORD_RESET")
                            .thenReturn(UserAccountMapper.toResponse(saved))
                    }
            }
            .doOnNext {
                log.info("user.resetPassword ok by={} accountId={}", currentUser.username, id)
            }
            .doOnError { error ->
                log.warn(
                    "user.resetPassword failed by={} accountId={} reason={}",
                    currentUser.username,
                    id,
                    error.message
                )
            }
    }

    private fun validateCreateAuthorization(currentUser: CurrentUser, request: CreateUserAccountRequest) {
        if (request.roles.isEmpty()) {
            throw BusinessRuleException("ROLES_REQUIRED", "At least one role is required")
        }
        if (request.roles.contains(Role.SUPER_ADMIN) && !currentUser.isSuperAdmin()) {
            throw ForbiddenException("FORBIDDEN", "Only SUPER_ADMIN can create SUPER_ADMIN accounts")
        }
        val creatingAdminRoles = request.roles.any { it == Role.ADMINISTRATION || it == Role.DIRECTOR }
        val creatingStaffPortal = request.roles.any { it == Role.TEACHER || it == Role.ADMINISTRATION || it == Role.DIRECTOR }
        val creatingFamily = request.roles.any { it == Role.STUDENT || it == Role.PARENT }

        when {
            currentUser.isSuperAdmin() -> Unit
            creatingAdminRoles && !currentUser.hasAnyRole(Role.DIRECTOR, Role.SUPER_ADMIN) ->
                throw ForbiddenException("FORBIDDEN", "Only DIRECTOR can create administration accounts")
            creatingFamily && !currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN) ->
                throw ForbiddenException("FORBIDDEN", "Insufficient role to create student/parent accounts")
            creatingStaffPortal && !creatingAdminRoles &&
                !currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN) ->
                throw ForbiddenException("FORBIDDEN", "Insufficient role to create staff accounts")
            !currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN) ->
                throw ForbiddenException("FORBIDDEN", "Insufficient role to create accounts")
        }

        if (!currentUser.isSuperAdmin()) {
            val schoolId = currentUser.schoolId
                ?: throw ForbiddenException("FORBIDDEN", "Missing school context")
            if (request.schoolId != null && request.schoolId != schoolId) {
                throw ForbiddenException("FORBIDDEN", "Cannot create account for another school")
            }
        }
    }

    private fun ensureUsernameAvailable(schoolId: String?, username: String): Mono<Void> {
        val existing = if (schoolId.isNullOrBlank()) {
            userAccountRepository.findBySchoolIdIsNullAndUsername(username)
        } else {
            userAccountRepository.findBySchoolIdAndUsername(schoolId, username)
        }
        return existing.flatMap<Void> {
            Mono.error(ConflictException("USERNAME_TAKEN", "Username already exists"))
        }.switchIfEmpty(Mono.empty())
    }

    private fun canRead(currentUser: CurrentUser, account: UserAccount): Boolean {
        if (currentUser.accountId == account.id) return true
        if (currentUser.isSuperAdmin()) return true
        if (currentUser.schoolId != null && currentUser.schoolId == account.schoolId) {
            return currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR)
        }
        return false
    }

    private fun canManage(currentUser: CurrentUser, account: UserAccount): Boolean {
        if (currentUser.accountId == account.id) return false
        if (account.roles.contains(Role.SUPER_ADMIN) && !currentUser.isSuperAdmin()) return false
        if (account.roles.any { it == Role.ADMINISTRATION || it == Role.DIRECTOR }) {
            return currentUser.hasAnyRole(Role.DIRECTOR, Role.SUPER_ADMIN) &&
                (currentUser.isSuperAdmin() || currentUser.schoolId == account.schoolId)
        }
        return currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN) &&
            (currentUser.isSuperAdmin() || currentUser.schoolId == account.schoolId)
    }
}
