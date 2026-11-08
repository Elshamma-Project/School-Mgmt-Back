package com.kobe.smels.dto.user

import com.kobe.smels.model.user.AccountStatus
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class LoginRequest(
    @field:NotBlank
    val username: String,
    @field:NotBlank
    val password: String,
    /** Requis pour les comptes rattachés à un établissement ; omis pour SUPER_ADMIN. */
    val schoolId: String? = null,
    val deviceLabel: String? = null
)

data class RefreshTokenRequest(
    @field:NotBlank
    val refreshToken: String
)

data class ChangePasswordRequest(
    @field:NotBlank
    val currentPassword: String,
    @field:NotBlank
    @field:Size(min = 8, max = 128)
    val newPassword: String,
    @field:NotBlank
    val confirmPassword: String
)

data class CreateUserAccountRequest(
    @field:NotBlank
    val username: String,
    @field:NotBlank
    @field:Size(min = 8, max = 128)
    val password: String,
    val schoolId: String? = null,
    val roles: Set<Role>,
    val profileType: ProfileType = ProfileType.NONE,
    val profileId: String? = null,
    val status: AccountStatus = AccountStatus.ACTIVE,
    val mustChangePassword: Boolean = true
)

data class AuthTokensResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresInMs: Long,
    val sessionId: String,
    val mustChangePassword: Boolean,
    val user: UserAccountResponse
)

data class UserAccountResponse(
    val id: String,
    val schoolId: String?,
    val username: String,
    val roles: Set<Role>,
    val profileType: ProfileType,
    val profileId: String?,
    val status: AccountStatus,
    val mustChangePassword: Boolean,
    val lastLoginAt: Instant?
)

data class SessionResponse(
    val id: String,
    val deviceLabel: String?,
    val userAgent: String?,
    val ipAddress: String?,
    val createdAt: Instant,
    val lastSeenAt: Instant,
    val expiresAt: Instant,
    val current: Boolean
)
