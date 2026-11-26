package com.kobe.beesm.configuration.seed

import com.kobe.beesm.configuration.MockUsersBootstrapRunner
import com.kobe.beesm.configuration.SecurityProperties
import com.kobe.beesm.dto.grading.CreateAssessmentRequest
import com.kobe.beesm.dto.grading.GenerateReportSheetRequest
import com.kobe.beesm.model.academic.AcademicYear
import com.kobe.beesm.model.academic.ClassSubject
import com.kobe.beesm.model.academic.School
import com.kobe.beesm.model.academic.SchoolClass
import com.kobe.beesm.model.grading.AssessmentType
import com.kobe.beesm.model.grading.EvaluationSession
import com.kobe.beesm.model.grading.GradeSheet
import com.kobe.beesm.model.grading.GradeStatus
import com.kobe.beesm.model.grading.ReportSheetStatus
import com.kobe.beesm.model.grading.SessionStatus
import com.kobe.beesm.model.grading.SheetStatus
import com.kobe.beesm.model.shared.Cycle
import com.kobe.beesm.model.shared.Section
import com.kobe.beesm.model.shared.Sex
import com.kobe.beesm.model.shared.ContactInfo
import com.kobe.beesm.model.student.Enrollment
import com.kobe.beesm.model.student.EnrollmentStatus
import com.kobe.beesm.model.student.GuardianLink
import com.kobe.beesm.model.student.GuardianRelationship
import com.kobe.beesm.model.student.Student
import com.kobe.beesm.dto.grading.UpsertGradeRequest
import com.kobe.beesm.model.user.ProfileType
import com.kobe.beesm.model.user.Role
import com.kobe.beesm.repository.academic.AcademicYearRepository
import com.kobe.beesm.repository.academic.ClassSubjectRepository
import com.kobe.beesm.repository.academic.SchoolClassRepository
import com.kobe.beesm.repository.academic.SchoolRepository
import com.kobe.beesm.repository.academic.SubjectRepository
import com.kobe.beesm.repository.grading.AssessmentRepository
import com.kobe.beesm.repository.grading.EvaluationSessionRepository
import com.kobe.beesm.repository.grading.GradeReportSheetRepository
import com.kobe.beesm.repository.grading.GradeRepository
import com.kobe.beesm.repository.grading.GradeSheetRepository
import com.kobe.beesm.repository.staff.StaffRepository
import com.kobe.beesm.repository.student.EnrollmentRepository
import com.kobe.beesm.repository.student.StudentRepository
import com.kobe.beesm.repository.user.UserAccountRepository
import java.time.LocalDate
import com.kobe.beesm.service.grading.AssessmentService
import com.kobe.beesm.service.grading.EvaluationSessionService
import com.kobe.beesm.service.grading.GradeReportSheetService
import com.kobe.beesm.service.grading.GradeService
import com.kobe.beesm.service.grading.GradingMapper
import com.kobe.beesm.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.text.Normalizer

/**
 * Seed DEV « première tranche enseignant » : une 6ème francophone avec une dizaine d'élèves
 * **fictifs**, deux matières affectées à `staff-demo-teacher`, séquences 1 et 2 ouvertes, par fiche une
 * colonne SEQUENCE_MARK (/20) + une colonne BONUS + une colonne MALUS (max 5 pts, Q-25) avec quelques notes
 * (certaines PENDING), puis la fiche de report du trimestre 1 (générée, ou régénérée si elle est encore
 * en brouillon / rejetée).
 *
 * - `@Order(25)` : après [MockUsersBootstrapRunner] (20) qui crée le compte et la fiche Staff enseignant.
 * - Activé par `beesm.security.mock-users.enabled` (DEV uniquement).
 * - Idempotent (insert-if-absent). N'écrase jamais un `teacherId` déjà posé pour un autre enseignant,
 *   ni une note déjà saisie.
 * - Les notes passent par [GradeService] / [AssessmentService] (AGENTS.md règle 8) ; l'ouverture de la
 *   séquence par [EvaluationSessionService] ; aucune donnée réelle (règle 12) : élèves `DEMO …`.
 * - Aucun identifiant d'élève dans les logs (BR-43) : compteurs uniquement.
 */
@Component
@Order(25)
class TeacherDemoSeedRunner(
    private val securityProperties: SecurityProperties,
    private val elShammahSeedProperties: ElShammahSeedProperties,
    private val schoolRepository: SchoolRepository,
    private val academicYearRepository: AcademicYearRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val classSubjectRepository: ClassSubjectRepository,
    private val subjectRepository: SubjectRepository,
    private val staffRepository: StaffRepository,
    private val userAccountRepository: UserAccountRepository,
    private val studentRepository: StudentRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val evaluationSessionRepository: EvaluationSessionRepository,
    private val gradeSheetRepository: GradeSheetRepository,
    private val assessmentRepository: AssessmentRepository,
    private val gradeRepository: GradeRepository,
    private val gradeReportSheetRepository: GradeReportSheetRepository,
    private val evaluationSessionService: EvaluationSessionService,
    private val assessmentService: AssessmentService,
    private val gradeService: GradeService,
    private val gradeReportSheetService: GradeReportSheetService
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (!securityProperties.mockUsers.enabled) {
            log.info("Teacher demo seed skipped (beesm.security.mock-users.enabled=false)")
            return
        }
        try {
            seed()
        } catch (error: Exception) {
            // Ne jamais empêcher le démarrage de l'application pour un jeu de démo.
            log.error("Teacher demo seed failed: {}", error.message, error)
        }
    }

    private fun seed() {
        val cfg = securityProperties.mockUsers
        val preferredSchoolId = elShammahSeedProperties.schoolId.takeIf { elShammahSeedProperties.enabled }
            ?: cfg.schoolId

        val school = resolveSchool(preferredSchoolId, cfg.schoolCode)
        if (school == null) {
            log.warn("Teacher demo seed skipped: tenant school not found (id={})", preferredSchoolId)
            return
        }
        val schoolId = requireNotNull(school.id) { "School.id missing" }

        staffRepository.findByIdAndSchoolId(MockUsersBootstrapRunner.STAFF_TEACHER, schoolId).block()
            ?: run {
                log.warn("Teacher demo seed skipped: staff {} not found", MockUsersBootstrapRunner.STAFF_TEACHER)
                return
            }

        val year = resolveCurrentYear(schoolId)
        if (year == null) {
            log.warn("Teacher demo seed skipped: no academic year for schoolId={}", schoolId)
            return
        }
        val yearId = requireNotNull(year.id) { "AcademicYear.id missing" }

        val report = SeedReport()

        // 2. Classe 6ème francophone de l'année courante (copie de structure si absente).
        val schoolClass = resolveSixiemeClass(schoolId, year, report)
        if (schoolClass == null) {
            log.warn(
                "Teacher demo seed skipped: no francophone 6ème class found for year={} " +
                    "(create one in the academic module, or keep the El Shammah seed JSON)",
                year.label
            )
            return
        }
        val classId = requireNotNull(schoolClass.id) { "SchoolClass.id missing" }

        // 3. Affectation de l'enseignant démo (1–2 ClassSubjects).
        val assigned = assignTeacher(schoolId, classId, report)
        if (assigned.isEmpty()) {
            log.warn("Teacher demo seed: no ClassSubject available for the demo teacher in class '{}'", schoolClass.name)
        }

        // 4–5. Élèves fictifs + inscriptions.
        val demoStudentIds = seedStudentsAndEnrollments(schoolId, yearId, schoolClass, report)

        if (assigned.isEmpty()) {
            logSummary(schoolId, year, schoolClass, report)
            return
        }

        // 6. Séquences 1 puis 2 (la 2 s'ouvre après la 1).
        val directorUser = syntheticUser(schoolId, "seed-demo-director", Role.DIRECTOR, ProfileType.NONE, null)
        val sessions = SEEDED_SESSION_NUMBERS.mapNotNull { number ->
            ensureSession(directorUser, schoolId, yearId, number, report)
        }

        // 7. Fiches / colonnes SEQUENCE_MARK + BONUS + MALUS / notes, pour chaque séquence OPEN.
        val teacherUser = teacherUser(schoolId)
        for (session in sessions) {
            if (session.status == SessionStatus.OPEN) {
                assigned.forEachIndexed { index, cs ->
                    seedSheetAndGrades(teacherUser, schoolId, session, cs, demoStudentIds, index, report)
                }
            } else {
                log.warn(
                    "Teacher demo seed: session {} is {} — sheets/grades not seeded (reopen as DIRECTOR if needed)",
                    session.number, session.status
                )
            }
        }

        // 8. Fiche de report du trimestre 1 : générée, ou régénérée après les notes des deux séquences.
        if (sessions.any { it.status != SessionStatus.PLANNED }) {
            assigned.forEach { cs -> seedReportSheet(teacherUser, schoolId, cs, report) }
        }

        logSummary(schoolId, year, schoolClass, report)
    }

    // ── Résolution tenant / année / classe ──────────────────────────────────

    private fun resolveSchool(preferredSchoolId: String, schoolCode: String): School? =
        schoolRepository.findById(preferredSchoolId)
            .switchIfEmpty(schoolRepository.findByCode(schoolCode))
            .switchIfEmpty(schoolRepository.findById(MockUsersBootstrapRunner.LEGACY_DEMO_SCHOOL_ID))
            .switchIfEmpty(schoolRepository.findByCode(MockUsersBootstrapRunner.LEGACY_DEMO_SCHOOL_CODE))
            .block()

    private fun resolveCurrentYear(schoolId: String): AcademicYear? {
        val years = academicYearRepository.findBySchoolIdOrderByStartDateDesc(schoolId).collectList().block()
            ?: return null
        return years.firstOrNull { it.current } ?: years.firstOrNull()
    }

    private fun resolveSixiemeClass(schoolId: String, year: AcademicYear, report: SeedReport): SchoolClass? {
        val yearId = requireNotNull(year.id)
        val inYear = schoolClassRepository
            .findBySchoolIdAndAcademicYearIdAndActiveTrue(schoolId, yearId)
            .collectList().block().orEmpty()
        pickSixieme(inYear)?.let { return it }

        // Aucune 6ème pour l'année courante : copie de structure depuis la 6ème d'une autre année.
        val years = academicYearRepository.findBySchoolIdOrderByStartDateDesc(schoolId).collectList().block().orEmpty()
        for (other in years.filter { it.id != yearId }) {
            val template = pickSixieme(
                schoolClassRepository.findBySchoolIdAndAcademicYearIdAndActiveTrue(schoolId, requireNotNull(other.id))
                    .collectList().block().orEmpty()
            ) ?: continue

            val existing = schoolClassRepository
                .findBySchoolIdAndAcademicYearIdAndName(schoolId, yearId, template.name).block()
            val target = existing ?: schoolClassRepository.save(
                SchoolClass(
                    schoolId = schoolId,
                    academicYearId = yearId,
                    levelId = template.levelId,
                    levelCode = template.levelCode,
                    section = template.section,
                    cycle = template.cycle,
                    name = template.name,
                    trackCode = template.trackCode,
                    capacity = template.capacity,
                    defaultLanguage = template.defaultLanguage,
                    availableLanguages = template.availableLanguages,
                    gradingSchemeId = template.gradingSchemeId,
                    active = true
                )
            ).block()?.also { report.classCreated = true } ?: continue

            val targetId = requireNotNull(target.id)
            val templateSubjects = classSubjectRepository.findBySchoolIdAndClassId(schoolId, requireNotNull(template.id))
                .filter { it.active }
                .collectList().block().orEmpty()
            for (src in templateSubjects) {
                val present = classSubjectRepository
                    .findBySchoolIdAndClassIdAndSubjectId(schoolId, targetId, src.subjectId).block()
                if (present != null) continue
                classSubjectRepository.save(
                    ClassSubject(
                        schoolId = schoolId,
                        academicYearId = yearId,
                        classId = targetId,
                        subjectId = src.subjectId,
                        groupId = src.groupId,
                        coefficient = src.coefficient,
                        teacherId = null,
                        weeklyHours = src.weeklyHours,
                        teachingLanguage = src.teachingLanguage,
                        displayOrder = src.displayOrder,
                        maxScore = src.maxScore,
                        countsInAverage = src.countsInAverage,
                        optional = src.optional,
                        active = true
                    )
                ).block()
                report.classSubjectsCopied++
            }
            if (report.classCreated || report.classSubjectsCopied > 0) {
                log.info(
                    "Teacher demo seed: created class '{}' for year {} from year {} template ({} class subjects)",
                    target.name, year.label, other.label, report.classSubjectsCopied
                )
            }
            return target
        }
        return null
    }

    private fun pickSixieme(classes: List<SchoolClass>): SchoolClass? {
        val candidates = classes.filter {
            it.section == Section.FRANCOPHONE &&
                it.cycle == Cycle.SECONDAIRE_1ER_CYCLE &&
                normalize(it.name).startsWith("6e")
        }
        return candidates.firstOrNull { normalize(it.name) == "6eme" } ?: candidates.firstOrNull()
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()

    // ── Affectation enseignant ──────────────────────────────────────────────

    private fun assignTeacher(schoolId: String, classId: String, report: SeedReport): List<ClassSubject> {
        val all = classSubjectRepository.findBySchoolIdAndClassId(schoolId, classId)
            .filter { it.active }
            .collectList().block().orEmpty()

        val mine = all.filter { it.teacherId == TEACHER_ID }
        if (mine.isNotEmpty()) {
            report.alreadyAssigned = mine.size
            return mine
        }

        val codeBySubjectId = all.associate { cs ->
            cs.subjectId to (subjectRepository.findByIdAndSchoolId(cs.subjectId, schoolId).block()?.code ?: "")
        }
        val ranked = all
            .filter { it.teacherId == null }
            .sortedWith(
                compareBy<ClassSubject>(
                    { PREFERRED_SUBJECT_CODES.indexOf(codeBySubjectId[it.subjectId]).let { i -> if (i < 0) Int.MAX_VALUE else i } },
                    { it.displayOrder }
                )
            )
            .take(MAX_ASSIGNED_SUBJECTS)

        val assigned = mutableListOf<ClassSubject>()
        for (cs in ranked) {
            cs.teacherId = TEACHER_ID
            assigned += classSubjectRepository.save(cs).block() ?: continue
            report.assignedNow++
        }
        return assigned
    }

    // ── Élèves fictifs + inscriptions ───────────────────────────────────────

    private fun seedStudentsAndEnrollments(
        schoolId: String,
        yearId: String,
        schoolClass: SchoolClass,
        report: SeedReport
    ): List<String> {
        val classId = requireNotNull(schoolClass.id)
        val existingEnrollments = enrollmentRepository
            .findBySchoolIdAndClassIdAndStatus(schoolId, classId, EnrollmentStatus.ENROLLED)
            .collectList().block().orEmpty()
        val demoIds = DEMO_STUDENTS.map { it.id }.toSet()
        val rollOffset = existingEnrollments
            .filter { it.studentId !in demoIds }
            .maxOfOrNull { it.rollNumber ?: 0 } ?: 0

        val enrolled = mutableListOf<String>()
        DEMO_STUDENTS.forEachIndexed { index, demo ->
            val existing = studentRepository.findById(demo.id).block()
                ?: studentRepository.findBySchoolIdAndMatricule(schoolId, demo.matricule).block()
            val student = if (existing == null) {
                studentRepository.save(demo.toStudent(schoolId)).block()?.also { report.studentsCreated++ }
            } else {
                enrichDemoStudentProfile(existing, demo, schoolId)
            }

            val studentId = student?.id ?: return@forEachIndexed

            val enrollment = enrollmentRepository
                .findBySchoolIdAndStudentIdAndAcademicYearId(schoolId, studentId, yearId).block()
                ?: enrollmentRepository.findById(demo.enrollmentId).block()
                ?: enrollmentRepository.save(
                    Enrollment(
                        id = demo.enrollmentId,
                        schoolId = schoolId,
                        studentId = studentId,
                        academicYearId = yearId,
                        classId = classId,
                        levelId = schoolClass.levelId,
                        rollNumber = rollOffset + index + 1,
                        status = EnrollmentStatus.ENROLLED
                    )
                ).block()?.also { report.enrollmentsCreated++ }

            if (enrollment != null &&
                enrollment.classId == classId &&
                enrollment.status == EnrollmentStatus.ENROLLED
            ) {
                enrolled += studentId
            }
        }
        return enrolled
    }

    /**
     * Complète les champs vides d’un élève démo déjà créé (DOB, contact, tuteurs)
     * sans écraser une saisie UI non vide.
     */
    private fun enrichDemoStudentProfile(
        existing: Student,
        demo: DemoStudent,
        schoolId: String
    ): Student {
        var dirty = false
        existing.schoolId = schoolId
        if (existing.sex == null) {
            existing.sex = demo.sex
            dirty = true
        }
        if (existing.dateOfBirth == null) {
            existing.dateOfBirth = demo.dateOfBirth
            dirty = true
        }
        if (existing.placeOfBirth.isNullOrBlank()) {
            existing.placeOfBirth = demo.placeOfBirth
            dirty = true
        }
        if (existing.nationality.isNullOrBlank()) {
            existing.nationality = demo.nationality
            dirty = true
        }
        val contact = existing.contact
        if (contact.phones.isEmpty() && contact.email.isNullOrBlank() &&
            contact.address.isNullOrBlank() && contact.city.isNullOrBlank()
        ) {
            existing.contact = demo.contact
            dirty = true
        }
        if (existing.guardians.isEmpty() && demo.guardians.isNotEmpty()) {
            existing.guardians = demo.guardians
            dirty = true
        }
        return if (dirty) {
            studentRepository.save(existing).block() ?: existing
        } else {
            existing
        }
    }

    // ── Séquences (1 et 2) ──────────────────────────────────────────────────

    private fun ensureSession(
        directorUser: CurrentUser,
        schoolId: String,
        yearId: String,
        number: Int,
        report: SeedReport
    ): EvaluationSession? {
        var session = evaluationSessionRepository
            .findBySchoolIdAndAcademicYearIdAndNumber(schoolId, yearId, number).block()
        if (session == null) {
            evaluationSessionService.ensureYearSessions(directorUser, yearId, schoolId).collectList().block()
            session = evaluationSessionRepository
                .findBySchoolIdAndAcademicYearIdAndNumber(schoolId, yearId, number).block()
            if (session != null) report.sessionsEnsured = true
        }
        if (session == null) return null

        if (session.status == SessionStatus.PLANNED) {
            // Passe par le service : crée aussi les GradeSheets des ClassSubjects actifs (BR-11, BR-21).
            evaluationSessionService.open(directorUser, requireNotNull(session.id), schoolId).block()
            report.sessionsOpened++
            session = evaluationSessionRepository
                .findBySchoolIdAndAcademicYearIdAndNumber(schoolId, yearId, number).block()
        }
        return session
    }

    private fun seedSheetAndGrades(
        teacherUser: CurrentUser,
        schoolId: String,
        session: EvaluationSession,
        cs: ClassSubject,
        studentIds: List<String>,
        offset: Int,
        report: SeedReport
    ) {
        val sessionId = requireNotNull(session.id)
        val csId = requireNotNull(cs.id)

        var sheet = gradeSheetRepository.findBySchoolIdAndClassSubjectIdAndSessionId(schoolId, csId, sessionId).block()
        if (sheet == null) {
            // Séquence déjà ouverte avant l'affectation / la copie de classe : fiche manquante.
            sheet = gradeSheetRepository.save(
                GradeSheet(
                    schoolId = schoolId,
                    academicYearId = session.academicYearId,
                    classId = cs.classId,
                    classSubjectId = csId,
                    sessionId = sessionId,
                    sessionNumber = session.number,
                    teacherId = cs.teacherId,
                    status = SheetStatus.DRAFT,
                    totalization = GradingMapper.defaultTotalization()
                )
            ).block()?.also { report.sheetsCreated++ }
        }
        if (sheet == null) return
        if (sheet.teacherId == null) {
            sheet.teacherId = cs.teacherId
            sheet = gradeSheetRepository.save(sheet).block() ?: return
        }
        if (sheet.teacherId != TEACHER_ID) return // fiche d'un autre enseignant : ne pas toucher

        val sheetId = requireNotNull(sheet.id)
        // Décalage des notes d'exemple : varie selon la matière et la séquence.
        val sampleOffset = offset * 3 + (session.number - 1) * 2

        val sequenceId = ensureAssessment(
            teacherUser, schoolId, sheetId, AssessmentType.SEQUENCE_MARK, ASSESSMENT_TITLE, SEQUENCE_MAX_SCORE, report
        )
        if (sequenceId != null) {
            seedGrades(teacherUser, schoolId, sequenceId, studentIds, SAMPLE_MARKS, sampleOffset, report, bonusMalus = false)
        }

        // Q-25 : colonnes BONUS / MALUS (points seulement, max 5) — la plupart des élèves restent PENDING.
        val bonusId = ensureAssessment(
            teacherUser, schoolId, sheetId, AssessmentType.BONUS, BONUS_TITLE, BONUS_MALUS_MAX_SCORE, report
        )
        if (bonusId != null) {
            seedGrades(teacherUser, schoolId, bonusId, studentIds, SAMPLE_BONUS, sampleOffset, report, bonusMalus = true)
        }
        val malusId = ensureAssessment(
            teacherUser, schoolId, sheetId, AssessmentType.MALUS, MALUS_TITLE, BONUS_MALUS_MAX_SCORE, report
        )
        if (malusId != null) {
            seedGrades(teacherUser, schoolId, malusId, studentIds, SAMPLE_MALUS, sampleOffset, report, bonusMalus = true)
        }
    }

    /** Colonne de la fiche (première du type) : insert-if-absent, via [AssessmentService]. */
    private fun ensureAssessment(
        teacherUser: CurrentUser,
        schoolId: String,
        sheetId: String,
        type: AssessmentType,
        title: String,
        maxScore: BigDecimal,
        report: SeedReport
    ): String? {
        val existing = assessmentRepository.findBySchoolIdAndSheetId(schoolId, sheetId)
            .filter { it.type == type }
            .next().block()
        if (existing != null) return existing.id
        return assessmentService.create(
            teacherUser,
            CreateAssessmentRequest(
                sheetId = sheetId,
                title = title,
                type = type,
                maxScore = maxScore,
                weight = BigDecimal.ONE,
                schoolId = schoolId
            )
        ).block()?.also {
            if (type == AssessmentType.SEQUENCE_MARK) report.assessmentsCreated++ else report.bonusMalusColumnsCreated++
        }?.id
    }

    /** Notes d'exemple insert-if-absent (jamais d'écrasement d'une saisie UI) ; `null` = PENDING (jamais 0). */
    private fun seedGrades(
        teacherUser: CurrentUser,
        schoolId: String,
        assessmentId: String,
        studentIds: List<String>,
        samples: List<BigDecimal?>,
        sampleOffset: Int,
        report: SeedReport,
        bonusMalus: Boolean
    ) {
        studentIds.forEachIndexed { index, studentId ->
            val present = gradeRepository
                .findBySchoolIdAndAssessmentIdAndStudentId(schoolId, assessmentId, studentId).block()
            if (present != null) return@forEachIndexed
            val sample = samples[(index + sampleOffset) % samples.size]
            gradeService.upsert(
                teacherUser,
                UpsertGradeRequest(
                    assessmentId = assessmentId,
                    studentId = studentId,
                    status = if (sample == null) GradeStatus.PENDING else GradeStatus.SCORED,
                    value = sample,
                    schoolId = schoolId
                )
            ).block()
            when {
                bonusMalus && sample != null -> report.bonusMalusScored++
                bonusMalus -> report.bonusMalusPending++
                sample == null -> report.gradesPending++
                else -> report.gradesScored++
            }
        }
    }

    // ── Fiche de report T1 ──────────────────────────────────────────────────

    private fun seedReportSheet(
        teacherUser: CurrentUser,
        schoolId: String,
        cs: ClassSubject,
        report: SeedReport
    ) {
        val csId = requireNotNull(cs.id)
        try {
            val existing = gradeReportSheetRepository
                .findBySchoolIdAndClassSubjectIdAndTermNumber(schoolId, csId, 1).block()
            if (existing == null) {
                gradeReportSheetService.generate(
                    teacherUser,
                    GenerateReportSheetRequest(
                        classSubjectId = csId,
                        termNumber = 1,
                        blank = false,
                        schoolId = schoolId
                    )
                ).block()
                report.reportSheetsCreated++
            } else if (existing.status == ReportSheetStatus.DRAFT || existing.status == ReportSheetStatus.REJECTED) {
                // Rafraîchit les 2 séquences (notes + bonus/malus). Une fiche déjà soumise / visée n'est pas
                // régénérée : la régénération la repasserait en DRAFT et effacerait les visas de test.
                gradeReportSheetService.regenerate(
                    teacherUser,
                    requireNotNull(existing.id),
                    blank = false,
                    schoolIdOverride = schoolId
                ).block()
                report.reportSheetsRegenerated++
            }
        } catch (error: Exception) {
            log.warn("Teacher demo seed: report sheet term 1 not generated/refreshed: {}", error.message)
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun teacherUser(schoolId: String): CurrentUser {
        val accountId = userAccountRepository
            .findBySchoolIdAndUsername(schoolId, MockUsersBootstrapRunner.EMAIL_TEACHER)
            .block()?.id ?: "seed-demo-teacher"
        return syntheticUser(schoolId, accountId, Role.TEACHER, ProfileType.STAFF, TEACHER_ID)
    }

    private fun syntheticUser(
        schoolId: String,
        accountId: String,
        role: Role,
        profileType: ProfileType,
        profileId: String?
    ) = CurrentUser(
        accountId = accountId,
        sessionId = "seed-demo-session",
        username = "seed-demo-runner",
        schoolId = schoolId,
        roles = setOf(role),
        profileType = profileType,
        profileId = profileId,
        mustChangePassword = false
    )

    private fun logSummary(schoolId: String, year: AcademicYear, schoolClass: SchoolClass, report: SeedReport) {
        log.info(
            "Teacher demo seed done schoolId={} year={} class='{}' | classCreated={} classSubjectsCopied={} " +
                "teacherAssignedNow={} teacherAlreadyAssigned={} studentsCreated={} enrollmentsCreated={} " +
                "sessionsEnsured={} sessionsOpenedNow={} sheetsCreated={} assessmentsCreated={} " +
                "bonusMalusColumnsCreated={} gradesScored={} gradesPending={} bonusMalusScored={} " +
                "bonusMalusPending={} reportSheetsCreated={} reportSheetsRegenerated={} (teacher login: {})",
            schoolId, year.label, schoolClass.name, report.classCreated, report.classSubjectsCopied,
            report.assignedNow, report.alreadyAssigned, report.studentsCreated, report.enrollmentsCreated,
            report.sessionsEnsured, report.sessionsOpened, report.sheetsCreated, report.assessmentsCreated,
            report.bonusMalusColumnsCreated, report.gradesScored, report.gradesPending, report.bonusMalusScored,
            report.bonusMalusPending, report.reportSheetsCreated, report.reportSheetsRegenerated,
            MockUsersBootstrapRunner.EMAIL_TEACHER
        )
    }

    private class SeedReport {
        var classCreated = false
        var classSubjectsCopied = 0
        var assignedNow = 0
        var alreadyAssigned = 0
        var studentsCreated = 0
        var enrollmentsCreated = 0
        var sessionsEnsured = false
        var sessionsOpened = 0
        var sheetsCreated = 0
        var assessmentsCreated = 0
        var bonusMalusColumnsCreated = 0
        var gradesScored = 0
        var gradesPending = 0
        var bonusMalusScored = 0
        var bonusMalusPending = 0
        var reportSheetsCreated = 0
        var reportSheetsRegenerated = 0
    }

    private data class DemoStudent(
        val id: String,
        val enrollmentId: String,
        val matricule: String,
        val lastName: String,
        val firstName: String,
        val sex: Sex,
        val dateOfBirth: LocalDate,
        val placeOfBirth: String,
        val nationality: String,
        val contact: ContactInfo,
        val guardians: List<GuardianLink>,
        val active: Boolean = true
    ) {
        fun toStudent(schoolId: String): Student =
            Student(
                id = id,
                schoolId = schoolId,
                matricule = matricule,
                lastName = lastName,
                firstName = firstName,
                sex = sex,
                dateOfBirth = dateOfBirth,
                placeOfBirth = placeOfBirth,
                nationality = nationality,
                contact = contact,
                guardians = guardians,
                active = active
            )
    }

    companion object {
        private const val TEACHER_ID = MockUsersBootstrapRunner.STAFF_TEACHER
        private const val MAX_ASSIGNED_SUBJECTS = 2
        private const val ASSESSMENT_TITLE = "Note de séquence / Sequence mark"
        private const val BONUS_TITLE = "Bonus / Bonus"
        private const val MALUS_TITLE = "Malus / Malus"
        private val SEQUENCE_MAX_SCORE = BigDecimal("20")
        /** Q-25 : bonus et malus sont des points, pas des notes /20. */
        private val BONUS_MALUS_MAX_SCORE = BigDecimal("5")
        private val SEEDED_SESSION_NUMBERS = listOf(1, 2)

        /** Matières préférées (codes du seed El Shammah), dans l'ordre. */
        private val PREFERRED_SUBJECT_CODES = listOf("MATH", "LANG_FR", "EXP_ECR", "ETUDE_TXT", "CORR_ORTH", "FRENCH")

        /** Notes d'exemple /20 (≤ 2 décimales) ; `null` = reste PENDING (jamais 0). */
        private val SAMPLE_MARKS: List<BigDecimal?> = listOf(
            BigDecimal("14.50"), BigDecimal("9.00"), BigDecimal("16.00"), BigDecimal("11.50"),
            null, BigDecimal("18.00"), BigDecimal("7.25"), null, BigDecimal("12.75"), null
        )

        /** Bonus d'exemple (≤ 5 pts) : une minorité SCORED, le reste PENDING. */
        private val SAMPLE_BONUS: List<BigDecimal?> = listOf(
            null, BigDecimal("1.00"), null, null, BigDecimal("2.50"), null, null, BigDecimal("0.50"), null, null
        )

        /** Malus d'exemple (≤ 5 pts) : une minorité SCORED, le reste PENDING. */
        private val SAMPLE_MALUS: List<BigDecimal?> = listOf(
            null, null, null, BigDecimal("1.00"), null, null, BigDecimal("0.50"), null, null, null
        )

        private fun parentLinks(primaryMother: Boolean = true): List<GuardianLink> = listOf(
            GuardianLink(
                guardianId = MockUsersBootstrapRunner.GUARDIAN_PARENT,
                relationship = GuardianRelationship.MOTHER,
                primary = primaryMother,
                canViewGrades = true,
                canDownloadReportCards = true
            ),
            GuardianLink(
                guardianId = MockUsersBootstrapRunner.GUARDIAN_FATHER,
                relationship = GuardianRelationship.FATHER,
                primary = !primaryMother,
                canViewGrades = true,
                canDownloadReportCards = true
            )
        )

        /**
         * Élèves **fictifs** enrichis (identité, contact, tuteurs) — aucun nom réel (règle 12).
         * Les 3 premiers sont liés au parent démo (multi-enfants / rappels scolarité).
         */
        private val DEMO_STUDENTS: List<DemoStudent> = listOf(
            DemoStudent(
                id = "stu-demo-01",
                enrollmentId = "enr-demo-01",
                matricule = "DEMO-2026-01",
                lastName = "DEMO",
                firstName = "Abena Kofi",
                sex = Sex.MALE,
                dateOfBirth = LocalDate.of(2014, 3, 12),
                placeOfBirth = "Douala",
                nationality = "Camerounaise",
                contact = ContactInfo(
                    phones = listOf("+237 6 90 00 00 01"),
                    email = "abena.demo@elshammah.demo",
                    address = "Bonamoussadi, Rue des Écoles",
                    city = "Douala"
                ),
                guardians = parentLinks(primaryMother = true)
            ),
            DemoStudent(
                id = "stu-demo-02",
                enrollmentId = "enr-demo-02",
                matricule = "DEMO-2026-02",
                lastName = "DEMO",
                firstName = "Ngozi Ada",
                sex = Sex.FEMALE,
                dateOfBirth = LocalDate.of(2014, 7, 28),
                placeOfBirth = "Yaoundé",
                nationality = "Camerounaise",
                contact = ContactInfo(
                    phones = listOf("+237 6 90 00 00 02"),
                    email = "ngozi.demo@elshammah.demo",
                    address = "Bonamoussadi, Rue des Écoles",
                    city = "Douala"
                ),
                guardians = parentLinks(primaryMother = true)
            ),
            DemoStudent(
                id = "stu-demo-03",
                enrollmentId = "enr-demo-03",
                matricule = "DEMO-2026-03",
                lastName = "DEMO",
                firstName = "Moussa Idriss",
                sex = Sex.MALE,
                dateOfBirth = LocalDate.of(2013, 11, 5),
                placeOfBirth = "Garoua",
                nationality = "Camerounaise",
                contact = ContactInfo(
                    phones = listOf("+237 6 90 00 00 03"),
                    address = "Makepe, Carrefour Lycée",
                    city = "Douala"
                ),
                guardians = parentLinks(primaryMother = false)
            ),
            DemoStudent(
                id = "stu-demo-04",
                enrollmentId = "enr-demo-04",
                matricule = "DEMO-2026-04",
                lastName = "DEMO",
                firstName = "Esther Miria",
                sex = Sex.FEMALE,
                dateOfBirth = LocalDate.of(2014, 1, 19),
                placeOfBirth = "Bafoussam",
                nationality = "Camerounaise",
                contact = ContactInfo(
                    phones = listOf("+237 6 90 00 00 04"),
                    city = "Douala"
                ),
                guardians = emptyList()
            ),
            DemoStudent(
                id = "stu-demo-05",
                enrollmentId = "enr-demo-05",
                matricule = "DEMO-2026-05",
                lastName = "DEMO",
                firstName = "Chantal Mengue",
                sex = Sex.FEMALE,
                dateOfBirth = LocalDate.of(2014, 9, 2),
                placeOfBirth = "Douala",
                nationality = "Camerounaise",
                contact = ContactInfo(
                    email = "chantal.demo@elshammah.demo",
                    address = "Akwa Nord",
                    city = "Douala"
                ),
                guardians = emptyList()
            ),
            DemoStudent(
                id = "stu-demo-06",
                enrollmentId = "enr-demo-06",
                matricule = "DEMO-2026-06",
                lastName = "DEMO",
                firstName = "Junior Ewane",
                sex = Sex.MALE,
                dateOfBirth = LocalDate.of(2013, 5, 21),
                placeOfBirth = "Limbé",
                nationality = "Camerounaise",
                contact = ContactInfo(phones = listOf("+237 6 90 00 00 06"), city = "Douala"),
                guardians = emptyList()
            ),
            DemoStudent(
                id = "stu-demo-07",
                enrollmentId = "enr-demo-07",
                matricule = "DEMO-2026-07",
                lastName = "DEMO",
                firstName = "Grace Mbarga",
                sex = Sex.FEMALE,
                dateOfBirth = LocalDate.of(2014, 12, 14),
                placeOfBirth = "Ebolowa",
                nationality = "Camerounaise",
                contact = ContactInfo(city = "Douala"),
                guardians = emptyList()
            ),
            DemoStudent(
                id = "stu-demo-08",
                enrollmentId = "enr-demo-08",
                matricule = "DEMO-2026-08",
                lastName = "DEMO",
                firstName = "Paul Ngoa",
                sex = Sex.MALE,
                dateOfBirth = LocalDate.of(2014, 4, 8),
                placeOfBirth = "Edéa",
                nationality = "Camerounaise",
                contact = ContactInfo(
                    phones = listOf("+237 6 90 00 00 08"),
                    email = "paul.demo@elshammah.demo",
                    city = "Douala"
                ),
                guardians = emptyList()
            ),
            DemoStudent(
                id = "stu-demo-09",
                enrollmentId = "enr-demo-09",
                matricule = "DEMO-2026-09",
                lastName = "DEMO",
                firstName = "Sandra Fotso",
                sex = Sex.FEMALE,
                dateOfBirth = LocalDate.of(2013, 8, 30),
                placeOfBirth = "Douala",
                nationality = "Camerounaise",
                contact = ContactInfo(address = "Deido", city = "Douala"),
                guardians = emptyList(),
                active = false
            ),
            DemoStudent(
                id = "stu-demo-10",
                enrollmentId = "enr-demo-10",
                matricule = "DEMO-2026-10",
                lastName = "DEMO",
                firstName = "Kevin Tchana",
                sex = Sex.MALE,
                dateOfBirth = LocalDate.of(2014, 2, 17),
                placeOfBirth = "Kribi",
                nationality = "Camerounaise",
                contact = ContactInfo(phones = listOf("+237 6 90 00 00 10"), city = "Douala"),
                guardians = emptyList()
            )
        )
    }
}
