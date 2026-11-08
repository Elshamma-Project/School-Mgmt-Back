package com.kobe.smels.shared.security

import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role

/**
 * Identité extraite du jeton d'accès. Les modules lisent ces champs, jamais le corps de requête
 * pour l'identité (docs/05 §4, docs/08 §6).
 */
data class CurrentUser(
    val accountId: String,
    val sessionId: String,
    val username: String,
    val schoolId: String?,
    val roles: Set<Role>,
    val profileType: ProfileType,
    val profileId: String?,
    val mustChangePassword: Boolean
) {
    fun hasRole(role: Role): Boolean = roles.contains(role)

    fun hasAnyRole(vararg candidates: Role): Boolean = candidates.any { roles.contains(it) }

    fun isSuperAdmin(): Boolean = hasRole(Role.SUPER_ADMIN)

    /** Établissement effectif : celui du jeton, sauf SUPER_ADMIN qui peut en préciser un. */
    fun requireSchoolId(overrideSchoolId: String? = null): String {
        if (isSuperAdmin()) {
            return overrideSchoolId
                ?: schoolId
                ?: throw IllegalStateException("SUPER_ADMIN must provide schoolId for tenant-scoped actions")
        }
        val sid = schoolId ?: throw IllegalStateException("Authenticated user has no schoolId")
        if (overrideSchoolId != null && overrideSchoolId != sid) {
            throw IllegalStateException("Cross-school access denied")
        }
        return sid
    }
}
