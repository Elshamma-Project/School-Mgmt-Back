package com.kobe.smels.configuration.seed

import com.kobe.smels.model.academic.AcademicYear
import com.kobe.smels.model.academic.AcademicYearStatus
import com.kobe.smels.model.academic.ClassSubject
import com.kobe.smels.model.academic.Level
import com.kobe.smels.model.academic.OfficialHeader
import com.kobe.smels.model.academic.School
import com.kobe.smels.model.academic.SchoolClass
import com.kobe.smels.model.academic.Subject
import com.kobe.smels.model.academic.SubjectGroup
import com.kobe.smels.model.academic.SubjectKind
import com.kobe.smels.model.academic.Term
import com.kobe.smels.model.grading.AbsencePolicy
import com.kobe.smels.model.grading.AnnualAveragePolicy
import com.kobe.smels.model.grading.AppreciationScale
import com.kobe.smels.model.grading.DistinctionLevel
import com.kobe.smels.model.grading.DistinctionRule
import com.kobe.smels.model.grading.EvaluationSession
import com.kobe.smels.model.grading.GradingScheme
import com.kobe.smels.model.grading.MissingMarkPolicy
import com.kobe.smels.model.grading.RankingEligibility
import com.kobe.smels.model.grading.RankingPolicy
import com.kobe.smels.model.grading.RoundingPolicy
import com.kobe.smels.model.grading.ScoreBand
import com.kobe.smels.model.grading.SessionStatus
import com.kobe.smels.model.grading.SubjectYearAveragePolicy
import com.kobe.smels.model.reporting.ReportPeriodType
import com.kobe.smels.model.reporting.ReportTemplate
import com.kobe.smels.model.shared.ContactInfo
import com.kobe.smels.model.shared.Cycle
import com.kobe.smels.model.shared.Language
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.PageFormat
import com.kobe.smels.model.shared.Section
import com.kobe.smels.repository.academic.AcademicYearRepository
import com.kobe.smels.repository.academic.ClassSubjectRepository
import com.kobe.smels.repository.academic.LevelRepository
import com.kobe.smels.repository.academic.SchoolClassRepository
import com.kobe.smels.repository.academic.SchoolRepository
import com.kobe.smels.repository.academic.SubjectGroupRepository
import com.kobe.smels.repository.academic.SubjectRepository
import com.kobe.smels.repository.grading.EvaluationSessionRepository
import com.kobe.smels.repository.grading.GradingSchemeRepository
import com.kobe.smels.repository.reporting.ReportTemplateRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.annotation.Order
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Charge la structure académique El Shammah depuis le JSON au démarrage.
 *
 * Ordre 15 : avant [com.kobe.smels.configuration.MockUsersBootstrapRunner] (20),
 * pour que les comptes mock s’appuient sur l’école réelle plutôt que sur le mini jeu démo.
 */
@Component
@Order(15)
@EnableConfigurationProperties(ElShammahSeedProperties::class)
class ElShammahAcademicSeedRunner(
    private val properties: ElShammahSeedProperties,
    private val resourceLoader: ResourceLoader,
    private val objectMapper: ObjectMapper,
    private val schoolRepository: SchoolRepository,
    private val academicYearRepository: AcademicYearRepository,
    private val levelRepository: LevelRepository,
    private val subjectGroupRepository: SubjectGroupRepository,
    private val subjectRepository: SubjectRepository,
    private val gradingSchemeRepository: GradingSchemeRepository,
    private val reportTemplateRepository: ReportTemplateRepository,
    private val evaluationSessionRepository: EvaluationSessionRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val classSubjectRepository: ClassSubjectRepository
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (!properties.enabled) {
            log.info("El Shammah academic seed skipped (smels.seed.el-shammah.enabled=false)")
            return
        }

        try {
            val seed = loadSeedFile()
            seedAll(seed)
                .doOnSuccess {
                    log.info(
                        "El Shammah academic seed done schoolId={} overwrite={}",
                        properties.schoolId,
                        properties.overwriteExisting
                    )
                }
                .doOnError { error ->
                    log.error("El Shammah academic seed failed: {}", error.message, error)
                }
                .block()
        } catch (error: Exception) {
            log.error("El Shammah academic seed aborted: {}", error.message, error)
        }
    }

    private fun loadSeedFile(): ElShammahSeedFile {
        val resource = resourceLoader.getResource(properties.resource)
        require(resource.exists()) {
            "Seed resource not found: ${properties.resource}"
        }
        resource.inputStream.use { input ->
            return objectMapper.readValue(input, ElShammahSeedFile::class.java)
        }
    }

    private fun seedAll(seed: ElShammahSeedFile): Mono<Void> {
        val schoolSeed = seed.academic_schools.firstOrNull()
            ?: return Mono.error(IllegalStateException("academic_schools is empty"))

        return ensureSchool(schoolSeed).flatMap { school ->
            val schoolId = requireNotNull(school.id) { "School.id missing after seed" }

            val yearIds = mutableMapOf<String, String>()
            val levelIds = mutableMapOf<String, String>()
            val groupIds = mutableMapOf<String, String>()
            val subjectIds = mutableMapOf<String, String>()
            val schemeIds = mutableMapOf<String, String>()
            val classIds = mutableMapOf<String, String>() // key = "$yearLabel|$className"

            ensureYears(schoolId, seed.academic_years, yearIds)
                .then(ensureLevels(schoolId, seed.academic_levels, levelIds))
                .then(ensureGroups(schoolId, seed.academic_subject_groups, groupIds))
                .then(ensureSubjects(schoolId, seed.academic_subjects, subjectIds))
                .then(ensureSchemes(schoolId, seed.grading_schemes, schemeIds))
                .then(ensureTemplates(schoolId, seed.reporting_templates))
                .then(ensureSessions(schoolId, seed.grading_sessions, yearIds))
                .then(ensureClasses(schoolId, seed.academic_classes, yearIds, levelIds, schemeIds, classIds))
                .then(
                    ensureClassSubjects(
                        schoolId,
                        seed.academic_class_subjects,
                        yearIds,
                        groupIds,
                        subjectIds,
                        classIds
                    )
                )
        }
    }

    // ── School ──────────────────────────────────────────────────────────────

    private fun ensureSchool(seed: SeedSchool): Mono<School> {
        val configuredId = properties.schoolId.trim()
        return schoolRepository.findById(configuredId)
            .switchIfEmpty(schoolRepository.findByCode(seed.code))
            .switchIfEmpty(
                // Bases locales antérieures (id / code « demo »).
                if (seed.code == "ELSHAMMAH") {
                    schoolRepository.findById(LEGACY_DEMO_SCHOOL_ID)
                        .switchIfEmpty(schoolRepository.findByCode(LEGACY_DEMO_SCHOOL_CODE))
                } else {
                    Mono.empty()
                }
            )
            .flatMap { existing ->
                if (existing.id != configuredId) {
                    log.warn(
                        "Reusing legacy tenant id={} (canonical={}) — wipe Mongo or set school-id to match",
                        existing.id,
                        configuredId
                    )
                }
                if (!properties.overwriteExisting) {
                    // Migration douce du code démo → code officiel (une seule fois).
                    if (existing.code == LEGACY_DEMO_SCHOOL_CODE && seed.code == "ELSHAMMAH") {
                        existing.code = seed.code
                        return@flatMap schoolRepository.save(existing)
                    }
                    Mono.just(existing)
                } else {
                    applySchool(existing, seed, configuredId)
                    schoolRepository.save(existing)
                }
            }
            .switchIfEmpty(
                Mono.defer {
                    val created = School(
                        id = configuredId,
                        code = seed.code,
                        name = loc(seed.name),
                        shortName = seed.shortName,
                        officialHeaders = seed.officialHeaders.mapNotNull { toOfficialHeader(it) },
                        contact = ContactInfo(
                            phones = seed.contact.phones,
                            email = seed.contact.email,
                            address = seed.contact.address,
                            city = seed.contact.city
                        ),
                        postalBox = seed.postalBox,
                        signaturePlace = seed.signaturePlace,
                        headTitle = seed.headTitle?.let { loc(it) },
                        reportCardNotice = seed.reportCardNotice?.let { loc(it) },
                        reportCardDisclaimer = seed.reportCardDisclaimer?.let { loc(it) },
                        timezone = seed.timezone ?: "Africa/Douala",
                        active = seed.active
                    )
                    schoolRepository.save(created)
                }
            )
    }

    private fun applySchool(target: School, seed: SeedSchool, configuredId: String) {
        if (target.id.isNullOrBlank()) target.id = configuredId
        target.code = seed.code
        target.name = loc(seed.name)
        target.shortName = seed.shortName
        target.officialHeaders = seed.officialHeaders.mapNotNull { toOfficialHeader(it) }
        target.contact = ContactInfo(
            phones = seed.contact.phones,
            email = seed.contact.email,
            address = seed.contact.address,
            city = seed.contact.city
        )
        target.postalBox = seed.postalBox
        target.signaturePlace = seed.signaturePlace
        target.headTitle = seed.headTitle?.let { loc(it) }
        target.reportCardNotice = seed.reportCardNotice?.let { loc(it) }
        target.reportCardDisclaimer = seed.reportCardDisclaimer?.let { loc(it) }
        target.timezone = seed.timezone ?: target.timezone
        target.active = seed.active
    }

    private fun toOfficialHeader(seed: SeedOfficialHeader): OfficialHeader? {
        val republic = seed.republic ?: return null
        val motto = seed.motto ?: return null
        val ministry = seed.ministry ?: return null
        return OfficialHeader(
            cycles = seed.cycles.mapNotNull { enumOrNull<Cycle>(it) }.toSet(),
            republic = loc(republic),
            motto = loc(motto),
            ministry = loc(ministry),
            regionalDelegation = seed.regionalDelegation?.let { loc(it) },
            divisionalDelegation = seed.divisionalDelegation?.let { loc(it) }
        )
    }

    // ── Years ───────────────────────────────────────────────────────────────

    private fun ensureYears(
        schoolId: String,
        rows: List<SeedYear>,
        outIds: MutableMap<String, String>
    ): Mono<Void> =
        Flux.fromIterable(rows).concatMap { row ->
            academicYearRepository.findBySchoolIdAndLabel(schoolId, row.label)
                .flatMap { existing ->
                    outIds[row.label] = requireNotNull(existing.id)
                    if (properties.overwriteExisting) {
                        applyYear(existing, schoolId, row)
                        academicYearRepository.save(existing)
                    } else {
                        Mono.just(existing)
                    }
                }
                .switchIfEmpty(
                    Mono.defer {
                        val created = AcademicYear(
                            schoolId = schoolId,
                            label = row.label,
                            startDate = date(row.startDate),
                            endDate = date(row.endDate),
                            current = row.current,
                            status = enumValueOf<AcademicYearStatus>(row.status),
                            terms = row.terms.map { t ->
                                Term(
                                    number = t.number,
                                    name = loc(t.name),
                                    startDate = date(t.startDate),
                                    endDate = date(t.endDate)
                                )
                            }
                        )
                        academicYearRepository.save(created)
                            .doOnNext { saved -> outIds[row.label] = requireNotNull(saved.id) }
                    }
                )
        }.then()
            .then(
                // Une seule année current=true : respecter le JSON au premier seed uniquement
                // (si overwrite) ; sinon ne pas toucher aux flags déjà en base.
                if (!properties.overwriteExisting) {
                    Mono.empty()
                } else {
                    val currentLabel = rows.firstOrNull { it.current }?.label
                    if (currentLabel == null) Mono.empty()
                    else {
                        val currentId = outIds[currentLabel]
                        academicYearRepository.findBySchoolIdOrderByStartDateDesc(schoolId)
                            .filter { it.id != currentId && it.current }
                            .concatMap { other ->
                                other.current = false
                                academicYearRepository.save(other)
                            }
                            .then()
                    }
                }
            )

    private fun applyYear(target: AcademicYear, schoolId: String, row: SeedYear) {
        target.schoolId = schoolId
        target.label = row.label
        target.startDate = date(row.startDate)
        target.endDate = date(row.endDate)
        target.current = row.current
        target.status = enumValueOf(row.status)
        target.terms = row.terms.map { t ->
            Term(
                number = t.number,
                name = loc(t.name),
                startDate = date(t.startDate),
                endDate = date(t.endDate)
            )
        }
    }

    // ── Levels / groups / subjects ──────────────────────────────────────────

    private fun ensureLevels(
        schoolId: String,
        rows: List<SeedLevel>,
        outIds: MutableMap<String, String>
    ): Mono<Void> =
        Flux.fromIterable(rows).concatMap { row ->
            levelRepository.findBySchoolIdAndCode(schoolId, row.code)
                .flatMap { existing ->
                    outIds[row.code] = requireNotNull(existing.id)
                    if (properties.overwriteExisting) {
                        applyLevel(existing, schoolId, row)
                        levelRepository.save(existing)
                    } else Mono.just(existing)
                }
                .switchIfEmpty(
                    Mono.defer {
                        levelRepository.save(
                            Level(
                                schoolId = schoolId,
                                code = row.code,
                                name = loc(row.name),
                                cycle = enumValueOf(row.cycle),
                                section = enumValueOf(row.section),
                                order = row.order,
                                equivalenceIndex = row.equivalenceIndex,
                                nextLevelCode = row.nextLevelCode,
                                active = row.active
                            )
                        ).doOnNext { saved -> outIds[row.code] = requireNotNull(saved.id) }
                    }
                )
        }.then()

    private fun applyLevel(target: Level, schoolId: String, row: SeedLevel) {
        target.schoolId = schoolId
        target.code = row.code
        target.name = loc(row.name)
        target.cycle = enumValueOf(row.cycle)
        target.section = enumValueOf(row.section)
        target.order = row.order
        target.equivalenceIndex = row.equivalenceIndex
        target.nextLevelCode = row.nextLevelCode
        target.active = row.active
    }

    private fun ensureGroups(
        schoolId: String,
        rows: List<SeedSubjectGroup>,
        outIds: MutableMap<String, String>
    ): Mono<Void> =
        Flux.fromIterable(rows).concatMap { row ->
            subjectGroupRepository.findBySchoolIdAndCode(schoolId, row.code)
                .flatMap { existing ->
                    outIds[row.code] = requireNotNull(existing.id)
                    if (properties.overwriteExisting) {
                        existing.name = loc(row.name)
                        existing.displayOrder = row.displayOrder
                        existing.active = row.active
                        subjectGroupRepository.save(existing)
                    } else Mono.just(existing)
                }
                .switchIfEmpty(
                    Mono.defer {
                        subjectGroupRepository.save(
                            SubjectGroup(
                                schoolId = schoolId,
                                code = row.code,
                                name = loc(row.name),
                                displayOrder = row.displayOrder,
                                active = row.active
                            )
                        ).doOnNext { saved -> outIds[row.code] = requireNotNull(saved.id) }
                    }
                )
        }.then()

    private fun ensureSubjects(
        schoolId: String,
        rows: List<SeedSubject>,
        outIds: MutableMap<String, String>
    ): Mono<Void> =
        Flux.fromIterable(rows).concatMap { row ->
            subjectRepository.findBySchoolIdAndCode(schoolId, row.code)
                .flatMap { existing ->
                    outIds[row.code] = requireNotNull(existing.id)
                    if (properties.overwriteExisting) {
                        applySubject(existing, schoolId, row)
                        subjectRepository.save(existing)
                    } else Mono.just(existing)
                }
                .switchIfEmpty(
                    Mono.defer {
                        subjectRepository.save(
                            Subject(
                                schoolId = schoolId,
                                code = row.code,
                                name = loc(row.name),
                                shortName = row.shortName?.let { loc(it) },
                                timetableAlias = row.timetableAlias,
                                kind = enumValueOf(row.kind),
                                teachingLanguage = row.teachingLanguage?.let { enumOrNull<Language>(it) },
                                active = row.active
                            )
                        ).doOnNext { saved -> outIds[row.code] = requireNotNull(saved.id) }
                    }
                )
        }.then()

    private fun applySubject(target: Subject, schoolId: String, row: SeedSubject) {
        target.schoolId = schoolId
        target.code = row.code
        target.name = loc(row.name)
        target.shortName = row.shortName?.let { loc(it) }
        target.timetableAlias = row.timetableAlias
        target.kind = enumValueOf(row.kind)
        target.teachingLanguage = row.teachingLanguage?.let { enumOrNull<Language>(it) }
        target.active = row.active
    }

    // ── Grading schemes / templates / sessions ──────────────────────────────

    private fun ensureSchemes(
        schoolId: String,
        rows: List<SeedGradingScheme>,
        outIds: MutableMap<String, String>
    ): Mono<Void> =
        Flux.fromIterable(rows).concatMap { row ->
            gradingSchemeRepository.findBySchoolIdAndCode(schoolId, row.code)
                .flatMap { existing ->
                    outIds[row.code] = requireNotNull(existing.id)
                    if (properties.overwriteExisting) {
                        applyScheme(existing, schoolId, row)
                        gradingSchemeRepository.save(existing)
                    } else Mono.just(existing)
                }
                .switchIfEmpty(
                    Mono.defer {
                        val created = GradingScheme(
                            schoolId = schoolId,
                            code = row.code,
                            name = loc(row.name)
                        )
                        applyScheme(created, schoolId, row)
                        gradingSchemeRepository.save(created)
                            .doOnNext { saved -> outIds[row.code] = requireNotNull(saved.id) }
                    }
                )
        }.then()

    private fun applyScheme(target: GradingScheme, schoolId: String, row: SeedGradingScheme) {
        target.schoolId = schoolId
        target.code = row.code
        target.name = loc(row.name)
        target.cycles = row.cycles.mapNotNull { enumOrNull<Cycle>(it) }.toSet()
        target.sections = row.sections.mapNotNull { enumOrNull<Section>(it) }.toSet()
        target.maxScore = decimal(row.maxScore)
        target.passMark = decimal(row.passMark)
        target.classSuccessThreshold = decimal(row.classSuccessThreshold)
        target.decimals = row.decimals
        target.roundingPolicy = enumValueOf(row.roundingPolicy)
        target.roundTermSubjectAverages = row.roundTermSubjectAverages
        target.missingMarkPolicy = enumValueOf(row.missingMarkPolicy)
        target.absencePolicy = enumValueOf(row.absencePolicy)
        target.rankingPolicy = enumValueOf(row.rankingPolicy)
        target.rankingEligibility = enumValueOf(row.rankingEligibility)
        target.annualAveragePolicy = enumValueOf(row.annualAveragePolicy)
        target.subjectYearAveragePolicy = enumValueOf(row.subjectYearAveragePolicy)
        target.appreciationScales = row.appreciationScales.map { scale ->
            AppreciationScale(
                code = scale.code,
                bands = scale.bands.map { toScoreBand(it) }
            )
        }
        target.overallMentionBands = row.overallMentionBands.map { toScoreBand(it) }
        target.honourRollMinAverage = decimal(row.honourRollMinAverage)
        target.honourRollBlockedBySanction = row.honourRollBlockedBySanction
        target.effortSubjectThreshold = row.effortSubjectThreshold?.let { decimal(it) }
        target.distinctionRules = row.distinctionRules.map {
            DistinctionRule(
                level = enumValueOf<DistinctionLevel>(it.level),
                minAverage = decimal(it.minAverage)
            )
        }
        target.active = row.active
    }

    private fun toScoreBand(seed: SeedScoreBand): ScoreBand =
        ScoreBand(
            code = seed.code,
            label = seed.label?.let { loc(it) },
            minInclusive = decimal(seed.minInclusive),
            maxExclusive = seed.maxExclusive?.let { decimal(it) }
        )

    private fun ensureTemplates(schoolId: String, rows: List<SeedReportTemplate>): Mono<Void> =
        Flux.fromIterable(rows).concatMap { row ->
            reportTemplateRepository.findBySchoolIdAndCode(schoolId, row.code)
                .flatMap { existing ->
                    if (properties.overwriteExisting) {
                        applyTemplate(existing, schoolId, row)
                        reportTemplateRepository.save(existing)
                    } else Mono.just(existing)
                }
                .switchIfEmpty(
                    Mono.defer {
                        val created = ReportTemplate(
                            schoolId = schoolId,
                            code = row.code,
                            name = loc(row.name),
                            periodType = enumValueOf(row.periodType)
                        )
                        applyTemplate(created, schoolId, row)
                        reportTemplateRepository.save(created)
                    }
                )
        }.then()

    private fun applyTemplate(target: ReportTemplate, schoolId: String, row: SeedReportTemplate) {
        target.schoolId = schoolId
        target.code = row.code
        target.name = loc(row.name)
        target.periodType = enumValueOf(row.periodType)
        target.cycles = row.cycles.mapNotNull { enumOrNull<Cycle>(it) }.toSet()
        target.sections = row.sections.mapNotNull { enumOrNull<Section>(it) }.toSet()
        target.levelCodes = row.levelCodes.toSet()
        target.languages = row.languages.mapNotNull { enumOrNull<Language>(it) }.toSet()
            .ifEmpty { setOf(Language.FR, Language.EN) }
        target.page = enumOrNull<PageFormat>(row.page) ?: PageFormat.A4_PORTRAIT
        target.sequenceColumns = row.sequenceColumns
        target.appreciationScaleCode = row.appreciationScaleCode
        target.showCompetency = row.showCompetency
        target.showSubjectRank = row.showSubjectRank
        target.showSubjectClassAverage = row.showSubjectClassAverage
        target.showSubjectPassedCount = row.showSubjectPassedCount
        target.showGroupTotals = row.showGroupTotals
        target.showTermRecap = row.showTermRecap
        target.showClassProfile = row.showClassProfile
        target.showEffortSubjects = row.showEffortSubjects
        target.showTotalPointsOverMax = row.showTotalPointsOverMax
        target.classProfileThreshold = row.classProfileThreshold?.let { decimal(it) }
        target.legend = row.legend?.let { loc(it) }
        target.minFontSizePt = row.minFontSizePt
        target.maxFontSizePt = row.maxFontSizePt
        target.active = row.active
    }

    private fun ensureSessions(
        schoolId: String,
        rows: List<SeedSession>,
        yearIds: Map<String, String>
    ): Mono<Void> =
        Flux.fromIterable(rows).concatMap { row ->
            val yearId = yearIds[row.academicYearLabel]
            if (yearId == null) {
                log.warn(
                    "Skip grading session DEV{}: year {} not resolved",
                    row.number,
                    row.academicYearLabel
                )
                return@concatMap Mono.empty<EvaluationSession>()
            }
            evaluationSessionRepository.findBySchoolIdAndAcademicYearIdAndNumber(schoolId, yearId, row.number)
                .flatMap { existing ->
                    if (properties.overwriteExisting) {
                        applySession(existing, schoolId, yearId, row)
                        evaluationSessionRepository.save(existing)
                    } else Mono.just(existing)
                }
                .switchIfEmpty(
                    Mono.defer {
                        val created = EvaluationSession(
                            schoolId = schoolId,
                            academicYearId = yearId,
                            number = row.number,
                            termNumber = row.termNumber,
                            shortLabel = row.shortLabel,
                            name = loc(row.name)
                        )
                        applySession(created, schoolId, yearId, row)
                        evaluationSessionRepository.save(created)
                    }
                )
        }.then()

    private fun applySession(
        target: EvaluationSession,
        schoolId: String,
        yearId: String,
        row: SeedSession
    ) {
        target.schoolId = schoolId
        target.academicYearId = yearId
        target.number = row.number
        target.termNumber = row.termNumber
        target.shortLabel = row.shortLabel
        target.name = loc(row.name)
        target.startDate = row.startDate?.let { date(it) }
        target.endDate = row.endDate?.let { date(it) }
        target.entryDeadline = row.entryDeadline?.let { date(it) }
        target.status = enumValueOf(row.status)
    }

    // ── Classes / class subjects ────────────────────────────────────────────

    private fun ensureClasses(
        schoolId: String,
        rows: List<SeedClass>,
        yearIds: Map<String, String>,
        levelIds: Map<String, String>,
        schemeIds: Map<String, String>,
        outIds: MutableMap<String, String>
    ): Mono<Void> =
        Flux.fromIterable(rows).concatMap { row ->
            val yearId = yearIds[row.academicYearLabel]
            val levelId = levelIds[row.levelCode]
            if (yearId == null || levelId == null) {
                log.warn(
                    "Skip class '{}': year={} level={} unresolved",
                    row.name,
                    row.academicYearLabel,
                    row.levelCode
                )
                return@concatMap Mono.empty<SchoolClass>()
            }
            val key = classKey(row.academicYearLabel, row.name)
            schoolClassRepository.findBySchoolIdAndAcademicYearIdAndName(schoolId, yearId, row.name)
                .flatMap { existing ->
                    outIds[key] = requireNotNull(existing.id)
                    if (properties.overwriteExisting) {
                        applyClass(existing, schoolId, yearId, levelId, schemeIds, row)
                        schoolClassRepository.save(existing)
                    } else Mono.just(existing)
                }
                .switchIfEmpty(
                    Mono.defer {
                        val created = SchoolClass(
                            schoolId = schoolId,
                            academicYearId = yearId,
                            levelId = levelId,
                            levelCode = row.levelCode,
                            section = enumValueOf(row.section),
                            cycle = enumValueOf(row.cycle),
                            name = row.name,
                            trackCode = row.trackCode,
                            capacity = row.capacity,
                            defaultLanguage = enumValueOf(row.defaultLanguage),
                            availableLanguages = row.availableLanguages
                                .mapNotNull { enumOrNull<Language>(it) }
                                .toSet()
                                .ifEmpty { setOf(Language.FR, Language.EN) },
                            gradingSchemeId = row.gradingSchemeCode?.let { schemeIds[it] },
                            active = row.active
                        )
                        schoolClassRepository.save(created)
                            .doOnNext { saved -> outIds[key] = requireNotNull(saved.id) }
                    }
                )
        }.then()

    private fun applyClass(
        target: SchoolClass,
        schoolId: String,
        yearId: String,
        levelId: String,
        schemeIds: Map<String, String>,
        row: SeedClass
    ) {
        target.schoolId = schoolId
        target.academicYearId = yearId
        target.levelId = levelId
        target.levelCode = row.levelCode
        target.section = enumValueOf(row.section)
        target.cycle = enumValueOf(row.cycle)
        target.name = row.name
        target.trackCode = row.trackCode
        target.capacity = row.capacity
        target.defaultLanguage = enumValueOf(row.defaultLanguage)
        target.availableLanguages = row.availableLanguages
            .mapNotNull { enumOrNull<Language>(it) }
            .toSet()
            .ifEmpty { setOf(Language.FR, Language.EN) }
        target.gradingSchemeId = row.gradingSchemeCode?.let { schemeIds[it] }
        target.active = row.active
    }

    private fun ensureClassSubjects(
        schoolId: String,
        rows: List<SeedClassSubject>,
        yearIds: Map<String, String>,
        groupIds: Map<String, String>,
        subjectIds: Map<String, String>,
        classIds: Map<String, String>
    ): Mono<Void> =
        Flux.fromIterable(rows).concatMap { row ->
            val yearId = yearIds[row.academicYearLabel]
            val classId = classIds[classKey(row.academicYearLabel, row.className)]
            val subjectId = subjectIds[row.subjectCode]
            val groupId = groupIds[row.groupCode]
            val coefficient = resolveCoefficient(row)

            if (yearId == null || classId == null || subjectId == null || groupId == null || coefficient == null) {
                log.warn(
                    "Skip classSubject {}/{}: unresolved refs or coefficient (year={} class={} subject={} group={} coeff={})",
                    row.className,
                    row.subjectCode,
                    row.academicYearLabel,
                    classId != null,
                    subjectId != null,
                    groupId != null,
                    coefficient
                )
                return@concatMap Mono.empty<ClassSubject>()
            }

            classSubjectRepository.findBySchoolIdAndClassIdAndSubjectId(schoolId, classId, subjectId)
                .flatMap { existing ->
                    if (properties.overwriteExisting) {
                        applyClassSubject(existing, schoolId, yearId, classId, subjectId, groupId, coefficient, row)
                        classSubjectRepository.save(existing)
                    } else Mono.just(existing)
                }
                .switchIfEmpty(
                    Mono.defer {
                        val created = ClassSubject(
                            schoolId = schoolId,
                            academicYearId = yearId,
                            classId = classId,
                            subjectId = subjectId,
                            groupId = groupId,
                            coefficient = coefficient
                        )
                        applyClassSubject(created, schoolId, yearId, classId, subjectId, groupId, coefficient, row)
                        classSubjectRepository.save(created)
                    }
                )
        }.then()

    private fun resolveCoefficient(row: SeedClassSubject): BigDecimal? {
        val raw = row.coefficient?.trim()?.takeIf { it.isNotEmpty() }
            ?: row.coefficientHypothesis?.trim()?.takeIf { it.isNotEmpty() }?.also {
                log.warn(
                    "ClassSubject {}/{} : coefficient null → hypothèse {} (Q-42)",
                    row.className,
                    row.subjectCode,
                    it
                )
            }
        return raw?.let { decimal(it) }?.takeIf { it.compareTo(BigDecimal.ZERO) > 0 }
    }

    private fun applyClassSubject(
        target: ClassSubject,
        schoolId: String,
        yearId: String,
        classId: String,
        subjectId: String,
        groupId: String,
        coefficient: BigDecimal,
        row: SeedClassSubject
    ) {
        target.schoolId = schoolId
        target.academicYearId = yearId
        target.classId = classId
        target.subjectId = subjectId
        target.groupId = groupId
        target.coefficient = coefficient
        target.weeklyHours = row.weeklyHours
        target.teachingLanguage = row.teachingLanguage?.let { enumOrNull<Language>(it) }
        target.displayOrder = row.displayOrder
        target.maxScore = decimal(row.maxScore)
        target.countsInAverage = row.countsInAverage
        target.optional = row.optional
        target.active = row.active
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun loc(value: SeedLocalized): LocalizedText =
        LocalizedText(fr = value.fr, en = value.en)

    private fun date(value: String): LocalDate = LocalDate.parse(value)

    private fun decimal(value: String): BigDecimal = BigDecimal(value.trim())

    private fun classKey(yearLabel: String, className: String): String = "$yearLabel|$className"

    private inline fun <reified T : Enum<T>> enumOrNull(raw: String?): T? {
        if (raw.isNullOrBlank()) return null
        return runCatching { enumValueOf<T>(raw.trim()) }.getOrNull()
    }

    companion object {
        private const val LEGACY_DEMO_SCHOOL_ID = "school-demo-elshammah"
        private const val LEGACY_DEMO_SCHOOL_CODE = "ELSHAMMAH-DEMO"
    }
}
