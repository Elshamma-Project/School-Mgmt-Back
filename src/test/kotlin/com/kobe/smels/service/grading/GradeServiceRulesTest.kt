package com.kobe.smels.service.grading

import com.kobe.smels.dto.grading.UpsertGradeRequest
import com.kobe.smels.dto.student.EnrollmentSummaryDto
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.model.grading.Assessment
import com.kobe.smels.model.grading.AssessmentType
import com.kobe.smels.model.grading.EvaluationSession
import com.kobe.smels.model.grading.Grade
import com.kobe.smels.model.grading.GradeAuditEntry
import com.kobe.smels.model.grading.GradeSheet
import com.kobe.smels.model.grading.GradeStatus
import com.kobe.smels.model.grading.SequenceMark
import com.kobe.smels.model.grading.SessionStatus
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.OutboxEvent
import com.kobe.smels.model.student.EnrollmentStatus
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.grading.AssessmentRepository
import com.kobe.smels.repository.grading.EvaluationSessionRepository
import com.kobe.smels.repository.grading.GradeAuditEntryRepository
import com.kobe.smels.repository.grading.GradeRepository
import com.kobe.smels.repository.grading.GradeSheetRepository
import com.kobe.smels.repository.grading.SequenceMarkRepository
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

class GradeServiceRulesTest {
    private val gradeRepository: GradeRepository = mock()
    private val assessmentRepository: AssessmentRepository = mock()
    private val gradeSheetRepository: GradeSheetRepository = mock()
    private val evaluationSessionRepository: EvaluationSessionRepository = mock()
    private val gradeAuditEntryRepository: GradeAuditEntryRepository = mock()
    private val sequenceMarkRepository: SequenceMarkRepository = mock()
    private val enrollmentApi: EnrollmentApi = mock()
    private val outboxWriter: OutboxWriter = mock()

    private val sequenceMarkService = SequenceMarkService(
        sequenceMarkRepository,
        gradeRepository,
        assessmentRepository,
        gradeSheetRepository,
        gradeAuditEntryRepository,
        mock(),
        mock()
    )

    private val academicYearApi: com.kobe.smels.service.academic.api.AcademicYearApi = mock()
    private val gradeService = GradeService(
        gradeRepository,
        assessmentRepository,
        gradeSheetRepository,
        evaluationSessionRepository,
        gradeAuditEntryRepository,
        enrollmentApi,
        academicYearApi,
        sequenceMarkService,
        outboxWriter
    )

    @Test
    fun `BR-22 teacher cannot edit when session closed`() {
        stubContext(SessionStatus.CLOSED)
        val request = UpsertGradeRequest(
            assessmentId = "a1",
            studentId = "stu-1",
            status = GradeStatus.SCORED,
            value = BigDecimal("12.00")
        )
        StepVerifier.create(Mono.defer { gradeService.upsert(teacherUser(), request) })
            .expectError(BusinessRuleException::class.java)
            .verify()
    }

    @Test
    fun `BR-22 admin correction after close requires reason`() {
        stubContext(SessionStatus.CLOSED)
        stubEnrollment()
        val request = UpsertGradeRequest(
            assessmentId = "a1",
            studentId = "stu-1",
            status = GradeStatus.SCORED,
            value = BigDecimal("12.00"),
            reason = null
        )
        StepVerifier.create(Mono.defer { gradeService.upsert(adminUser(), request) })
            .expectError(BusinessRuleException::class.java)
            .verify()
    }

    @Test
    fun `BR-13 rejects score above maxScore`() {
        stubContext(SessionStatus.OPEN)
        stubEnrollment()
        val request = UpsertGradeRequest(
            assessmentId = "a1",
            studentId = "stu-1",
            status = GradeStatus.SCORED,
            value = BigDecimal("21.00")
        )
        StepVerifier.create(Mono.defer { gradeService.upsert(teacherUser(), request) })
            .expectError(BusinessRuleException::class.java)
            .verify()
    }

    @Test
    fun `BR-16 pending must not carry numeric value`() {
        stubContext(SessionStatus.OPEN)
        stubEnrollment()
        val request = UpsertGradeRequest(
            assessmentId = "a1",
            studentId = "stu-1",
            status = GradeStatus.PENDING,
            value = BigDecimal.ZERO
        )
        StepVerifier.create(Mono.defer { gradeService.upsert(teacherUser(), request) })
            .expectError(BusinessRuleException::class.java)
            .verify()
    }

    @Test
    fun `open session teacher can upsert scored grade`() {
        stubContext(SessionStatus.OPEN)
        stubEnrollment()
        whenever(gradeRepository.findBySchoolIdAndAssessmentIdAndStudentId("school-1", "a1", "stu-1"))
            .thenReturn(Mono.empty())
        whenever(gradeRepository.save(any<Grade>())).thenAnswer {
            val g = it.getArgument<Grade>(0)
            g.id = "g1"
            Mono.just(g)
        }
        whenever(assessmentRepository.findBySchoolIdAndSheetId("school-1", "sheet-1"))
            .thenReturn(Flux.just(sampleAssessment()))
        whenever(
            gradeRepository.findBySchoolIdAndClassSubjectIdAndSessionIdAndStudentId(
                "school-1", "cs-1", "sess-1", "stu-1"
            )
        ).thenAnswer {
            Mono.fromCallable {
                // after save, recalculation loads grades — return the scored one
                listOf(
                    Grade(
                        id = "g1",
                        schoolId = "school-1",
                        academicYearId = "year-1",
                        classId = "class-1",
                        classSubjectId = "cs-1",
                        sessionId = "sess-1",
                        assessmentId = "a1",
                        studentId = "stu-1",
                        enrollmentId = "enr-1",
                        status = GradeStatus.SCORED,
                        value = BigDecimal("12.50")
                    )
                )
            }.flatMapMany { Flux.fromIterable(it) }
        }
        whenever(sequenceMarkRepository.findBySchoolIdAndSheetIdAndStudentId("school-1", "sheet-1", "stu-1"))
            .thenReturn(Mono.empty())
        whenever(sequenceMarkRepository.save(any<SequenceMark>())).thenAnswer {
            val m = it.getArgument<SequenceMark>(0)
            m.id = "sm1"
            Mono.just(m)
        }
        whenever(gradeAuditEntryRepository.save(any<GradeAuditEntry>())).thenAnswer {
            Mono.just(it.getArgument(0))
        }
        whenever(gradeSheetRepository.save(any<GradeSheet>())).thenAnswer { Mono.just(it.getArgument(0)) }
        whenever(outboxWriter.append(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(
                Mono.just(
                    OutboxEvent(
                        schoolId = "school-1",
                        module = "grading",
                        aggregateType = "Grade",
                        aggregateId = "stu-1",
                        eventType = "grading.mark.changed",
                        occurredAt = Instant.parse("2026-01-01T00:00:00Z")
                    )
                )
            )

        val request = UpsertGradeRequest(
            assessmentId = "a1",
            studentId = "stu-1",
            status = GradeStatus.SCORED,
            value = BigDecimal("12.50")
        )
        StepVerifier.create(gradeService.upsert(teacherUser(), request))
            .assertNext { response ->
                kotlin.test.assertEquals(GradeStatus.SCORED, response.status)
                kotlin.test.assertEquals(0, response.value!!.compareTo(BigDecimal("12.50")))
            }
            .verifyComplete()
    }

    private fun stubContext(status: SessionStatus) {
        whenever(assessmentRepository.findByIdAndSchoolId("a1", "school-1"))
            .thenReturn(Mono.just(sampleAssessment()))
        whenever(gradeSheetRepository.findByIdAndSchoolId("sheet-1", "school-1"))
            .thenReturn(Mono.just(sampleSheet()))
        whenever(evaluationSessionRepository.findByIdAndSchoolId("sess-1", "school-1"))
            .thenReturn(
                Mono.just(
                    EvaluationSession(
                        id = "sess-1",
                        schoolId = "school-1",
                        academicYearId = "year-1",
                        number = 1,
                        termNumber = 1,
                        shortLabel = "DEV1",
                        name = LocalizedText(fr = "S1", en = "S1"),
                        status = status
                    )
                )
            )
        whenever(academicYearApi.assertWritable("school-1", "year-1"))
            .thenReturn(
                Mono.just(
                    com.kobe.smels.dto.academic.AcademicYearSummaryDto(
                        id = "year-1",
                        schoolId = "school-1",
                        label = "2025-2026",
                        current = true,
                        status = com.kobe.smels.model.academic.AcademicYearStatus.ACTIVE,
                        locked = false
                    )
                )
            )
    }

    private fun stubEnrollment() {
        whenever(enrollmentApi.findByStudentAndYear("school-1", "stu-1", "year-1"))
            .thenReturn(
                Mono.just(
                    EnrollmentSummaryDto(
                        id = "enr-1",
                        schoolId = "school-1",
                        studentId = "stu-1",
                        academicYearId = "year-1",
                        classId = "class-1",
                        levelId = "level-1",
                        status = EnrollmentStatus.ENROLLED,
                        repeater = false
                    )
                )
            )
    }

    private fun sampleAssessment() = Assessment(
        id = "a1",
        schoolId = "school-1",
        academicYearId = "year-1",
        classId = "class-1",
        classSubjectId = "cs-1",
        sessionId = "sess-1",
        sheetId = "sheet-1",
        sessionNumber = 1,
        title = "Note",
        type = AssessmentType.SEQUENCE_MARK,
        maxScore = BigDecimal("20")
    )

    private fun sampleSheet() = GradeSheet(
        id = "sheet-1",
        schoolId = "school-1",
        academicYearId = "year-1",
        classId = "class-1",
        classSubjectId = "cs-1",
        sessionId = "sess-1",
        sessionNumber = 1,
        teacherId = "staff-t"
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

    private fun adminUser() = CurrentUser(
        accountId = "acc-a",
        sessionId = "s2",
        username = "admin",
        schoolId = "school-1",
        roles = setOf(Role.ADMINISTRATION),
        profileType = ProfileType.STAFF,
        profileId = "staff-a",
        mustChangePassword = false
    )
}
