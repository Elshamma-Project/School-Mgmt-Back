package com.kobe.smels.service.academic

import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.model.user.Role
import com.kobe.smels.shared.security.CurrentUser
import org.slf4j.LoggerFactory

internal object AcademicAccess {
    private val log = LoggerFactory.getLogger(AcademicAccess::class.java)

    fun assertCanManage(currentUser: CurrentUser) {
        if (!currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)) {
            throw ForbiddenException("FORBIDDEN", "Insufficient role to manage academic data")
        }
    }

    fun assertCanReadCatalog(currentUser: CurrentUser) {
        if (!currentUser.hasAnyRole(
                Role.TEACHER,
                Role.ADMINISTRATION,
                Role.DIRECTOR,
                Role.SUPER_ADMIN
            )
        ) {
            throw ForbiddenException("FORBIDDEN", "Insufficient role to read academic catalog")
        }
    }

    fun assertCanManageSchool(currentUser: CurrentUser) {
        if (!currentUser.hasAnyRole(Role.DIRECTOR, Role.SUPER_ADMIN, Role.ADMINISTRATION)) {
            throw ForbiddenException("FORBIDDEN", "Insufficient role to manage school")
        }
    }

    /** Identité, logo, en-têtes documents — directeur uniquement (+ SUPER_ADMIN). */
    fun assertCanManageSchoolSettings(currentUser: CurrentUser) {
        if (!currentUser.hasAnyRole(Role.DIRECTOR, Role.SUPER_ADMIN)) {
            throw ForbiddenException("FORBIDDEN", "Only the director can manage school settings")
        }
    }

    fun resolveSchoolId(currentUser: CurrentUser, overrideSchoolId: String?): String =
        currentUser.requireSchoolId(overrideSchoolId)

    fun logSuperAdmin(currentUser: CurrentUser, schoolId: String, action: String) {
        if (currentUser.isSuperAdmin()) {
            log.info(
                "SUPER_ADMIN tenant action action={} by={} targetSchoolId={} tokenSchoolId={}",
                action,
                currentUser.username,
                schoolId,
                currentUser.schoolId
            )
        }
    }
}
