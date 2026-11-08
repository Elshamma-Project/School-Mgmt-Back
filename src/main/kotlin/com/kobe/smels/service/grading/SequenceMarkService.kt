package com.kobe.smels.service.grading

import com.kobe.smels.dto.grading.SequenceMarkSummaryDto
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.grading.AuditAction
import com.kobe.smels.model.grading.AuditTarget
import com.kobe.smels.model.grading.EvaluationSession
import com.kobe.smels.model.grading.GradeAuditEntry
import com.kobe.smels.model.grading.GradeSheet
import com.kobe.smels.model.grading.GradeStatus
import com.kobe.smels.model.grading.MarkSource
import com.kobe.smels.model.grading.SequenceMark
import com.kobe.smels.model.grading.SessionStatus
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.grading.AssessmentRepository
import com.kobe.smels.repository.grading.GradeAuditEntryRepository
import com.kobe.smels.repository.grading.GradeRepository
import com.kobe.smels.repository.grading.GradeSheetRepository
import com.kobe.smels.repository.grading.SequenceMarkRepository
import com.kobe.smels.service.academic.api.ClassSubjectApi
import com.kobe.smels.service.grading.api.SequenceMarkApi
import com.kobe.smels.service.student.api.StudentApi
import com.kobe.smels.shared.security.CurrentUser
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class SequenceMarkService(
    private val sequenceMarkRepository: SequenceMarkRepository,
    private val gradeRepository: GradeRepository,
    private val assessmentRepository: AssessmentRepository,
    private val gradeSheetRepository: GradeSheetRepository,
    private val gradeAuditEntryRepository: GradeAuditEntryRepository,
    private val studentApi: StudentApi,
    private val classSubjectApi: ClassSubjectApi
) : SequenceMarkApi {

    /**
     * Recalcule la SequenceMark d'un élève pour une fiche (BR-12).
     * Appelé uniquement depuis GradeService dans la même chaîne réactive.
     */
    fun recalculateForStudent(
        currentUser: CurrentUser,
        sheet: GradeSheet,
        session: EvaluationSession,
        studentId: String,
        enrollmentId: String
    ): Mono<SequenceMark> {
        return assessmentRepository.findBySchoolIdAndSheetId(sheet.schoolId, sheet.id!!)
            .collectList()
            .flatMap { assessments ->
                gradeRepository.findBySchoolIdAndClassSubjectIdAndSessionIdAndStudentId(
                    sheet.schoolId,
                    sheet.classSubjectId,
                    sheet.sessionId,
                    studentId
                ).collectList().map { grades -> assessments to grades }
            }
            .flatMap { (assessments, grades) ->
                val byAssessment = grades.associateBy { it.assessmentId }
                val columns = assessments.map { a ->
                    SequenceMarkCalculator.InputColumn(a, byAssessment[a.id])
                }
                val result = SequenceMarkCalculator.totalize(sheet.totalization, columns)
                upsertComputed(
                    currentUser = currentUser,
                    sheet = sheet,
                    session = session,
                    studentId = studentId,
                    enrollmentId = enrollmentId,
                    result = result,
                    afterClosed = session.status == SessionStatus.CLOSED
                )
            }
            .flatMap { mark ->
                sheet.lastTotalizedAt = Instant.now()
                gradeSheetRepository.save(sheet).thenReturn(mark)
            }
    }

    private fun upsertComputed(
        currentUser: CurrentUser,
        sheet: GradeSheet,
        session: EvaluationSession,
        studentId: String,
        enrollmentId: String,
        result: SequenceMarkCalculator.Result,
        afterClosed: Boolean
    ): Mono<SequenceMark> {
        return sequenceMarkRepository.findBySchoolIdAndSheetIdAndStudentId(
            sheet.schoolId,
            sheet.id!!,
            studentId
        )
            .flatMap { existing ->
                val previousValue = existing.value
                val previousStatus = existing.status
                existing.value = result.value
                existing.status = result.status
                existing.source = MarkSource.COMPUTED
                existing.computedAt = Instant.now()
                existing.calculationVersion = "Q25-default-v1"
                sequenceMarkRepository.save(existing).flatMap { saved ->
                    writeAudit(
                        currentUser,
                        sheet,
                        session.id!!,
                        studentId,
                        saved,
                        AuditAction.UPDATED,
                        previousValue,
                        previousStatus,
                        afterClosed
                    ).thenReturn(saved)
                }
            }
            .switchIfEmpty(
                Mono.defer {
                    val created = SequenceMark(
                        schoolId = sheet.schoolId,
                        academicYearId = sheet.academicYearId,
                        classId = sheet.classId,
                        classSubjectId = sheet.classSubjectId,
                        sessionId = sheet.sessionId,
                        sessionNumber = sheet.sessionNumber,
                        termNumber = session.termNumber,
                        sheetId = sheet.id!!,
                        studentId = studentId,
                        enrollmentId = enrollmentId,
                        value = result.value,
                        status = result.status,
                        source = MarkSource.COMPUTED,
                        computedAt = Instant.now(),
                        calculationVersion = "Q25-default-v1"
                    )
                    sequenceMarkRepository.save(created).flatMap { saved ->
                        writeAudit(
                            currentUser,
                            sheet,
                            session.id!!,
                            studentId,
                            saved,
                            AuditAction.CREATED,
                            null,
                            null,
                            afterClosed
                        ).thenReturn(saved)
                    }
                }
            )
    }

    private fun writeAudit(
        currentUser: CurrentUser,
        sheet: GradeSheet,
        sessionId: String,
        studentId: String,
        mark: SequenceMark,
        action: AuditAction,
        previousValue: java.math.BigDecimal?,
        previousStatus: GradeStatus?,
        afterClosed: Boolean
    ): Mono<GradeAuditEntry> =
        gradeAuditEntryRepository.save(
            GradeAuditEntry(
                schoolId = sheet.schoolId,
                target = AuditTarget.SEQUENCE_MARK,
                sequenceMarkId = mark.id,
                classSubjectId = sheet.classSubjectId,
                sessionId = sessionId,
                studentId = studentId,
                action = action,
                previousValue = previousValue,
                newValue = mark.value,
                previousStatus = previousStatus,
                newStatus = mark.status,
                changedBy = currentUser.accountId,
                changedByRole = currentUser.roles.firstOrNull(),
                changedAt = Instant.now(),
                afterSessionClosed = afterClosed
            )
        )

    override fun findByClassSubjectAndSession(
        schoolId: String,
        classSubjectId: String,
        sessionId: String
    ): Flux<SequenceMarkSummaryDto> =
        sequenceMarkRepository.findBySchoolIdAndClassSubjectIdAndSessionId(schoolId, classSubjectId, sessionId)
            .map { GradingMapper.toSequenceMarkSummary(it) }

    override fun findByClassSubject(
        schoolId: String,
        classSubjectId: String
    ): Flux<SequenceMarkSummaryDto> =
        sequenceMarkRepository.findBySchoolIdAndClassSubjectId(schoolId, classSubjectId)
            .map { GradingMapper.toSequenceMarkSummary(it) }

    override fun findByStudentAndSession(
        schoolId: String,
        studentId: String,
        academicYearId: String,
        sessionId: String
    ): Flux<SequenceMarkSummaryDto> =
        sequenceMarkRepository.findBySchoolIdAndStudentIdAndAcademicYearIdAndSessionId(
            schoolId, studentId, academicYearId, sessionId
        ).map { GradingMapper.toSequenceMarkSummary(it) }

    override fun getBySheetAndStudent(
        schoolId: String,
        sheetId: String,
        studentId: String
    ): Mono<SequenceMarkSummaryDto> =
        sequenceMarkRepository.findBySchoolIdAndSheetIdAndStudentId(schoolId, sheetId, studentId)
            .switchIfEmpty(Mono.error(NotFoundException("SEQUENCE_MARK_NOT_FOUND", "Sequence mark not found")))
            .map { GradingMapper.toSequenceMarkSummary(it) }

    /**
     * Consultation notes de séquence (docs/05 §2) — lecture seule.
     * Sans `academicYearId` : tout le parcours (toutes les années persistées).
     */
    fun listForStudentYear(
        currentUser: CurrentUser,
        studentId: String,
        academicYearId: String? = null,
        schoolIdOverride: String? = null
    ): Flux<SequenceMarkSummaryDto> {
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        val source = if (academicYearId.isNullOrBlank()) {
            sequenceMarkRepository.findBySchoolIdAndStudentId(schoolId, studentId)
        } else {
            sequenceMarkRepository.findBySchoolIdAndStudentIdAndAcademicYearId(
                schoolId, studentId, academicYearId
            )
        }
        return assertCanViewSequenceMarks(currentUser, schoolId, studentId)
            .thenMany(source)
            .concatMap { mark ->
                filterMarkForTeacher(currentUser, schoolId, mark.academicYearId, mark)
            }
            .map { GradingMapper.toSequenceMarkSummary(it) }
    }

    private fun assertCanViewSequenceMarks(
        currentUser: CurrentUser,
        schoolId: String,
        studentId: String
    ): Mono<Void> {
        if (currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)) {
            return Mono.empty()
        }
        if (currentUser.hasRole(Role.STUDENT) &&
            currentUser.profileType == ProfileType.STUDENT &&
            currentUser.profileId == studentId
        ) {
            return Mono.empty()
        }
        if (currentUser.hasRole(Role.PARENT) &&
            currentUser.profileType == ProfileType.GUARDIAN &&
            currentUser.profileId != null
        ) {
            return studentApi.findGuardianLink(schoolId, studentId, currentUser.profileId!!)
                .flatMap<Void> { link ->
                    if (!link.canViewGrades) {
                        Mono.error(ForbiddenException("FORBIDDEN", "Guardian cannot view grades for this student"))
                    } else {
                        Mono.empty()
                    }
                }
                .onErrorMap(NotFoundException::class.java) {
                    ForbiddenException("FORBIDDEN", "Guardian is not linked to this student")
                }
        }
        if (currentUser.hasRole(Role.TEACHER) &&
            currentUser.profileType == ProfileType.STAFF &&
            currentUser.profileId != null
        ) {
            return Mono.empty() // filtrage par ClassSubject dans filterMarkForTeacher
        }
        return Mono.error(ForbiddenException("FORBIDDEN", "Cannot view sequence marks"))
    }

    private fun filterMarkForTeacher(
        currentUser: CurrentUser,
        schoolId: String,
        academicYearId: String,
        mark: SequenceMark
    ): Mono<SequenceMark> {
        if (!currentUser.hasRole(Role.TEACHER) ||
            currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)
        ) {
            return Mono.just(mark)
        }
        val teacherId = currentUser.profileId ?: return Mono.empty()
        return classSubjectApi.findByTeacher(schoolId, teacherId, academicYearId)
            .map { it.id }
            .collectList()
            .flatMap { allowed ->
                if (mark.classSubjectId in allowed) Mono.just(mark) else Mono.empty()
            }
    }
}
