package com.kobe.smels.dto.reporting

import com.kobe.smels.model.grading.DistinctionLevel
import com.kobe.smels.model.reporting.CouncilDecision
import com.kobe.smels.model.reporting.ReportCardStatus
import com.kobe.smels.model.reporting.ReportPeriodType
import com.kobe.smels.model.reporting.SanctionLevel
import com.kobe.smels.model.shared.Language
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant

data class LocalizedTextDto(val fr: String? = null, val en: String? = null)

data class GenerateReportCardsRequest(
    @field:NotBlank val classId: String,
    @field:NotNull val periodType: ReportPeriodType,
    val periodNumber: Int? = null,
    val language: Language? = null,
    val templateCode: String? = null,
    val schoolId: String? = null
)

data class ReportCardSummaryResponse(
    val id: String,
    val schoolId: String,
    val studentId: String,
    val classId: String,
    val periodType: ReportPeriodType,
    val periodNumber: Int?,
    val revision: Int,
    val status: ReportCardStatus,
    val language: Language,
    val verificationCode: String,
    val average: BigDecimal?,
    val rank: Int?,
    val generatedAt: Instant
)

data class ReportCardResponse(
    val id: String,
    val schoolId: String,
    val studentId: String,
    val classId: String,
    val periodType: ReportPeriodType,
    val periodNumber: Int?,
    val revision: Int,
    val status: ReportCardStatus,
    val language: Language,
    val verificationCode: String,
    val average: BigDecimal?,
    val rank: Int?,
    val rankedCount: Int,
    val templateCode: String?,
    val generatedAt: Instant
)

data class ReportTemplateResponse(
    val id: String,
    val schoolId: String,
    val code: String,
    val name: LocalizedTextDto,
    val periodType: ReportPeriodType,
    val sequenceColumns: List<Int>,
    val appreciationScaleCode: String,
    val minFontSizePt: Double,
    val maxFontSizePt: Double,
    val active: Boolean
)

data class VerificationResponse(
    val valid: Boolean,
    val className: String? = null,
    val academicYearLabel: String? = null,
    val issuedAt: Instant? = null
)

data class UpsertStudentPeriodReviewRequest(
    @field:NotBlank val enrollmentId: String,
    @field:NotBlank val studentId: String,
    @field:NotBlank val classId: String,
    @field:NotBlank val academicYearId: String,
    @field:NotNull val periodType: ReportPeriodType,
    val periodNumber: Int? = null,
    val absenceHoursTotal: Int = 0,
    val absenceHoursUnjustified: Int = 0,
    val lateCount: Int = 0,
    val absenceDays: Int? = null,
    val distinction: DistinctionLevel = DistinctionLevel.NONE,
    val workSanction: SanctionLevel = SanctionLevel.NONE,
    val conductSanction: SanctionLevel = SanctionLevel.NONE,
    val councilDecision: CouncilDecision? = null,
    val councilComment: String? = null,
    val schoolId: String? = null
)

data class StudentPeriodReviewResponse(
    val id: String,
    val schoolId: String,
    val academicYearId: String,
    val enrollmentId: String,
    val studentId: String,
    val classId: String,
    val periodType: ReportPeriodType,
    val periodNumber: Int?,
    val absenceHoursTotal: Int,
    val absenceHoursUnjustified: Int,
    val lateCount: Int,
    val absenceDays: Int?,
    val distinction: DistinctionLevel,
    val workSanction: SanctionLevel,
    val conductSanction: SanctionLevel,
    val councilDecision: CouncilDecision?,
    val councilComment: String?
)
