package com.kobe.beesm.service.user

import com.kobe.beesm.dto.user.AvatarBinaryDto
import com.kobe.beesm.dto.user.CreateUserAccountRequest
import com.kobe.beesm.dto.user.StaffLinkedAccountDto
import com.kobe.beesm.dto.user.UserAccountResponse
import com.kobe.beesm.exception.BusinessRuleException
import com.kobe.beesm.exception.ConflictException
import com.kobe.beesm.exception.ForbiddenException
import com.kobe.beesm.exception.NotFoundException
import com.kobe.beesm.model.user.AccountStatus
import com.kobe.beesm.model.user.ProfileType
import com.kobe.beesm.model.user.Role
import com.kobe.beesm.model.user.UserAccount
import com.kobe.beesm.repository.user.UserAccountRepository
import com.kobe.beesm.repository.user.UserAvatarRepository
import com.kobe.beesm.service.user.api.UserAccountApi
import com.kobe.beesm.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * Gestion des comptes. Contrôles de rôle dans la couche service (docs/05).
 */
@Service
class UserAccountService(
    private val userAccountRepository: UserAccountRepository,
    private val userAvatarRepository: UserAvatarRepository,
    private val passwordEncoder: PasswordEncoder,
    private val userSessionService: UserSessionService
) : UserAccountApi {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun createAccount(
        currentUser: CurrentUser,
        request: CreateUserAccountRequest
    ): Mono<UserAccountResponse> = create(currentUser, request)

    fun create(currentUser: CurrentUser, request: CreateUserAccountRequest): Mono<UserAccountResponse> {
        val username = request.username.trim().lowercase()
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

    override fun findStaffLinkedAccounts(
        schoolId: String,
        staffIds: Collection<String>
    ): Mono<Map<String, StaffLinkedAccountDto>> =
        findLinkedAccounts(schoolId, ProfileType.STAFF, staffIds)

    override fun findGuardianLinkedAccounts(
        schoolId: String,
        guardianIds: Collection<String>
    ): Mono<Map<String, StaffLinkedAccountDto>> =
        findLinkedAccounts(schoolId, ProfileType.GUARDIAN, guardianIds)

    private fun findLinkedAccounts(
        schoolId: String,
        profileType: ProfileType,
        profileIds: Collection<String>
    ): Mono<Map<String, StaffLinkedAccountDto>> {
        val ids = profileIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (ids.isEmpty()) {
            return Mono.just(emptyMap())
        }
        return userAccountRepository
            .findBySchoolIdAndProfileTypeAndProfileIdIn(schoolId, profileType, ids)
            .collectList()
            .map { accounts ->
                accounts
                    .mapNotNull { account ->
                        val profileId = account.profileId?.trim()?.takeIf { it.isNotEmpty() }
                            ?: return@mapNotNull null
                        val accountId = account.id?.trim()?.takeIf { it.isNotEmpty() }
                            ?: return@mapNotNull null
                        profileId to StaffLinkedAccountDto(
                            accountId = accountId,
                            username = account.username,
                            lastLoginAt = account.lastLoginAt,
                            hasAvatar = !account.avatarFileId.isNullOrBlank()
                        )
                    }
                    .toMap()
            }
    }

    override fun findStaffLastLogins(
        schoolId: String,
        staffIds: Collection<String>
    ): Mono<Map<String, Instant>> =
        findStaffLinkedAccounts(schoolId, staffIds).map { linked ->
            linked.mapNotNull { (staffId, account) ->
                account.lastLoginAt?.let { staffId to it }
            }.toMap()
        }

    override fun findStaffAvatar(schoolId: String, staffId: String): Mono<AvatarBinaryDto> {
        val sid = staffId.trim()
        if (sid.isEmpty()) {
            return Mono.error(NotFoundException("AVATAR_NOT_FOUND", "No avatar"))
        }
        return userAccountRepository
            .findBySchoolIdAndProfileTypeAndProfileIdIn(schoolId, ProfileType.STAFF, listOf(sid))
            .next()
            .switchIfEmpty(Mono.error(NotFoundException("AVATAR_NOT_FOUND", "No avatar")))
            .flatMap { account ->
                val avatarId = account.avatarFileId?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@flatMap Mono.error(NotFoundException("AVATAR_NOT_FOUND", "No avatar"))
                userAvatarRepository.findById(avatarId)
                    .switchIfEmpty(
                        userAvatarRepository.findByAccountId(account.id!!)
                            .switchIfEmpty(Mono.error(NotFoundException("AVATAR_NOT_FOUND", "No avatar")))
                    )
            }
            .map { avatar ->
                AvatarBinaryDto(
                    contentType = avatar.contentType,
                    sizeBytes = avatar.sizeBytes,
                    data = avatar.data.data
                )
            }
    }

    override fun findActiveAccountIdsByRoles(schoolId: String, roles: Collection<Role>): Flux<String> {
        val roleSet = roles.toSet()
        if (roleSet.isEmpty()) return Flux.empty()
        return userAccountRepository
            .findBySchoolIdAndStatusAndRolesIn(schoolId, AccountStatus.ACTIVE, roleSet)
            .mapNotNull { it.id }
            .distinct()
    }

    override fun findUsernamesByIds(
        schoolId: String,
        accountIds: Collection<String>
    ): Mono<Map<String, String>> {
        val ids = accountIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (ids.isEmpty()) {
            return Mono.just(emptyMap())
        }
        return userAccountRepository
            .findAllById(ids)
            .filter { account ->
                val id = account.id?.trim().orEmpty()
                id.isNotEmpty() && account.schoolId == schoolId
            }
            .collectMap(
                { it.id!!.trim() },
                { it.username.trim() }
            )
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
