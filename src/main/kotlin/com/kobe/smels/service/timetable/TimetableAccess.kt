package com.kobe.smels.service.timetable

import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.model.user.Role
import com.kobe.smels.shared.security.CurrentUser
import org.slf4j.LoggerFactory

internal object TimetableAccess {
    private val log = LoggerFactory.getLogger(TimetableAccess::class.java)

    fun assertCanRead(currentUser: CurrentUser) {
        if (!currentUser.hasAnyRole(
                Role.TEACHER,
                Role.SURVEILLANT_GENERAL,
                Role.ADMINISTRATION,
                Role.DIRECTOR,
                Role.SUPER_ADMIN,
                Role.STUDENT,
                Role.PARENT
            )
        ) {
            throw ForbiddenException("FORBIDDEN", "Insufficient role to read timetable")
        }
    }

    /**
     * Modification / publication EDT.
     * SG seulement si [sgCanEdit] (School.timetableSurveillantCanEdit, Q-32).
     */
    fun assertCanManage(currentUser: CurrentUser, sgCanEdit: Boolean = false) {
        if (currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)) return
        if (sgCanEdit && currentUser.hasRole(Role.SURVEILLANT_GENERAL)) return
        throw ForbiddenException("FORBIDDEN", "Insufficient role to manage timetable")
    }

    /** Visa des demandes de modification de créneau (enseignant → admin/directeur). */
    fun assertCanReviewChangeRequests(currentUser: CurrentUser) {
        if (!currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)) {
            throw ForbiddenException(
                "FORBIDDEN",
                "Only ADMINISTRATION or DIRECTOR can review timetable change requests"
            )
        }
    }

    fun assertTeacher(currentUser: CurrentUser) {
        if (!currentUser.hasRole(Role.TEACHER) &&
            !currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)
        ) {
            throw ForbiddenException("FORBIDDEN", "Only teachers can request timetable changes")
        }
    }

    fun resolveSchoolId(currentUser: CurrentUser, override: String?): String =
        currentUser.requireSchoolId(override)

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
