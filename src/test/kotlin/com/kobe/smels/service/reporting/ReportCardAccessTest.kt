package com.kobe.smels.service.reporting

import com.kobe.smels.dto.student.GuardianLinkDto
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.reporting.ClassSnapshot
import com.kobe.smels.model.reporting.OverallResult
import com.kobe.smels.model.reporting.ReportCard
import com.kobe.smels.model.reporting.ReportCardStatus
import com.kobe.smels.model.reporting.ReportPeriod
import com.kobe.smels.model.reporting.ReportPeriodType
import com.kobe.smels.model.reporting.StudentSnapshot
import com.kobe.smels.model.shared.Language
import com.kobe.smels.model.shared.Section
import com.kobe.smels.model.student.GuardianRelationship
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.reporting.ReportCardRepository
import com.kobe.smels.repository.reporting.StudentPeriodReviewRepository
import com.kobe.smels.service.academic.api.AcademicYearApi
import com.kobe.smels.service.academic.api.SchoolClassApi
import com.kobe.smels.service.grading.api.EvaluationSessionApi
import com.kobe.smels.service.staff.api.StaffApi
import com.kobe.smels.service.student.api.EnrollmentApi
import com.kobe.smels.service.student.api.StudentApi
import com.kobe.smels.shared.outbox.OutboxWriter
import com.kobe.smels.shared.security.CurrentUser
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test

class ReportCardAccessTest {
    private val reportCardRepository: ReportCardRepository = mock()
    private val studentPeriodReviewRepository: StudentPeriodReviewRepository = mock()
    private val reportTemplateService: ReportTemplateService = mock()
    private val gradeCalculationService: GradeCalculationService = mock()
    private val reportCardPdfService: ReportCardPdfService = mock()
    private val schoolClassApi: SchoolClassApi = mock()
    private val schoolBrandApi: com.kobe.smels.service.academic.api.SchoolBrandApi = mock()
    private val academicYearApi: AcademicYearApi = mock()
    private val enrollmentApi: EnrollmentApi = mock()
    private val studentApi: StudentApi = mock()
    private val staffApi: StaffApi = mock()
    private val evaluationSessionApi: EvaluationSessionApi = mock()
    private val outboxWriter: OutboxWriter = mock()

    private val service = ReportCardService(
        reportCardRepository,
        studentPeriodReviewRepository,
        reportTemplateService,
        gradeCalculationService,
        reportCardPdfService,
        schoolClassApi,
        schoolBrandApi,
        academicYearApi,
        enrollmentApi,
        studentApi,
        staffApi,
        evaluationSessionApi,
        outboxWriter
    )

    @Test
    fun `parent can read published annual card of linked child`() {
        val card = sampleCard(studentId = "stu-child", status = ReportCardStatus.PUBLISHED)
        whenever(reportCardRepository.findByIdAndSchoolId("rc-1", "school-1")).thenReturn(Mono.just(card))
        whenever(studentApi.findGuardianLink("school-1", "stu-child", "guard-1"))
            .thenReturn(Mono.just(link(canDownload = true)))

        StepVerifier.create(service.getById(parentUser(), "rc-1"))
            .expectNextMatches { it.id == "rc-1" }
            .verifyComplete()
    }

    @Test
    fun `parent cannot read card of another child`() {
        val card = sampleCard(studentId = "stu-other", status = ReportCardStatus.PUBLISHED)
        whenever(reportCardRepository.findByIdAndSchoolId("rc-1", "school-1")).thenReturn(Mono.just(card))
        whenever(studentApi.findGuardianLink("school-1", "stu-other", "guard-1"))
            .thenReturn(Mono.error(NotFoundException("GUARDIAN_LINK_NOT_FOUND", "Guardian link not found")))

        StepVerifier.create(service.getById(parentUser(), "rc-1"))
            .expectError(ForbiddenException::class.java)
            .verify()
    }

    @Test
    fun `parent with canDownloadReportCards false is refused`() {
        val card = sampleCard(studentId = "stu-child", status = ReportCardStatus.PUBLISHED)
        whenever(reportCardRepository.findByIdAndSchoolId("rc-1", "school-1")).thenReturn(Mono.just(card))
        whenever(studentApi.findGuardianLink("school-1", "stu-child", "guard-1"))
            .thenReturn(Mono.just(link(canDownload = false)))

        StepVerifier.create(service.getById(parentUser(), "rc-1"))
            .expectError(ForbiddenException::class.java)
            .verify()
    }

    @Test
    fun `student cannot read TERM or DRAFT report cards`() {
        val termCard = sampleCard(
            studentId = "stu-1",
            periodType = ReportPeriodType.TERM,
            periodNumber = 1,
            status = ReportCardStatus.PUBLISHED
        )
        whenever(reportCardRepository.findByIdAndSchoolId("rc-term", "school-1")).thenReturn(Mono.just(termCard))

        StepVerifier.create(service.getById(studentUser("stu-1"), "rc-term"))
            .expectError(ForbiddenException::class.java)
            .verify()

        val draft = sampleCard(studentId = "stu-1", status = ReportCardStatus.DRAFT)
        whenever(reportCardRepository.findByIdAndSchoolId("rc-draft", "school-1")).thenReturn(Mono.just(draft))

        StepVerifier.create(service.getById(studentUser("stu-1"), "rc-draft"))
            .expectError(ForbiddenException::class.java)
            .verify()
    }

    @Test
    fun `teacher cannot access report cards`() {
        val card = sampleCard(studentId = "stu-1", status = ReportCardStatus.PUBLISHED)
        whenever(reportCardRepository.findByIdAndSchoolId("rc-1", "school-1")).thenReturn(Mono.just(card))

        StepVerifier.create(service.getById(teacherUser(), "rc-1"))
            .expectError(ForbiddenException::class.java)
            .verify()
    }

    private fun sampleCard(
        studentId: String,
        periodType: ReportPeriodType = ReportPeriodType.ANNUAL,
        periodNumber: Int? = null,
        status: ReportCardStatus
    ) = ReportCard(
        id = if (periodType == ReportPeriodType.TERM) "rc-term" else if (status == ReportCardStatus.DRAFT) "rc-draft" else "rc-1",
        schoolId = "school-1",
        academicYearId = "year-1",
        enrollmentId = "enr-1",
        studentId = studentId,
        classId = "class-1",
        period = ReportPeriod(periodType, periodNumber),
        status = status,
        language = Language.FR,
        verificationCode = "1234567890123",
        student = StudentSnapshot(lastName = "SAMPLE", firstName = "One", matricule = "TEST-1"),
        classInfo = ClassSnapshot(
            className = "6ème A",
            levelCode = "6EME",
            section = Section.FRANCOPHONE,
            effectif = 30,
            rankedCount = 28
        ),
        overall = OverallResult(totalCoefficient = BigDecimal("40")),
        calculationVersion = "1",
        generatedAt = Instant.parse("2026-01-01T00:00:00Z")
    )

    private fun link(canDownload: Boolean) = GuardianLinkDto(
        guardianId = "guard-1",
        relationship = GuardianRelationship.FATHER,
        primary = true,
        canViewGrades = true,
        canDownloadReportCards = canDownload
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

    private fun studentUser(profileId: String) = CurrentUser(
        accountId = "acc-s",
        sessionId = "s2",
        username = "student",
        schoolId = "school-1",
        roles = setOf(Role.STUDENT),
        profileType = ProfileType.STUDENT,
        profileId = profileId,
        mustChangePassword = false
    )

    private fun teacherUser() = CurrentUser(
        accountId = "acc-t",
        sessionId = "s3",
        username = "teacher",
        schoolId = "school-1",
        roles = setOf(Role.TEACHER),
        profileType = ProfileType.STAFF,
        profileId = "staff-t",
        mustChangePassword = false
    )
}
