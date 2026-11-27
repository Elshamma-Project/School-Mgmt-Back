package com.kobe.beesm.dto.fees

import com.kobe.beesm.model.fees.FeePaymentKind
import com.kobe.beesm.model.fees.FeePaymentMethod
import com.kobe.beesm.model.fees.FeePaymentStatus
import com.kobe.beesm.model.fees.SolvencyStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class UpsertTuitionScheduleRequest(
    @field:NotBlank
    val academicYearId: String,
    @field:NotBlank
    val levelId: String,
    @field:NotNull
    @field:DecimalMin("0.0", inclusive = true)
    val enrollmentFee: BigDecimal,
    @field:NotNull
    @field:Size(min = 1, max = 12)
    @field:Valid
    val installments: List<TuitionInstallmentAmountDto>,
    val currency: String? = "XAF",
    val schoolId: String? = null
)

data class TuitionInstallmentAmountDto(
    @field:Min(1)
    @field:Max(12)
    val index: Int,
    @field:NotNull
    @field:DecimalMin("0.0", inclusive = true)
    val amount: BigDecimal,
    @field:NotNull
    val dueDate: LocalDate
)

data class TuitionInstallmentResponse(
    val index: Int,
    val amount: BigDecimal,
    val dueDate: LocalDate
)

data class TuitionScheduleResponse(
    val id: String,
    val schoolId: String,
    val academicYearId: String,
    val levelId: String,
    val levelLabel: String,
    val enrollmentFee: BigDecimal,
    val installments: List<TuitionInstallmentResponse>,
    val currency: String,
    val active: Boolean
)

data class RecordFeePaymentRequest(
    @field:NotBlank
    val academicYearId: String,
    @field:NotBlank
    val studentId: String,
    @field:NotNull
    val kind: FeePaymentKind,
    @field:Min(1)
    @field:Max(12)
    val installmentIndex: Int? = null,
    @field:NotNull
    @field:DecimalMin("0.01", inclusive = true)
    val amount: BigDecimal,
    val paidAt: Instant? = null,
    val method: FeePaymentMethod = FeePaymentMethod.CASH,
    val reference: String? = null,
    val note: String? = null,
    val schoolId: String? = null
)

data class VoidFeePaymentRequest(
    @field:NotBlank
    val reason: String,
    val schoolId: String? = null
)

data class FeePaymentResponse(
    val id: String,
    val schoolId: String,
    val academicYearId: String,
    val studentId: String,
    val enrollmentId: String,
    val classId: String,
    val scheduleId: String,
    val kind: FeePaymentKind,
    val installmentIndex: Int?,
    val amount: BigDecimal,
    val paidAt: Instant,
    val method: FeePaymentMethod,
    val reference: String?,
    val recordedByAccountId: String,
    val note: String?,
    val status: FeePaymentStatus,
    val voidedAt: Instant?,
    val voidReason: String?
)

data class SolvencyResponse(
    val studentId: String,
    val academicYearId: String,
    val status: SolvencyStatus,
    val enrollmentFeeDue: BigDecimal?,
    val enrollmentFeePaid: BigDecimal?,
    val installments: List<InstallmentSolvencyDto> = emptyList()
)

data class InstallmentSolvencyDto(
    val index: Int,
    val amount: BigDecimal,
    val dueDate: LocalDate,
    val paidAmount: BigDecimal,
    val settled: Boolean
)

/** DTO public pour les autres modules (liste élèves, portal). */
data class SolvencySnapshotDto(
    val studentId: String,
    val status: SolvencyStatus
)

data class StudentFeeAccountResponse(
    val studentId: String,
    val academicYearId: String,
    val enrollmentId: String?,
    val classId: String?,
    val classLabel: String?,
    val scheduleId: String?,
    val currency: String?,
    val solvency: SolvencyStatus,
    val enrollmentFeeDue: BigDecimal?,
    val enrollmentFeePaid: BigDecimal?,
    val installments: List<InstallmentSolvencyDto>,
    val payments: List<FeePaymentResponse>,
    /** Exclusion scolarité active (BR-F14 / Q-80). */
    val feesExcluded: Boolean = false,
    val penaltyTotalAccrued: BigDecimal = BigDecimal.ZERO,
    val penaltyPaid: BigDecimal = BigDecimal.ZERO,
    val penaltySettled: Boolean = true,
    /** Tuition (inscription + toutes tranches) soldée. */
    val tuitionSettled: Boolean = false,
    /** Direction seule : tuition + pénalités OK et exclusion fees. */
    val canReactivateAfterFees: Boolean = false
)

data class ReactivateAfterFeesRequest(
    @field:NotBlank
    val academicYearId: String,
    val schoolId: String? = null
)

data class ReactivateAfterFeesResponse(
    val studentId: String,
    val academicYearId: String,
    val active: Boolean,
    val feesExcluded: Boolean
)

/**
 * Droits fees pour l’UI (BR-F09 / BR-F10) :
 * - barèmes + politique rappels/pénalités : Direction / Super-admin
 * - paiements (reçu banque → solvabilité) : Direction / Super-admin / Administration **comptable**
 */
data class FeesAccessResponse(
    val canRead: Boolean,
    val canManageSchedules: Boolean,
    val canRecordPayments: Boolean,
    val canManagePolicy: Boolean = false
)

/**
 * Compteurs d’impayés / insolvabilité pour le hub élèves (année donnée).
 * Une ligne = élèves ENROLLED avec barème, échéance due et non soldée.
 */
data class FeesUnpaidSummaryResponse(
    val academicYearId: String,
    val enrollmentUnpaid: Int,
    val installmentsUnpaid: List<InstallmentUnpaidCountDto> = emptyList(),
    /** Élèves au statut dérivé INSOLVENT. */
    val insolventStudents: Int
)

data class InstallmentUnpaidCountDto(
    val index: Int,
    val unpaidCount: Int
)

/**
 * Tableau de bord Solvabilité (workspace) : KPI montants, pénalités, grâce,
 * niveaux, encaissements récents, ponctualité + séries pour courbes.
 */
data class FeesDashboardSummaryResponse(
    val academicYearId: String,
    val currency: String = "XAF",
    val enrollmentUnpaid: Int,
    val installmentsUnpaid: List<InstallmentUnpaidCountDto> = emptyList(),
    val insolventStudents: Int,
    val solventStudents: Int = 0,
    val pendingStudents: Int = 0,
    val notApplicableStudents: Int = 0,
    /** Total dû (inscription + tranches) pour les élèves avec barème. */
    val totalDue: BigDecimal = BigDecimal.ZERO,
    /** Total encaissé (paiements RECORDED). */
    val totalCollected: BigDecimal = BigDecimal.ZERO,
    /** Reste à recouvrer (max 0). */
    val totalOutstanding: BigDecimal = BigDecimal.ZERO,
    val enrollmentFeeUnpaidStudents: Int = 0,
    val studentsWithPenalties: Int = 0,
    val penaltyTotalAmount: BigDecimal = BigDecimal.ZERO,
    val exclusionRisk: ExclusionRiskDto = ExclusionRiskDto(),
    val insolventByLevel: List<LevelInsolvencyDto> = emptyList(),
    val recentPayments: RecentPaymentsDto = RecentPaymentsDto(),
    val punctuality: PunctualityStatsDto = PunctualityStatsDto(),
    /** Série quotidienne 30 j. (encaissements) pour courbes. */
    val dailyCollections: List<DailyAmountDto> = emptyList()
)

data class ExclusionRiskDto(
    val day14: Int = 0,
    val day21: Int = 0,
    val day28OrMax: Int = 0,
    val graceDays: Int = 28
)

data class LevelInsolvencyDto(
    val levelId: String,
    val levelLabel: String,
    val insolventCount: Int,
    val studentCount: Int
)

data class RecentPaymentsDto(
    val last7DaysCount: Int = 0,
    val last7DaysAmount: BigDecimal = BigDecimal.ZERO,
    val last30DaysCount: Int = 0,
    val last30DaysAmount: BigDecimal = BigDecimal.ZERO
)

data class PunctualityStatsDto(
    val onTimeCount: Int = 0,
    val lateCount: Int = 0,
    val unpaidDueCount: Int = 0
)

data class DailyAmountDto(
    val date: LocalDate,
    val amount: BigDecimal,
    val count: Int
)

data class FeesPolicyResponse(
    val id: String,
    val schoolId: String,
    val reminderDaysBeforeDue: List<Int>,
    val remindOnDueDate: Boolean,
    val weeklyPenaltyAmount: BigDecimal,
    val currency: String,
    val maxInsolvencyGraceDays: Int,
    val exclusionEnabled: Boolean,
    val exclusionWarningDaysBefore: Int,
    val active: Boolean
)

data class UpdateFeesPolicyRequest(
    @field:NotNull
    @field:Size(min = 1, max = 12)
    val reminderDaysBeforeDue: List<@Min(0) @Max(60) Int>,
    val remindOnDueDate: Boolean = true,
    @field:NotNull
    @field:DecimalMin("0.0", inclusive = true)
    val weeklyPenaltyAmount: BigDecimal,
    val currency: String? = "XAF",
    @field:Min(1)
    @field:Max(365)
    val maxInsolvencyGraceDays: Int,
    val exclusionEnabled: Boolean = true,
    @field:Min(0)
    @field:Max(60)
    val exclusionWarningDaysBefore: Int = 7,
    val active: Boolean = true,
    val schoolId: String? = null
)
