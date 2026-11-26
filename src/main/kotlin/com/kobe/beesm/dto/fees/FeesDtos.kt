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
    val classId: String,
    @field:NotNull
    @field:DecimalMin("0.0", inclusive = true)
    val enrollmentFee: BigDecimal,
    @field:NotNull
    @field:Size(min = 3, max = 3)
    @field:Valid
    val installments: List<TuitionInstallmentAmountDto>,
    val currency: String? = "XAF",
    val schoolId: String? = null
)

data class TuitionInstallmentAmountDto(
    @field:Min(1)
    @field:Max(3)
    val index: Int,
    @field:NotNull
    @field:DecimalMin("0.0", inclusive = true)
    val amount: BigDecimal
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
    val classId: String,
    val classLabel: String,
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
    @field:Max(3)
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
    val payments: List<FeePaymentResponse>
)
