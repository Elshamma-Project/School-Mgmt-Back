package com.kobe.beesm.service.grading

import com.kobe.beesm.exception.ForbiddenException
import com.kobe.beesm.model.user.ProfileType
import com.kobe.beesm.model.user.Role
import com.kobe.beesm.shared.security.CurrentUser
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Visa SG + lecture fiches de report (Q-38). */
class GradingAccessSgTest {

    @Test
    fun `SURVEILLANT_GENERAL can visa report sheets`() {
        GradingAccess.assertSurveillantGeneral(sgUser())
    }

    @Test
    fun `SURVEILLANT_GENERAL can read any report sheet`() {
        assertTrue(GradingAccess.canReadReportSheet(sgUser(), teacherId = "other-teacher"))
        GradingAccess.assertCanReadReportSheet(sgUser(), "other-teacher")
    }

    @Test
    fun `SURVEILLANT_GENERAL can read evaluation sessions`() {
        GradingAccess.assertStaffCanReadSessions(sgUser())
    }

    @Test
    fun `TEACHER cannot visa as Surveillant General`() {
        assertFailsWith<ForbiddenException> {
            GradingAccess.assertSurveillantGeneral(teacherUser())
        }
    }

    @Test
    fun `SURVEILLANT_GENERAL cannot give Principal visa`() {
        assertFailsWith<ForbiddenException> {
            GradingAccess.assertPrincipalVisa(sgUser())
        }
    }

    private fun sgUser() = CurrentUser(
        accountId = "acc-sg",
        sessionId = "s1",
        username = "sg",
        schoolId = "school-1",
        roles = setOf(Role.SURVEILLANT_GENERAL),
        profileType = ProfileType.STAFF,
        profileId = "staff-sg",
        mustChangePassword = false
    )

    private fun teacherUser() = CurrentUser(
        accountId = "acc-t",
        sessionId = "s1",
        username = "teacher",
        schoolId = "school-1",
        roles = setOf(Role.TEACHER),
        profileType = ProfileType.STAFF,
        profileId = "staff-t",
        mustChangePassword = false
    )
}
