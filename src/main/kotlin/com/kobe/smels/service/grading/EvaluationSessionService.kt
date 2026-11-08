package com.kobe.smels.service.grading

import com.kobe.smels.dto.grading.EvaluationSessionResponse
import com.kobe.smels.dto.grading.EvaluationSessionSummaryDto
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.grading.EvaluationSession
import com.kobe.smels.model.grading.GradeSheet
import com.kobe.smels.model.grading.SessionStatus
import com.kobe.smels.model.grading.SheetStatus
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.repository.grading.EvaluationSessionRepository
import com.kobe.smels.repository.grading.GradeSheetRepository
import com.kobe.smels.service.academic.api.AcademicYearApi
import com.kobe.smels.service.academic.api.ClassSubjectApi
import com.kobe.smels.service.grading.api.EvaluationSessionApi
import com.kobe.smels.shared.outbox.OutboxWriter
import com.kobe.smels.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class EvaluationSessionService(
    private val evaluationSessionRepository: EvaluationSessionRepository,
    private val gradeSheetRepository: GradeSheetRepository,
    private val classSubjectApi: ClassSubjectApi,
    private val academicYearApi: AcademicYearApi,
    private val outboxWriter: OutboxWriter
) : EvaluationSessionApi {
    private val log = LoggerFactory.getLogger(javaClass)

    /** BR-20 : crée les 6 séquences d'une année si absentes. */
    fun ensureYearSessions(
        currentUser: CurrentUser,
        academicYearId: String,
        schoolIdOverride: String? = null
    ): Flux<EvaluationSessionResponse> {
        GradingAccess.assertAdmin(currentUser)
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return academicYearApi.getSummary(schoolId, academicYearId)
            .thenMany(Flux.range(1, 6))
            .concatMap { number ->
                evaluationSessionRepository.findBySchoolIdAndAcademicYearIdAndNumber(schoolId, academicYearId, number)
                    .switchIfEmpty(
                        Mono.defer {
                            evaluationSessionRepository.save(
                                EvaluationSession(
                                    schoolId = schoolId,
                                    academicYearId = academicYearId,
                                    number = number,
                                    termNumber = ((number - 1) / 2) + 1,
                                    shortLabel = "DEV$number",
                                    name = LocalizedText(fr = "Séquence $number", en = "Sequence $number"),
                                    status = SessionStatus.PLANNED
                                )
                            )
                        }
                    )
            }
            .map { GradingMapper.toSessionResponse(it) }
    }

    fun listByYear(
        currentUser: CurrentUser,
        academicYearId: String,
        schoolIdOverride: String? = null
    ): Flux<EvaluationSessionResponse> {
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return evaluationSessionRepository.findBySchoolIdAndAcademicYearId(schoolId, academicYearId)
            .sort(compareBy { it.number })
            .map { GradingMapper.toSessionResponse(it) }
    }

    fun getById(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<EvaluationSessionResponse> {
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return evaluationSessionRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SESSION_NOT_FOUND", "Evaluation session not found")))
            .map { GradingMapper.toSessionResponse(it) }
    }

    fun updateExamDate(
        currentUser: CurrentUser,
        id: String,
        examDate: java.time.LocalDate,
        schoolIdOverride: String? = null
    ): Mono<EvaluationSessionResponse> {
        GradingAccess.assertAdmin(currentUser)
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return evaluationSessionRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SESSION_NOT_FOUND", "Evaluation session not found")))
            .flatMap { session ->
                // TODO(Q-04) : surcharge examDate par classe
                session.examDate = examDate
                evaluationSessionRepository.save(session)
            }
            .map { GradingMapper.toSessionResponse(it) }
    }

    /** BR-21 : ouvre la séquence et crée une GradeSheet par ClassSubject actif (BR-11). */
    fun open(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<EvaluationSessionResponse> {
        GradingAccess.assertAdmin(currentUser)
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        GradingAccess.logSuperAdmin(currentUser, schoolId, "session.open")
        log.info("session.open start by={} schoolId={}", currentUser.username, schoolId)

        return evaluationSessionRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SESSION_NOT_FOUND", "Evaluation session not found")))
            .flatMap { session ->
                when (session.status) {
                    SessionStatus.OPEN -> Mono.just(session)
                    SessionStatus.CLOSED ->
                        Mono.error(BusinessRuleException("SESSION_CLOSED", "Reopen the session before opening again"))
                    SessionStatus.PLANNED -> {
                        session.status = SessionStatus.OPEN
                        session.openedAt = Instant.now()
                        evaluationSessionRepository.save(session)
                            .flatMap { opened -> createMissingSheets(opened).thenReturn(opened) }
                            .flatMap { opened ->
                                outboxWriter.append(
                                    schoolId = opened.schoolId,
                                    module = "grading",
                                    aggregateType = "EvaluationSession",
                                    aggregateId = opened.id!!,
                                    eventType = "grading.session.opened",
                                    payload = mapOf(
                                        "sessionId" to opened.id,
                                        "number" to opened.number,
                                        "termNumber" to opened.termNumber
                                    )
                                ).thenReturn(opened)
                            }
                    }
                }
            }
            .map { GradingMapper.toSessionResponse(it) }
            .doOnNext { log.info("session.open ok by={} schoolId={} number={}", currentUser.username, schoolId, it.number) }
    }

    /** BR-23 : clôture (admin). */
    fun close(
        currentUser: CurrentUser,
        id: String,
        closeNote: String?,
        schoolIdOverride: String? = null
    ): Mono<EvaluationSessionResponse> {
        GradingAccess.assertAdmin(currentUser)
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        log.info("session.close start by={} schoolId={}", currentUser.username, schoolId)
        return evaluationSessionRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SESSION_NOT_FOUND", "Evaluation session not found")))
            .flatMap { session ->
                if (session.status != SessionStatus.OPEN) {
                    Mono.error(BusinessRuleException("SESSION_NOT_OPEN", "Only an OPEN session can be closed"))
                } else {
                    session.status = SessionStatus.CLOSED
                    session.closedAt = Instant.now()
                    session.closedBy = currentUser.accountId
                    session.closeNote = closeNote?.trim()?.ifBlank { null }
                    evaluationSessionRepository.save(session)
                        .flatMap { closed ->
                            lockSheets(closed).then(
                                outboxWriter.append(
                                    schoolId = closed.schoolId,
                                    module = "grading",
                                    aggregateType = "EvaluationSession",
                                    aggregateId = closed.id!!,
                                    eventType = "grading.session.closed",
                                    payload = mapOf(
                                        "sessionId" to closed.id,
                                        "number" to closed.number,
                                        "termNumber" to closed.termNumber
                                    )
                                )
                            ).thenReturn(closed)
                        }
                }
            }
            .map { GradingMapper.toSessionResponse(it) }
    }

    /** BR-23 / Q-04 : rouvrir = directeur. */
    fun reopen(
        currentUser: CurrentUser,
        id: String,
        reason: String,
        schoolIdOverride: String? = null
    ): Mono<EvaluationSessionResponse> {
        GradingAccess.assertDirector(currentUser)
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        if (reason.isBlank()) {
            return Mono.error(BusinessRuleException("REASON_REQUIRED", "Reason is required to reopen a session"))
        }
        return evaluationSessionRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SESSION_NOT_FOUND", "Evaluation session not found")))
            .flatMap { session ->
                if (session.status != SessionStatus.CLOSED) {
                    Mono.error(BusinessRuleException("SESSION_NOT_CLOSED", "Only a CLOSED session can be reopened"))
                } else {
                    session.status = SessionStatus.OPEN
                    session.reopenedAt = Instant.now()
                    session.reopenedBy = currentUser.accountId
                    session.closedAt = null
                    session.closedBy = null
                    evaluationSessionRepository.save(session)
                        .flatMap { opened -> unlockSheets(opened).thenReturn(opened) }
                }
            }
            .map { GradingMapper.toSessionResponse(it) }
    }

    private fun createMissingSheets(session: EvaluationSession): Mono<Void> =
        classSubjectApi.findActiveByYear(session.schoolId, session.academicYearId)
            .flatMap { cs ->
                gradeSheetRepository.findBySchoolIdAndClassSubjectIdAndSessionId(
                    session.schoolId,
                    cs.id,
                    session.id!!
                ).switchIfEmpty(
                    Mono.defer {
                        gradeSheetRepository.save(
                            GradeSheet(
                                schoolId = session.schoolId,
                                academicYearId = session.academicYearId,
                                classId = cs.classId,
                                classSubjectId = cs.id,
                                sessionId = session.id!!,
                                sessionNumber = session.number,
                                teacherId = cs.teacherId,
                                status = SheetStatus.DRAFT,
                                totalization = GradingMapper.defaultTotalization()
                            )
                        )
                    }
                )
            }
            .then()

    private fun lockSheets(session: EvaluationSession): Mono<Void> =
        gradeSheetRepository.findBySchoolIdAndSessionId(session.schoolId, session.id!!)
            .flatMap { sheet ->
                sheet.status = SheetStatus.LOCKED
                sheet.lockedAt = Instant.now()
                gradeSheetRepository.save(sheet)
            }
            .then()

    private fun unlockSheets(session: EvaluationSession): Mono<Void> =
        gradeSheetRepository.findBySchoolIdAndSessionId(session.schoolId, session.id!!)
            .flatMap { sheet ->
                sheet.status = SheetStatus.DRAFT
                sheet.lockedAt = null
                gradeSheetRepository.save(sheet)
            }
            .then()

    override fun findByYear(schoolId: String, academicYearId: String): Flux<EvaluationSessionSummaryDto> =
        evaluationSessionRepository.findBySchoolIdAndAcademicYearId(schoolId, academicYearId)
            .map { GradingMapper.toSessionSummary(it) }

    override fun areRelevantSessionsClosed(
        schoolId: String,
        academicYearId: String,
        sessionNumbers: List<Int>
    ): Mono<Boolean> {
        if (sessionNumbers.isEmpty()) return Mono.just(true)
        return evaluationSessionRepository.findBySchoolIdAndAcademicYearId(schoolId, academicYearId)
            .filter { it.number in sessionNumbers }
            .collectList()
            .map { sessions ->
                sessions.size == sessionNumbers.distinct().size &&
                    sessions.all { it.status == SessionStatus.CLOSED }
            }
    }
}
