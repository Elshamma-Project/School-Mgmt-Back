package com.kobe.smels.service.grading

import com.kobe.smels.model.grading.EvaluationSession
import com.kobe.smels.model.grading.GradeAuditEntry
import com.kobe.smels.model.grading.GradeReportSheet
import com.kobe.smels.model.grading.ReportSheetStatus
import com.kobe.smels.model.grading.SessionStatus
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.OutboxEvent
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.grading.EvaluationSessionRepository
import com.kobe.smels.repository.grading.GradeAuditEntryRepository
import com.kobe.smels.repository.grading.GradeReportSheetRepository
import com.kobe.smels.repository.grading.GradingSchemeRepository
import com.kobe.smels.repository.grading.SequenceMarkRepository
import com.kobe.smels.shared.outbox.OutboxWriter
import com.kobe.smels.shared.security.CurrentUser
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** BR-79 : clôture de séquence → visa enseignant auto sur les fiches DRAFT/REJECTED du trimestre. */
class ReportSheetAutoSubmitOnCloseTest {
    private val gradeReportSheetRepository: GradeReportSheetRepository = mock()
    private val evaluationSessionRepository: EvaluationSessionRepository = mock()
    private val sequenceMarkRepository: SequenceMarkRepository = mock()
    private val gradingSchemeRepository: GradingSchemeRepository = mock()
    private val gradeAuditEntryRepository: GradeAuditEntryRepository = mock()
    private val outboxWriter: OutboxWriter = mock()

    private val service = GradeReportSheetService(
        gradeReportSheetRepository,
        evaluationSessionRepository,
        sequenceMarkRepository,
        gradingSchemeRepository,
        gradeAuditEntryRepository,
        mock(),
        mock(),
        mock(),
        mock(),
        mock(),
        mock(),
        outboxWriter
    )

    @Test
    fun `BR-79 auto-submits draft and rejected sheets of the term`() {
        val closed = closedSession()
        val draft = sheet("rs-draft", ReportSheetStatus.DRAFT)
        val rejected = sheet("rs-rejected", ReportSheetStatus.REJECTED)
        val already = sheet("rs-sg", ReportSheetStatus.VISA_SG)

        whenever(
            gradeReportSheetRepository.findBySchoolIdAndAcademicYearIdAndTermNumber("sch-1", "year-1", 1)
        ).thenReturn(Flux.just(draft, rejected, already))
        whenever(gradeReportSheetRepository.save(any())).thenAnswer { Mono.just(it.arguments[0] as GradeReportSheet) }
        whenever(gradeAuditEntryRepository.save(any())).thenAnswer { Mono.just(it.arguments[0] as GradeAuditEntry) }
        whenever(outboxWriter.append(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(
                Mono.just(
                    OutboxEvent(
                        schoolId = "sch-1",
                        module = "grading",
                        aggregateType = "GradeReportSheet",
                        aggregateId = "x",
                        eventType = "grading.report-sheet.submitted",
                        payload = emptyMap()
                    )
                )
            )

        StepVerifier.create(service.autoSubmitTeacherVisasOnSessionClosed(adminUser(), closed))
            .verifyComplete()

        assertEquals(ReportSheetStatus.SUBMITTED, draft.status)
        assertNotNull(draft.submittedAt)
        assertEquals("acc-admin", draft.submittedBy)
        assertEquals(ReportSheetStatus.SUBMITTED, rejected.status)
        assertEquals(ReportSheetStatus.VISA_SG, already.status)

        val saved = argumentCaptor<GradeReportSheet>()
        verify(gradeReportSheetRepository, times(2)).save(saved.capture())
        assertEquals(setOf("rs-draft", "rs-rejected"), saved.allValues.map { it.id }.toSet())
        verify(gradeReportSheetRepository, never()).save(already)
    }

    @Test
    fun `BR-79 does nothing when no pending sheets`() {
        val closed = closedSession()
        whenever(
            gradeReportSheetRepository.findBySchoolIdAndAcademicYearIdAndTermNumber("sch-1", "year-1", 1)
        ).thenReturn(Flux.just(sheet("rs-sg", ReportSheetStatus.VISA_SG)))

        StepVerifier.create(service.autoSubmitTeacherVisasOnSessionClosed(adminUser(), closed))
            .verifyComplete()

        verify(gradeReportSheetRepository, never()).save(any())
    }

    private fun closedSession() =
        EvaluationSession(
            id = "sess-1",
            schoolId = "sch-1",
            academicYearId = "year-1",
            number = 1,
            termNumber = 1,
            shortLabel = "DEV1",
            name = LocalizedText(fr = "Séquence 1", en = "Sequence 1"),
            status = SessionStatus.CLOSED
        )

    private fun sheet(id: String, status: ReportSheetStatus) =
        GradeReportSheet(
            id = id,
            schoolId = "sch-1",
            academicYearId = "year-1",
            classId = "class-1",
            classSubjectId = "cs-1",
            termNumber = 1,
            teacherId = "teacher-1",
            status = status
        )

    private fun adminUser() =
        CurrentUser(
            accountId = "acc-admin",
            sessionId = "sess-admin",
            username = "admin.demo",
            schoolId = "sch-1",
            roles = setOf(Role.ADMINISTRATION),
            profileType = ProfileType.STAFF,
            profileId = "staff-admin",
            mustChangePassword = false
        )
}
