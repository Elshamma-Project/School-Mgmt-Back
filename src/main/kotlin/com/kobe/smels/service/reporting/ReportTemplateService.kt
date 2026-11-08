package com.kobe.smels.service.reporting

import com.kobe.smels.dto.reporting.LocalizedTextDto
import com.kobe.smels.dto.reporting.ReportTemplateResponse
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.reporting.ReportPeriodType
import com.kobe.smels.model.reporting.ReportTemplate
import com.kobe.smels.model.shared.Language
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.repository.reporting.ReportTemplateRepository
import com.kobe.smels.shared.security.CurrentUser
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal

@Service
class ReportTemplateService(
    private val reportTemplateRepository: ReportTemplateRepository
) {
    fun ensureDefaults(schoolId: String): Mono<Void> =
        ensureCode(
            schoolId = schoolId,
            code = CODE_ANNUAL,
            template = ReportTemplate(
                schoolId = schoolId,
                code = CODE_ANNUAL,
                name = LocalizedText(fr = "Bulletin annuel", en = "Annual report card"),
                periodType = ReportPeriodType.ANNUAL,
                languages = setOf(Language.FR, Language.EN),
                sequenceColumns = listOf(1, 2, 3, 4, 5, 6),
                appreciationScaleCode = "LETTER",
                showCompetency = false,
                showSubjectRank = false,
                showSubjectClassAverage = false,
                showSubjectPassedCount = false,
                showGroupTotals = true,
                showTermRecap = true,
                showClassProfile = true,
                showEffortSubjects = false,
                showTotalPointsOverMax = false,
                classProfileThreshold = BigDecimal("12"),
                minFontSizePt = 6.5,
                maxFontSizePt = 9.0,
                active = true
            )
        ).then(
            ensureCode(
                schoolId = schoolId,
                code = CODE_TERM,
                template = ReportTemplate(
                    schoolId = schoolId,
                    code = CODE_TERM,
                    name = LocalizedText(fr = "Bulletin de trimestre (compétences)", en = "Term competency report"),
                    periodType = ReportPeriodType.TERM,
                    languages = setOf(Language.FR, Language.EN),
                    sequenceColumns = listOf(1, 2),
                    appreciationScaleCode = "COMPETENCY",
                    showCompetency = true,
                    showSubjectRank = true,
                    showSubjectClassAverage = true,
                    showSubjectPassedCount = true,
                    showGroupTotals = true,
                    showTermRecap = false,
                    showClassProfile = true,
                    showEffortSubjects = true,
                    showTotalPointsOverMax = true,
                    classProfileThreshold = BigDecimal("10"),
                    legend = LocalizedText(
                        fr = "NA = non acquis ; ECA = en cours d'acquisition ; A = acquis ; A+ = parfaitement acquis",
                        en = "NA = not acquired ; ECA = being acquired ; A = acquired ; A+ = fully acquired"
                    ),
                    minFontSizePt = 6.5,
                    maxFontSizePt = 9.0,
                    active = true
                )
            )
        ).then()

    fun list(currentUser: CurrentUser, schoolIdOverride: String? = null): Flux<ReportTemplateResponse> {
        ReportingAccess.assertCanReadTemplates(currentUser)
        val schoolId = ReportingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return ensureDefaults(schoolId)
            .thenMany(reportTemplateRepository.findBySchoolId(schoolId))
            .map { toResponse(it) }
    }

    fun seedDefaults(currentUser: CurrentUser, schoolIdOverride: String? = null): Mono<Void> {
        ReportingAccess.assertDirector(currentUser)
        val schoolId = ReportingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return ensureDefaults(schoolId)
    }

    fun getByCode(schoolId: String, code: String): Mono<ReportTemplate> =
        reportTemplateRepository.findBySchoolIdAndCode(schoolId, code)
            .switchIfEmpty(
                ensureDefaults(schoolId).then(
                    reportTemplateRepository.findBySchoolIdAndCode(schoolId, code)
                )
            )
            .switchIfEmpty(Mono.error(NotFoundException("TEMPLATE_NOT_FOUND", "Report template not found")))

    fun resolveForPeriod(
        schoolId: String,
        periodType: ReportPeriodType,
        templateCode: String?
    ): Mono<ReportTemplate> {
        val code = templateCode?.trim()?.uppercase()
            ?: if (periodType == ReportPeriodType.TERM) CODE_TERM else CODE_ANNUAL
        return getByCode(schoolId, code)
    }

    private fun ensureCode(schoolId: String, code: String, template: ReportTemplate): Mono<ReportTemplate> =
        reportTemplateRepository.findBySchoolIdAndCode(schoolId, code)
            .switchIfEmpty(
                Mono.defer {
                    reportTemplateRepository.save(template)
                }
            )

    private fun toResponse(t: ReportTemplate) = ReportTemplateResponse(
        id = t.id!!,
        schoolId = t.schoolId,
        code = t.code,
        name = LocalizedTextDto(t.name.fr, t.name.en),
        periodType = t.periodType,
        sequenceColumns = t.sequenceColumns,
        appreciationScaleCode = t.appreciationScaleCode,
        minFontSizePt = t.minFontSizePt,
        maxFontSizePt = t.maxFontSizePt,
        active = t.active
    )

    companion object {
        const val CODE_ANNUAL = "ANNUAL_STANDARD"
        const val CODE_TERM = "TERM_COMPETENCY"
        const val CALCULATION_VERSION = "1.0"
    }
}
