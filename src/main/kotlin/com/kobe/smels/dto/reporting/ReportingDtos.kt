package com.kobe.smels.dto.reporting

import com.kobe.smels.model.reporting.ReportCardStatus
import com.kobe.smels.model.reporting.ReportPeriodType
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
