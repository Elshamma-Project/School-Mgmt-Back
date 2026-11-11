package com.kobe.smels.configuration

import com.kobe.smels.model.academic.AcademicYear
import com.kobe.smels.model.academic.AcademicYearStatus
import com.kobe.smels.model.academic.ClassSubject
import com.kobe.smels.model.academic.Level
import com.kobe.smels.model.academic.Room
import com.kobe.smels.model.academic.RoomType
import com.kobe.smels.model.academic.OfficialHeader
import com.kobe.smels.model.academic.School
import com.kobe.smels.model.academic.SchoolClass
import com.kobe.smels.model.academic.Subject
import com.kobe.smels.model.academic.SubjectGroup
import com.kobe.smels.model.academic.SubjectKind
import com.kobe.smels.model.academic.Term
import com.kobe.smels.model.grading.AppreciationScale
import com.kobe.smels.model.grading.EvaluationSession
import com.kobe.smels.model.grading.GradeSheet
import com.kobe.smels.model.grading.GradeStatus
import com.kobe.smels.model.grading.GradingScheme
import com.kobe.smels.model.grading.MarkSource
import com.kobe.smels.model.grading.ScoreBand
import com.kobe.smels.model.grading.SequenceMark
import com.kobe.smels.model.grading.SessionStatus
import com.kobe.smels.model.grading.SheetStatus
import com.kobe.smels.model.shared.ContactInfo
import com.kobe.smels.model.shared.Cycle
import com.kobe.smels.model.shared.Language
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.Section
import com.kobe.smels.model.shared.Sex
import com.kobe.smels.model.staff.Civility
import com.kobe.smels.model.staff.Staff
import com.kobe.smels.model.staff.StaffFunction
import com.kobe.smels.model.student.Enrollment
import com.kobe.smels.model.student.EnrollmentStatus
import com.kobe.smels.model.student.Guardian
import com.kobe.smels.model.student.GuardianLink
import com.kobe.smels.model.student.GuardianRelationship
import com.kobe.smels.model.student.Student
import com.kobe.smels.model.user.AccountStatus
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.model.user.UserAccount
import com.kobe.smels.repository.academic.AcademicYearRepository
import com.kobe.smels.repository.academic.ClassSubjectRepository
import com.kobe.smels.repository.academic.LevelRepository
import com.kobe.smels.repository.academic.RoomRepository
import com.kobe.smels.repository.academic.SchoolClassRepository
import com.kobe.smels.repository.academic.SchoolRepository
import com.kobe.smels.repository.academic.SubjectGroupRepository
import com.kobe.smels.repository.academic.SubjectRepository
import com.kobe.smels.repository.grading.EvaluationSessionRepository
import com.kobe.smels.repository.grading.GradeSheetRepository
import com.kobe.smels.repository.grading.GradingSchemeRepository
import com.kobe.smels.repository.grading.SequenceMarkRepository
import com.kobe.smels.repository.staff.StaffRepository
import com.kobe.smels.repository.student.EnrollmentRepository
import com.kobe.smels.repository.student.GuardianRepository
import com.kobe.smels.repository.student.StudentRepository
import com.kobe.smels.repository.user.UserAccountRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Seed DEV : école démo + fiches Staff/Guardian/Student + comptes pour chaque rôle connectable.
 * Idempotent (par username / id fixe). Voir docs/mock-logins.md.
 *
 * Désactiver en prod : smels.security.mock-users.enabled=false
 */
@Component
@Order(20)
class MockUsersBootstrapRunner(
    private val securityProperties: SecurityProperties,
    private val schoolRepository: SchoolRepository,
    private val staffRepository: StaffRepository,
    private val guardianRepository: GuardianRepository,
    private val studentRepository: StudentRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val userAccountRepository: UserAccountRepository,
    private val academicYearRepository: AcademicYearRepository,
    private val levelRepository: LevelRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val subjectRepository: SubjectRepository,
    private val subjectGroupRepository: SubjectGroupRepository,
    private val classSubjectRepository: ClassSubjectRepository,
    private val roomRepository: RoomRepository,
    private val gradingSchemeRepository: GradingSchemeRepository,
    private val evaluationSessionRepository: EvaluationSessionRepository,
    private val gradeSheetRepository: GradeSheetRepository,
    private val sequenceMarkRepository: SequenceMarkRepository,
    private val passwordEncoder: PasswordEncoder
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val cfg = securityProperties.mockUsers
        if (!cfg.enabled) {
            log.info("Mock users seed skipped (smels.security.mock-users.enabled=false)")
            return
        }
        if (cfg.password.length < 8) {
            log.warn("Mock users seed skipped: password too short")
            return
        }

        val schoolId = cfg.schoolId
        val passwordHash = requireNotNull(passwordEncoder.encode(cfg.password)) {
            "PasswordEncoder returned null hash"
        }
        val superAdminUsername = securityProperties.bootstrap.username.trim().ifBlank { "superadmin" }

        ensureSchool(schoolId, cfg.schoolCode)
            .then(ensureStaff(schoolId))
            .then(ensureAcademicDemo(schoolId))
            .then(ensureGuardianAndStudent(schoolId))
            .then(ensureEnrollmentAndGradingDemo(schoolId))
            .then(ensureAccounts(schoolId, passwordHash, superAdminUsername))
            .doOnSuccess {
                log.info(
                    "Mock users ready schoolId={} (logins: docs/mock-logins.md)",
                    schoolId
                )
            }
            .doOnError { error ->
                log.error("Mock users seed failed: {}", error.message)
            }
            .block()
    }

    private fun ensureSchool(schoolId: String, code: String): Mono<School> =
        schoolRepository.findById(schoolId)
            .switchIfEmpty(
                schoolRepository.findByCode(code)
                    .switchIfEmpty(
                        Mono.defer {
                            schoolRepository.save(demoSchool(schoolId, code))
                        }
                    )
            )
            .flatMap { school ->
                var dirty = false
                // Réaligner l’en-tête officiel démo sur le modèle bulletin El Shammah (hors logo).
                if (school.officialHeaders.isEmpty() || looksLikePlaceholderHeader(school)) {
                    school.officialHeaders = demoOfficialHeaders()
                    dirty = true
                }
                if (school.name.fr?.contains("démo", ignoreCase = true) == true ||
                    school.name.en?.contains("Demo", ignoreCase = true) == true
                ) {
                    school.name = LocalizedText(
                        fr = "COLLÈGE BILINGUE EL SHAMMAH",
                        en = "EL SHAMMAH BILINGUAL COLLEGE"
                    )
                    school.shortName = "El Shammah"
                    dirty = true
                }
                if (school.reportCardNotice == null) {
                    school.reportCardNotice = LocalizedText(
                        fr = "Bulletin consultable par les parents via l’espace famille.",
                        en = "Report card available to parents in the family portal."
                    )
                    dirty = true
                }
                if (school.reportCardDisclaimer == null) {
                    school.reportCardDisclaimer = LocalizedText(
                        fr = "Le bulletin est délivré sans rature ni surcharge.",
                        en = "This report card is issued without erasure or alteration."
                    )
                    dirty = true
                }
                if (school.contact.phones.isEmpty() ||
                    school.contact.phones == listOf("+237 600 000 000")
                ) {
                    school.contact = school.contact.copy(
                        phones = listOf("690408433", "676595725", "656434258"),
                        email = school.contact.email ?: "contact@elshammah.demo",
                        address = school.contact.address,
                        city = school.contact.city ?: "Yaoundé"
                    )
                    dirty = true
                }
                if (school.postalBox.isNullOrBlank() || school.postalBox == "BP 0000") {
                    school.postalBox = "YDE"
                    dirty = true
                }
                if (school.signaturePlace.isNullOrBlank()) {
                    school.signaturePlace = "YAOUNDE"
                    dirty = true
                }
                if (dirty) schoolRepository.save(school) else Mono.just(school)
            }

    private fun looksLikePlaceholderHeader(school: School): Boolean {
        val first = school.officialHeaders.firstOrNull() ?: return true
        val ministryFr = first.ministry.fr.orEmpty()
        // Ancien seed générique (casse / libellés différents du bulletin réel).
        return ministryFr.contains("Ministère", ignoreCase = false) ||
            first.regionalDelegation?.en?.contains("Centre Regional", ignoreCase = true) == true
    }

    private fun demoSchool(schoolId: String, code: String): School =
        School(
            id = schoolId,
            code = code,
            name = LocalizedText(
                fr = "COLLÈGE BILINGUE EL SHAMMAH",
                en = "EL SHAMMAH BILINGUAL COLLEGE"
            ),
            shortName = "El Shammah",
            officialHeaders = demoOfficialHeaders(),
            contact = ContactInfo(
                phones = listOf("690408433", "676595725", "656434258"),
                email = "contact@elshammah.demo",
                city = "Yaoundé"
            ),
            postalBox = "YDE",
            signaturePlace = "YAOUNDE",
            headTitle = LocalizedText(fr = "LE PRINCIPAL", en = "THE PRINCIPAL"),
            reportCardNotice = LocalizedText(
                fr = "Bulletin consultable par les parents via l’espace famille.",
                en = "Report card available to parents in the family portal."
            ),
            reportCardDisclaimer = LocalizedText(
                fr = "Le bulletin est délivré sans rature ni surcharge.",
                en = "This report card is issued without erasure or alteration."
            ),
            active = true
        )

    /**
     * En-tête type bulletin : colonne FR | logo | colonne EN.
     * Les libellés suivent le document officiel El Shammah (hors logo, fourni séparément).
     */
    private fun demoOfficialHeaders(): List<OfficialHeader> =
        listOf(
            OfficialHeader(
                cycles = setOf(Cycle.SECONDAIRE_1ER_CYCLE, Cycle.SECONDAIRE_2ND_CYCLE),
                republic = LocalizedText(
                    fr = "REPUBLIQUE DU CAMEROUN",
                    en = "REPUBLIC OF CAMEROON"
                ),
                motto = LocalizedText(
                    fr = "Paix-Travail-Patrie",
                    en = "Peace-Work-Fatherland"
                ),
                ministry = LocalizedText(
                    fr = "MINISTERE DES ENSEIGNEMENTS SECONDAIRES",
                    en = "MINISTRY OF SECONDARY EDUCATION"
                ),
                regionalDelegation = LocalizedText(
                    fr = "DELEGATION REGIONALE DU CENTRE",
                    en = "REGIONAL DELEGATION OF THE CENTER"
                ),
                divisionalDelegation = LocalizedText(
                    fr = "DELEGATION DEPARTEMENTALE DU MFOUNDI",
                    en = "DIVISIONAL DELEGATION OF MFOUNDI"
                )
            ),
            OfficialHeader(
                cycles = setOf(Cycle.PRIMAIRE, Cycle.MATERNELLE),
                republic = LocalizedText(
                    fr = "REPUBLIQUE DU CAMEROUN",
                    en = "REPUBLIC OF CAMEROON"
                ),
                motto = LocalizedText(
                    fr = "Paix-Travail-Patrie",
                    en = "Peace-Work-Fatherland"
                ),
                ministry = LocalizedText(
                    fr = "MINISTERE DE L’EDUCATION DE BASE",
                    en = "MINISTRY OF BASIC EDUCATION"
                ),
                regionalDelegation = LocalizedText(
                    fr = "DELEGATION REGIONALE DU CENTRE",
                    en = "REGIONAL DELEGATION OF THE CENTER"
                ),
                divisionalDelegation = LocalizedText(
                    fr = "DELEGATION DEPARTEMENTALE DU MFOUNDI",
                    en = "DIVISIONAL DELEGATION OF MFOUNDI"
                )
            )
        )

    private fun ensureStaff(schoolId: String): Mono<Void> =
        Mono.`when`(
            upsertStaff(
                id = STAFF_DIRECTOR,
                schoolId = schoolId,
                lastName = "Directeur",
                firstName = "Demo",
                civility = Civility.MR,
                functions = setOf(StaffFunction.HEAD_OF_SCHOOL, StaffFunction.TEACHER),
                email = EMAIL_DIRECTOR
            ),
            upsertStaff(
                id = STAFF_ADMIN,
                schoolId = schoolId,
                lastName = "Administration",
                firstName = "Demo",
                civility = Civility.MME,
                functions = setOf(StaffFunction.SECRETARY),
                email = EMAIL_ADMIN
            ),
            upsertStaff(
                id = STAFF_SG,
                schoolId = schoolId,
                lastName = "Surveillant",
                firstName = "General",
                civility = Civility.MR,
                functions = setOf(StaffFunction.SUPERVISOR),
                email = EMAIL_SG
            ),
            upsertStaff(
                id = STAFF_TEACHER,
                schoolId = schoolId,
                lastName = "Enseignant",
                firstName = "Demo",
                civility = Civility.MME,
                functions = setOf(StaffFunction.TEACHER),
                email = EMAIL_TEACHER,
                teachableSubjectIds = setOf(
                    SUBJECT_FR, SUBJECT_ANG, SUBJECT_MATH, SUBJECT_PC, SUBJECT_SVT, SUBJECT_EPS
                )
            )
        )

    private fun upsertStaff(
        id: String,
        schoolId: String,
        lastName: String,
        firstName: String,
        civility: Civility,
        functions: Set<StaffFunction>,
        email: String,
        teachableSubjectIds: Set<String> = emptySet()
    ): Mono<Staff> =
        staffRepository.findById(id)
            .flatMap { existing ->
                // Réaligner le contact démo (anciens seeds sans e-mail).
                existing.lastName = lastName
                existing.firstName = firstName
                existing.civility = civility
                existing.functions = functions
                existing.contact = ContactInfo(
                    phones = existing.contact.phones,
                    email = email,
                    address = existing.contact.address,
                    city = existing.contact.city
                )
                if (teachableSubjectIds.isNotEmpty()) {
                    existing.teachableSubjectIds = teachableSubjectIds
                }
                existing.active = true
                staffRepository.save(existing)
            }
            .switchIfEmpty(
                Mono.defer {
                    staffRepository.save(
                        Staff(
                            id = id,
                            schoolId = schoolId,
                            staffNumber = id.uppercase(),
                            lastName = lastName,
                            firstName = firstName,
                            civility = civility,
                            sex = Sex.MALE,
                            contact = ContactInfo(email = email),
                            functions = functions,
                            teachableSubjectIds = teachableSubjectIds,
                            active = true
                        )
                    )
                }
            )

    /** Année / niveaux / salles / groupes / matières / classes / ClassSubject. */
    private fun ensureAcademicDemo(schoolId: String): Mono<Void> {
        val yearStart = LocalDate.of(2025, 9, 1)
        val yearEnd = LocalDate.of(2026, 6, 30)
        return academicYearRepository.findById(YEAR_DEMO)
            .switchIfEmpty(
                Mono.defer {
                    academicYearRepository.save(
                        AcademicYear(
                            id = YEAR_DEMO,
                            schoolId = schoolId,
                            label = "2025-2026",
                            startDate = yearStart,
                            endDate = yearEnd,
                            current = true,
                            status = AcademicYearStatus.ACTIVE,
                            terms = listOf(
                                Term(
                                    number = 1,
                                    name = LocalizedText(fr = "1er trimestre", en = "1st term"),
                                    startDate = yearStart,
                                    endDate = LocalDate.of(2025, 12, 15)
                                ),
                                Term(
                                    number = 2,
                                    name = LocalizedText(fr = "2e trimestre", en = "2nd term"),
                                    startDate = LocalDate.of(2026, 1, 5),
                                    endDate = LocalDate.of(2026, 3, 31)
                                ),
                                Term(
                                    number = 3,
                                    name = LocalizedText(fr = "3e trimestre", en = "3rd term"),
                                    startDate = LocalDate.of(2026, 4, 1),
                                    endDate = yearEnd
                                )
                            )
                        )
                    )
                }
            )
            .then(ensureGradingScheme(schoolId))
            .then(
                Mono.`when`(
                    upsertLevel(
                        LEVEL_6EME, schoolId, "6EME",
                        LocalizedText(fr = "6ème", en = "Form 1"),
                        order = 1, next = "5EME"
                    ),
                    upsertLevel(
                        LEVEL_5EME, schoolId, "5EME",
                        LocalizedText(fr = "5ème", en = "Form 2"),
                        order = 2, next = "4EME"
                    ),
                    upsertRoom(ROOM_S1, schoolId, "Salle 1", RoomType.CLASSROOM, 45),
                    upsertRoom(ROOM_LAB, schoolId, "Labo sciences", RoomType.LABORATORY, 30),
                    upsertRoom(ROOM_INFO, schoolId, "Salle informatique", RoomType.COMPUTER_LAB, 28),
                    upsertGroup(
                        GROUP_LANG, schoolId, "LANG_LIT",
                        LocalizedText(fr = "Langues et littératures", en = "Languages and literature"),
                        1
                    ),
                    upsertGroup(
                        GROUP_SCI, schoolId, "SCI_TECH",
                        LocalizedText(fr = "Sciences et technologie", en = "Science and technology"),
                        2
                    ),
                    upsertSubject(
                        SUBJECT_FR, schoolId, "FR",
                        LocalizedText(fr = "Français", en = "French"),
                        LocalizedText(fr = "Fr", en = "Fr")
                    ),
                    upsertSubject(
                        SUBJECT_ANG, schoolId, "ANG",
                        LocalizedText(fr = "Anglais", en = "English"),
                        LocalizedText(fr = "Ang", en = "Eng")
                    ),
                    upsertSubject(
                        SUBJECT_MATH, schoolId, "MATH",
                        LocalizedText(fr = "Mathématiques", en = "Mathematics"),
                        LocalizedText(fr = "Maths", en = "Maths")
                    ),
                    upsertSubject(
                        SUBJECT_PC, schoolId, "PC",
                        LocalizedText(fr = "Physique-Chimie", en = "Physics-Chemistry"),
                        LocalizedText(fr = "PC", en = "PC")
                    ),
                    upsertSubject(
                        SUBJECT_SVT, schoolId, "SVT",
                        LocalizedText(fr = "SVT", en = "Biology"),
                        LocalizedText(fr = "SVT", en = "Bio")
                    ),
                    upsertSubject(
                        SUBJECT_EPS, schoolId, "EPS",
                        LocalizedText(fr = "EPS", en = "PE"),
                        LocalizedText(fr = "EPS", en = "PE"),
                        kind = SubjectKind.ACTIVITY
                    )
                )
            )
            .then(
                Mono.`when`(
                    upsertDemoClass(
                        schoolId, CLASS_6A, "6ème A",
                        LEVEL_6EME, "6EME", ROOM_S1
                    ),
                    upsertDemoClass(
                        schoolId, CLASS_5A, "5ème A",
                        LEVEL_5EME, "5EME", ROOM_S1
                    ),
                    upsertDemoClass(
                        schoolId, CLASS_6B, "6ème B",
                        LEVEL_6EME, "6EME", null
                    )
                )
            )
            .then(
                Mono.`when`(
                    upsertClassSubject(CS_FR_6A, schoolId, CLASS_6A, SUBJECT_FR, GROUP_LANG, BigDecimal("4"), 5, 1),
                    upsertClassSubject(CS_ANG_6A, schoolId, CLASS_6A, SUBJECT_ANG, GROUP_LANG, BigDecimal("3"), 3, 2),
                    upsertClassSubject(CS_MATH_6A, schoolId, CLASS_6A, SUBJECT_MATH, GROUP_SCI, BigDecimal("4"), 5, 3),
                    upsertClassSubject(CS_PC_6A, schoolId, CLASS_6A, SUBJECT_PC, GROUP_SCI, BigDecimal("3"), 3, 4),
                    upsertClassSubject(CS_SVT_6A, schoolId, CLASS_6A, SUBJECT_SVT, GROUP_SCI, BigDecimal("2"), 2, 5),
                    upsertClassSubject(CS_EPS_6A, schoolId, CLASS_6A, SUBJECT_EPS, GROUP_SCI, BigDecimal("1"), 2, 6),
                    upsertClassSubject(CS_MATH_5A, schoolId, CLASS_5A, SUBJECT_MATH, GROUP_SCI, BigDecimal("4"), 5, 1),
                    upsertClassSubject(CS_FR_5A, schoolId, CLASS_5A, SUBJECT_FR, GROUP_LANG, BigDecimal("4"), 5, 2)
                )
            )
            .then()
    }

    private fun ensureGradingScheme(schoolId: String): Mono<GradingScheme> =
        gradingSchemeRepository.findById(SCHEME_SECONDAIRE_FR)
            .switchIfEmpty(
                Mono.defer {
                    gradingSchemeRepository.save(
                        GradingScheme(
                            id = SCHEME_SECONDAIRE_FR,
                            schoolId = schoolId,
                            code = "SECONDAIRE_FR",
                            name = LocalizedText(
                                fr = "Secondaire francophone",
                                en = "Francophone secondary"
                            ),
                            cycles = setOf(Cycle.SECONDAIRE_1ER_CYCLE, Cycle.SECONDAIRE_2ND_CYCLE),
                            sections = setOf(Section.FRANCOPHONE),
                            maxScore = BigDecimal("20"),
                            passMark = BigDecimal("10"),
                            classSuccessThreshold = BigDecimal("12"),
                            decimals = 2,
                            appreciationScales = listOf(
                                AppreciationScale(
                                    code = "LETTER",
                                    bands = listOf(
                                        ScoreBand("A", LocalizedText(fr = "A", en = "A"), BigDecimal("16"), null),
                                        ScoreBand("B+", LocalizedText(fr = "B+", en = "B+"), BigDecimal("14"), BigDecimal("16")),
                                        ScoreBand("B", LocalizedText(fr = "B", en = "B"), BigDecimal("12"), BigDecimal("14")),
                                        ScoreBand("C", LocalizedText(fr = "C", en = "C"), BigDecimal.ZERO, BigDecimal("12"))
                                    )
                                )
                            ),
                            overallMentionBands = listOf(
                                ScoreBand(
                                    "TB",
                                    LocalizedText(fr = "TRÈS BIEN", en = "VERY GOOD"),
                                    BigDecimal("16"),
                                    null
                                ),
                                ScoreBand(
                                    "B",
                                    LocalizedText(fr = "BIEN", en = "GOOD"),
                                    BigDecimal("14"),
                                    BigDecimal("16")
                                ),
                                ScoreBand(
                                    "AB",
                                    LocalizedText(fr = "ASSEZ BIEN", en = "FAIRLY GOOD"),
                                    BigDecimal("12"),
                                    BigDecimal("14")
                                ),
                                ScoreBand(
                                    "PASSABLE",
                                    LocalizedText(fr = "PASSABLE", en = "PASS"),
                                    BigDecimal("10"),
                                    BigDecimal("12")
                                ),
                                ScoreBand(
                                    "INSUFFISANT",
                                    LocalizedText(fr = "TRAVAIL INSUFFISANT", en = "INSUFFICIENT"),
                                    BigDecimal.ZERO,
                                    BigDecimal("10")
                                )
                            ),
                            active = true
                        )
                    )
                }
            )

    /**
     * Inscription élève démo + 6 séquences + notes de séquence (pour générer un bulletin).
     */
    private fun ensureEnrollmentAndGradingDemo(schoolId: String): Mono<Void> {
        val enrollmentMono = enrollmentRepository.findById(ENROLLMENT_DEMO)
            .switchIfEmpty(
                Mono.defer {
                    enrollmentRepository.save(
                        Enrollment(
                            id = ENROLLMENT_DEMO,
                            schoolId = schoolId,
                            studentId = STUDENT_DEMO,
                            academicYearId = YEAR_DEMO,
                            classId = CLASS_6A,
                            levelId = LEVEL_6EME,
                            rollNumber = 1,
                            repeater = false,
                            newStudent = true,
                            status = EnrollmentStatus.ENROLLED,
                            enrolledOn = LocalDate.of(2025, 9, 2)
                        )
                    )
                }
            )

        // Jeu de test volontairement hétérogène (docs/mock-logins.md) :
        // DEV1-2 CLOSED, DEV3-4 OPEN, DEV5-6 PLANNED (pas encore ouvertes).
        val sessionsMono = Flux.range(1, 6)
            .concatMap { number ->
                val term = ((number - 1) / 2) + 1
                val id = "$SESSION_PREFIX$number"
                val desired = demoSessionStatus(number)
                val examDate = LocalDate.of(2025, 9, 10).plusWeeks((number - 1).toLong() * 6)
                evaluationSessionRepository.findById(id)
                    .flatMap { existing ->
                        applyDemoSessionState(existing, desired)
                        existing.examDate = existing.examDate ?: examDate
                        evaluationSessionRepository.save(existing)
                    }
                    .switchIfEmpty(
                        Mono.defer {
                            val session = EvaluationSession(
                                id = id,
                                schoolId = schoolId,
                                academicYearId = YEAR_DEMO,
                                number = number,
                                termNumber = term,
                                shortLabel = "DEV$number",
                                name = LocalizedText(
                                    fr = "Séquence $number",
                                    en = "Sequence $number"
                                ),
                                examDate = examDate
                            )
                            applyDemoSessionState(session, desired)
                            evaluationSessionRepository.save(session)
                        }
                    )
            }
            .then()

        val marksMono = Flux.fromIterable(
            listOf(
                DemoMark(CS_FR_6A, listOf("12.5", "13.0", "11.5", "14.0", "13.5", "12.0")),
                DemoMark(CS_ANG_6A, listOf("10.0", "11.0", "9.5", "12.0", "11.5", "10.5")),
                DemoMark(CS_MATH_6A, listOf("14.0", "15.5", "13.0", "16.0", "14.5", "15.0")),
                DemoMark(CS_PC_6A, listOf("11.0", "12.0", "10.5", "13.0", "12.5", "11.5")),
                DemoMark(CS_SVT_6A, listOf("13.0", "12.5", "14.0", "13.5", "12.0", "13.0")),
                DemoMark(CS_EPS_6A, listOf("15.0", "14.0", "16.0", "15.5", "14.5", "15.0"))
            )
        ).concatMap { demo ->
            Flux.range(1, 6).concatMap { number ->
                val sessionId = "$SESSION_PREFIX$number"
                val sheetId = "sheet-${demo.classSubjectId}-$number"
                val markId = "sm-${demo.classSubjectId}-$number"
                val value = BigDecimal(demo.values[number - 1])
                gradeSheetRepository.findById(sheetId)
                    .switchIfEmpty(
                        Mono.defer {
                            gradeSheetRepository.save(
                                GradeSheet(
                                    id = sheetId,
                                    schoolId = schoolId,
                                    academicYearId = YEAR_DEMO,
                                    classId = CLASS_6A,
                                    classSubjectId = demo.classSubjectId,
                                    sessionId = sessionId,
                                    sessionNumber = number,
                                    teacherId = STAFF_TEACHER,
                                    status = demoSheetStatus(number),
                                    lastTotalizedAt = Instant.now()
                                )
                            )
                        }
                    )
                    .flatMap { sheet ->
                        val desired = demoSheetStatus(number)
                        if (sheet.status == desired) {
                            Mono.just(sheet)
                        } else {
                            sheet.status = desired
                            if (desired == SheetStatus.LOCKED) {
                                sheet.lockedAt = sheet.lockedAt ?: Instant.parse("2025-12-20T17:00:00Z")
                            } else {
                                sheet.lockedAt = null
                            }
                            gradeSheetRepository.save(sheet)
                        }
                    }
                    .then(
                        sequenceMarkRepository.findById(markId)
                            .flatMap { existing ->
                                existing.value = value
                                existing.status = GradeStatus.SCORED
                                existing.source = MarkSource.MANUAL_OVERRIDE
                                existing.computedAt = Instant.now()
                                sequenceMarkRepository.save(existing)
                            }
                            .switchIfEmpty(
                                Mono.defer {
                                    sequenceMarkRepository.save(
                                        SequenceMark(
                                            id = markId,
                                            schoolId = schoolId,
                                            academicYearId = YEAR_DEMO,
                                            classId = CLASS_6A,
                                            classSubjectId = demo.classSubjectId,
                                            sessionId = sessionId,
                                            sessionNumber = number,
                                            termNumber = ((number - 1) / 2) + 1,
                                            sheetId = sheetId,
                                            studentId = STUDENT_DEMO,
                                            enrollmentId = ENROLLMENT_DEMO,
                                            value = value,
                                            status = GradeStatus.SCORED,
                                            source = MarkSource.MANUAL_OVERRIDE,
                                            computedAt = Instant.now(),
                                            calculationVersion = "demo-seed"
                                        )
                                    )
                                }
                            )
                    )
            }
        }.then()

        return enrollmentMono.then(sessionsMono).then(marksMono)
    }

    private data class DemoMark(
        val classSubjectId: String,
        val values: List<String>
    )

    /** DEV1-2 clôturées, DEV3-4 ouvertes, DEV5-6 pas encore ouvertes. */
    private fun demoSessionStatus(number: Int): SessionStatus =
        when (number) {
            1, 2 -> SessionStatus.CLOSED
            3, 4 -> SessionStatus.OPEN
            else -> SessionStatus.PLANNED
        }

    private fun demoSheetStatus(number: Int): SheetStatus =
        when (demoSessionStatus(number)) {
            SessionStatus.CLOSED -> SheetStatus.LOCKED
            SessionStatus.OPEN -> SheetStatus.DRAFT
            SessionStatus.PLANNED -> SheetStatus.DRAFT
        }

    private fun applyDemoSessionState(session: EvaluationSession, status: SessionStatus) {
        session.status = status
        when (status) {
            SessionStatus.PLANNED -> {
                session.openedAt = null
                session.closedAt = null
                session.closedBy = null
                session.reopenedAt = null
                session.reopenedBy = null
                session.closeNote = null
            }
            SessionStatus.OPEN -> {
                session.openedAt = session.openedAt ?: Instant.parse("2025-09-15T08:00:00Z")
                session.closedAt = null
                session.closedBy = null
                session.reopenedAt = null
                session.reopenedBy = null
                session.closeNote = null
            }
            SessionStatus.CLOSED -> {
                session.openedAt = session.openedAt ?: Instant.parse("2025-09-15T08:00:00Z")
                session.closedAt = session.closedAt ?: Instant.parse("2025-12-20T17:00:00Z")
                session.reopenedAt = null
                session.reopenedBy = null
            }
        }
    }

    private fun upsertLevel(
        id: String,
        schoolId: String,
        code: String,
        name: LocalizedText,
        order: Int,
        next: String?
    ): Mono<Level> =
        levelRepository.findById(id)
            .switchIfEmpty(
                Mono.defer {
                    levelRepository.save(
                        Level(
                            id = id,
                            schoolId = schoolId,
                            code = code,
                            name = name,
                            cycle = Cycle.SECONDAIRE_1ER_CYCLE,
                            section = Section.FRANCOPHONE,
                            order = order,
                            equivalenceIndex = order,
                            nextLevelCode = next,
                            defaultGradingSchemeId = SCHEME_SECONDAIRE_FR,
                            active = true
                        )
                    )
                }
            )

    private fun upsertRoom(
        id: String,
        schoolId: String,
        name: String,
        type: RoomType,
        capacity: Int
    ): Mono<Room> =
        roomRepository.findById(id)
            .switchIfEmpty(
                Mono.defer {
                    roomRepository.save(
                        Room(
                            id = id,
                            schoolId = schoolId,
                            name = name,
                            type = type,
                            capacity = capacity,
                            active = true
                        )
                    )
                }
            )

    private fun upsertGroup(
        id: String,
        schoolId: String,
        code: String,
        name: LocalizedText,
        displayOrder: Int
    ): Mono<SubjectGroup> =
        subjectGroupRepository.findById(id)
            .switchIfEmpty(
                Mono.defer {
                    subjectGroupRepository.save(
                        SubjectGroup(
                            id = id,
                            schoolId = schoolId,
                            code = code,
                            name = name,
                            displayOrder = displayOrder,
                            active = true
                        )
                    )
                }
            )

    private fun upsertSubject(
        id: String,
        schoolId: String,
        code: String,
        name: LocalizedText,
        shortName: LocalizedText,
        kind: SubjectKind = SubjectKind.ACADEMIC
    ): Mono<Subject> =
        subjectRepository.findById(id)
            .switchIfEmpty(
                Mono.defer {
                    subjectRepository.save(
                        Subject(
                            id = id,
                            schoolId = schoolId,
                            code = code,
                            name = name,
                            shortName = shortName,
                            kind = kind,
                            active = true
                        )
                    )
                }
            )

    private fun upsertDemoClass(
        schoolId: String,
        id: String,
        name: String,
        levelId: String,
        levelCode: String,
        homeRoomId: String?
    ): Mono<SchoolClass> =
        schoolClassRepository.findById(id)
            .flatMap { existing ->
                existing.levelId = levelId
                existing.levelCode = levelCode
                existing.gradingSchemeId = SCHEME_SECONDAIRE_FR
                existing.homeRoomId = homeRoomId
                existing.mainTeacherId = STAFF_TEACHER
                existing.active = true
                schoolClassRepository.save(existing)
            }
            .switchIfEmpty(
                Mono.defer {
                    schoolClassRepository.save(
                        SchoolClass(
                            id = id,
                            schoolId = schoolId,
                            academicYearId = YEAR_DEMO,
                            levelId = levelId,
                            levelCode = levelCode,
                            section = Section.FRANCOPHONE,
                            cycle = Cycle.SECONDAIRE_1ER_CYCLE,
                            name = name,
                            defaultLanguage = Language.FR,
                            availableLanguages = setOf(Language.FR, Language.EN),
                            mainTeacherId = STAFF_TEACHER,
                            homeRoomId = homeRoomId,
                            gradingSchemeId = SCHEME_SECONDAIRE_FR,
                            capacity = 45,
                            active = true
                        )
                    )
                }
            )

    private fun upsertClassSubject(
        id: String,
        schoolId: String,
        classId: String,
        subjectId: String,
        groupId: String,
        coefficient: BigDecimal,
        weeklyHours: Int,
        displayOrder: Int
    ): Mono<ClassSubject> =
        classSubjectRepository.findById(id)
            .flatMap { existing ->
                existing.teacherId = STAFF_TEACHER
                existing.groupId = groupId
                existing.coefficient = coefficient
                existing.weeklyHours = weeklyHours
                existing.displayOrder = displayOrder
                existing.active = true
                classSubjectRepository.save(existing)
            }
            .switchIfEmpty(
                Mono.defer {
                    classSubjectRepository.save(
                        ClassSubject(
                            id = id,
                            schoolId = schoolId,
                            academicYearId = YEAR_DEMO,
                            classId = classId,
                            subjectId = subjectId,
                            groupId = groupId,
                            coefficient = coefficient,
                            teacherId = STAFF_TEACHER,
                            weeklyHours = weeklyHours,
                            displayOrder = displayOrder,
                            maxScore = BigDecimal("20"),
                            countsInAverage = true,
                            optional = false,
                            active = true
                        )
                    )
                }
            )

    private fun ensureGuardianAndStudent(schoolId: String): Mono<Void> =
        guardianRepository.findById(GUARDIAN_PARENT)
            .switchIfEmpty(
                Mono.defer {
                    guardianRepository.save(
                        Guardian(
                            id = GUARDIAN_PARENT,
                            schoolId = schoolId,
                            lastName = "Parent",
                            firstName = "Demo",
                            sex = Sex.FEMALE,
                            active = true
                        )
                    )
                }
            )
            .flatMap {
                studentRepository.findById(STUDENT_DEMO)
                    .switchIfEmpty(
                        Mono.defer {
                            studentRepository.save(
                                Student(
                                    id = STUDENT_DEMO,
                                    schoolId = schoolId,
                                    matricule = STUDENT_MATRICULE,
                                    lastName = "Élève",
                                    firstName = "Demo",
                                    sex = Sex.MALE,
                                    guardians = listOf(
                                        GuardianLink(
                                            guardianId = GUARDIAN_PARENT,
                                            relationship = GuardianRelationship.MOTHER,
                                            primary = true,
                                            canViewGrades = true,
                                            canDownloadReportCards = true
                                        )
                                    ),
                                    active = true
                                )
                            )
                        }
                    )
            }
            .then()

    private fun ensureAccounts(
        schoolId: String,
        passwordHash: String,
        superAdminUsername: String
    ): Mono<Void> {
        val saEmail = superAdminUsername.trim().lowercase().ifBlank { EMAIL_SUPER_ADMIN }
        val specs = listOf(
            AccountSpec(
                email = saEmail,
                legacyUsernames = listOf("superadmin", EMAIL_SUPER_ADMIN),
                schoolId = null,
                roles = setOf(Role.SUPER_ADMIN),
                profileType = ProfileType.NONE,
                profileId = null
            ),
            AccountSpec(
                email = EMAIL_DIRECTOR,
                legacyUsernames = listOf("directeur"),
                schoolId = schoolId,
                roles = setOf(Role.DIRECTOR),
                profileType = ProfileType.STAFF,
                profileId = STAFF_DIRECTOR
            ),
            AccountSpec(
                email = EMAIL_ADMIN,
                legacyUsernames = listOf("admin"),
                schoolId = schoolId,
                roles = setOf(Role.ADMINISTRATION),
                profileType = ProfileType.STAFF,
                profileId = STAFF_ADMIN
            ),
            AccountSpec(
                email = EMAIL_SG,
                legacyUsernames = listOf("surveillant"),
                schoolId = schoolId,
                roles = setOf(Role.SURVEILLANT_GENERAL),
                profileType = ProfileType.STAFF,
                profileId = STAFF_SG
            ),
            AccountSpec(
                email = EMAIL_TEACHER,
                legacyUsernames = listOf("enseignant"),
                schoolId = schoolId,
                roles = setOf(Role.TEACHER),
                profileType = ProfileType.STAFF,
                profileId = STAFF_TEACHER
            ),
            AccountSpec(
                email = EMAIL_PARENT,
                legacyUsernames = listOf("parent.demo"),
                schoolId = schoolId,
                roles = setOf(Role.PARENT),
                profileType = ProfileType.GUARDIAN,
                profileId = GUARDIAN_PARENT
            ),
            AccountSpec(
                email = EMAIL_STUDENT,
                legacyUsernames = listOf(STUDENT_MATRICULE),
                schoolId = schoolId,
                roles = setOf(Role.STUDENT),
                profileType = ProfileType.STUDENT,
                profileId = STUDENT_DEMO
            )
        )
        return Mono.`when`(specs.map { upsertAccount(it, passwordHash) })
    }

    private fun upsertAccount(spec: AccountSpec, passwordHash: String): Mono<UserAccount> {
        val email = spec.email.lowercase()
        return findExistingAccount(email, spec)
            .flatMap { existing ->
                // DEV : aligne e-mail / hash / rôles (migration depuis anciens logins courts)
                existing.username = email
                existing.passwordHash = passwordHash
                existing.status = AccountStatus.ACTIVE
                existing.mustChangePassword = false
                existing.failedLoginCount = 0
                existing.lockedUntil = null
                existing.roles = spec.roles
                existing.profileType = spec.profileType
                existing.profileId = spec.profileId
                if (spec.schoolId != null) {
                    existing.schoolId = spec.schoolId
                }
                userAccountRepository.save(existing)
            }
            .switchIfEmpty(
                Mono.defer {
                    userAccountRepository.save(
                        UserAccount(
                            schoolId = spec.schoolId,
                            username = email,
                            passwordHash = passwordHash,
                            roles = spec.roles,
                            profileType = spec.profileType,
                            profileId = spec.profileId,
                            status = AccountStatus.ACTIVE,
                            mustChangePassword = false
                        )
                    )
                }
            )
    }

    private fun findExistingAccount(email: String, spec: AccountSpec): Mono<UserAccount> {
        val byEmail = if (spec.schoolId == null) {
            userAccountRepository.findBySchoolIdIsNullAndUsername(email)
                .switchIfEmpty(userAccountRepository.findByUsername(email))
        } else {
            userAccountRepository.findBySchoolIdAndUsername(spec.schoolId, email)
        }
        return byEmail.switchIfEmpty(
            Mono.defer {
                Flux.fromIterable(spec.legacyUsernames.distinct())
                    .concatMap { legacy ->
                        if (spec.schoolId == null) {
                            userAccountRepository.findBySchoolIdIsNullAndUsername(legacy)
                                .switchIfEmpty(userAccountRepository.findByUsername(legacy))
                        } else {
                            userAccountRepository.findBySchoolIdAndUsername(spec.schoolId, legacy)
                        }
                    }
                    .next()
            }
        )
    }

    private data class AccountSpec(
        val email: String,
        val legacyUsernames: List<String> = emptyList(),
        val schoolId: String?,
        val roles: Set<Role>,
        val profileType: ProfileType,
        val profileId: String?
    )

    companion object {
        const val STAFF_DIRECTOR = "staff-demo-director"
        const val STAFF_ADMIN = "staff-demo-admin"
        const val STAFF_SG = "staff-demo-sg"
        const val STAFF_TEACHER = "staff-demo-teacher"
        const val GUARDIAN_PARENT = "guardian-demo-parent"
        const val STUDENT_DEMO = "student-demo-01"
        const val STUDENT_MATRICULE = "250000001"

        const val EMAIL_SUPER_ADMIN = "superadmin@smels.local"
        const val EMAIL_DIRECTOR = "directeur@elshammah.demo"
        const val EMAIL_ADMIN = "admin@elshammah.demo"
        const val EMAIL_SG = "surveillant@elshammah.demo"
        const val EMAIL_TEACHER = "enseignant@elshammah.demo"
        const val EMAIL_PARENT = "parent.demo@elshammah.demo"
        const val EMAIL_STUDENT = "eleve.demo@elshammah.demo"

        const val YEAR_DEMO = "year-demo-2025"
        const val SCHEME_SECONDAIRE_FR = "scheme-demo-secondaire-fr"
        const val LEVEL_6EME = "level-demo-6eme"
        const val LEVEL_5EME = "level-demo-5eme"
        const val CLASS_6A = "class-demo-6a"
        const val CLASS_6B = "class-demo-6b"
        const val CLASS_5A = "class-demo-5a"
        const val ROOM_S1 = "room-demo-s1"
        const val ROOM_LAB = "room-demo-lab"
        const val ROOM_INFO = "room-demo-info"
        const val GROUP_LANG = "group-demo-lang"
        const val GROUP_SCI = "group-demo-sci"
        const val SUBJECT_FR = "subject-demo-fr"
        const val SUBJECT_ANG = "subject-demo-ang"
        const val SUBJECT_MATH = "subject-demo-math"
        const val SUBJECT_PC = "subject-demo-pc"
        const val SUBJECT_SVT = "subject-demo-svt"
        const val SUBJECT_EPS = "subject-demo-eps"
        const val CS_FR_6A = "cs-demo-fr-6a"
        const val CS_ANG_6A = "cs-demo-ang-6a"
        const val CS_MATH_6A = "cs-demo-math-6a"
        const val CS_PC_6A = "cs-demo-pc-6a"
        const val CS_SVT_6A = "cs-demo-svt-6a"
        const val CS_EPS_6A = "cs-demo-eps-6a"
        const val CS_MATH_5A = "cs-demo-math-5a"
        const val CS_FR_5A = "cs-demo-fr-5a"
        const val ENROLLMENT_DEMO = "enrollment-demo-01"
        const val SESSION_PREFIX = "session-demo-"
    }
}
