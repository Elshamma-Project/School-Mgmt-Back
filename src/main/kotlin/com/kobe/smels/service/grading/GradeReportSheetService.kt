package com.kobe.smels.service.grading

import com.kobe.smels.dto.grading.GenerateReportSheetRequest
import com.kobe.smels.dto.grading.GradeReportSheetResponse
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.grading.AuditAction
import com.kobe.smels.model.grading.AuditTarget
import com.kobe.smels.model.grading.EvaluationSession
import com.kobe.smels.model.grading.GradeAuditEntry
import com.kobe.smels.model.grading.GradeReportSheet
import com.kobe.smels.model.grading.GradeReportSheetLine
import com.kobe.smels.model.grading.GradeStatus
import com.kobe.smels.model.grading.ReportSheetSequenceCell
import com.kobe.smels.model.grading.ReportSheetStatus
import com.kobe.smels.model.grading.ReportSheetVisa
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.grading.EvaluationSessionRepository
import com.kobe.smels.repository.grading.GradeAuditEntryRepository
import com.kobe.smels.repository.grading.GradeReportSheetRepository
import com.kobe.smels.repository.grading.GradingSchemeRepository
import com.kobe.smels.repository.grading.SequenceMarkRepository
import com.kobe.smels.service.academic.api.ClassSubjectApi
import com.kobe.smels.service.academic.api.SchoolClassApi
import com.kobe.smels.service.academic.api.SubjectApi
import com.kobe.smels.service.staff.api.StaffApi
import com.kobe.smels.service.student.api.EnrollmentApi
import com.kobe.smels.service.student.api.StudentApi
import com.kobe.smels.shared.outbox.OutboxWriter
import com.kobe.smels.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Fiche de report de notes (BR-70..BR-78) — instantané trimestriel distinct de [GradeSheet].
 */
@Service
class GradeReportSheetService(
    private val gradeReportSheetRepository: GradeReportSheetRepository,
    private val evaluationSessionRepository: EvaluationSessionRepository,
    private val sequenceMarkRepository: SequenceMarkRepository,
    private val gradingSchemeRepository: GradingSchemeRepository,
    private val gradeAuditEntryRepository: GradeAuditEntryRepository,
    private val classSubjectApi: ClassSubjectApi,
    private val schoolClassApi: SchoolClassApi,
    private val subjectApi: SubjectApi,
    private val staffApi: StaffApi,
    private val enrollmentApi: EnrollmentApi,
    private val studentApi: StudentApi,
    private val outboxWriter: OutboxWriter
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val douala = ZoneId.of("Africa/Douala")

    fun generate(
        currentUser: CurrentUser,
        request: GenerateReportSheetRequest
    ): Mono<GradeReportSheetResponse> {
        val schoolId = GradingAccess.resolveSchoolId(currentUser, request.schoolId)
        if (!currentUser.hasAnyRole(
                Role.TEACHER, Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN
            )
        ) {
            return Mono.error(ForbiddenException("FORBIDDEN", "Cannot generate report sheet"))
        }
        log.info(
            "report-sheet.generate start by={} schoolId={} term={} blank={}",
            currentUser.username, schoolId, request.termNumber, request.blank
        )
        return classSubjectApi.getSummary(schoolId, request.classSubjectId)
            .flatMap { cs ->
                GradingAccess.assertCanManageSheet(currentUser, cs.teacherId)
                schoolClassApi.getSummary(schoolId, cs.classId).flatMap { clazz ->
                    subjectApi.getSummary(schoolId, cs.subjectId).flatMap { subject ->
                        resolveTeacherLabel(schoolId, cs.teacherId).flatMap { teacherLabel ->
                            resolveScheme(schoolId, clazz.gradingSchemeId).flatMap { scheme ->
                                loadTermSessions(schoolId, cs.academicYearId, request.termNumber)
                                    .flatMap { sessions ->
                                        buildSheet(
                                            schoolId = schoolId,
                                            cs = cs,
                                            classLabel = clazz.name,
                                            subjectLabel = LocalizedText(subject.name.fr, subject.name.en),
                                            teacherLabel = teacherLabel,
                                            scheme = scheme,
                                            sessions = sessions,
                                            blank = request.blank,
                                            termNumber = request.termNumber
                                        )
                                    }
                            }
                        }
                    }
                }
            }
            .flatMap { built -> upsertDraft(currentUser, built) }
            .map { GradingMapper.toReportSheetResponse(it) }
    }

    fun getById(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<GradeReportSheetResponse> {
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return gradeReportSheetRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("REPORT_SHEET_NOT_FOUND", "Report sheet not found")))
            .flatMap { sheet ->
                GradingAccess.assertCanReadReportSheet(currentUser, sheet.teacherId)
                resolveScheme(schoolId, null).map { scheme ->
                    GradingMapper.toReportSheetResponse(sheet, deadlineWarning(sheet, scheme))
                }
            }
    }

    fun listByYear(
        currentUser: CurrentUser,
        academicYearId: String,
        termNumber: Int,
        schoolIdOverride: String? = null
    ): Flux<GradeReportSheetResponse> {
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        if (!currentUser.hasAnyRole(
                Role.SURVEILLANT_GENERAL, Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN
            )
        ) {
            val teacherId = GradingAccess.staffProfileId(currentUser)
                ?: return Flux.error(ForbiddenException("FORBIDDEN", "Missing staff profile"))
            return gradeReportSheetRepository.findBySchoolIdAndTeacherIdAndTermNumber(schoolId, teacherId, termNumber)
                .filter { it.academicYearId == academicYearId }
                .map { GradingMapper.toReportSheetResponse(it) }
        }
        return gradeReportSheetRepository
            .findBySchoolIdAndAcademicYearIdAndTermNumber(schoolId, academicYearId, termNumber)
            .map { GradingMapper.toReportSheetResponse(it) }
    }

    fun submit(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<GradeReportSheetResponse> {
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return gradeReportSheetRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("REPORT_SHEET_NOT_FOUND", "Report sheet not found")))
            .flatMap { sheet ->
                GradingAccess.assertCanManageSheet(currentUser, sheet.teacherId)
                if (sheet.status != ReportSheetStatus.DRAFT && sheet.status != ReportSheetStatus.REJECTED) {
                    return@flatMap Mono.error(
                        BusinessRuleException("INVALID_STATUS", "Only DRAFT or REJECTED sheets can be submitted")
                    )
                }
                resolveScheme(schoolId, null).flatMap { scheme ->
                    val warning = deadlineWarning(sheet, scheme)
                    if (warning && scheme.reportSheetDeadlineBlocking) {
                        return@flatMap Mono.error(
                            BusinessRuleException(
                                "DEADLINE_PASSED",
                                "Report sheet deadline exceeded (${scheme.reportSheetDeadlineHours}h after exam)"
                            )
                        )
                    }
                    val previous = sheet.status
                    sheet.status = ReportSheetStatus.SUBMITTED
                    sheet.submittedAt = Instant.now()
                    sheet.submittedBy = currentUser.accountId
                    sheet.rejectionReason = null
                    sheet.rejectedAt = null
                    sheet.rejectedBy = null
                    gradeReportSheetRepository.save(sheet).flatMap { saved ->
                        auditTransition(currentUser, saved, previous, AuditAction.SUBMITTED, null)
                            .then(
                                outboxWriter.append(
                                    schoolId = schoolId,
                                    module = "grading",
                                    aggregateType = "GradeReportSheet",
                                    aggregateId = saved.id!!,
                                    eventType = "grading.report-sheet.submitted",
                                    payload = mapOf(
                                        "reportSheetId" to saved.id,
                                        "classSubjectId" to saved.classSubjectId,
                                        "termNumber" to saved.termNumber,
                                        "deadlineWarning" to warning
                                    )
                                )
                            ).thenReturn(GradingMapper.toReportSheetResponse(saved, warning))
                    }
                }
            }
    }

    fun visaSupervisor(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<GradeReportSheetResponse> {
        GradingAccess.assertSurveillantGeneral(currentUser)
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return gradeReportSheetRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("REPORT_SHEET_NOT_FOUND", "Report sheet not found")))
            .flatMap { sheet ->
                if (sheet.status != ReportSheetStatus.SUBMITTED) {
                    return@flatMap Mono.error(
                        BusinessRuleException("INVALID_STATUS", "Sheet must be SUBMITTED before SG visa")
                    )
                }
                val previous = sheet.status
                sheet.status = ReportSheetStatus.VISA_SG
                sheet.visaSupervisor = ReportSheetVisa(currentUser.accountId, Instant.now())
                gradeReportSheetRepository.save(sheet).flatMap { saved ->
                    auditTransition(currentUser, saved, previous, AuditAction.VISAED, null)
                        .then(emitVisaed(saved, "SUPERVISOR"))
                        .thenReturn(GradingMapper.toReportSheetResponse(saved))
                }
            }
    }

    fun visaPrincipal(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<GradeReportSheetResponse> {
        GradingAccess.assertPrincipalVisa(currentUser)
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return gradeReportSheetRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("REPORT_SHEET_NOT_FOUND", "Report sheet not found")))
            .flatMap { sheet ->
                if (sheet.status != ReportSheetStatus.VISA_SG && sheet.status != ReportSheetStatus.SUBMITTED) {
                    return@flatMap Mono.error(
                        BusinessRuleException("INVALID_STATUS", "Sheet must be SUBMITTED or VISA_SG before Principal visa")
                    )
                }
                val previous = sheet.status
                sheet.status = ReportSheetStatus.VISA_PRINCIPAL
                sheet.visaPrincipal = ReportSheetVisa(currentUser.accountId, Instant.now())
                gradeReportSheetRepository.save(sheet).flatMap { saved ->
                    auditTransition(currentUser, saved, previous, AuditAction.VISAED, null)
                        .then(emitVisaed(saved, "PRINCIPAL"))
                        .thenReturn(GradingMapper.toReportSheetResponse(saved))
                }
            }
    }

    fun reject(
        currentUser: CurrentUser,
        id: String,
        reason: String,
        schoolIdOverride: String? = null
    ): Mono<GradeReportSheetResponse> {
        if (!currentUser.hasAnyRole(
                Role.SURVEILLANT_GENERAL, Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN
            )
        ) {
            return Mono.error(ForbiddenException("FORBIDDEN", "Cannot reject report sheet"))
        }
        if (reason.isBlank()) {
            return Mono.error(BusinessRuleException("REASON_REQUIRED", "Rejection reason is required"))
        }
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return gradeReportSheetRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("REPORT_SHEET_NOT_FOUND", "Report sheet not found")))
            .flatMap { sheet ->
                if (sheet.status == ReportSheetStatus.DRAFT || sheet.status == ReportSheetStatus.REJECTED) {
                    return@flatMap Mono.error(
                        BusinessRuleException("INVALID_STATUS", "Cannot reject a DRAFT or already REJECTED sheet")
                    )
                }
                val previous = sheet.status
                sheet.status = ReportSheetStatus.REJECTED
                sheet.rejectionReason = reason.trim()
                sheet.rejectedAt = Instant.now()
                sheet.rejectedBy = currentUser.accountId
                gradeReportSheetRepository.save(sheet).flatMap { saved ->
                    auditTransition(currentUser, saved, previous, AuditAction.REJECTED, reason.trim())
                        .then(
                            outboxWriter.append(
                                schoolId = schoolId,
                                module = "grading",
                                aggregateType = "GradeReportSheet",
                                aggregateId = saved.id!!,
                                eventType = "grading.report-sheet.rejected",
                                payload = mapOf(
                                    "reportSheetId" to saved.id,
                                    "classSubjectId" to saved.classSubjectId,
                                    "termNumber" to saved.termNumber
                                )
                            )
                        ).thenReturn(GradingMapper.toReportSheetResponse(saved))
                }
            }
    }

    fun regenerate(
        currentUser: CurrentUser,
        id: String,
        blank: Boolean = false,
        schoolIdOverride: String? = null
    ): Mono<GradeReportSheetResponse> {
        if (!currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN, Role.TEACHER)) {
            return Mono.error(ForbiddenException("FORBIDDEN", "Cannot regenerate report sheet"))
        }
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return gradeReportSheetRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("REPORT_SHEET_NOT_FOUND", "Report sheet not found")))
            .flatMap { existing ->
                GradingAccess.assertCanManageSheet(currentUser, existing.teacherId)
                if (existing.status == ReportSheetStatus.VISA_PRINCIPAL &&
                    !currentUser.hasAnyRole(Role.DIRECTOR, Role.SUPER_ADMIN, Role.ADMINISTRATION)
                ) {
                    return@flatMap Mono.error(
                        BusinessRuleException("LOCKED", "Cannot regenerate a fully visaed sheet without admin")
                    )
                }
                generate(
                    currentUser,
                    GenerateReportSheetRequest(
                        classSubjectId = existing.classSubjectId,
                        termNumber = existing.termNumber,
                        blank = blank,
                        schoolId = schoolId
                    )
                ).flatMap { regenerated ->
                    gradeReportSheetRepository.findByIdAndSchoolId(regenerated.id, schoolId)
                        .flatMap { sheet ->
                            auditTransition(
                                currentUser, sheet, existing.status, AuditAction.REGENERATED, "regenerate"
                            ).thenReturn(regenerated)
                        }
                }
            }
    }

    private fun emitVisaed(saved: GradeReportSheet, level: String): Mono<Void> =
        outboxWriter.append(
            schoolId = saved.schoolId,
            module = "grading",
            aggregateType = "GradeReportSheet",
            aggregateId = saved.id!!,
            eventType = "grading.report-sheet.visaed",
            payload = mapOf(
                "reportSheetId" to saved.id,
                "classSubjectId" to saved.classSubjectId,
                "termNumber" to saved.termNumber,
                "level" to level
            )
        ).then()

    private fun auditTransition(
        currentUser: CurrentUser,
        sheet: GradeReportSheet,
        previous: ReportSheetStatus,
        action: AuditAction,
        reason: String?
    ): Mono<GradeAuditEntry> =
        gradeAuditEntryRepository.save(
            GradeAuditEntry(
                schoolId = sheet.schoolId,
                target = AuditTarget.REPORT_SHEET,
                reportSheetId = sheet.id,
                classSubjectId = sheet.classSubjectId,
                action = action,
                previousReportStatus = previous,
                newReportStatus = sheet.status,
                changedBy = currentUser.accountId,
                changedByRole = currentUser.roles.firstOrNull(),
                changedAt = Instant.now(),
                reason = reason
            )
        )

    private fun upsertDraft(currentUser: CurrentUser, built: GradeReportSheet): Mono<GradeReportSheet> =
        gradeReportSheetRepository.findBySchoolIdAndClassSubjectIdAndTermNumber(
            built.schoolId, built.classSubjectId, built.termNumber
        )
            .flatMap { existing ->
                if (existing.status == ReportSheetStatus.VISA_PRINCIPAL &&
                    !currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)
                ) {
                    Mono.error(BusinessRuleException("LOCKED", "Fully visaed sheet cannot be overwritten"))
                } else {
                    existing.teacherId = built.teacherId
                    existing.teacherLabel = built.teacherLabel
                    existing.subjectLabel = built.subjectLabel
                    existing.classLabel = built.classLabel
                    existing.coefficient = built.coefficient
                    existing.headcount = built.headcount
                    existing.boysCount = built.boysCount
                    existing.girlsCount = built.girlsCount
                    existing.sexCountWarning = built.sexCountWarning
                    existing.sequence1SessionId = built.sequence1SessionId
                    existing.sequence2SessionId = built.sequence2SessionId
                    existing.sequence1ExamDate = built.sequence1ExamDate
                    existing.sequence2ExamDate = built.sequence2ExamDate
                    existing.sequence1Number = built.sequence1Number
                    existing.sequence2Number = built.sequence2Number
                    existing.lines = built.lines
                    existing.appreciationScaleCode = built.appreciationScaleCode
                    existing.lastRegeneratedAt = Instant.now()
                    existing.status = ReportSheetStatus.DRAFT
                    existing.submittedAt = null
                    existing.submittedBy = null
                    existing.visaSupervisor = null
                    existing.visaPrincipal = null
                    existing.rejectionReason = null
                    gradeReportSheetRepository.save(existing)
                }
            }
            .switchIfEmpty(
                Mono.defer {
                    built.generatedAt = Instant.now()
                    built.status = ReportSheetStatus.DRAFT
                    gradeReportSheetRepository.save(built)
                }
            )

    private fun buildSheet(
        schoolId: String,
        cs: com.kobe.smels.dto.academic.ClassSubjectSummaryDto,
        classLabel: String,
        subjectLabel: LocalizedText,
        teacherLabel: String,
        scheme: com.kobe.smels.model.grading.GradingScheme,
        sessions: List<EvaluationSession>,
        blank: Boolean,
        termNumber: Int
    ): Mono<GradeReportSheet> {
        val ordered = sessions.sortedBy { it.number }
        if (ordered.size != 2) {
            return Mono.error(
                BusinessRuleException(
                    "SESSIONS_MISSING",
                    "Term $termNumber must have exactly 2 evaluation sessions"
                )
            )
        }
        val s1 = ordered[0]
        val s2 = ordered[1]
        val scaleCode = scheme.reportSheetAppreciationScaleCode
        val scale = scheme.appreciationScales.firstOrNull { it.code == scaleCode }
            ?: ReportSheetCalculator.defaultLetterScale()
        val coefficient = cs.coefficient

        return enrollmentApi.findEnrolledByClass(schoolId, cs.classId)
            .collectList()
            .flatMap { enrollments ->
                val sorted = enrollments.sortedWith(
                    compareBy({ it.rollNumber ?: Int.MAX_VALUE }, { it.studentId })
                )
                Flux.fromIterable(sorted)
                    .concatMap { enr ->
                        studentApi.getSummary(schoolId, enr.studentId).map { student ->
                            Triple(enr, student, student.sex)
                        }
                    }
                    .collectList()
                    .flatMap { rows ->
                        val sexes = rows.map { it.third }
                        val counts = ReportSheetCalculator.headcounts(sexes)
                        val marks1Mono = if (blank) {
                            Mono.just(emptyMap())
                        } else {
                            sequenceMarkRepository
                                .findBySchoolIdAndClassSubjectIdAndSessionId(schoolId, cs.id, s1.id!!)
                                .collectMap({ it.studentId }, { it })
                        }
                        val marks2Mono = if (blank) {
                            Mono.just(emptyMap())
                        } else {
                            sequenceMarkRepository
                                .findBySchoolIdAndClassSubjectIdAndSessionId(schoolId, cs.id, s2.id!!)
                                .collectMap({ it.studentId }, { it })
                        }
                        Mono.zip(marks1Mono, marks2Mono).map { tuple ->
                            val m1 = tuple.t1
                            val m2 = tuple.t2
                            val ranks1 = ReportSheetCalculator.ranks(
                                rows.map { (enr, _, _) ->
                                    val mark = m1[enr.studentId]
                                    ReportSheetCalculator.ScoredStudent(
                                        enr.studentId,
                                        if (mark?.status == GradeStatus.SCORED) mark.value else null
                                    )
                                },
                                scheme.rankingPolicy
                            )
                            val ranks2 = ReportSheetCalculator.ranks(
                                rows.map { (enr, _, _) ->
                                    val mark = m2[enr.studentId]
                                    ReportSheetCalculator.ScoredStudent(
                                        enr.studentId,
                                        if (mark?.status == GradeStatus.SCORED) mark.value else null
                                    )
                                },
                                scheme.rankingPolicy
                            )
                            val lines = rows.map { (enr, student, sex) ->
                                val mark1 = m1[enr.studentId]
                                val mark2 = m2[enr.studentId]
                                GradeReportSheetLine(
                                    rollNumber = enr.rollNumber,
                                    studentId = enr.studentId,
                                    studentLabel = student.displayName,
                                    sex = sex,
                                    sequence1 = cell(
                                        blank, mark1?.value, mark1?.status ?: GradeStatus.PENDING,
                                        coefficient, ranks1[enr.studentId], scale
                                    ),
                                    sequence2 = cell(
                                        blank, mark2?.value, mark2?.status ?: GradeStatus.PENDING,
                                        coefficient, ranks2[enr.studentId], scale
                                    )
                                )
                            }
                            GradeReportSheet(
                                schoolId = schoolId,
                                academicYearId = cs.academicYearId,
                                classId = cs.classId,
                                classSubjectId = cs.id,
                                termNumber = termNumber,
                                teacherId = cs.teacherId,
                                teacherLabel = teacherLabel.ifBlank { null },
                                subjectLabel = subjectLabel,
                                classLabel = classLabel,
                                coefficient = coefficient,
                                headcount = counts.headcount,
                                boysCount = counts.boysCount,
                                girlsCount = counts.girlsCount,
                                sexCountWarning = counts.sexCountWarning,
                                sequence1SessionId = s1.id,
                                sequence2SessionId = s2.id,
                                sequence1ExamDate = s1.examDate,
                                sequence2ExamDate = s2.examDate,
                                sequence1Number = s1.number,
                                sequence2Number = s2.number,
                                appreciationScaleCode = scale.code,
                                lines = lines
                            )
                        }
                    }
            }
    }

    private fun cell(
        blank: Boolean,
        mark: BigDecimal?,
        status: GradeStatus,
        coefficient: BigDecimal,
        rank: Int?,
        scale: com.kobe.smels.model.grading.AppreciationScale
    ): ReportSheetSequenceCell {
        if (blank) {
            return ReportSheetSequenceCell(
                mark = null,
                markStatus = GradeStatus.PENDING,
                coefficient = coefficient,
                weightedMark = null,
                rank = null,
                appreciation = null
            )
        }
        val effectiveStatus = if (mark != null) GradeStatus.SCORED else status
        val weighted = ReportSheetCalculator.weightedMark(
            if (effectiveStatus == GradeStatus.SCORED) mark else null,
            coefficient
        )
        return ReportSheetSequenceCell(
            mark = if (effectiveStatus == GradeStatus.SCORED) mark else null,
            markStatus = effectiveStatus,
            coefficient = coefficient,
            weightedMark = weighted,
            rank = if (effectiveStatus == GradeStatus.SCORED) rank else null,
            appreciation = ReportSheetCalculator.appreciation(mark, effectiveStatus, scale)
        )
    }

    private fun loadTermSessions(
        schoolId: String,
        academicYearId: String,
        termNumber: Int
    ): Mono<List<EvaluationSession>> =
        evaluationSessionRepository
            .findBySchoolIdAndAcademicYearIdAndTermNumber(schoolId, academicYearId, termNumber)
            .collectList()

    private fun resolveTeacherLabel(schoolId: String, teacherId: String?): Mono<String> =
        if (teacherId == null) Mono.just("")
        else staffApi.getSummary(schoolId, teacherId)
            .map { it.displayName }
            .defaultIfEmpty("")

    private fun resolveScheme(
        schoolId: String,
        gradingSchemeId: String?
    ): Mono<com.kobe.smels.model.grading.GradingScheme> =
        if (gradingSchemeId != null) {
            gradingSchemeRepository.findByIdAndSchoolId(gradingSchemeId, schoolId)
                .switchIfEmpty(defaultScheme(schoolId))
        } else {
            defaultScheme(schoolId)
        }

    private fun defaultScheme(schoolId: String): Mono<com.kobe.smels.model.grading.GradingScheme> =
        gradingSchemeRepository.findBySchoolIdAndActiveTrue(schoolId).next()
            .switchIfEmpty(
                Mono.just(
                    com.kobe.smels.model.grading.GradingScheme(
                        schoolId = schoolId,
                        code = "DEFAULT",
                        name = LocalizedText(fr = "Défaut", en = "Default"),
                        appreciationScales = listOf(ReportSheetCalculator.defaultLetterScale())
                    )
                )
            )

    private fun deadlineWarning(
        sheet: GradeReportSheet,
        scheme: com.kobe.smels.model.grading.GradingScheme
    ): Boolean {
        val examDates = listOfNotNull(sheet.sequence1ExamDate, sheet.sequence2ExamDate)
        if (examDates.isEmpty()) return false
        val latest = examDates.maxOrNull() ?: return false
        val examInstant = latest.atStartOfDay(douala).toInstant()
        val hours = ChronoUnit.HOURS.between(examInstant, Instant.now())
        return hours > scheme.reportSheetDeadlineHours.toLong()
    }
}
