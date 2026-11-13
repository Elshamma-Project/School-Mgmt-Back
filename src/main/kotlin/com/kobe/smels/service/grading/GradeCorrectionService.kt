package com.kobe.smels.service.grading

import com.kobe.smels.dto.grading.CreateGradeCorrectionRequest
import com.kobe.smels.dto.grading.GradeCorrectionEvidenceDto
import com.kobe.smels.dto.grading.GradeCorrectionEvidenceUploadDto
import com.kobe.smels.dto.grading.GradeCorrectionRequestResponse
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.grading.AssessmentType
import com.kobe.smels.model.grading.GradeCorrectionEvidenceFile
import com.kobe.smels.model.grading.GradeCorrectionEvidenceMeta
import com.kobe.smels.model.grading.GradeCorrectionRequest
import com.kobe.smels.model.grading.GradeCorrectionStatus
import com.kobe.smels.model.grading.GradeStatus
import com.kobe.smels.model.grading.SessionStatus
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.grading.AssessmentRepository
import com.kobe.smels.repository.grading.EvaluationSessionRepository
import com.kobe.smels.repository.grading.GradeCorrectionEvidenceFileRepository
import com.kobe.smels.repository.grading.GradeCorrectionRequestRepository
import com.kobe.smels.repository.grading.GradeRepository
import com.kobe.smels.repository.grading.GradeSheetRepository
import com.kobe.smels.service.academic.api.ClassSubjectApi
import com.kobe.smels.service.academic.api.SchoolClassApi
import com.kobe.smels.service.academic.api.SubjectApi
import com.kobe.smels.service.grading.api.GradeCorrectionApi
import com.kobe.smels.service.staff.api.StaffApi
import com.kobe.smels.service.student.api.EnrollmentApi
import com.kobe.smels.service.student.api.StudentApi
import com.kobe.smels.shared.outbox.OutboxWriter
import com.kobe.smels.shared.security.CurrentUser
import org.bson.types.Binary
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * BR-80 : demandes de correction post-clôture (enseignant) → approbation directeur seule.
 */
@Service
class GradeCorrectionService(
    private val requestRepository: GradeCorrectionRequestRepository,
    private val evidenceRepository: GradeCorrectionEvidenceFileRepository,
    private val evaluationSessionRepository: EvaluationSessionRepository,
    private val gradeSheetRepository: GradeSheetRepository,
    private val assessmentRepository: AssessmentRepository,
    private val gradeRepository: GradeRepository,
    private val classSubjectApi: ClassSubjectApi,
    private val schoolClassApi: SchoolClassApi,
    private val subjectApi: SubjectApi,
    private val studentApi: StudentApi,
    private val enrollmentApi: EnrollmentApi,
    private val staffApi: StaffApi,
    private val gradeService: GradeService,
    private val outboxWriter: OutboxWriter
) : GradeCorrectionApi {
    private val log = LoggerFactory.getLogger(javaClass)

    fun create(
        currentUser: CurrentUser,
        request: CreateGradeCorrectionRequest
    ): Mono<GradeCorrectionRequestResponse> {
        val schoolId = GradingAccess.resolveSchoolId(currentUser, request.schoolId)
        val sessionId = request.sessionId
        val classSubjectId = request.classSubjectId
        val studentId = request.studentId
        val reason = request.reason.trim()
        if (reason.length < MIN_REASON_LEN) {
            return Mono.error(
                BusinessRuleException(
                    "REASON_TOO_SHORT",
                    "Justification must be at least $MIN_REASON_LEN characters"
                )
            )
        }
        if (request.evidence.size > MAX_EVIDENCE) {
            return Mono.error(
                BusinessRuleException("TOO_MANY_FILES", "At most $MAX_EVIDENCE evidence files are allowed")
            )
        }

        val proposedStatus = parseStatus(request.proposedStatus)
        val proposedValue = parseProposedValue(request.proposedValue, proposedStatus)
        val evidenceUploads = request.evidence

        return evaluationSessionRepository.findByIdAndSchoolId(sessionId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SESSION_NOT_FOUND", "Evaluation session not found")))
            .flatMap { session ->
                if (session.status != SessionStatus.CLOSED) {
                    return@flatMap Mono.error(
                        BusinessRuleException("SESSION_NOT_CLOSED", "Corrections require a CLOSED session")
                    )
                }
                classSubjectApi.getSummary(schoolId, classSubjectId)
                    .switchIfEmpty(Mono.error(NotFoundException("CLASS_SUBJECT_NOT_FOUND", "Class subject not found")))
                    .flatMap { cs ->
                        GradingAccess.assertCanManageSheet(currentUser, cs.teacherId)
                        gradeSheetRepository.findBySchoolIdAndClassSubjectIdAndSessionId(
                            schoolId, classSubjectId, sessionId
                        )
                            .switchIfEmpty(Mono.error(NotFoundException("SHEET_NOT_FOUND", "Grade sheet not found")))
                            .flatMap { sheet ->
                                assessmentRepository.findBySchoolIdAndSheetId(schoolId, sheet.id!!)
                                    .filter { it.type == AssessmentType.SEQUENCE_MARK }
                                    .next()
                                    .switchIfEmpty(
                                        Mono.error(
                                            BusinessRuleException(
                                                "ASSESSMENT_MISSING",
                                                "SEQUENCE_MARK assessment missing for this sheet"
                                            )
                                        )
                                    )
                                    .flatMap { assessment ->
                                        requestRepository
                                            .findBySchoolIdAndSessionIdAndClassSubjectIdAndStudentIdAndAssessmentIdAndStatus(
                                                schoolId,
                                                sessionId,
                                                classSubjectId,
                                                studentId,
                                                assessment.id!!,
                                                GradeCorrectionStatus.PENDING
                                            )
                                            .flatMap<GradeCorrectionRequestResponse> {
                                                Mono.error(
                                                    BusinessRuleException(
                                                        "PENDING_EXISTS",
                                                        "A pending correction already exists for this student"
                                                    )
                                                )
                                            }
                                            .switchIfEmpty(
                                                Mono.defer {
                                                    loadCreateContext(
                                                        schoolId,
                                                        cs.classId,
                                                        cs.subjectId,
                                                        studentId,
                                                        session.academicYearId,
                                                        cs.teacherId
                                                    ).flatMap { labels ->
                                                        gradeRepository
                                                            .findBySchoolIdAndAssessmentIdAndStudentId(
                                                                schoolId, assessment.id!!, studentId
                                                            )
                                                            .map { g -> g.status to g.value }
                                                            .defaultIfEmpty(GradeStatus.PENDING to null)
                                                            .flatMap { prev ->
                                                                val prevStatus = prev.first
                                                                val prevValue = prev.second
                                                                validateProposed(
                                                                    proposedStatus,
                                                                    proposedValue,
                                                                    assessment.maxScore
                                                                )
                                                                val request = GradeCorrectionRequest(
                                                                    schoolId = schoolId,
                                                                    academicYearId = session.academicYearId,
                                                                    sessionId = sessionId,
                                                                    sessionNumber = session.number,
                                                                    classId = cs.classId,
                                                                    classSubjectId = classSubjectId,
                                                                    assessmentId = assessment.id!!,
                                                                    studentId = studentId,
                                                                    enrollmentId = labels.enrollmentId,
                                                                    studentLabel = labels.studentLabel,
                                                                    classLabel = labels.classLabel,
                                                                    subjectLabelFr = labels.subjectFr,
                                                                    subjectLabelEn = labels.subjectEn,
                                                                    teacherId = cs.teacherId,
                                                                    teacherLabel = labels.teacherLabel,
                                                                    previousStatus = prevStatus,
                                                                    previousValue = prevValue,
                                                                    proposedStatus = proposedStatus,
                                                                    proposedValue = proposedValue,
                                                                    reason = reason,
                                                                    requestedBy = currentUser.accountId,
                                                                    requestedAt = Instant.now()
                                                                )
                                                                requestRepository.save(request).flatMap { saved ->
                                                                    persistEvidence(schoolId, saved.id!!, evidenceUploads)
                                                                        .flatMap { metas ->
                                                                            saved.evidence = metas
                                                                            requestRepository.save(saved)
                                                                        }
                                                                        .flatMap { withEvidence ->
                                                                            outboxWriter.append(
                                                                                schoolId = schoolId,
                                                                                module = "grading",
                                                                                aggregateType = "GradeCorrectionRequest",
                                                                                aggregateId = withEvidence.id!!,
                                                                                eventType = "grading.correction.requested",
                                                                                payload = mapOf(
                                                                                    "requestId" to withEvidence.id,
                                                                                    "sessionId" to sessionId,
                                                                                    "classSubjectId" to classSubjectId,
                                                                                    "studentLabel" to withEvidence.studentLabel,
                                                                                    "classLabel" to withEvidence.classLabel,
                                                                                    "teacherLabel" to withEvidence.teacherLabel,
                                                                                    "status" to GradeCorrectionStatus.PENDING.name
                                                                                )
                                                                            ).thenReturn(toResponse(withEvidence))
                                                                        }
                                                                }
                                                            }
                                                    }
                                                }
                                            )
                                    }
                            }
                    }
            }
            .doOnNext {
                log.info(
                    "correction.request ok by={} schoolId={} sessionNumber={}",
                    currentUser.username,
                    schoolId,
                    it.sessionNumber
                )
            }
    }

    fun listPending(currentUser: CurrentUser, schoolIdOverride: String? = null): Flux<GradeCorrectionRequestResponse> {
        GradingAccess.assertDirector(currentUser)
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return requestRepository.findBySchoolIdAndStatusOrderByRequestedAtDesc(
            schoolId,
            GradeCorrectionStatus.PENDING
        ).map { toResponse(it) }
    }

    fun listMine(currentUser: CurrentUser, schoolIdOverride: String? = null): Flux<GradeCorrectionRequestResponse> {
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        if (!currentUser.hasAnyRole(Role.TEACHER, Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)) {
            return Flux.error(ForbiddenException("FORBIDDEN", "Insufficient role"))
        }
        return requestRepository.findBySchoolIdAndRequestedByOrderByRequestedAtDesc(
            schoolId,
            currentUser.accountId
        ).map { toResponse(it) }
    }

    fun listForSheet(
        currentUser: CurrentUser,
        sessionId: String,
        classSubjectId: String,
        schoolIdOverride: String? = null
    ): Flux<GradeCorrectionRequestResponse> {
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return classSubjectApi.getSummary(schoolId, classSubjectId)
            .switchIfEmpty(Mono.error(NotFoundException("CLASS_SUBJECT_NOT_FOUND", "Class subject not found")))
            .flatMapMany { cs ->
                GradingAccess.assertCanReadReportSheet(currentUser, cs.teacherId)
                requestRepository.findBySchoolIdAndSessionIdAndClassSubjectIdAndStatus(
                    schoolId, sessionId, classSubjectId, GradeCorrectionStatus.PENDING
                ).map { toResponse(it) }
            }
    }

    fun approve(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<GradeCorrectionRequestResponse> {
        GradingAccess.assertDirector(currentUser)
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return requestRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("CORRECTION_NOT_FOUND", "Correction request not found")))
            .flatMap { request ->
                if (request.status != GradeCorrectionStatus.PENDING) {
                    return@flatMap Mono.error(
                        BusinessRuleException("INVALID_STATUS", "Only PENDING requests can be approved")
                    )
                }
                gradeService.applyDirectorApprovedCorrection(
                    director = currentUser,
                    assessmentId = request.assessmentId,
                    studentId = request.studentId,
                    status = request.proposedStatus,
                    value = request.proposedValue,
                    reason = request.reason,
                    requestId = request.id!!
                ).flatMap {
                    request.status = GradeCorrectionStatus.APPROVED
                    request.reviewedBy = currentUser.accountId
                    request.reviewedAt = Instant.now()
                    requestRepository.save(request).flatMap { saved ->
                        outboxWriter.append(
                            schoolId = schoolId,
                            module = "grading",
                            aggregateType = "GradeCorrectionRequest",
                            aggregateId = saved.id!!,
                            eventType = "grading.correction.approved",
                            payload = mapOf(
                                "requestId" to saved.id,
                                "sessionId" to saved.sessionId,
                                "classSubjectId" to saved.classSubjectId,
                                "requestedBy" to saved.requestedBy,
                                "teacherId" to saved.teacherId,
                                "studentLabel" to saved.studentLabel,
                                "decision" to "APPROVED"
                            )
                        ).thenReturn(toResponse(saved))
                    }
                }
            }
    }

    fun reject(
        currentUser: CurrentUser,
        id: String,
        reviewNote: String,
        schoolIdOverride: String? = null
    ): Mono<GradeCorrectionRequestResponse> {
        GradingAccess.assertDirector(currentUser)
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        val note = reviewNote.trim()
        if (note.length < 5) {
            return Mono.error(BusinessRuleException("REVIEW_NOTE_REQUIRED", "Review note is required"))
        }
        return requestRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("CORRECTION_NOT_FOUND", "Correction request not found")))
            .flatMap { request ->
                if (request.status != GradeCorrectionStatus.PENDING) {
                    return@flatMap Mono.error(
                        BusinessRuleException("INVALID_STATUS", "Only PENDING requests can be rejected")
                    )
                }
                request.status = GradeCorrectionStatus.REJECTED
                request.reviewedBy = currentUser.accountId
                request.reviewedAt = Instant.now()
                request.reviewNote = note
                requestRepository.save(request).flatMap { saved ->
                    outboxWriter.append(
                        schoolId = schoolId,
                        module = "grading",
                        aggregateType = "GradeCorrectionRequest",
                        aggregateId = saved.id!!,
                        eventType = "grading.correction.rejected",
                        payload = mapOf(
                            "requestId" to saved.id,
                            "sessionId" to saved.sessionId,
                            "classSubjectId" to saved.classSubjectId,
                            "requestedBy" to saved.requestedBy,
                            "teacherId" to saved.teacherId,
                            "studentLabel" to saved.studentLabel,
                            "decision" to "REJECTED",
                            "reviewNote" to saved.reviewNote
                        )
                    ).thenReturn(toResponse(saved))
                }
            }
    }

    fun getEvidence(
        currentUser: CurrentUser,
        requestId: String,
        evidenceId: String,
        schoolIdOverride: String? = null
    ): Mono<GradeCorrectionEvidenceFile> {
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return requestRepository.findByIdAndSchoolId(requestId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("CORRECTION_NOT_FOUND", "Correction request not found")))
            .flatMap { request ->
                val canRead =
                    currentUser.hasAnyRole(Role.DIRECTOR, Role.SUPER_ADMIN, Role.ADMINISTRATION) ||
                        request.requestedBy == currentUser.accountId ||
                        GradingAccess.canManageSheet(currentUser, request.teacherId)
                if (!canRead) {
                    return@flatMap Mono.error(ForbiddenException("FORBIDDEN", "Not allowed to read evidence"))
                }
                evidenceRepository.findByIdAndSchoolIdAndRequestId(evidenceId, schoolId, requestId)
                    .switchIfEmpty(Mono.error(NotFoundException("EVIDENCE_NOT_FOUND", "Evidence not found")))
            }
    }

    private fun persistEvidence(
        schoolId: String,
        requestId: String,
        uploads: List<GradeCorrectionEvidenceUploadDto>
    ): Mono<List<GradeCorrectionEvidenceMeta>> {
        if (uploads.isEmpty()) return Mono.just(emptyList())
        return Flux.fromIterable(uploads)
            .concatMap { upload -> Mono.fromCallable { decodeAndValidate(upload) } }
            .concatMap { loaded ->
                val id = UUID.randomUUID().toString().replace("-", "")
                val meta = GradeCorrectionEvidenceMeta(
                    id = id,
                    fileName = loaded.fileName,
                    contentType = loaded.contentType,
                    sizeBytes = loaded.bytes.size.toLong(),
                    sha256 = loaded.sha256
                )
                evidenceRepository.save(
                    GradeCorrectionEvidenceFile(
                        id = id,
                        schoolId = schoolId,
                        requestId = requestId,
                        fileName = loaded.fileName,
                        contentType = loaded.contentType,
                        sizeBytes = loaded.bytes.size.toLong(),
                        sha256 = loaded.sha256,
                        data = Binary(loaded.bytes)
                    )
                ).thenReturn(meta)
            }
            .collectList()
    }

    private fun decodeAndValidate(upload: GradeCorrectionEvidenceUploadDto): LoadedFile {
        val contentType = upload.contentType.substringBefore(";").trim().lowercase()
        if (contentType !in ALLOWED_TYPES) {
            throw BusinessRuleException("EVIDENCE_TYPE", "Evidence must be JPEG, PNG, WebP or PDF")
        }
        val raw = upload.base64Data.substringAfter("base64,", upload.base64Data).trim()
        val bytes = try {
            Base64.getDecoder().decode(raw)
        } catch (_: IllegalArgumentException) {
            throw BusinessRuleException("EVIDENCE_INVALID", "Invalid evidence encoding")
        }
        if (bytes.isEmpty()) {
            throw BusinessRuleException("EVIDENCE_EMPTY", "Evidence file is empty")
        }
        if (bytes.size > MAX_EVIDENCE_BYTES) {
            throw BusinessRuleException("EVIDENCE_TOO_LARGE", "Each evidence file must be at most 5 MB")
        }
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        return LoadedFile(
            fileName = upload.fileName.ifBlank { "evidence" },
            contentType = contentType,
            bytes = bytes,
            sha256 = sha
        )
    }

    private data class Labels(
        val studentLabel: String,
        val classLabel: String?,
        val subjectFr: String?,
        val subjectEn: String?,
        val teacherLabel: String?,
        val enrollmentId: String?
    )

    private data class LoadedFile(
        val fileName: String,
        val contentType: String,
        val bytes: ByteArray,
        val sha256: String
    )

    private fun loadCreateContext(
        schoolId: String,
        classId: String,
        subjectId: String,
        studentId: String,
        academicYearId: String,
        teacherId: String?
    ): Mono<Labels> =
        Mono.zip(
            studentApi.getSummary(schoolId, studentId)
                .map { it.displayName }
                .defaultIfEmpty("—"),
            schoolClassApi.getSummary(schoolId, classId)
                .map { it.name }
                .defaultIfEmpty("—"),
            subjectApi.getSummary(schoolId, subjectId)
                .map { listOf(it.name.fr, it.name.en) }
                .defaultIfEmpty(listOf(null, null)),
            resolveTeacherLabel(schoolId, teacherId),
            enrollmentApi.findByStudentAndYear(schoolId, studentId, academicYearId)
                .map { it.id }
                .defaultIfEmpty("")
        ).map { tuple ->
            Labels(
                studentLabel = tuple.t1,
                classLabel = tuple.t2.takeIf { it.isNotBlank() && it != "—" },
                subjectFr = tuple.t3.getOrNull(0),
                subjectEn = tuple.t3.getOrNull(1),
                teacherLabel = tuple.t4.takeIf { it.isNotBlank() },
                enrollmentId = tuple.t5.ifBlank { null }
            )
        }

    private fun resolveTeacherLabel(schoolId: String, teacherId: String?): Mono<String> =
        if (teacherId.isNullOrBlank()) Mono.just("")
        else staffApi.getSummary(schoolId, teacherId)
            .map { it.displayName }
            .defaultIfEmpty("")

    private fun parseStatus(raw: String?): GradeStatus {
        if (raw.isNullOrBlank()) return GradeStatus.SCORED
        return runCatching { GradeStatus.valueOf(raw.trim().uppercase()) }
            .getOrElse { throw BusinessRuleException("INVALID_STATUS", "Invalid proposed status") }
    }

    private fun parseProposedValue(raw: String?, status: GradeStatus): BigDecimal? {
        if (status != GradeStatus.SCORED) return null
        val cleaned = raw?.trim()?.replace(",", ".")?.ifBlank { null }
            ?: throw BusinessRuleException("VALUE_REQUIRED", "Proposed mark is required")
        return runCatching { BigDecimal(cleaned) }
            .getOrElse { throw BusinessRuleException("INVALID_VALUE", "Invalid proposed mark") }
    }

    private fun validateProposed(status: GradeStatus, value: BigDecimal?, maxScore: BigDecimal) {
        when (status) {
            GradeStatus.SCORED -> {
                if (value == null) throw BusinessRuleException("VALUE_REQUIRED", "Proposed mark is required")
                try {
                    SequenceMarkCalculator.assertScoreBounds(value, maxScore)
                } catch (ex: IllegalArgumentException) {
                    throw BusinessRuleException("SCORE_OUT_OF_BOUNDS", ex.message ?: "Invalid score")
                }
                if (value.scale() > 2) {
                    throw BusinessRuleException("SCORE_PRECISION", "Score must have at most 2 decimal places")
                }
            }
            GradeStatus.PENDING, GradeStatus.ABSENT, GradeStatus.ABSENT_EXCUSED, GradeStatus.EXEMPTED -> Unit
        }
    }

    private fun toResponse(request: GradeCorrectionRequest) =
        GradeCorrectionRequestResponse(
            id = request.id!!,
            schoolId = request.schoolId,
            academicYearId = request.academicYearId,
            sessionId = request.sessionId,
            sessionNumber = request.sessionNumber,
            classId = request.classId,
            classSubjectId = request.classSubjectId,
            assessmentId = request.assessmentId,
            studentId = request.studentId,
            studentLabel = request.studentLabel,
            classLabel = request.classLabel,
            subjectLabelFr = request.subjectLabelFr,
            subjectLabelEn = request.subjectLabelEn,
            teacherId = request.teacherId,
            teacherLabel = request.teacherLabel,
            previousStatus = request.previousStatus,
            previousValue = request.previousValue,
            proposedStatus = request.proposedStatus,
            proposedValue = request.proposedValue,
            reason = request.reason,
            evidence = request.evidence.map {
                GradeCorrectionEvidenceDto(
                    id = it.id,
                    fileName = it.fileName,
                    contentType = it.contentType,
                    sizeBytes = it.sizeBytes,
                    sha256 = it.sha256,
                    capturedAt = it.capturedAt
                )
            },
            status = request.status,
            requestedBy = request.requestedBy,
            requestedAt = request.requestedAt,
            reviewedBy = request.reviewedBy,
            reviewedAt = request.reviewedAt,
            reviewNote = request.reviewNote
        )

    override fun countPending(schoolId: String): Mono<Long> =
        requestRepository.countBySchoolIdAndStatus(schoolId, GradeCorrectionStatus.PENDING)
            .defaultIfEmpty(0L)

    companion object {
        private const val MIN_REASON_LEN = 15
        private const val MAX_EVIDENCE = 3
        private const val MAX_EVIDENCE_BYTES = 5L * 1024 * 1024
        private val ALLOWED_TYPES = setOf(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "application/pdf"
        )
    }
}
