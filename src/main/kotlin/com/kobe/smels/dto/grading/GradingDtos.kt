package com.kobe.smels.dto.grading

import com.kobe.smels.model.grading.AssessmentType
import com.kobe.smels.model.grading.GradeStatus
import com.kobe.smels.model.grading.MarkSource
import com.kobe.smels.model.grading.SessionStatus
import com.kobe.smels.model.grading.SheetStatus
import com.kobe.smels.model.grading.TotalizationFormula
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

data class LocalizedTextDto(val fr: String? = null, val en: String? = null)

data class EnsureSessionsRequest(
    @field:NotBlank val academicYearId: String,
    val schoolId: String? = null
)

data class CloseSessionRequest(
    val closeNote: String? = null,
    val schoolId: String? = null
)

data class ReopenSessionRequest(
    @field:NotBlank val reason: String,
    val schoolId: String? = null
)

data class EvaluationSessionResponse(
    val id: String,
    val schoolId: String,
    val academicYearId: String,
    val number: Int,
    val termNumber: Int,
    val shortLabel: String,
    val name: LocalizedTextDto,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val entryDeadline: LocalDate?,
    val status: SessionStatus,
    val openedAt: Instant?,
    val closedAt: Instant?
)

data class TotalizationRuleDto(
    val formula: TotalizationFormula = TotalizationFormula.AVERAGE_PLUS_BONUS_MINUS_MALUS,
    val minScore: BigDecimal = BigDecimal.ZERO,
    val maxScore: BigDecimal = BigDecimal("20"),
    val capAtMax: Boolean = true
)

data class GradeSheetResponse(
    val id: String,
    val schoolId: String,
    val academicYearId: String,
    val classId: String,
    val classSubjectId: String,
    val sessionId: String,
    val sessionNumber: Int,
    val teacherId: String?,
    val status: SheetStatus,
    val totalization: TotalizationRuleDto,
    val lastTotalizedAt: Instant?
)

data class CreateAssessmentRequest(
    @field:NotBlank val sheetId: String,
    @field:NotBlank @field:Size(max = 120) val title: String,
    val type: AssessmentType = AssessmentType.SEQUENCE_MARK,
    @field:NotNull @field:DecimalMin("0.01") val maxScore: BigDecimal = BigDecimal("20"),
    @field:NotNull @field:DecimalMin("0.0") val weight: BigDecimal = BigDecimal.ONE,
    val date: LocalDate? = null,
    val published: Boolean = true,
    val schoolId: String? = null
)

data class UpdateAssessmentRequest(
    @field:NotBlank @field:Size(max = 120) val title: String,
    val type: AssessmentType = AssessmentType.SEQUENCE_MARK,
    @field:NotNull @field:DecimalMin("0.01") val maxScore: BigDecimal = BigDecimal("20"),
    @field:NotNull @field:DecimalMin("0.0") val weight: BigDecimal = BigDecimal.ONE,
    val date: LocalDate? = null,
    val published: Boolean = true
)

data class AssessmentResponse(
    val id: String,
    val schoolId: String,
    val sheetId: String?,
    val classId: String,
    val classSubjectId: String,
    val sessionId: String,
    val sessionNumber: Int,
    val title: String,
    val type: AssessmentType,
    val maxScore: BigDecimal,
    val weight: BigDecimal,
    val date: LocalDate?,
    val published: Boolean
)

data class UpsertGradeRequest(
    @field:NotBlank val assessmentId: String,
    @field:NotBlank val studentId: String,
    @field:NotNull val status: GradeStatus,
    val value: BigDecimal? = null,
    val comment: String? = null,
    /** Obligatoire si séquence CLOSED (BR-22). */
    val reason: String? = null,
    val schoolId: String? = null
)

data class BatchUpsertGradesRequest(
    @field:NotBlank val assessmentId: String,
    @field:Valid val grades: List<BatchGradeItem>,
    val reason: String? = null,
    val schoolId: String? = null
)

data class BatchGradeItem(
    @field:NotBlank val studentId: String,
    @field:NotNull val status: GradeStatus,
    val value: BigDecimal? = null,
    val comment: String? = null
)

data class GradeResponse(
    val id: String,
    val schoolId: String,
    val assessmentId: String,
    val studentId: String,
    val enrollmentId: String,
    val classId: String,
    val classSubjectId: String,
    val sessionId: String,
    val value: BigDecimal?,
    val status: GradeStatus,
    val comment: String?
)

data class SequenceMarkResponse(
    val id: String,
    val schoolId: String,
    val sheetId: String,
    val studentId: String,
    val classSubjectId: String,
    val sessionId: String,
    val sessionNumber: Int,
    val termNumber: Int,
    val value: BigDecimal?,
    val status: GradeStatus,
    val source: MarkSource,
    val computedAt: Instant?
)

data class SequenceMarkSummaryDto(
    val id: String,
    val schoolId: String,
    val studentId: String,
    val classSubjectId: String,
    val sessionId: String,
    val sessionNumber: Int,
    val termNumber: Int,
    val value: BigDecimal?,
    val status: GradeStatus
)

data class CreateGradingSchemeRequest(
    @field:NotBlank val code: String,
    @field:NotNull val name: LocalizedTextDto,
    val schoolId: String? = null
)

data class GradingSchemeResponse(
    val id: String,
    val schoolId: String,
    val code: String,
    val name: LocalizedTextDto,
    val maxScore: BigDecimal,
    val passMark: BigDecimal,
    val decimals: Int,
    val active: Boolean
)
