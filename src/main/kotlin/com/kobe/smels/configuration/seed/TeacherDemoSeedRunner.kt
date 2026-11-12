package com.kobe.smels.configuration.seed

import com.kobe.smels.configuration.MockUsersBootstrapRunner
import com.kobe.smels.configuration.SecurityProperties
import com.kobe.smels.dto.grading.CreateAssessmentRequest
import com.kobe.smels.dto.grading.GenerateReportSheetRequest
import com.kobe.smels.model.academic.AcademicYear
import com.kobe.smels.model.academic.ClassSubject
import com.kobe.smels.model.academic.School
import com.kobe.smels.model.academic.SchoolClass
import com.kobe.smels.model.grading.AssessmentType
import com.kobe.smels.model.grading.EvaluationSession
import com.kobe.smels.model.grading.GradeSheet
import com.kobe.smels.model.grading.GradeStatus
import com.kobe.smels.model.grading.SessionStatus
import com.kobe.smels.model.grading.SheetStatus
import com.kobe.smels.model.shared.Cycle
import com.kobe.smels.model.shared.Section
import com.kobe.smels.model.shared.Sex
import com.kobe.smels.model.student.Enrollment
import com.kobe.smels.model.student.EnrollmentStatus
import com.kobe.smels.model.student.Student
import com.kobe.smels.dto.grading.UpsertGradeRequest
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.academic.AcademicYearRepository
import com.kobe.smels.repository.academic.ClassSubjectRepository
import com.kobe.smels.repository.academic.SchoolClassRepository
import com.kobe.smels.repository.academic.SchoolRepository
import com.kobe.smels.repository.academic.SubjectRepository
import com.kobe.smels.repository.grading.AssessmentRepository
import com.kobe.smels.repository.grading.EvaluationSessionRepository
import com.kobe.smels.repository.grading.GradeReportSheetRepository
import com.kobe.smels.repository.grading.GradeRepository
import com.kobe.smels.repository.grading.GradeSheetRepository
import com.kobe.smels.repository.staff.StaffRepository
import com.kobe.smels.repository.student.EnrollmentRepository
import com.kobe.smels.repository.student.StudentRepository
import com.kobe.smels.repository.user.UserAccountRepository
import com.kobe.smels.service.grading.AssessmentService
import com.kobe.smels.service.grading.EvaluationSessionService
import com.kobe.smels.service.grading.GradeReportSheetService
import com.kobe.smels.service.grading.GradeService
import com.kobe.smels.service.grading.GradingMapper
import com.kobe.smels.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.text.Normalizer

/**
 * Seed DEV « première tranche enseignant » : une 6ème francophone avec une dizaine d'élèves
 * **fictifs**, deux matières affectées à `staff-demo-teacher`, séquence 1 ouverte, une colonne
 * SEQUENCE_MARK avec quelques notes (certaines PENDING) et une fiche de report du trimestre 1.
 *
 * - `@Order(25)` : après [MockUsersBootstrapRunner] (20) qui crée le compte et la fiche Staff enseignant.
 * - Activé par `smels.security.mock-users.enabled` (DEV uniquement).
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
            log.info("Teacher demo seed skipped (smels.security.mock-users.enabled=false)")
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

        // 6. Séquence 1.
        val directorUser = syntheticUser(schoolId, "seed-demo-director", Role.DIRECTOR, ProfileType.NONE, null)
        val session = ensureSessionOne(directorUser, schoolId, yearId, report)

        // 7. Fiches / colonne SEQUENCE_MARK / notes.
        if (session != null && session.status == SessionStatus.OPEN) {
            val teacherUser = teacherUser(schoolId)
            assigned.forEachIndexed { index, cs ->
                seedSheetAndGrades(teacherUser, schoolId, session, cs, demoStudentIds, index, report)
            }
        } else if (session != null) {
            log.warn(
                "Teacher demo seed: session 1 is {} — sheets/grades not seeded (reopen as DIRECTOR if needed)",
                session.status
            )
        }

        // 8. Fiche de report du trimestre 1 (remplie).
        if (session != null && session.status != SessionStatus.PLANNED) {
            val teacherUser = teacherUser(schoolId)
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
            val student = studentRepository.findById(demo.id).block()
                ?: studentRepository.findBySchoolIdAndMatricule(schoolId, demo.matricule).block()
                ?: studentRepository.save(
                    Student(
                        id = demo.id,
                        schoolId = schoolId,
                        matricule = demo.matricule,
                        lastName = demo.lastName,
                        firstName = demo.firstName,
                        sex = demo.sex,
                        active = true
                    )
                ).block()?.also { report.studentsCreated++ }

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

    // ── Séquence 1 ──────────────────────────────────────────────────────────

    private fun ensureSessionOne(
        directorUser: CurrentUser,
        schoolId: String,
        yearId: String,
        report: SeedReport
    ): EvaluationSession? {
        var session = evaluationSessionRepository
            .findBySchoolIdAndAcademicYearIdAndNumber(schoolId, yearId, 1).block()
        if (session == null) {
            evaluationSessionService.ensureYearSessions(directorUser, yearId, schoolId).collectList().block()
            session = evaluationSessionRepository
                .findBySchoolIdAndAcademicYearIdAndNumber(schoolId, yearId, 1).block()
            report.sessionsEnsured = session != null
        }
        if (session == null) return null

        if (session.status == SessionStatus.PLANNED) {
            // Passe par le service : crée aussi les GradeSheets des ClassSubjects actifs (BR-11, BR-21).
            evaluationSessionService.open(directorUser, requireNotNull(session.id), schoolId).block()
            report.sessionOpened = true
            session = evaluationSessionRepository
                .findBySchoolIdAndAcademicYearIdAndNumber(schoolId, yearId, 1).block()
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
        val assessment = assessmentRepository.findBySchoolIdAndSheetId(schoolId, sheetId)
            .filter { it.type == AssessmentType.SEQUENCE_MARK }
            .next().block()
            ?: assessmentService.create(
                teacherUser,
                CreateAssessmentRequest(
                    sheetId = sheetId,
                    title = ASSESSMENT_TITLE,
                    type = AssessmentType.SEQUENCE_MARK,
                    maxScore = BigDecimal("20"),
                    weight = BigDecimal.ONE,
                    schoolId = schoolId
                )
            ).block()?.also { report.assessmentsCreated++ }?.let { created ->
                assessmentRepository.findByIdAndSchoolId(created.id, schoolId).block()
            }
        val assessmentId = assessment?.id ?: return

        studentIds.forEachIndexed { index, studentId ->
            val present = gradeRepository
                .findBySchoolIdAndAssessmentIdAndStudentId(schoolId, assessmentId, studentId).block()
            if (present != null) return@forEachIndexed
            val sample = SAMPLE_MARKS[(index + offset * 3) % SAMPLE_MARKS.size]
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
            if (sample == null) report.gradesPending++ else report.gradesScored++
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
        val existing = gradeReportSheetRepository
            .findBySchoolIdAndClassSubjectIdAndTermNumber(schoolId, csId, 1).block()
        if (existing != null) return
        try {
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
        } catch (error: Exception) {
            log.warn("Teacher demo seed: report sheet term 1 not generated: {}", error.message)
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
                "sessionsEnsured={} session1Opened={} sheetsCreated={} assessmentsCreated={} " +
                "gradesScored={} gradesPending={} reportSheetsCreated={} (teacher login: {})",
            schoolId, year.label, schoolClass.name, report.classCreated, report.classSubjectsCopied,
            report.assignedNow, report.alreadyAssigned, report.studentsCreated, report.enrollmentsCreated,
            report.sessionsEnsured, report.sessionOpened, report.sheetsCreated, report.assessmentsCreated,
            report.gradesScored, report.gradesPending, report.reportSheetsCreated,
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
        var sessionOpened = false
        var sheetsCreated = 0
        var assessmentsCreated = 0
        var gradesScored = 0
        var gradesPending = 0
        var reportSheetsCreated = 0
    }

    private data class DemoStudent(
        val id: String,
        val enrollmentId: String,
        val matricule: String,
        val lastName: String,
        val firstName: String,
        val sex: Sex
    )

    companion object {
        private const val TEACHER_ID = MockUsersBootstrapRunner.STAFF_TEACHER
        private const val MAX_ASSIGNED_SUBJECTS = 2
        private const val ASSESSMENT_TITLE = "Note de séquence / Sequence mark"

        /** Matières préférées (codes du seed El Shammah), dans l'ordre. */
        private val PREFERRED_SUBJECT_CODES = listOf("MATH", "LANG_FR", "EXP_ECR", "ETUDE_TXT", "CORR_ORTH", "FRENCH")

        /** Notes d'exemple /20 (≤ 2 décimales) ; `null` = reste PENDING (jamais 0). */
        private val SAMPLE_MARKS: List<BigDecimal?> = listOf(
            BigDecimal("14.50"), BigDecimal("9.00"), BigDecimal("16.00"), BigDecimal("11.50"),
            null, BigDecimal("18.00"), BigDecimal("7.25"), null, BigDecimal("12.75"), null
        )

        /** Élèves **fictifs** : aucun nom réel (règle 12). */
        private val DEMO_STUDENTS: List<DemoStudent> = listOf(
            Triple("Abena Kofi", Sex.MALE, 1),
            Triple("Ngozi Ada", Sex.FEMALE, 2),
            Triple("Élève Démo 03", Sex.MALE, 3),
            Triple("Élève Démo 04", Sex.FEMALE, 4),
            Triple("Élève Démo 05", Sex.FEMALE, 5),
            Triple("Élève Démo 06", Sex.MALE, 6),
            Triple("Élève Démo 07", Sex.FEMALE, 7),
            Triple("Élève Démo 08", Sex.MALE, 8),
            Triple("Élève Démo 09", Sex.FEMALE, 9),
            Triple("Élève Démo 10", Sex.MALE, 10)
        ).map { (firstName, sex, n) ->
            val nn = n.toString().padStart(2, '0')
            DemoStudent(
                id = "stu-demo-$nn",
                enrollmentId = "enr-demo-$nn",
                matricule = "DEMO-2026-$nn",
                lastName = "DEMO",
                firstName = firstName,
                sex = sex
            )
        }
    }
}
