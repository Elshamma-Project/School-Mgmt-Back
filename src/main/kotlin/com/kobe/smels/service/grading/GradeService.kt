package com.kobe.smels.service.grading

import com.kobe.smels.dto.grading.BatchUpsertGradesRequest
import com.kobe.smels.dto.grading.GradeResponse
import com.kobe.smels.dto.grading.UpsertGradeRequest
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.grading.Assessment
import com.kobe.smels.model.grading.AuditAction
import com.kobe.smels.model.grading.AuditTarget
import com.kobe.smels.model.grading.EvaluationSession
import com.kobe.smels.model.grading.Grade
import com.kobe.smels.model.grading.GradeAuditEntry
import com.kobe.smels.model.grading.GradeSheet
import com.kobe.smels.model.grading.GradeStatus
import com.kobe.smels.model.grading.SessionStatus
import com.kobe.smels.repository.grading.AssessmentRepository
import com.kobe.smels.repository.grading.EvaluationSessionRepository
import com.kobe.smels.repository.grading.GradeAuditEntryRepository
import com.kobe.smels.repository.grading.GradeRepository
import com.kobe.smels.repository.grading.GradeSheetRepository
import com.kobe.smels.model.student.EnrollmentStatus
import com.kobe.smels.service.academic.api.AcademicYearApi
import com.kobe.smels.service.student.api.EnrollmentApi
import com.kobe.smels.shared.outbox.OutboxWriter
import com.kobe.smels.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant

/**
 * UNIQUE chemin d'écriture des notes (AGENTS.md, BR-11..BR-18, BR-21..22).
 */
@Service
class GradeService(
    private val gradeRepository: GradeRepository,
    private val assessmentRepository: AssessmentRepository,
    private val gradeSheetRepository: GradeSheetRepository,
    private val evaluationSessionRepository: EvaluationSessionRepository,
    private val gradeAuditEntryRepository: GradeAuditEntryRepository,
    private val enrollmentApi: EnrollmentApi,
    private val academicYearApi: AcademicYearApi,
    private val sequenceMarkService: SequenceMarkService,
    private val outboxWriter: OutboxWriter
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun upsert(currentUser: CurrentUser, request: UpsertGradeRequest): Mono<GradeResponse> {
        val schoolId = GradingAccess.resolveSchoolId(currentUser, request.schoolId)
        log.info("grade.upsert start by={} schoolId={} status={}", currentUser.username, schoolId, request.status)

        return loadContext(schoolId, request.assessmentId)
            .flatMap { ctx ->
                GradingAccess.assertCanManageSheet(currentUser, ctx.sheet.teacherId)
                academicYearApi.assertWritable(schoolId, ctx.session.academicYearId)
                    .then(
                        Mono.defer {
                            assertSessionWritable(currentUser, ctx.session, request.reason)
                            validatePayload(request.status, request.value, ctx.assessment.maxScore)
                            enrollmentApi.findByStudentAndYear(schoolId, request.studentId, ctx.sheet.academicYearId)
                                .flatMap { enrollment ->
                                    if (enrollment.classId != ctx.sheet.classId ||
                                        enrollment.status != EnrollmentStatus.ENROLLED
                                    ) {
                                        Mono.error(
                                            BusinessRuleException(
                                                "NOT_ENROLLED",
                                                "Student is not ENROLLED in the sheet class for this year"
                                            )
                                        )
                                    } else {
                                        writeGrade(currentUser, ctx, request.studentId, enrollment.id, request)
                                    }
                                }
                        }
                    )
            }
            .map { GradingMapper.toGradeResponse(it) }
            .doOnError { error ->
                log.warn("grade.upsert failed by={} schoolId={} reason={}", currentUser.username, schoolId, error.message)
            }
    }

    fun batchUpsert(currentUser: CurrentUser, request: BatchUpsertGradesRequest): Flux<GradeResponse> {
        return Flux.fromIterable(request.grades)
            .concatMap { item ->
                upsert(
                    currentUser,
                    UpsertGradeRequest(
                        assessmentId = request.assessmentId,
                        studentId = item.studentId,
                        status = item.status,
                        value = item.value,
                        comment = item.comment,
                        reason = request.reason,
                        schoolId = request.schoolId
                    )
                )
            }
    }

    fun listByAssessment(
        currentUser: CurrentUser,
        assessmentId: String,
        schoolIdOverride: String? = null
    ): Flux<GradeResponse> {
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return loadContext(schoolId, assessmentId)
            .flatMapMany { ctx ->
                GradingAccess.assertCanManageSheet(currentUser, ctx.sheet.teacherId)
                gradeRepository.findBySchoolIdAndAssessmentId(schoolId, assessmentId)
                    .map { GradingMapper.toGradeResponse(it) }
            }
    }

    fun delete(
        currentUser: CurrentUser,
        assessmentId: String,
        studentId: String,
        reason: String?,
        schoolIdOverride: String? = null
    ): Mono<Void> {
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return loadContext(schoolId, assessmentId)
            .flatMap { ctx ->
                GradingAccess.assertCanManageSheet(currentUser, ctx.sheet.teacherId)
                academicYearApi.assertWritable(schoolId, ctx.session.academicYearId)
                    .then(
                        Mono.defer {
                            assertSessionWritable(currentUser, ctx.session, reason)
                            gradeRepository.findBySchoolIdAndAssessmentIdAndStudentId(schoolId, assessmentId, studentId)
                                .switchIfEmpty(Mono.error(NotFoundException("GRADE_NOT_FOUND", "Grade not found")))
                                .flatMap { grade ->
                                    val previousValue = grade.value
                                    val previousStatus = grade.status
                                    gradeRepository.delete(grade)
                                        .then(
                                            gradeAuditEntryRepository.save(
                                                GradeAuditEntry(
                                                    schoolId = schoolId,
                                                    target = AuditTarget.GRADE,
                                                    gradeId = grade.id,
                                                    assessmentId = assessmentId,
                                                    classSubjectId = ctx.sheet.classSubjectId,
                                                    sessionId = ctx.session.id!!,
                                                    studentId = studentId,
                                                    action = AuditAction.DELETED,
                                                    previousValue = previousValue,
                                                    newValue = null,
                                                    previousStatus = previousStatus,
                                                    newStatus = null,
                                                    changedBy = currentUser.accountId,
                                                    changedByRole = currentUser.roles.firstOrNull(),
                                                    changedAt = Instant.now(),
                                                    reason = reason,
                                                    afterSessionClosed = ctx.session.status == SessionStatus.CLOSED
                                                )
                                            )
                                        )
                                        .then(
                                            sequenceMarkService.recalculateForStudent(
                                                currentUser,
                                                ctx.sheet,
                                                ctx.session,
                                                studentId,
                                                grade.enrollmentId
                                            )
                                        )
                                        .then(emitMarkChanged(ctx, studentId))
                                }
                        }
                    )
            }
    }

    private fun writeGrade(
        currentUser: CurrentUser,
        ctx: GradeContext,
        studentId: String,
        enrollmentId: String,
        request: UpsertGradeRequest
    ): Mono<Grade> {
        val afterClosed = ctx.session.status == SessionStatus.CLOSED
        return gradeRepository.findBySchoolIdAndAssessmentIdAndStudentId(
            ctx.sheet.schoolId,
            request.assessmentId,
            studentId
        )
            .flatMap { existing ->
                val previousValue = existing.value
                val previousStatus = existing.status
                existing.status = request.status
                existing.value = valueForStatus(request.status, request.value)
                existing.comment = request.comment?.trim()?.ifBlank { null }
                gradeRepository.save(existing).flatMap { saved ->
                    auditGrade(
                        currentUser, ctx, saved, AuditAction.UPDATED,
                        previousValue, previousStatus, request.reason, afterClosed
                    ).then(
                        sequenceMarkService.recalculateForStudent(
                            currentUser, ctx.sheet, ctx.session, studentId, enrollmentId
                        )
                    ).then(emitMarkChanged(ctx, studentId)).thenReturn(saved)
                }
            }
            .switchIfEmpty(
                Mono.defer {
                    val created = Grade(
                        schoolId = ctx.sheet.schoolId,
                        academicYearId = ctx.sheet.academicYearId,
                        classId = ctx.sheet.classId,
                        classSubjectId = ctx.sheet.classSubjectId,
                        sessionId = ctx.sheet.sessionId,
                        assessmentId = request.assessmentId,
                        studentId = studentId,
                        enrollmentId = enrollmentId,
                        status = request.status,
                        value = valueForStatus(request.status, request.value),
                        comment = request.comment?.trim()?.ifBlank { null }
                    )
                    gradeRepository.save(created).flatMap { saved ->
                        auditGrade(
                            currentUser, ctx, saved, AuditAction.CREATED,
                            null, null, request.reason, afterClosed
                        ).then(
                            sequenceMarkService.recalculateForStudent(
                                currentUser, ctx.sheet, ctx.session, studentId, enrollmentId
                            )
                        ).then(emitMarkChanged(ctx, studentId)).thenReturn(saved)
                    }
                }
            )
    }

    private fun auditGrade(
        currentUser: CurrentUser,
        ctx: GradeContext,
        grade: Grade,
        action: AuditAction,
        previousValue: BigDecimal?,
        previousStatus: GradeStatus?,
        reason: String?,
        afterClosed: Boolean
    ): Mono<GradeAuditEntry> =
        gradeAuditEntryRepository.save(
            GradeAuditEntry(
                schoolId = ctx.sheet.schoolId,
                target = AuditTarget.GRADE,
                gradeId = grade.id,
                assessmentId = grade.assessmentId,
                classSubjectId = ctx.sheet.classSubjectId,
                sessionId = ctx.session.id!!,
                studentId = grade.studentId,
                action = action,
                previousValue = previousValue,
                newValue = grade.value,
                previousStatus = previousStatus,
                newStatus = grade.status,
                changedBy = currentUser.accountId,
                changedByRole = currentUser.roles.firstOrNull(),
                changedAt = Instant.now(),
                reason = reason,
                afterSessionClosed = afterClosed
            )
        )

    private fun emitMarkChanged(ctx: GradeContext, studentId: String): Mono<Void> =
        outboxWriter.append(
            schoolId = ctx.sheet.schoolId,
            module = "grading",
            aggregateType = "Grade",
            aggregateId = studentId,
            eventType = "grading.mark.changed",
            payload = mapOf(
                "classSubjectId" to ctx.sheet.classSubjectId,
                "sessionId" to ctx.session.id,
                // studentId dans l'événement métier (docs/08), jamais dans les logs (BR-43)
                "studentId" to studentId
            )
        ).then()

    private fun loadContext(schoolId: String, assessmentId: String): Mono<GradeContext> =
        assessmentRepository.findByIdAndSchoolId(assessmentId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("ASSESSMENT_NOT_FOUND", "Assessment not found")))
            .flatMap { assessment ->
                val sheetId = assessment.sheetId
                    ?: return@flatMap Mono.error<GradeContext>(
                        BusinessRuleException("SHEET_REQUIRED", "Assessment is not linked to a sheet")
                    )
                gradeSheetRepository.findByIdAndSchoolId(sheetId, schoolId)
                    .switchIfEmpty(Mono.error(NotFoundException("SHEET_NOT_FOUND", "Grade sheet not found")))
                    .flatMap { sheet ->
                        evaluationSessionRepository.findByIdAndSchoolId(assessment.sessionId, schoolId)
                            .switchIfEmpty(Mono.error(NotFoundException("SESSION_NOT_FOUND", "Session not found")))
                            .map { session -> GradeContext(assessment, sheet, session) }
                    }
            }

    /** BR-21 / BR-22 (après contrôle YEAR_LOCKED). */
    private fun assertSessionWritable(currentUser: CurrentUser, session: EvaluationSession, reason: String?) {
        when (session.status) {
            SessionStatus.PLANNED ->
                throw BusinessRuleException("SESSION_NOT_OPEN", "Session is not open for entry")
            SessionStatus.OPEN -> Unit
            SessionStatus.CLOSED -> {
                if (!currentUser.hasAnyRole(
                        com.kobe.smels.model.user.Role.ADMINISTRATION,
                        com.kobe.smels.model.user.Role.DIRECTOR,
                        com.kobe.smels.model.user.Role.SUPER_ADMIN
                    )
                ) {
                    throw BusinessRuleException("SESSION_CLOSED", "Teachers cannot edit grades after session close")
                }
                if (reason.isNullOrBlank()) {
                    throw BusinessRuleException("REASON_REQUIRED", "Reason is required after session close")
                }
            }
        }
    }

    /** BR-13, BR-16 */
    private fun validatePayload(status: GradeStatus, value: BigDecimal?, maxScore: BigDecimal) {
        when (status) {
            GradeStatus.SCORED -> {
                if (value == null) {
                    throw BusinessRuleException("VALUE_REQUIRED", "SCORED grade requires a value")
                }
                try {
                    SequenceMarkCalculator.assertScoreBounds(value, maxScore)
                } catch (ex: IllegalArgumentException) {
                    throw BusinessRuleException("SCORE_OUT_OF_BOUNDS", ex.message ?: "Invalid score")
                }
                if (value.scale() > 2) {
                    throw BusinessRuleException("SCORE_PRECISION", "Score must have at most 2 decimal places")
                }
            }
            GradeStatus.PENDING, GradeStatus.ABSENT, GradeStatus.ABSENT_EXCUSED, GradeStatus.EXEMPTED -> {
                if (value != null) {
                    throw BusinessRuleException("VALUE_NOT_ALLOWED", "Non-SCORED status must not carry a numeric value")
                }
            }
        }
    }

    private fun valueForStatus(status: GradeStatus, value: BigDecimal?): BigDecimal? =
        if (status == GradeStatus.SCORED) value else null

    private data class GradeContext(
        val assessment: Assessment,
        val sheet: GradeSheet,
        val session: EvaluationSession
    )
}
