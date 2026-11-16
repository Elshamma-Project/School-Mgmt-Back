package com.kobe.smels.service.timetable

import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.shared.security.CurrentUser
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Q-32 : SG n’écrit l’EDT que si timetableSurveillantCanEdit. */
class TimetableAccessTest {

    @Test
    fun `ADMINISTRATION can manage timetable`() {
        TimetableAccess.assertCanManage(adminUser(), sgCanEdit = false)
    }

    @Test
    fun `DIRECTOR can manage timetable`() {
        TimetableAccess.assertCanManage(directorUser(), sgCanEdit = false)
    }

    @Test
    fun `SURVEILLANT_GENERAL cannot manage when flag false`() {
        assertFailsWith<ForbiddenException> {
            TimetableAccess.assertCanManage(sgUser(), sgCanEdit = false)
        }
    }

    @Test
    fun `SURVEILLANT_GENERAL can manage when flag true`() {
        TimetableAccess.assertCanManage(sgUser(), sgCanEdit = true)
    }

    @Test
    fun `TEACHER cannot manage timetable`() {
        assertFailsWith<ForbiddenException> {
            TimetableAccess.assertCanManage(teacherUser(), sgCanEdit = true)
        }
    }

    @Test
    fun `TEACHER can read timetable`() {
        TimetableAccess.assertCanRead(teacherUser())
    }

    @Test
    fun `SURVEILLANT_GENERAL can read timetable`() {
        TimetableAccess.assertCanRead(sgUser())
    }

    private fun adminUser() = user("admin", Role.ADMINISTRATION)
    private fun directorUser() = user("dir", Role.DIRECTOR)
    private fun sgUser() = user("sg", Role.SURVEILLANT_GENERAL)
    private fun teacherUser() = user("teacher", Role.TEACHER, profileId = "staff-t")

    private fun user(
        username: String,
        role: Role,
        profileId: String? = "staff-1"
    ) = CurrentUser(
        accountId = "acc-$username",
        sessionId = "s1",
        username = username,
        schoolId = "school-1",
        roles = setOf(role),
        profileType = ProfileType.STAFF,
        profileId = profileId,
        mustChangePassword = false
    )
}
