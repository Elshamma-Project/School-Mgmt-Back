package com.kobe.smels.service.grading

import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.shared.security.CurrentUser
import org.slf4j.LoggerFactory

internal object GradingAccess {
    private val log = LoggerFactory.getLogger(GradingAccess::class.java)

    fun assertAdmin(currentUser: CurrentUser) {
        if (!currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)) {
            throw ForbiddenException("FORBIDDEN", "Insufficient role for grading administration")
        }
    }

    fun assertDirector(currentUser: CurrentUser) {
        if (!currentUser.hasAnyRole(Role.DIRECTOR, Role.SUPER_ADMIN)) {
            throw ForbiddenException("FORBIDDEN", "Only DIRECTOR can reopen a closed session")
        }
    }

    fun resolveSchoolId(currentUser: CurrentUser, override: String?): String =
        currentUser.requireSchoolId(override)

    fun staffProfileId(currentUser: CurrentUser): String? =
        currentUser.profileId.takeIf { currentUser.profileType == ProfileType.STAFF }

    fun canManageSheet(currentUser: CurrentUser, teacherId: String?): Boolean {
        if (currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)) return true
        return currentUser.hasRole(Role.TEACHER) &&
            currentUser.profileType == ProfileType.STAFF &&
            currentUser.profileId != null &&
            currentUser.profileId == teacherId
    }

    fun assertCanManageSheet(currentUser: CurrentUser, teacherId: String?) {
        if (!canManageSheet(currentUser, teacherId)) {
            throw ForbiddenException("FORBIDDEN", "Not allowed to manage this grade sheet")
        }
    }

    fun logSuperAdmin(currentUser: CurrentUser, schoolId: String, action: String) {
        if (currentUser.isSuperAdmin()) {
            log.info(
                "SUPER_ADMIN tenant action action={} by={} targetSchoolId={}",
                action,
                currentUser.username,
                schoolId
            )
        }
    }
}
