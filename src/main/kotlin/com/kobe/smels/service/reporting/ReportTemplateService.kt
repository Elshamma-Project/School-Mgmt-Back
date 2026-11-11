package com.kobe.smels.service.reporting

import com.kobe.smels.dto.reporting.LocalizedTextDto
import com.kobe.smels.dto.reporting.ReportTemplateResponse
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.reporting.ReportPeriodType
import com.kobe.smels.model.reporting.ReportTemplate
import com.kobe.smels.model.shared.Language
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.repository.reporting.ReportTemplateRepository
import com.kobe.smels.service.academic.api.AcademicYearApi
import com.kobe.smels.service.academic.api.SchoolBrandApi
import com.kobe.smels.shared.security.CurrentUser
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal

@Service
class ReportTemplateService(
    private val reportTemplateRepository: ReportTemplateRepository,
    private val reportCardPdfService: ReportCardPdfService,
    private val schoolBrandApi: SchoolBrandApi,
    private val academicYearApi: AcademicYearApi,
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
                legend = LocalizedText(
                    fr = "A ≥ 16 · B+ ≥ 14 · B ≥ 12 · C < 12",
                    en = "A ≥ 16 · B+ ≥ 14 · B ≥ 12 · C < 12"
                ),
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
                    name = LocalizedText(fr = "Bulletin trimestriel", en = "Term report card"),
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
                        fr = "A+ parfaitement acquis · A acquis ≥ 14 · ECA en cours 10–14 · NA non acquis < 10",
                        en = "A+ perfectly acquired · A acquired ≥ 14 · ECA in progress 10–14 · NA not acquired < 10"
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

    fun getResponseByCode(
        currentUser: CurrentUser,
        code: String,
        schoolIdOverride: String? = null
    ): Mono<ReportTemplateResponse> {
        ReportingAccess.assertCanReadTemplates(currentUser)
        val schoolId = ReportingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return ensureDefaults(schoolId)
            .then(getByCode(schoolId, code.trim().uppercase()))
            .map { toResponse(it) }
    }

    /**
     * PDF d’exemple (données anonymisées) pour visualiser le gabarit A4 une page
     * sans générer un vrai bulletin élève.
     */
    fun renderSamplePdf(
        currentUser: CurrentUser,
        code: String,
        language: Language = Language.FR,
        schoolIdOverride: String? = null
    ): Mono<ByteArray> {
        ReportingAccess.assertCanReadTemplates(currentUser)
        val schoolId = ReportingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return ensureDefaults(schoolId)
            .then(getByCode(schoolId, code.trim().uppercase()))
            .flatMap { template ->
                Mono.zip(
                    schoolBrandApi.getBulletinBrand(schoolId)
                        .map { BulletinPdfContext.fromBrand(it) }
                        .onErrorReturn(BulletinPdfContext())
                        .defaultIfEmpty(BulletinPdfContext()),
                    academicYearApi.findCurrent(schoolId)
                        .map { it.label }
                        .onErrorReturn("—")
                        .defaultIfEmpty("—"),
                ).map { tuple ->
                    val card = SampleReportCardFactory.build(schoolId, template, language)
                    val ctx = tuple.t1.copy(
                        studentPhotoBytes = SampleReportCardFactory.loadSampleSchoolPhoto(),
                    )
                    reportCardPdfService.render(card, template, tuple.t2, context = ctx)
                }
            }
    }

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
            .flatMap { existing ->
                var dirty = false
                // Réaligner le libellé du gabarit trimestre (maquette KOBE).
                if (code == CODE_TERM &&
                    (existing.name.fr != template.name.fr || existing.name.en != template.name.en)
                ) {
                    existing.name = template.name
                    dirty = true
                }
                // Légende pied de page (maquette annuel / trimestre).
                if (existing.legend == null && template.legend != null) {
                    existing.legend = template.legend
                    dirty = true
                }
                if (dirty) reportTemplateRepository.save(existing) else Mono.just(existing)
            }
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
        showCompetency = t.showCompetency,
        showSubjectRank = t.showSubjectRank,
        showSubjectClassAverage = t.showSubjectClassAverage,
        showSubjectPassedCount = t.showSubjectPassedCount,
        showGroupTotals = t.showGroupTotals,
        showTermRecap = t.showTermRecap,
        showClassProfile = t.showClassProfile,
        showEffortSubjects = t.showEffortSubjects,
        showTotalPointsOverMax = t.showTotalPointsOverMax,
        classProfileThreshold = t.classProfileThreshold,
        legend = t.legend?.let { LocalizedTextDto(it.fr, it.en) },
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
