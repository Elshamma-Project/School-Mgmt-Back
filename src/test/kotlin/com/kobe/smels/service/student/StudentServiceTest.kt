package com.kobe.smels.service.student

import com.kobe.smels.dto.academic.AcademicYearSummaryDto
import com.kobe.smels.dto.academic.ClassSubjectSummaryDto
import com.kobe.smels.dto.student.CreateStudentRequest
import com.kobe.smels.dto.student.EnrollmentSummaryDto
import com.kobe.smels.exception.ConflictException
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.model.academic.AcademicYearStatus
import com.kobe.smels.model.shared.OutboxEvent
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse

class StudentServiceTest {
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
    fun `BR-40 rejects duplicate matricule`() {
        whenever(studentRepository.findBySchoolIdAndMatricule("school-1", "TEST-0001"))
            .thenReturn(
                Mono.just(
                    Student(
                        id = "existing",
                        schoolId = "school-1",
                        matricule = "TEST-0001",
                        lastName = "EXISTING",
                        firstName = "One",
                        sex = Sex.MALE
                    )
                )
            )

        val request = CreateStudentRequest(
            matricule = "TEST-0001",
            lastName = "NEW",
            firstName = "One",
            sex = Sex.FEMALE
        )

        StepVerifier.create(Mono.defer { service.create(adminUser(), request) })
            .expectError(ConflictException::class.java)
            .verify()
    }

    @Test
    fun `BR-42 deactivate sets active false`() {
        val student = Student(
            id = "stu-1",
            schoolId = "school-1",
            matricule = "TEST-0002",
            lastName = "ALPHA",
            firstName = "Sample",
            sex = Sex.FEMALE,
            active = true
        )
        whenever(studentRepository.findByIdAndSchoolId("stu-1", "school-1")).thenReturn(Mono.just(student))
        whenever(studentRepository.save(any<Student>())).thenAnswer { Mono.just(it.getArgument(0)) }
        whenever(outboxWriter.append(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Mono.just(sampleOutbox()))

        StepVerifier.create(service.deactivate(adminUser(), "stu-1"))
            .assertNext { assertFalse(it.active) }
            .verifyComplete()
        assertFalse(student.active)
    }

    @Test
    fun `student can read own profile`() {
        val student = Student(
            id = "stu-42",
            schoolId = "school-1",
            matricule = "TEST-0042",
            lastName = "SELF",
            firstName = "Reader",
            sex = Sex.MALE
        )
        whenever(studentRepository.findByIdAndSchoolId("stu-42", "school-1")).thenReturn(Mono.just(student))

        StepVerifier.create(service.getById(studentUser("stu-42"), "stu-42"))
            .assertNext { kotlin.test.assertEquals("stu-42", it.id) }
            .verifyComplete()
    }

    @Test
    fun `student cannot read another profile`() {
        val student = Student(
            id = "stu-99",
            schoolId = "school-1",
            matricule = "TEST-0099",
            lastName = "OTHER",
            firstName = "Reader",
            sex = Sex.MALE
        )
        whenever(studentRepository.findByIdAndSchoolId("stu-99", "school-1")).thenReturn(Mono.just(student))

        StepVerifier.create(service.getById(studentUser("stu-42"), "stu-99"))
            .expectError(ForbiddenException::class.java)
            .verify()
    }

    @Test
    fun `parent can read linked child only`() {
        val linked = Student(
            id = "stu-1",
            schoolId = "school-1",
            matricule = "TEST-0001",
            lastName = "CHILD",
            firstName = "One",
            sex = Sex.FEMALE,
            guardians = listOf(
                GuardianLink(
                    guardianId = "g-1",
                    relationship = GuardianRelationship.MOTHER,
                    primary = true
                )
            )
        )
        val other = Student(
            id = "stu-2",
            schoolId = "school-1",
            matricule = "TEST-0002",
            lastName = "CHILD",
            firstName = "Two",
            sex = Sex.MALE
        )
        whenever(studentRepository.findByIdAndSchoolId("stu-1", "school-1")).thenReturn(Mono.just(linked))
        whenever(studentRepository.findByIdAndSchoolId("stu-2", "school-1")).thenReturn(Mono.just(other))

        StepVerifier.create(service.getById(parentUser("g-1"), "stu-1"))
            .assertNext { kotlin.test.assertEquals("stu-1", it.id) }
            .verifyComplete()

        StepVerifier.create(service.getById(parentUser("g-1"), "stu-2"))
            .expectError(ForbiddenException::class.java)
            .verify()
    }

    @Test
    fun `teacher cannot read student outside own ClassSubject`() {
        val student = Student(
            id = "stu-out",
            schoolId = "school-1",
            matricule = "TEST-OUT",
            lastName = "OUTSIDE",
            firstName = "Class",
            sex = Sex.MALE
        )
        whenever(studentRepository.findByIdAndSchoolId("stu-out", "school-1")).thenReturn(Mono.just(student))
        whenever(academicYearApi.findCurrent("school-1")).thenReturn(
            Mono.just(
                AcademicYearSummaryDto(
                    id = "year-1",
                    schoolId = "school-1",
                    label = "2025-2026",
                    current = true,
                    status = AcademicYearStatus.ACTIVE
                )
            )
        )
        whenever(classSubjectApi.findByTeacher("school-1", "staff-t", "year-1"))
            .thenReturn(
                Flux.just(
                    ClassSubjectSummaryDto(
                        id = "cs-1",
                        schoolId = "school-1",
                        academicYearId = "year-1",
                        classId = "class-A",
                        subjectId = "subj-1",
                        groupId = "grp-1",
                        coefficient = BigDecimal("2"),
                        teacherId = "staff-t",
                        weeklyHours = 3,
                        maxScore = BigDecimal("20"),
                        countsInAverage = true,
                        active = true
                    )
                )
            )
        whenever(enrollmentApi.findEnrolledByClass("school-1", "class-A"))
            .thenReturn(
                Flux.just(
                    EnrollmentSummaryDto(
                        id = "enr-1",
                        schoolId = "school-1",
                        studentId = "stu-other",
                        academicYearId = "year-1",
                        classId = "class-A",
                        levelId = "lvl-1",
                        status = EnrollmentStatus.ENROLLED,
                        repeater = false
                    )
                )
            )

        StepVerifier.create(service.getById(teacherUser(), "stu-out"))
            .expectError(ForbiddenException::class.java)
            .verify()
    }

    private fun sampleOutbox() = OutboxEvent(
        schoolId = "school-1",
        module = "student",
        aggregateType = "Student",
        aggregateId = "stu-1",
        eventType = "student.changed",
        occurredAt = Instant.parse("2026-01-01T00:00:00Z")
    )

    private fun adminUser() = CurrentUser(
        accountId = "acc-admin",
        sessionId = "sess-1",
        username = "admin",
        schoolId = "school-1",
        roles = setOf(Role.ADMINISTRATION),
        profileType = ProfileType.STAFF,
        profileId = "staff-admin",
        mustChangePassword = false
    )

    private fun studentUser(profileId: String) = CurrentUser(
        accountId = "acc-student",
        sessionId = "sess-2",
        username = "student",
        schoolId = "school-1",
        roles = setOf(Role.STUDENT),
        profileType = ProfileType.STUDENT,
        profileId = profileId,
        mustChangePassword = false
    )

    private fun parentUser(guardianId: String) = CurrentUser(
        accountId = "acc-parent",
        sessionId = "sess-3",
        username = "parent",
        schoolId = "school-1",
        roles = setOf(Role.PARENT),
        profileType = ProfileType.GUARDIAN,
        profileId = guardianId,
        mustChangePassword = false
    )

    private fun teacherUser() = CurrentUser(
        accountId = "acc-teacher",
        sessionId = "sess-4",
        username = "teacher",
        schoolId = "school-1",
        roles = setOf(Role.TEACHER),
        profileType = ProfileType.STAFF,
        profileId = "staff-t",
        mustChangePassword = false
    )
}
