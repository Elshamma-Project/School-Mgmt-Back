package com.kobe.smels.configuration.seed

import com.kobe.smels.model.academic.ClassSubject
import com.kobe.smels.model.academic.SubjectKind
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.staff.Civility
import com.kobe.smels.model.staff.Staff
import com.kobe.smels.model.staff.StaffFunction
import com.kobe.smels.model.staff.StaffSource
import com.kobe.smels.model.timetable.ActivityType
import com.kobe.smels.model.timetable.EntryKind
import com.kobe.smels.model.timetable.GridPeriod
import com.kobe.smels.model.timetable.PeriodKind
import com.kobe.smels.model.timetable.TimeSlotGrid
import com.kobe.smels.model.timetable.Timetable
import com.kobe.smels.model.timetable.TimetableEntry
import com.kobe.smels.model.timetable.TimetableStatus
import com.kobe.smels.model.timetable.TimetableSubjectAlias
import com.kobe.smels.repository.academic.AcademicYearRepository
import com.kobe.smels.repository.academic.ClassSubjectRepository
import com.kobe.smels.repository.academic.SchoolClassRepository
import com.kobe.smels.repository.academic.SubjectRepository
import com.kobe.smels.repository.staff.StaffRepository
import com.kobe.smels.repository.timetable.TimeSlotGridRepository
import com.kobe.smels.repository.timetable.TimetableEntryRepository
import com.kobe.smels.repository.timetable.TimetableRepository
import com.kobe.smels.repository.timetable.TimetableSubjectAliasRepository
import com.kobe.smels.service.timetable.TimetableConflictService
import com.kobe.smels.service.timetable.TimetableSubjectAliasService
import com.kobe.smels.shared.security.CurrentUser
import com.kobe.smels.model.user.Role
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.annotation.Order
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

/**
 * Charge l’extraction EDT privée (enseignants + cases 2026-2027) après le seed académique.
 *
 * AGENTS.md règle 12 : le JSON n’est **pas** dans Git ; logs = ids uniquement.
 * Upsert par clés naturelles (`seedKey`, classe×année, slot+option). Statut Timetable = DRAFT.
 */
@Component
@Order(16)
@EnableConfigurationProperties(ElShammahEdtSeedProperties::class)
class ElShammahEdtPrivateSeedRunner(
    private val properties: ElShammahEdtSeedProperties,
    private val resourceLoader: ResourceLoader,
    private val objectMapper: ObjectMapper,
    private val academicYearRepository: AcademicYearRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val subjectRepository: SubjectRepository,
    private val classSubjectRepository: ClassSubjectRepository,
    private val staffRepository: StaffRepository,
    private val timeSlotGridRepository: TimeSlotGridRepository,
    private val timetableRepository: TimetableRepository,
    private val timetableEntryRepository: TimetableEntryRepository,
    private val timetableSubjectAliasRepository: TimetableSubjectAliasRepository,
    private val timetableConflictService: TimetableConflictService
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (!properties.enabled) {
            log.info("El Shammah EDT private seed skipped (disabled)")
            return
        }
        val resource = resourceLoader.getResource(properties.resource)
        if (!resource.exists()) {
            log.info(
                "El Shammah EDT private seed skipped (resource missing). Place extraction at {} or set EL_SHAMMAH_EDT_SEED_RESOURCE",
                properties.resource
            )
            return
        }
        try {
            val extraction = resource.inputStream.use { objectMapper.readValue(it, EdtExtractionFile::class.java) }
            seed(extraction)
                .doOnNext { report ->
                    log.info(
                        "El Shammah EDT private seed done schoolId={} teachersUpserted={} entriesUpserted={} jointGroups={} warnings={}",
                        properties.schoolId,
                        report.teachersUpserted,
                        report.entriesUpserted,
                        report.jointGroups,
                        report.warningCount
                    )
                }
                .doOnError { err ->
                    log.error("El Shammah EDT private seed failed: {}", err.message, err)
                }
                .block()
        } catch (error: Exception) {
            log.error("El Shammah EDT private seed aborted: {}", error.message, error)
        }
    }

    private data class SeedReport(
        val teachersUpserted: Int,
        val entriesUpserted: Int,
        val jointGroups: Int,
        val warningCount: Int
    )

    private fun seed(extraction: EdtExtractionFile): Mono<SeedReport> {
        val schoolId = properties.schoolId
        val yearLabel = extraction.academicYearLabel?.takeIf { it.isNotBlank() }
            ?: properties.academicYearLabel

        return academicYearRepository.findBySchoolIdAndLabel(schoolId, yearLabel)
            .switchIfEmpty(
                Mono.error(IllegalStateException("Academic year $yearLabel missing — run academic seed first"))
            )
            .flatMap { year ->
                val yearId = year.id!!
                ensureTeachers(schoolId, extraction.teachers)
                    .flatMap { teacherIds ->
                        cloneClassSubjectsFromPreviousYear(schoolId, yearId, yearLabel)
                            .then(ensureGrid(schoolId, yearId))
                            .flatMap { gridId ->
                                ensureDraftTimetable(schoolId, yearId, gridId)
                                    .flatMap { timetableId ->
                                        ensureAliases(schoolId, yearId, extraction)
                                            .then(
                                                upsertEntries(
                                                    schoolId = schoolId,
                                                    yearId = yearId,
                                                    timetableId = timetableId,
                                                    extraction = extraction,
                                                    teacherIds = teacherIds
                                                )
                                            )
                                            .flatMap { entryStats ->
                                                runConflictReport(schoolId, timetableId)
                                                    .map { warnings ->
                                                        SeedReport(
                                                            teachersUpserted = teacherIds.size,
                                                            entriesUpserted = entryStats.first,
                                                            jointGroups = entryStats.second,
                                                            warningCount = warnings
                                                        )
                                                    }
                                            }
                                    }
                            }
                    }
            }
    }

    private fun ensureTeachers(
        schoolId: String,
        teachers: List<EdtTeacher>
    ): Mono<Map<String, String>> {
        if (teachers.isEmpty()) return Mono.just(emptyMap())
        return Flux.fromIterable(teachers)
            .concatMap { row ->
                val key = row.key.trim()
                if (key.isEmpty()) return@concatMap Mono.empty()
                val civility = parseCivility(row.civility)
                val lastName = row.printedName.trim()
                staffRepository.findBySchoolIdAndSeedKey(schoolId, key)
                    .flatMap { existing ->
                        if (!properties.overwriteExisting) {
                            Mono.just(key to existing.id!!)
                        } else {
                            existing.civility = civility ?: existing.civility
                            existing.lastName = lastName.ifBlank { existing.lastName }
                            existing.functions = existing.functions + StaffFunction.TEACHER
                            existing.source = StaffSource.EDT_2026_2027
                            existing.needsCompletion = true
                            existing.active = true
                            staffRepository.save(existing).map { key to it.id!! }
                        }
                    }
                    .switchIfEmpty(
                        Mono.defer {
                            val staff = Staff(
                                schoolId = schoolId,
                                lastName = lastName,
                                firstName = "",
                                civility = civility,
                                functions = setOf(StaffFunction.TEACHER),
                                seedKey = key,
                                source = StaffSource.EDT_2026_2027,
                                needsCompletion = true,
                                active = true
                            )
                            staffRepository.save(staff).map { saved ->
                                log.info("EDT seed staff created staffId={} seedKey={}", saved.id, key)
                                key to saved.id!!
                            }
                        }
                    )
            }
            .collectMap({ it.first }, { it.second })
    }

    private fun parseCivility(raw: String?): Civility? {
        val n = raw?.trim()?.uppercase()?.replace(".", "") ?: return null
        return when {
            n == "MR" || n == "M" || n.startsWith("MR") -> Civility.MR
            n == "MME" || n.startsWith("MME") || n == "MRS" || n == "MS" -> Civility.MME
            n == "MLLE" || n.startsWith("MLLE") -> Civility.MLLE
            else -> null
        }
    }

    /**
     * Copie ClassSubject 2025-2026 → 2026-2027 pour les classes homonymes /
     * fusion 4ème ALL+ESP → 4ème, 3ème ALL → 3ème (Q-41 côté EDT seulement).
     */
    private fun cloneClassSubjectsFromPreviousYear(
        schoolId: String,
        targetYearId: String,
        targetYearLabel: String
    ): Mono<Void> {
        val sourceYearLabel = "2025-2026"
        val classMap = mapOf(
            "6ème" to listOf("6ème"),
            "5ème" to listOf("5ème"),
            "4ème" to listOf("4ème ALL", "4ème ESP"),
            "3ème" to listOf("3ème ALL"),
            "2nde C" to listOf("2nde C"),
            "Form 1" to listOf("Form 1"),
            "Form 2" to listOf("Form 2"),
            "Form 3" to listOf("Form 3"),
            "Form 4 ARTS" to listOf("Form 4 ARTS"),
            "Form 4 SC" to listOf("Form 4 SC")
        )
        return academicYearRepository.findBySchoolIdAndLabel(schoolId, sourceYearLabel)
            .flatMap { sourceYear ->
                val sourceYearId = sourceYear.id!!
                Flux.fromIterable(classMap.entries)
                    .concatMap { (targetName, sourceNames) ->
                        schoolClassRepository
                            .findBySchoolIdAndAcademicYearIdAndName(schoolId, targetYearId, targetName)
                            .flatMap { targetClass ->
                                Flux.fromIterable(sourceNames)
                                    .concatMap { sourceName ->
                                        schoolClassRepository
                                            .findBySchoolIdAndAcademicYearIdAndName(
                                                schoolId,
                                                sourceYearId,
                                                sourceName
                                            )
                                            .flatMapMany { sourceClass ->
                                                classSubjectRepository
                                                    .findBySchoolIdAndClassId(schoolId, sourceClass.id!!)
                                            }
                                            .concatMap { src ->
                                                classSubjectRepository
                                                    .findBySchoolIdAndClassIdAndSubjectId(
                                                        schoolId,
                                                        targetClass.id!!,
                                                        src.subjectId
                                                    )
                                                    .flatMap { existing ->
                                                        if (!properties.overwriteExisting) {
                                                            Mono.just(existing)
                                                        } else {
                                                            existing.weeklyHours = src.weeklyHours
                                                            existing.coefficient = src.coefficient
                                                            existing.groupId = src.groupId
                                                            existing.active = true
                                                            classSubjectRepository.save(existing)
                                                        }
                                                    }
                                                    .switchIfEmpty(
                                                        Mono.defer {
                                                            val copy = ClassSubject(
                                                                schoolId = schoolId,
                                                                academicYearId = targetYearId,
                                                                classId = targetClass.id!!,
                                                                subjectId = src.subjectId,
                                                                groupId = src.groupId,
                                                                teacherId = null,
                                                                coefficient = src.coefficient,
                                                                weeklyHours = src.weeklyHours,
                                                                teachingLanguage = src.teachingLanguage,
                                                                displayOrder = src.displayOrder,
                                                                maxScore = src.maxScore,
                                                                countsInAverage = src.countsInAverage,
                                                                optional = src.optional,
                                                                active = true
                                                            )
                                                            classSubjectRepository.save(copy)
                                                        }
                                                    )
                                            }
                                    }
                                    .then()
                            }
                            .then()
                    }
                    .then()
                    .doOnSuccess {
                        log.info(
                            "EDT seed ClassSubject clone done targetYear={} label={}",
                            targetYearId,
                            targetYearLabel
                        )
                    }
            }
            .then()
    }

    private fun ensureGrid(schoolId: String, yearId: String): Mono<String> {
        return timeSlotGridRepository
            .findBySchoolIdAndAcademicYearIdAndActiveTrue(schoolId, yearId)
            .next()
            .map { it.id!! }
            .switchIfEmpty(
                Mono.defer {
                    val gridResource = resourceLoader.getResource(properties.gridResource)
                    val fallback = resourceLoader.getResource("file:docs/fixtures/timetable-grid.json")
                    val res = when {
                        gridResource.exists() -> gridResource
                        fallback.exists() -> fallback
                        else -> null
                    }
                    if (res == null) {
                        return@defer Mono.error(
                            IllegalStateException("timetable-grid.json not found (${properties.gridResource})")
                        )
                    }
                    val fixture = res.inputStream.use { objectMapper.readValue(it, GridFixture::class.java) }
                    val periods = fixture.periods.map { p ->
                        GridPeriod(
                            number = p.number,
                            kind = PeriodKind.valueOf(p.kind),
                            start = LocalTime.parse(p.start),
                            end = LocalTime.parse(p.end),
                            label = p.label?.let { LocalizedText(fr = it.fr, en = it.en) }
                        )
                    }
                    val days = fixture.days.map { DayOfWeek.valueOf(it) }.toSet()
                    val grid = TimeSlotGrid(
                        schoolId = schoolId,
                        academicYearId = yearId,
                        name = "Grille 2026-2027",
                        days = days,
                        periods = periods,
                        active = true
                    )
                    timeSlotGridRepository.save(grid).map { saved ->
                        log.info("EDT seed grid created gridId={}", saved.id)
                        saved.id!!
                    }
                }
            )
    }

    private fun ensureDraftTimetable(schoolId: String, yearId: String, gridId: String): Mono<String> {
        return timetableRepository
            .findBySchoolIdAndAcademicYearIdOrderByRevisionDesc(schoolId, yearId)
            .filter { it.revision == 1 }
            .next()
            .map { it.id!! }
            .switchIfEmpty(
                Mono.defer {
                    val tt = Timetable(
                        schoolId = schoolId,
                        academicYearId = yearId,
                        gridId = gridId,
                        revision = 1,
                        status = TimetableStatus.DRAFT
                    )
                    timetableRepository.save(tt).map { saved ->
                        log.info("EDT seed timetable DRAFT created timetableId={} revision=1", saved.id)
                        saved.id!!
                    }
                }
            )
    }

    private fun ensureAliases(
        schoolId: String,
        yearId: String,
        extraction: EdtExtractionFile
    ): Mono<Void> {
        if (extraction.printedAliasMap.isEmpty()) return Mono.empty()
        return subjectRepository.findBySchoolIdAndActiveTrue(schoolId)
            .collectList()
            .flatMap { subjects ->
                val byCode = subjects.associateBy { it.code }
                Flux.fromIterable(extraction.printedAliasMap.entries)
                    .concatMap { (printed, target) ->
                        val normalized = TimetableSubjectAliasService.normalizeAlias(printed)
                        val subject = byCode[target]
                        val sectionKey = "_"
                        timetableSubjectAliasRepository
                            .findBySchoolIdAndAcademicYearIdAndSectionKeyAndAliasNormalized(
                                schoolId,
                                yearId,
                                sectionKey,
                                normalized
                            )
                            .switchIfEmpty(
                                Mono.defer {
                                    if (subject == null) {
                                        log.warn(
                                            "EDT seed alias unresolved printedNormalized={} targetCode={}",
                                            normalized,
                                            target
                                        )
                                    }
                                    val alias = TimetableSubjectAlias(
                                        schoolId = schoolId,
                                        academicYearId = yearId,
                                        sectionKey = sectionKey,
                                        aliasNormalized = normalized,
                                        aliasVariants = listOf(printed),
                                        classSubjectIds = emptyList(),
                                        unresolved = subject == null,
                                        note = "seed EDT 2026-2027 → $target"
                                    )
                                    timetableSubjectAliasRepository.save(alias)
                                }
                            )
                    }
                    .then()
            }
    }

    private fun upsertEntries(
        schoolId: String,
        yearId: String,
        timetableId: String,
        extraction: EdtExtractionFile,
        teacherIds: Map<String, String>
    ): Mono<Pair<Int, Int>> {
        val sessionGroupIds = mutableMapOf<String, String>()
        fun groupIdFor(hint: String?): String? {
            val h = hint?.trim()?.ifBlank { null } ?: return null
            return sessionGroupIds.getOrPut(h) { "sg-" + UUID.nameUUIDFromBytes(h.toByteArray()).toString() }
        }

        return schoolClassRepository.findBySchoolIdAndAcademicYearIdAndActiveTrue(schoolId, yearId)
            .collectMap({ it.name }, { it.id!! })
            .flatMap { classIds ->
                subjectRepository.findBySchoolIdAndActiveTrue(schoolId).collectList()
                    .flatMap { subjects ->
                        val subjectByCode = subjects.associateBy { it.code }
                        val activityByAlias = mapOf(
                            "CHINOIS" to ActivityType.CHINESE,
                            "CNINOIS" to ActivityType.CHINESE,
                            "CNINESE" to ActivityType.CHINESE,
                            "CHINESE" to ActivityType.CHINESE,
                            "MUSIQUE" to ActivityType.MUSIC,
                            "MUSIC" to ActivityType.MUSIC,
                            "CLUB" to ActivityType.CLUB,
                            "BIBLIOTHEQUE" to ActivityType.LIBRARY,
                            "BIBLIOTHÈQUE" to ActivityType.LIBRARY,
                            "LIBRARY" to ActivityType.LIBRARY,
                            "MENAGE GENERAL" to ActivityType.GENERAL_CLEANING,
                            "MÉNAGE GÉNÉRAL" to ActivityType.GENERAL_CLEANING,
                            "GENERAL CLEAN UP" to ActivityType.GENERAL_CLEANING,
                            "GENERAL CLEANUP" to ActivityType.GENERAL_CLEANING,
                            "ART EDUC" to ActivityType.OTHER,
                            "ART. EDUC" to ActivityType.OTHER
                        )
                        classSubjectRepository.findBySchoolIdAndAcademicYearIdAndActiveTrue(schoolId, yearId)
                            .collectMultimap({ it.classId }, { it })
                            .flatMap { csByClass ->
                                Flux.fromIterable(extraction.entries)
                                    .concatMap { entry ->
                                        val classId = classIds[entry.`class`]
                                        if (classId == null) {
                                            log.warn(
                                                "EDT seed entry skipped unknownClass timetableId={}",
                                                timetableId
                                            )
                                            return@concatMap Mono.empty()
                                        }
                                        val day = parseDay(entry.day) ?: return@concatMap Mono.empty()
                                        val slotKey = when {
                                            entry.startPeriod != null -> "P${entry.startPeriod}"
                                            entry.customStart != null ->
                                                "T" + entry.customStart.replace(":", "")
                                            else -> return@concatMap Mono.empty()
                                        }
                                        val sessionGroupId =
                                            groupIdFor(entry.jointSessionId ?: entry.jointSessionHint)
                                        Flux.fromIterable(entry.resolvedItems())
                                            .concatMap { item ->
                                                upsertOneEntry(
                                                    schoolId = schoolId,
                                                    yearId = yearId,
                                                    timetableId = timetableId,
                                                    classId = classId,
                                                    day = day,
                                                    slotKey = slotKey,
                                                    entry = entry,
                                                    item = item,
                                                    sessionGroupId = sessionGroupId,
                                                    teacherIds = teacherIds,
                                                    subjectByCode = subjectByCode,
                                                    activityByAlias = activityByAlias,
                                                    classSubjects = csByClass[classId]?.toList().orEmpty(),
                                                    aliasMap = extraction.printedAliasMap
                                                )
                                            }
                                    }
                                    .count()
                                    .map { count -> count.toInt() to sessionGroupIds.size }
                            }
                    }
            }
    }

    private fun upsertOneEntry(
        schoolId: String,
        yearId: String,
        timetableId: String,
        classId: String,
        day: DayOfWeek,
        slotKey: String,
        entry: EdtEntry,
        item: EdtEntryItem,
        sessionGroupId: String?,
        teacherIds: Map<String, String>,
        subjectByCode: Map<String, com.kobe.smels.model.academic.Subject>,
        activityByAlias: Map<String, ActivityType>,
        classSubjects: List<ClassSubject>,
        aliasMap: Map<String, String>
    ): Mono<TimetableEntry> {
        val optionCode = item.optionCode?.trim().orEmpty()
        val printed = item.printedAlias?.trim().orEmpty()
        val normalized = TimetableSubjectAliasService.normalizeAlias(printed)
        val mappedCode = aliasMap[printed] ?: aliasMap.entries
            .firstOrNull { TimetableSubjectAliasService.normalizeAlias(it.key) == normalized }
            ?.value
        val activityType = item.activityType?.let {
            runCatching { ActivityType.valueOf(it) }.getOrNull()
        } ?: activityByAlias.entries
            .firstOrNull { TimetableSubjectAliasService.normalizeAlias(it.key) == normalized }
            ?.value
        val kind = when {
            item.kind.equals("ACTIVITY", ignoreCase = true) -> EntryKind.ACTIVITY
            activityType != null -> EntryKind.ACTIVITY
            subjectByCode[mappedCode]?.kind == SubjectKind.ACTIVITY -> EntryKind.ACTIVITY
            else -> EntryKind.LESSON
        }
        // Q-61 : enseignant inconnu (« Mme ... ») → pas de fiche fantôme
        val teacherId = item.teacherKey?.trim()?.takeIf { it.isNotEmpty() }?.let { teacherIds[it] }
        val subject = mappedCode?.let { subjectByCode[it] }
        val classSubjectIds =
            if (kind == EntryKind.ACTIVITY) {
                emptyList()
            } else {
                classSubjects.filter { it.subjectId == subject?.id }.mapNotNull { it.id }
            }

        return timetableEntryRepository
            .findBySchoolIdAndTimetableIdAndClassIdAndDayAndSlotKeyAndOptionCode(
                schoolId,
                timetableId,
                classId,
                day,
                slotKey,
                optionCode
            )
            .flatMap { existing ->
                if (!properties.overwriteExisting) {
                    Mono.just(existing)
                } else {
                    applyEntry(
                        existing,
                        entry,
                        kind,
                        activityType,
                        teacherId,
                        subject?.id,
                        classSubjectIds,
                        sessionGroupId,
                        item
                    )
                    timetableEntryRepository.save(existing)
                }
            }
            .switchIfEmpty(
                Mono.defer {
                    val created = TimetableEntry(
                        schoolId = schoolId,
                        academicYearId = yearId,
                        timetableId = timetableId,
                        classId = classId,
                        day = day,
                        startPeriod = entry.startPeriod,
                        periodCount = entry.periodCount.coerceAtLeast(1),
                        customStart = entry.customStart?.let { LocalTime.parse(it) },
                        customEnd = entry.customEnd?.let { LocalTime.parse(it) },
                        slotKey = slotKey,
                        kind = kind,
                        classSubjectIds = classSubjectIds,
                        subjectId = subject?.id,
                        teacherId = teacherId,
                        activityType = activityType,
                        sessionGroupId = sessionGroupId,
                        optionGroupCode = item.optionGroupCode,
                        optionCode = optionCode,
                        label = when {
                            item.labelFr != null || item.labelEn != null ->
                                LocalizedText(fr = item.labelFr, en = item.labelEn)
                            printed.isNotBlank() && kind == EntryKind.ACTIVITY ->
                                LocalizedText(fr = printed, en = printed)
                            else -> null
                        },
                        subjectLabel = subject?.name?.fr ?: printed.takeIf { it.isNotBlank() },
                        teacherLabel = null
                    )
                    timetableEntryRepository.save(created)
                }
            )
    }

    private fun applyEntry(
        existing: TimetableEntry,
        entry: EdtEntry,
        kind: EntryKind,
        activityType: ActivityType?,
        teacherId: String?,
        subjectId: String?,
        classSubjectIds: List<String>,
        sessionGroupId: String?,
        item: EdtEntryItem
    ) {
        existing.startPeriod = entry.startPeriod
        existing.periodCount = entry.periodCount.coerceAtLeast(1)
        existing.customStart = entry.customStart?.let { LocalTime.parse(it) }
        existing.customEnd = entry.customEnd?.let { LocalTime.parse(it) }
        existing.kind = kind
        existing.activityType = activityType
        existing.teacherId = teacherId
        existing.subjectId = subjectId
        existing.classSubjectIds = classSubjectIds
        existing.sessionGroupId = sessionGroupId
        existing.optionGroupCode = item.optionGroupCode
        existing.optionCode = item.optionCode?.trim().orEmpty()
    }

    private fun parseDay(raw: String): DayOfWeek? =
        runCatching { DayOfWeek.valueOf(raw.trim().uppercase()) }.getOrNull()
            ?: when (raw.trim().lowercase()) {
                "lundi", "lun" -> DayOfWeek.MONDAY
                "mardi", "mar" -> DayOfWeek.TUESDAY
                "mercredi", "mer" -> DayOfWeek.WEDNESDAY
                "jeudi", "jeu" -> DayOfWeek.THURSDAY
                "vendredi", "ven" -> DayOfWeek.FRIDAY
                "samedi", "sam" -> DayOfWeek.SATURDAY
                else -> null
            }

    private fun runConflictReport(schoolId: String, timetableId: String): Mono<Int> {
        val seedUser = CurrentUser(
            accountId = "seed-edt",
            sessionId = "seed-edt-session",
            username = "seed-edt",
            schoolId = schoolId,
            roles = setOf(Role.SUPER_ADMIN),
            profileType = com.kobe.smels.model.user.ProfileType.NONE,
            profileId = null,
            mustChangePassword = false
        )
        return timetableConflictService.analyze(seedUser, timetableId, schoolId)
            .map { result ->
                val blocking = result.blocking.size
                val warnings = result.warnings.size
                log.info(
                    "EDT seed conflict report timetableId={} blocking={} warnings={}",
                    timetableId,
                    blocking,
                    warnings
                )
                result.blocking.take(20).forEach { issue ->
                    log.warn(
                        "EDT seed conflict blocking code={} entryId={} otherEntryId={}",
                        issue.code,
                        issue.entryId,
                        issue.otherEntryId
                    )
                }
                result.warnings.take(30).forEach { issue ->
                    log.warn(
                        "EDT seed conflict warning code={} entryId={} otherEntryId={}",
                        issue.code,
                        issue.entryId,
                        issue.otherEntryId
                    )
                }
                blocking + warnings
            }
            .onErrorResume { err ->
                log.warn("EDT seed conflict analysis skipped: {}", err.message)
                Mono.just(0)
            }
    }
}

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
private data class GridFixture(
    val days: List<String> = emptyList(),
    val periods: List<GridFixturePeriod> = emptyList()
)

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
private data class GridFixturePeriod(
    val number: Int? = null,
    val kind: String,
    val start: String,
    val end: String,
    val label: SeedLocalized? = null
)
