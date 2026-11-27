package com.kobe.beesm.controller.fees

import com.kobe.beesm.dto.fees.ReactivateAfterFeesRequest
import com.kobe.beesm.dto.fees.RecordFeePaymentRequest
import com.kobe.beesm.dto.fees.UpdateFeesPolicyRequest
import com.kobe.beesm.dto.fees.UpsertTuitionScheduleRequest
import com.kobe.beesm.dto.fees.VoidFeePaymentRequest
import com.kobe.beesm.service.fees.FeesPolicyService
import com.kobe.beesm.service.fees.FeesService
import com.kobe.beesm.shared.security.SecuritySupport
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/v1/fees")
class FeesController(
    private val feesService: FeesService,
    private val feesPolicyService: FeesPolicyService
) {
    @GetMapping("/access")
    fun access(@RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { feesService.access(it, schoolId) }

    @GetMapping("/policy")
    fun getPolicy(@RequestParam(required = false) schoolId: String?) =
        SecuritySupport.currentUser().flatMap { feesPolicyService.getPolicy(it, schoolId) }

    @PutMapping("/policy")
    fun updatePolicy(@Valid @RequestBody request: UpdateFeesPolicyRequest) =
        SecuritySupport.currentUser().flatMap { feesPolicyService.updatePolicy(it, request) }

    @GetMapping("/schedules")
    fun listSchedules(
        @RequestParam academicYearId: String,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap {
        feesService.listSchedules(it, academicYearId, schoolId)
    }

    @PutMapping("/schedules")
    fun upsertSchedule(@Valid @RequestBody request: UpsertTuitionScheduleRequest) =
        SecuritySupport.currentUser().flatMap { feesService.upsertSchedule(it, request) }

    @PostMapping("/payments")
    fun recordPayment(@Valid @RequestBody request: RecordFeePaymentRequest) =
        SecuritySupport.currentUser().flatMap { feesService.recordPayment(it, request) }

    @PostMapping("/payments/{id}/void")
    fun voidPayment(
        @PathVariable id: String,
        @Valid @RequestBody request: VoidFeePaymentRequest
    ) = SecuritySupport.currentUser().flatMap { feesService.voidPayment(it, id, request) }

    /** Reçu PDF inscription (Q-69) — A4 portrait, 1 page. */
    @GetMapping("/payments/{id}/receipt-pdf")
    fun enrollmentReceiptPdf(
        @PathVariable id: String,
        @RequestParam(required = false) schoolId: String?
    ): Mono<ResponseEntity<ByteArray>> =
        SecuritySupport.currentUser().flatMap { user ->
            feesService.enrollmentReceiptPdf(user, id, schoolId).map { bytes ->
                ResponseEntity.ok()
                    .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"recu-inscription-$id.pdf\""
                    )
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(bytes)
            }
        }

    @GetMapping("/students/{studentId}/account")
    fun studentAccount(
        @PathVariable studentId: String,
        @RequestParam academicYearId: String,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap {
        feesService.getStudentAccount(it, studentId, academicYearId, schoolId)
    }

    /** BR-F05 / Q-65 — porte d’accès notes / EDT / bulletins (message soft-block portail). */
    @GetMapping("/students/{studentId}/academic-access")
    fun academicAccess(
        @PathVariable studentId: String,
        @RequestParam academicYearId: String,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap {
        feesService.getAcademicAccess(it, studentId, academicYearId, schoolId)
    }

    /** Q-80 : Direction — réactivation après soldes tuition + pénalités. */
    @PostMapping("/students/{studentId}/reactivate")
    fun reactivateAfterFees(
        @PathVariable studentId: String,
        @Valid @RequestBody request: ReactivateAfterFeesRequest
    ) = SecuritySupport.currentUser().flatMap {
        feesService.reactivateAfterFees(it, studentId, request)
    }

    /**
     * Batch solvabilité pour la liste élèves.
     * `studentIds` = ids séparés par des virgules.
     */
    @GetMapping("/solvency")
    fun solvency(
        @RequestParam academicYearId: String,
        @RequestParam studentIds: String,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMapMany { user ->
        val ids = studentIds.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        feesService.solvencyForStudents(user, academicYearId, ids, schoolId)
    }

    /** Compteurs d’impayés (inscription + N tranches) pour le hub élèves. */
    @GetMapping("/unpaid-summary")
    fun unpaidSummary(
        @RequestParam academicYearId: String,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap {
        feesService.unpaidSummary(it, academicYearId, schoolId)
    }

    /** Tableau de bord Solvabilité (montants, pénalités, grâce, courbes). */
    @GetMapping("/dashboard-summary")
    fun dashboardSummary(
        @RequestParam academicYearId: String,
        @RequestParam(required = false) schoolId: String?
    ) = SecuritySupport.currentUser().flatMap {
        feesService.dashboardSummary(it, academicYearId, schoolId)
    }
}
