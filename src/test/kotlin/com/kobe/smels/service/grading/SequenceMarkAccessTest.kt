package com.kobe.smels.service.grading

import com.kobe.smels.dto.academic.ClassSubjectSummaryDto
import com.kobe.smels.dto.student.GuardianLinkDto
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.model.grading.GradeStatus
import com.kobe.smels.model.grading.MarkSource
import com.kobe.smels.model.grading.SequenceMark
import com.kobe.smels.model.student.GuardianRelationship
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.grading.AssessmentRepository
import com.kobe.smels.repository.grading.GradeAuditEntryRepository
import com.kobe.smels.repository.grading.GradeRepository
import com.kobe.smels.repository.grading.GradeSheetRepository
import com.kobe.smels.repository.grading.SequenceMarkRepository
import com.kobe.smels.service.academic.api.ClassSubjectApi
import com.kobe.smels.service.student.api.StudentApi
import com.kobe.smels.shared.security.CurrentUser
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.math.BigDecimal
import kotlin.test.Test

class SequenceMarkAccessTest {
    private val sequenceMarkRepository: SequenceMarkRepository = mock()
    private val gradeRepository: GradeRepository = mock()
    private val assessmentRepository: AssessmentRepository = mock()
    private val gradeSheetRepository: GradeSheetRepository = mock()
    private val gradeAuditEntryRepository: GradeAuditEntryRepository = mock()
    private val studentApi: StudentApi = mock()
    private val classSubjectApi: ClassSubjectApi = mock()

    private val service = SequenceMarkService(
        sequenceMarkRepository,
        gradeRepository,
        assessmentRepository,
        gradeSheetRepository,
        gradeAuditEntryRepository,
        studentApi,
        classSubjectApi
    )

    @Test
    fun `parent with canViewGrades false cannot list sequence marks`() {
        whenever(studentApi.findGuardianLink("school-1", "stu-1", "guard-1"))
            .thenReturn(
                Mono.just(
                    GuardianLinkDto(
                        guardianId = "guard-1",
                        relationship = GuardianRelationship.MOTHER,
                        canViewGrades = false,
                        canDownloadReportCards = true
                    )
                )
            )

        StepVerifier.create(
            service.listForStudentYear(parentUser(), "stu-1", "year-1")
        )
            .expectError(ForbiddenException::class.java)
            .verify()
    }

    @Test
    fun `teacher only sees marks for own ClassSubject`() {
        val own = mark(id = "m-own", classSubjectId = "cs-own")
        val other = mark(id = "m-other", classSubjectId = "cs-other")
        whenever(
            sequenceMarkRepository.findBySchoolIdAndStudentIdAndAcademicYearId(
                "school-1", "stu-1", "year-1"
            )
        ).thenReturn(Flux.just(own, other))
        whenever(classSubjectApi.findByTeacher("school-1", "staff-t", "year-1"))
            .thenReturn(Flux.just(classSubject("cs-own")))

        StepVerifier.create(
            service.listForStudentYear(teacherUser(), "stu-1", "year-1")
        )
            .expectNextMatches { it.id == "m-own" }
            .verifyComplete()
    }

    private fun mark(id: String, classSubjectId: String) = SequenceMark(
        id = id,
        schoolId = "school-1",
        academicYearId = "year-1",
        classId = "class-1",
        classSubjectId = classSubjectId,
        sessionId = "sess-1",
        sessionNumber = 1,
        termNumber = 1,
        sheetId = "sheet-1",
        studentId = "stu-1",
        enrollmentId = "enr-1",
        value = BigDecimal("12.00"),
        status = GradeStatus.SCORED,
        source = MarkSource.COMPUTED
    )

    private fun classSubject(id: String) = ClassSubjectSummaryDto(
        id = id,
        schoolId = "school-1",
        academicYearId = "year-1",
        classId = "class-1",
        subjectId = "subj-1",
        groupId = "grp-1",
        coefficient = BigDecimal("2"),
        teacherId = "staff-t",
        weeklyHours = 3,
        maxScore = BigDecimal("20"),
        countsInAverage = true,
        active = true
    )

    private fun parentUser() = CurrentUser(
        accountId = "acc-p",
        sessionId = "s1",
        username = "parent",
        schoolId = "school-1",
        roles = setOf(Role.PARENT),
        profileType = ProfileType.GUARDIAN,
        profileId = "guard-1",
        mustChangePassword = false
    )

    private fun teacherUser() = CurrentUser(
        accountId = "acc-t",
        sessionId = "s2",
        username = "teacher",
        schoolId = "school-1",
        roles = setOf(Role.TEACHER),
        profileType = ProfileType.STAFF,
        profileId = "staff-t",
        mustChangePassword = false
    )
}
