package com.kobe.beesm.service.notification

import com.kobe.beesm.exception.ForbiddenException
import com.kobe.beesm.model.user.Role
import com.kobe.beesm.shared.security.CurrentUser

internal object NotificationAccess {
    fun resolveSchoolId(currentUser: CurrentUser, schoolIdOverride: String? = null): String =
        currentUser.requireSchoolId(schoolIdOverride)

    fun assertCanUseInbox(currentUser: CurrentUser) {
        if (!currentUser.hasAnyRole(
                Role.TEACHER,
                Role.ADMINISTRATION,
                Role.DIRECTOR,
                Role.SURVEILLANT_GENERAL,
                Role.SUPER_ADMIN
            )
        ) {
            throw ForbiddenException("FORBIDDEN", "Notifications not available for this role")
        }
    }

    fun assertCanSubmitSignal(currentUser: CurrentUser) {
        if (!currentUser.hasAnyRole(Role.TEACHER, Role.SUPER_ADMIN)) {
            throw ForbiddenException("FORBIDDEN", "Only teachers can submit official signals")
        }
    }

    fun assertCanManageSignals(currentUser: CurrentUser) {
        if (!currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)) {
            throw ForbiddenException("FORBIDDEN", "Only administration or director can manage signals")
        }
    }
}
