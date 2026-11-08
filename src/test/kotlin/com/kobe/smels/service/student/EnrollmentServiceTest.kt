package com.kobe.smels.service.student

import com.kobe.smels.dto.student.CreateEnrollmentRequest
import com.kobe.smels.exception.ConflictException
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.model.shared.OutboxEvent
import com.kobe.smels.model.shared.Sex
import com.kobe.smels.model.student.Enrollment
import com.kobe.smels.model.student.EnrollmentStatus
import com.kobe.smels.model.student.Student
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.dto.academic.AcademicYearSummaryDto
import com.kobe.smels.dto.academic.SchoolClassSummaryDto
import com.kobe.smels.model.academic.AcademicYearStatus
import com.kobe.smels.model.shared.Language
import com.kobe.smels.model.shared.Section
import com.kobe.smels.repository.student.EnrollmentRepository
import com.kobe.smels.repository.student.StudentRepository
import com.kobe.smels.service.academic.api.AcademicYearApi
import com.kobe.smels.service.academic.api.SchoolClassApi
import com.kobe.smels.shared.outbox.OutboxWriter
import com.kobe.smels.shared.security.CurrentUser
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class EnrollmentServiceTest {
    private val enrollmentRepository: EnrollmentRepository = mock()
    private val studentRepository: StudentRepository = mock()
    private val academicYearApi: AcademicYearApi = mock()
    private val schoolClassApi: SchoolClassApi = mock()
    private val outboxWriter: OutboxWriter = mock()
    private val service = EnrollmentService(
        enrollmentRepository,
        studentRepository,
        academicYearApi,
        schoolClassApi,
        outboxWriter
    )

    @Test
    fun `BR-41 rejects second enrollment for same academic year`() {
        stubActiveStudent()
        stubAcademicRefs()
        whenever(
            enrollmentRepository.findBySchoolIdAndStudentIdAndAcademicYearId("school-1", "stu-1", "year-1")
        ).thenReturn(
            Mono.just(
                Enrollment(
                    id = "e-existing",
                    schoolId = "school-1",
                    studentId = "stu-1",
                    academicYearId = "year-1",
                    classId = "class-a",
                    levelId = "level-1"
                )
            )
        )

        val request = CreateEnrollmentRequest(
            studentId = "stu-1",
            academicYearId = "year-1",
            classId = "class-b",
            levelId = "level-1"
        )

        StepVerifier.create(Mono.defer { service.create(adminUser(), request) })
            .expectError(ConflictException::class.java)
            .verify()
    }

    @Test
    fun `admin can create enrollment and emits student enrolled event`() {
        stubActiveStudent()
        stubAcademicRefs()
        whenever(
            enrollmentRepository.findBySchoolIdAndStudentIdAndAcademicYearId("school-1", "stu-1", "year-1")
        ).thenReturn(Mono.empty())
        whenever(enrollmentRepository.save(any<Enrollment>())).thenAnswer { invocation ->
            val enrollment = invocation.getArgument<Enrollment>(0)
            enrollment.id = "e-1"
            Mono.just(enrollment)
        }
        whenever(outboxWriter.append(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(
                Mono.just(
                    OutboxEvent(
                        schoolId = "school-1",
                        module = "student",
                        aggregateType = "Enrollment",
                        aggregateId = "e-1",
                        eventType = "student.enrolled",
                        occurredAt = Instant.parse("2026-01-01T00:00:00Z")
                    )
                )
            )

        val request = CreateEnrollmentRequest(
            studentId = "stu-1",
            academicYearId = "year-1",
            classId = "class-1",
            levelId = "level-1",
            newStudent = true
        )

        StepVerifier.create(service.create(adminUser(), request))
            .assertNext { response ->
                assertEquals("e-1", response.id)
                assertEquals(EnrollmentStatus.ENROLLED, response.status)
                assertEquals("class-1", response.classId)
            }
            .verifyComplete()

        verify(outboxWriter).append(
            org.mockito.kotlin.eq("school-1"),
            org.mockito.kotlin.eq("student"),
            org.mockito.kotlin.eq("Enrollment"),
            org.mockito.kotlin.eq("e-1"),
            org.mockito.kotlin.eq("student.enrolled"),
            any(),
            any()
        )
    }

    @Test
    fun `teacher cannot manage enrollments`() {
        val request = CreateEnrollmentRequest(
            studentId = "stu-1",
            academicYearId = "year-1",
            classId = "class-1",
            levelId = "level-1"
        )
        StepVerifier.create(Mono.defer { service.create(teacherUser(), request) })
            .expectError(ForbiddenException::class.java)
            .verify()
    }

    @Test
    fun `isEnrolled returns true only for matching enrolled class`() {
        whenever(
            enrollmentRepository.findBySchoolIdAndStudentIdAndAcademicYearId("school-1", "stu-1", "year-1")
        ).thenReturn(
            Mono.just(
                Enrollment(
                    id = "e-1",
                    schoolId = "school-1",
                    studentId = "stu-1",
                    academicYearId = "year-1",
                    classId = "class-1",
                    levelId = "level-1",
                    status = EnrollmentStatus.ENROLLED
                )
            )
        )

        StepVerifier.create(service.isEnrolled("school-1", "stu-1", "class-1", "year-1"))
            .assertNext { kotlin.test.assertTrue(it) }
            .verifyComplete()

        StepVerifier.create(service.isEnrolled("school-1", "stu-1", "class-other", "year-1"))
            .assertNext { kotlin.test.assertFalse(it) }
            .verifyComplete()
    }

    private fun stubActiveStudent() {
        whenever(studentRepository.findByIdAndSchoolId("stu-1", "school-1"))
            .thenReturn(
                Mono.just(
                    Student(
                        id = "stu-1",
                        schoolId = "school-1",
                        matricule = "TEST-0001",
                        lastName = "ALPHA",
                        firstName = "Sample",
                        sex = Sex.FEMALE,
                        active = true
                    )
                )
            )
    }

    private fun stubAcademicRefs() {
        whenever(academicYearApi.getSummary("school-1", "year-1"))
            .thenReturn(
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
        whenever(schoolClassApi.getSummary("school-1", "class-1"))
            .thenReturn(
                Mono.just(
                    SchoolClassSummaryDto(
                        id = "class-1",
                        schoolId = "school-1",
                        academicYearId = "year-1",
                        levelId = "level-1",
                        levelCode = "6EME",
                        name = "6eme A",
                        section = Section.FRANCOPHONE,
                        defaultLanguage = Language.FR,
                        active = true
                    )
                )
            )
        whenever(schoolClassApi.getSummary("school-1", "class-b"))
            .thenReturn(
                Mono.just(
                    SchoolClassSummaryDto(
                        id = "class-b",
                        schoolId = "school-1",
                        academicYearId = "year-1",
                        levelId = "level-1",
                        levelCode = "6EME",
                        name = "6eme B",
                        section = Section.FRANCOPHONE,
                        defaultLanguage = Language.FR,
                        active = true
                    )
                )
            )
    }

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

    private fun teacherUser() = CurrentUser(
        accountId = "acc-teacher",
        sessionId = "sess-2",
        username = "teacher",
        schoolId = "school-1",
        roles = setOf(Role.TEACHER),
        profileType = ProfileType.STAFF,
        profileId = "staff-t",
        mustChangePassword = false
    )
}
