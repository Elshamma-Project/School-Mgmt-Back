package com.kobe.smels.service.reporting

import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.model.user.Role
import com.kobe.smels.shared.security.CurrentUser

internal object ReportingAccess {
    fun assertAdmin(currentUser: CurrentUser) {
        if (!currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)) {
            throw ForbiddenException("FORBIDDEN", "Insufficient role for reporting administration")
        }
    }

    fun assertDirector(currentUser: CurrentUser) {
        if (!currentUser.hasAnyRole(Role.DIRECTOR, Role.SUPER_ADMIN)) {
            throw ForbiddenException("FORBIDDEN", "Only DIRECTOR can modify report templates")
        }
    }

    fun assertCanReadTemplates(currentUser: CurrentUser) {
        if (!currentUser.hasAnyRole(
                Role.ADMINISTRATION,
                Role.DIRECTOR,
                Role.SUPER_ADMIN,
                Role.TEACHER
            )
        ) {
            throw ForbiddenException("FORBIDDEN", "Insufficient role to read report templates")
        }
    }

    fun resolveSchoolId(currentUser: CurrentUser, override: String?): String =
        currentUser.requireSchoolId(override)
}
