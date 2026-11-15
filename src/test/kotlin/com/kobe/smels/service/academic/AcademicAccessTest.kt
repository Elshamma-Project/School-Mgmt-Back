package com.kobe.smels.service.academic

import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.shared.security.CurrentUser
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Lecture catalogue : enseignant, SG (Q-38 hub visas), admin, directeur, super-admin.
 */
class AcademicAccessTest {

    @Test
    fun `SURVEILLANT_GENERAL can read academic catalog`() {
        AcademicAccess.assertCanReadCatalog(sgUser())
    }

    @Test
    fun `TEACHER can read academic catalog`() {
        AcademicAccess.assertCanReadCatalog(teacherUser())
    }

    @Test
    fun `STUDENT cannot read academic catalog`() {
        assertFailsWith<ForbiddenException> {
            AcademicAccess.assertCanReadCatalog(studentUser())
        }
    }

    @Test
    fun `PARENT cannot read academic catalog`() {
        assertFailsWith<ForbiddenException> {
            AcademicAccess.assertCanReadCatalog(parentUser())
        }
    }

    @Test
    fun `SURVEILLANT_GENERAL cannot manage academic catalog`() {
        assertFailsWith<ForbiddenException> {
            AcademicAccess.assertCanManage(sgUser())
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

    private fun studentUser() = CurrentUser(
        accountId = "acc-st",
        sessionId = "s1",
        username = "student",
        schoolId = "school-1",
        roles = setOf(Role.STUDENT),
        profileType = ProfileType.STUDENT,
        profileId = "stu-1",
        mustChangePassword = false
    )

    private fun parentUser() = CurrentUser(
        accountId = "acc-p",
        sessionId = "s1",
        username = "parent",
        schoolId = "school-1",
        roles = setOf(Role.PARENT),
        profileType = ProfileType.GUARDIAN,
        profileId = "g-1",
        mustChangePassword = false
    )
}
