package com.kobe.smels.controller.grading

import com.kobe.smels.dto.grading.BatchUpsertGradesRequest
import com.kobe.smels.dto.grading.CloseSessionRequest
import com.kobe.smels.dto.grading.CreateAssessmentRequest
import com.kobe.smels.dto.grading.CreateGradingSchemeRequest
import com.kobe.smels.dto.grading.EnsureSessionsRequest
import com.kobe.smels.dto.grading.GenerateReportSheetRequest
import com.kobe.smels.dto.grading.RejectReportSheetRequest
import com.kobe.smels.dto.grading.ReopenSessionRequest
import com.kobe.smels.dto.grading.UpdateAssessmentRequest
import com.kobe.smels.dto.grading.UpdateSessionExamDateRequest
import com.kobe.smels.dto.grading.UpsertGradeRequest
import com.kobe.smels.model.shared.Language
import com.kobe.smels.service.grading.AssessmentService
import com.kobe.smels.service.grading.EvaluationSessionService
import com.kobe.smels.service.grading.GradeReportSheetPdfService
import com.kobe.smels.service.grading.GradeReportSheetService
import com.kobe.smels.service.grading.GradeAuditService
import com.kobe.smels.service.grading.GradeService
import com.kobe.smels.service.grading.GradeSheetService
import com.kobe.smels.service.grading.GradingSchemeService
import com.kobe.smels.service.grading.SequenceMarkService
import com.kobe.smels.shared.security.SecuritySupport
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/v1/evaluation-sessions")
class EvaluationSessionController(private val evaluationSessionService: EvaluationSessionService) {
    @PostMapping("/ensure")
    fun ensure(@Valid @RequestBody request: EnsureSessionsRequest) =
        SecuritySupport.currentUser().flatMapMany {
            evaluationSessionService.ensureYearSessions(it, request.academicYearId, request.schoolId)
        }

    @GetMapping
    fun list(
        @RequestParam academicYearId: String,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMapMany {
        evaluationSessionService.listByYear(it, academicYearId, schoolId)
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { evaluationSessionService.getById(it, id, schoolId) }

    @PostMapping("/{id}/open")
    fun open(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { evaluationSessionService.open(it, id, schoolId) }

    @PostMapping("/{id}/close")
    fun close(
        @PathVariable id: String,
        @RequestBody(required = false) request: CloseSessionRequest?,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap {
        evaluationSessionService.close(it, id, request?.closeNote, request?.schoolId ?: schoolId)
    }

    @PostMapping("/{id}/reopen")
    fun reopen(@PathVariable id: String, @Valid @RequestBody request: ReopenSessionRequest) =
        SecuritySupport.currentUser().flatMap {
            evaluationSessionService.reopen(it, id, request.reason, request.schoolId)
        }

    @PutMapping("/{id}/exam-date")
    fun updateExamDate(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateSessionExamDateRequest
    ) = SecuritySupport.currentUser().flatMap {
        evaluationSessionService.updateExamDate(it, id, request.examDate, request.schoolId)
    }
}

@RestController
@RequestMapping("/api/v1/grade-sheets")
class GradeSheetController(private val gradeSheetService: GradeSheetService) {
    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { gradeSheetService.getById(it, id, schoolId) }

    @GetMapping
    fun list(
        @RequestParam sessionId: String,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMapMany {
        gradeSheetService.listMineForSession(it, sessionId, schoolId)
    }

    @GetMapping("/{id}/sequence-marks")
    fun sequenceMarks(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMapMany {
            gradeSheetService.listSequenceMarks(it, id, schoolId)
        }
}

@RestController
@RequestMapping("/api/v1/assessments")
class AssessmentController(private val assessmentService: AssessmentService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateAssessmentRequest) =
        SecuritySupport.currentUser().flatMap { assessmentService.create(it, request) }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateAssessmentRequest,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap { assessmentService.update(it, id, request, schoolId) }

    @GetMapping
    fun list(
        @RequestParam sheetId: String,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMapMany {
        assessmentService.listBySheet(it, sheetId, schoolId)
    }
}

@RestController
@RequestMapping("/api/v1/grades")
class GradeController(private val gradeService: GradeService) {
    @PostMapping
    fun upsert(@Valid @RequestBody request: UpsertGradeRequest) =
        SecuritySupport.currentUser().flatMap { gradeService.upsert(it, request) }

    @PostMapping("/batch")
    fun batch(@Valid @RequestBody request: BatchUpsertGradesRequest) =
        SecuritySupport.currentUser().flatMapMany { gradeService.batchUpsert(it, request) }

    @GetMapping
    fun list(
        @RequestParam assessmentId: String,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMapMany {
        gradeService.listByAssessment(it, assessmentId, schoolId)
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @RequestParam assessmentId: String,
        @RequestParam studentId: String,
        @RequestParam(required = false) reason: String?,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap {
        gradeService.delete(it, assessmentId, studentId, reason, schoolId)
    }
}

@RestController
@RequestMapping("/api/v1/sequence-marks")
class SequenceMarkController(private val sequenceMarkService: SequenceMarkService) {
    @GetMapping
    fun listForStudent(
        @RequestParam studentId: String,
        @RequestParam(required = false) academicYearId: String?,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMapMany {
        sequenceMarkService.listForStudentYear(it, studentId, academicYearId, schoolId)
    }
}

@RestController
@RequestMapping("/api/v1/grade-audits")
class GradeAuditController(private val gradeAuditService: GradeAuditService) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMapMany {
        gradeAuditService.list(it, page, size, schoolId)
    }
}

@RestController
@RequestMapping("/api/v1/grading-schemes")
class GradingSchemeController(private val gradingSchemeService: GradingSchemeService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateGradingSchemeRequest) =
        SecuritySupport.currentUser().flatMap { gradingSchemeService.create(it, request) }

    @GetMapping
    fun list(@RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMapMany { gradingSchemeService.list(it, schoolId) }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { gradingSchemeService.getById(it, id, schoolId) }
}

@RestController
@RequestMapping("/api/v1/grade-report-sheets")
class GradeReportSheetController(
    private val gradeReportSheetService: GradeReportSheetService,
    private val gradeReportSheetPdfService: GradeReportSheetPdfService
) {
    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    fun generate(@Valid @RequestBody request: GenerateReportSheetRequest) =
        SecuritySupport.currentUser().flatMap { gradeReportSheetService.generate(it, request) }

    @GetMapping
    fun list(
        @RequestParam academicYearId: String,
        @RequestParam termNumber: Int,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMapMany {
        gradeReportSheetService.listByYear(it, academicYearId, termNumber, schoolId)
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { gradeReportSheetService.getById(it, id, schoolId) }

    @PostMapping("/{id}/submit")
    fun submit(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { gradeReportSheetService.submit(it, id, schoolId) }

    @PostMapping("/{id}/visa-supervisor")
    fun visaSupervisor(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { gradeReportSheetService.visaSupervisor(it, id, schoolId) }

    @PostMapping("/{id}/visa-principal")
    fun visaPrincipal(@PathVariable id: String, @RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { gradeReportSheetService.visaPrincipal(it, id, schoolId) }

    @PostMapping("/{id}/reject")
    fun reject(@PathVariable id: String, @Valid @RequestBody request: RejectReportSheetRequest) =
        SecuritySupport.currentUser().flatMap {
            gradeReportSheetService.reject(it, id, request.reason, request.schoolId)
        }

    @PostMapping("/{id}/regenerate")
    fun regenerate(
        @PathVariable id: String,
        @RequestParam(defaultValue = "false") blank: Boolean,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap {
        gradeReportSheetService.regenerate(it, id, blank, schoolId)
    }

    @GetMapping("/{id}/pdf")
    fun pdf(
        @PathVariable id: String,
        @RequestParam(required = false) language: Language?,
        @RequestParam(required = false) schoolId: String?
    ): Mono<ResponseEntity<ByteArray>> =
        SecuritySupport.currentUser().flatMap { user ->
            gradeReportSheetPdfService.renderPdf(user, id, language, schoolId).map { bytes ->
                ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"grade-report-sheet.pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(bytes)
            }
        }
}
