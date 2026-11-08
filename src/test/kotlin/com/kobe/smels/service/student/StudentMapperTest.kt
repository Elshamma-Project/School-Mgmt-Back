package com.kobe.smels.service.student

import com.kobe.smels.model.shared.Sex
import com.kobe.smels.model.student.Enrollment
import com.kobe.smels.model.student.EnrollmentStatus
import com.kobe.smels.model.student.Guardian
import com.kobe.smels.model.student.Student
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudentMapperTest {
    @Test
    fun `maps student summary without leaking contact`() {
        val student = Student(
            id = "stu-1",
            schoolId = "school-1",
            matricule = "TEST-0001",
            lastName = "ALPHA",
            firstName = "Sample",
            sex = Sex.FEMALE,
            active = true
        )
        val summary = StudentMapper.toStudentSummary(student)
        assertEquals("ALPHA Sample", summary.displayName)
        assertEquals("TEST-0001", summary.matricule)
        assertTrue(summary.active)
    }

    @Test
    fun `maps guardian and enrollment summaries`() {
        val guardian = Guardian(
            id = "g-1",
            schoolId = "school-1",
            lastName = "BETA",
            firstName = "Parent",
            active = true
        )
        val enrollment = Enrollment(
            id = "e-1",
            schoolId = "school-1",
            studentId = "stu-1",
            academicYearId = "year-1",
            classId = "class-1",
            levelId = "level-1",
            status = EnrollmentStatus.ENROLLED
        )
        assertEquals("BETA Parent", StudentMapper.toGuardianSummary(guardian).displayName)
        assertEquals(EnrollmentStatus.ENROLLED, StudentMapper.toEnrollmentSummary(enrollment).status)
    }
}
