package com.kobe.smels.service.student

import com.kobe.smels.dto.academic.AcademicYearSummaryDto
import com.kobe.smels.dto.academic.SchoolClassSummaryDto
import com.kobe.smels.dto.student.EnrollmentSummaryDto
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.model.academic.AcademicYearStatus
import com.kobe.smels.model.shared.Language
import com.kobe.smels.model.shared.Section
import com.kobe.smels.model.shared.Sex
import com.kobe.smels.model.student.EnrollmentStatus
import com.kobe.smels.model.student.GuardianLink
import com.kobe.smels.model.student.GuardianRelationship
import com.kobe.smels.model.student.Student
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.student.GuardianRepository
import com.kobe.smels.repository.student.StudentRepository
import com.kobe.smels.service.academic.api.AcademicYearApi
import com.kobe.smels.service.academic.api.ClassSubjectApi
import com.kobe.smels.service.academic.api.SchoolClassApi
import com.kobe.smels.service.student.api.EnrollmentApi
import com.kobe.smels.shared.outbox.OutboxWriter
import com.kobe.smels.shared.security.CurrentUser
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import kotlin.test.Test
import kotlin.test.assertEquals

class StudentAcademicRecordTest {
    private val studentRepository: StudentRepository = mock()
    private val guardianRepository: GuardianRepository = mock()
    private val outboxWriter: OutboxWriter = mock()
    private val classSubjectApi: ClassSubjectApi = mock()
    private val enrollmentApi: EnrollmentApi = mock()
    private val academicYearApi: AcademicYearApi = mock()
    private val schoolClassApi: SchoolClassApi = mock()
    private val service = StudentService(
        studentRepository,
        guardianRepository,
        outboxWriter,
        classSubjectApi,
        enrollmentApi,
        academicYearApi,
        schoolClassApi
    )

    @Test
    fun `academic record lists all years for linked parent`() {
        val student = Student(
            id = "stu-1",
            schoolId = "school-1",
            matricule = "TEST-0001",
            lastName = "SAMPLE",
            firstName = "One",
            sex = Sex.FEMALE,
            guardians = listOf(
                GuardianLink(guardianId = "g-1", relationship = GuardianRelationship.MOTHER, primary = true)
            )
        )
        whenever(studentRepository.findByIdAndSchoolId("stu-1", "school-1")).thenReturn(Mono.just(student))
        whenever(enrollmentApi.findByStudent("school-1", "stu-1")).thenReturn(
            Flux.just(
                EnrollmentSummaryDto(
                    id = "enr-6e",
                    schoolId = "school-1",
                    studentId = "stu-1",
                    academicYearId = "year-6e",
                    classId = "class-6e",
                    levelId = "lvl-6e",
                    status = EnrollmentStatus.COMPLETED,
                    repeater = false
                ),
                EnrollmentSummaryDto(
                    id = "enr-2nde",
                    schoolId = "school-1",
                    studentId = "stu-1",
                    academicYearId = "year-2nde",
                    classId = "class-2nde",
                    levelId = "lvl-2nde",
                    status = EnrollmentStatus.ENROLLED,
                    repeater = false
                )
            )
        )
        whenever(academicYearApi.getSummary("school-1", "year-6e")).thenReturn(
            Mono.just(
                AcademicYearSummaryDto(
                    id = "year-6e",
                    schoolId = "school-1",
                    label = "2020-2021",
                    current = false,
                    status = AcademicYearStatus.CLOSED
                )
            )
        )
        whenever(academicYearApi.getSummary("school-1", "year-2nde")).thenReturn(
            Mono.just(
                AcademicYearSummaryDto(
                    id = "year-2nde",
                    schoolId = "school-1",
                    label = "2025-2026",
                    current = true,
                    status = AcademicYearStatus.ACTIVE
                )
            )
        )
        whenever(schoolClassApi.getSummary("school-1", "class-6e")).thenReturn(
            Mono.just(
                SchoolClassSummaryDto(
                    id = "class-6e",
                    schoolId = "school-1",
                    academicYearId = "year-6e",
                    levelId = "lvl-6e",
                    levelCode = "6EME",
                    name = "6ème A",
                    section = Section.FRANCOPHONE,
                    defaultLanguage = Language.FR,
                    active = true
                )
            )
        )
        whenever(schoolClassApi.getSummary("school-1", "class-2nde")).thenReturn(
            Mono.just(
                SchoolClassSummaryDto(
                    id = "class-2nde",
                    schoolId = "school-1",
                    academicYearId = "year-2nde",
                    levelId = "lvl-2nde",
                    levelCode = "2NDE",
                    name = "2nde C",
                    section = Section.FRANCOPHONE,
                    defaultLanguage = Language.FR,
                    active = true
                )
            )
        )

        StepVerifier.create(service.getAcademicRecord(parentUser("g-1"), "stu-1"))
            .assertNext { record ->
                assertEquals("stu-1", record.studentId)
                assertEquals(2, record.years.size)
                assertEquals("2025-2026", record.years[0].academicYearLabel)
                assertEquals("2nde C", record.years[0].className)
                assertEquals("2020-2021", record.years[1].academicYearLabel)
                assertEquals("6ème A", record.years[1].className)
            }
            .verifyComplete()
    }

    @Test
    fun `academic record refused for unrelated parent`() {
        val student = Student(
            id = "stu-1",
            schoolId = "school-1",
            matricule = "TEST-0001",
            lastName = "SAMPLE",
            firstName = "One",
            sex = Sex.MALE
        )
        whenever(studentRepository.findByIdAndSchoolId("stu-1", "school-1")).thenReturn(Mono.just(student))

        StepVerifier.create(service.getAcademicRecord(parentUser("g-other"), "stu-1"))
            .expectError(ForbiddenException::class.java)
            .verify()
    }

    private fun parentUser(guardianId: String) = CurrentUser(
        accountId = "acc-p",
        sessionId = "s1",
        username = "parent",
        schoolId = "school-1",
        roles = setOf(Role.PARENT),
        profileType = ProfileType.GUARDIAN,
        profileId = guardianId,
        mustChangePassword = false
    )
}
