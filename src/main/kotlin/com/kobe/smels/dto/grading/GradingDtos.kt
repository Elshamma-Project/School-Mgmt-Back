package com.kobe.smels.dto.grading

import com.kobe.smels.model.grading.AssessmentType
import com.kobe.smels.model.grading.AuditAction
import com.kobe.smels.model.grading.AuditTarget
import com.kobe.smels.model.grading.GradeStatus
import com.kobe.smels.model.grading.MarkSource
import com.kobe.smels.model.grading.SessionStatus
import com.kobe.smels.model.grading.SheetStatus
import com.kobe.smels.model.grading.TotalizationFormula
import com.kobe.smels.model.user.Role
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
    val examDate: LocalDate?,
    val entryDeadline: LocalDate?,
    val status: SessionStatus,
    val openedAt: Instant?,
    val closedAt: Instant?
)

data class UpdateSessionExamDateRequest(
    @field:NotNull val examDate: LocalDate,
    val schoolId: String? = null
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
    val academicYearId: String,
    val classId: String,
    val classSubjectId: String,
    val sessionId: String,
    val sessionNumber: Int,
    val termNumber: Int,
    val value: BigDecimal?,
    val status: GradeStatus
)

data class ScoreBandCalcDto(
    val code: String,
    val labelFr: String? = null,
    val labelEn: String? = null,
    val minInclusive: BigDecimal,
    val maxExclusive: BigDecimal? = null
)

data class AppreciationScaleCalcDto(
    val code: String,
    val bands: List<ScoreBandCalcDto>
)

/** Barème pour calculs bulletin (reporting) — sans document Mongo. */
data class GradingSchemeCalcDto(
    val id: String,
    val schoolId: String,
    val code: String,
    val passMark: BigDecimal,
    val classSuccessThreshold: BigDecimal,
    val roundTermSubjectAverages: Boolean,
    val rankingPolicy: com.kobe.smels.model.grading.RankingPolicy,
    val effortSubjectThreshold: BigDecimal?,
    val appreciationScales: List<AppreciationScaleCalcDto>,
    val overallMentionBands: List<ScoreBandCalcDto>
)

data class EvaluationSessionSummaryDto(
    val id: String,
    val schoolId: String,
    val academicYearId: String,
    val number: Int,
    val termNumber: Int,
    val status: SessionStatus
)

data class CreateGradingSchemeRequest(
    @field:NotBlank val code: String,
    @field:NotNull val name: LocalizedTextDto,
    val schoolId: String? = null
)

data class ScoreBandWriteDto(
    @field:NotBlank val code: String,
    val label: LocalizedTextDto? = null,
    @field:NotNull val minInclusive: BigDecimal,
    val maxExclusive: BigDecimal? = null
)

data class AppreciationScaleWriteDto(
    @field:NotBlank val code: String,
    @field:Valid val bands: List<ScoreBandWriteDto> = emptyList()
)

data class UpdateGradingSchemeRequest(
    val name: LocalizedTextDto? = null,
    val maxScore: BigDecimal? = null,
    val passMark: BigDecimal? = null,
    val classSuccessThreshold: BigDecimal? = null,
    @field:Min(0) @field:Max(4) val decimals: Int? = null,
    val honourRollMinAverage: BigDecimal? = null,
    val honourRollBlockedBySanction: Boolean? = null,
    /** Passer une valeur pour définir ; omettre pour ne pas toucher. Null JSON explicite non supporté ici. */
    val effortSubjectThreshold: BigDecimal? = null,
    val clearEffortSubjectThreshold: Boolean = false,
    @field:Min(1) val reportSheetDeadlineHours: Int? = null,
    val reportSheetDeadlineBlocking: Boolean? = null,
    @field:Min(1) @field:Max(80) val reportSheetMaxRowsPerPage: Int? = null,
    val reportSheetAppreciationScaleCode: String? = null,
    /** Remplace entièrement les échelles si fourni. */
    @field:Valid val appreciationScales: List<AppreciationScaleWriteDto>? = null,
    /** Remplace entièrement les mentions générales si fourni. */
    @field:Valid val overallMentionBands: List<ScoreBandWriteDto>? = null,
    val active: Boolean? = null,
    val schoolId: String? = null
)

data class ScoreBandDto(
    val code: String,
    val label: LocalizedTextDto?,
    val minInclusive: BigDecimal,
    val maxExclusive: BigDecimal?
)

data class AppreciationScaleDto(
    val code: String,
    val bands: List<ScoreBandDto>
)

data class GradingSchemeResponse(
    val id: String,
    val schoolId: String,
    val code: String,
    val name: LocalizedTextDto,
    val maxScore: BigDecimal,
    val passMark: BigDecimal,
    val classSuccessThreshold: BigDecimal,
    val decimals: Int,
    val roundingPolicy: String,
    val roundTermSubjectAverages: Boolean,
    val honourRollMinAverage: BigDecimal,
    val honourRollBlockedBySanction: Boolean,
    val effortSubjectThreshold: BigDecimal?,
    val reportSheetDeadlineHours: Int,
    val reportSheetDeadlineBlocking: Boolean,
    val reportSheetMaxRowsPerPage: Int,
    val reportSheetAppreciationScaleCode: String,
    val appreciationScales: List<AppreciationScaleDto>,
    val overallMentionBands: List<ScoreBandDto>,
    val active: Boolean
)

data class GenerateReportSheetRequest(
    @field:NotBlank val classSubjectId: String,
    @field:Min(1) @field:Max(3) val termNumber: Int,
    /** Si true : noms seuls, cellules de notes vides (saisie manuscrite, Q-36). */
    val blank: Boolean = false,
    val schoolId: String? = null
)

data class RejectReportSheetRequest(
    @field:NotBlank val reason: String,
    val schoolId: String? = null
)

data class ReportSheetVisaDto(
    val userId: String,
    val at: Instant
)

data class ReportSheetSequenceCellDto(
    val mark: BigDecimal?,
    val markStatus: GradeStatus,
    val coefficient: BigDecimal?,
    val weightedMark: BigDecimal?,
    val rank: Int?,
    val appreciation: String?,
    val appreciationOverride: String?,
    val displayedAppreciation: String?
)

data class GradeReportSheetLineDto(
    val rollNumber: Int?,
    val studentId: String,
    val studentLabel: String,
    val sex: com.kobe.smels.model.shared.Sex?,
    val sequence1: ReportSheetSequenceCellDto,
    val sequence2: ReportSheetSequenceCellDto
)

data class GradeReportSheetResponse(
    val id: String,
    val schoolId: String,
    val academicYearId: String,
    val classId: String,
    val classSubjectId: String,
    val termNumber: Int,
    val teacherId: String?,
    val status: com.kobe.smels.model.grading.ReportSheetStatus,
    val teacherLabel: String?,
    val subjectLabel: LocalizedTextDto,
    val classLabel: String?,
    val coefficient: BigDecimal,
    val headcount: Int,
    val boysCount: Int,
    val girlsCount: Int,
    val sexCountWarning: Boolean,
    val sequence1ExamDate: java.time.LocalDate?,
    val sequence2ExamDate: java.time.LocalDate?,
    val sequence1Number: Int?,
    val sequence2Number: Int?,
    val appreciationScaleCode: String?,
    val deadlineWarning: Boolean = false,
    val lines: List<GradeReportSheetLineDto>,
    val submittedAt: Instant?,
    val visaSupervisor: ReportSheetVisaDto?,
    val visaPrincipal: ReportSheetVisaDto?,
    val rejectionReason: String?
)

data class GradeAuditEntryResponse(
    val id: String,
    val schoolId: String,
    val target: AuditTarget,
    val action: AuditAction,
    val classSubjectId: String,
    val sessionId: String?,
    val studentId: String?,
    val changedBy: String,
    val changedByRole: Role?,
    val changedAt: Instant,
    val reason: String?,
    val afterSessionClosed: Boolean,
    val previousValue: BigDecimal?,
    val newValue: BigDecimal?
)
