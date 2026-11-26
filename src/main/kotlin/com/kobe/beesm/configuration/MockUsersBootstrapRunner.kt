package com.kobe.beesm.configuration

import com.kobe.beesm.configuration.seed.ElShammahSeedProperties
import com.kobe.beesm.model.academic.School
import com.kobe.beesm.model.shared.ContactInfo
import com.kobe.beesm.model.shared.LocalizedText
import com.kobe.beesm.model.shared.Sex
import com.kobe.beesm.model.staff.Civility
import com.kobe.beesm.model.staff.Staff
import com.kobe.beesm.model.staff.StaffFunction
import com.kobe.beesm.model.student.Guardian
import com.kobe.beesm.model.student.GuardianLink
import com.kobe.beesm.model.student.GuardianRelationship
import com.kobe.beesm.model.student.Student
import com.kobe.beesm.model.user.AccountStatus
import com.kobe.beesm.model.user.ProfileType
import com.kobe.beesm.model.user.Role
import com.kobe.beesm.model.user.UserAccount
import com.kobe.beesm.repository.academic.SchoolRepository
import com.kobe.beesm.repository.staff.StaffRepository
import com.kobe.beesm.repository.student.GuardianRepository
import com.kobe.beesm.repository.student.StudentRepository
import com.kobe.beesm.repository.user.UserAccountRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Seed DEV : comptes de connexion + fiches Staff / Guardian / Student de test.
 * Idempotent (username / id fixe). Voir docs/mock-logins.md.
 *
 * Ne crée **plus** la structure académique ni n’écrase le document [School] :
 * celle-ci appartient au tenant (chargeur JSON El Shammah, ou saisie UI).
 *
 * Désactiver en prod : `beesm.security.mock-users.enabled=false`
 */
@Component
@Order(20)
class MockUsersBootstrapRunner(
    private val securityProperties: SecurityProperties,
    private val elShammahSeedProperties: ElShammahSeedProperties,
    private val schoolRepository: SchoolRepository,
    private val staffRepository: StaffRepository,
    private val guardianRepository: GuardianRepository,
    private val studentRepository: StudentRepository,
    private val userAccountRepository: UserAccountRepository,
    private val passwordEncoder: PasswordEncoder
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val cfg = securityProperties.mockUsers
        if (!cfg.enabled) {
            log.info("Mock users seed skipped (beesm.security.mock-users.enabled=false)")
            return
        }
        if (cfg.password.length < 8) {
            log.warn("Mock users seed skipped: password too short")
            return
        }

        val passwordHash = requireNotNull(passwordEncoder.encode(cfg.password)) {
            "PasswordEncoder returned null hash"
        }
        val superAdminUsername = securityProperties.bootstrap.username.trim().ifBlank { "superadmin" }
        val preferredSchoolId = elShammahSeedProperties.schoolId.takeIf { elShammahSeedProperties.enabled }
            ?: cfg.schoolId

        resolveTenantSchool(preferredSchoolId, cfg.schoolCode)
            .flatMap { school ->
                val schoolId = requireNotNull(school.id) { "School.id missing" }
                if (schoolId != preferredSchoolId) {
                    log.warn(
                        "Mock users attach to existing tenant id={} (preferred={}) — align NEXT_PUBLIC_DEFAULT_SCHOOL_ID or wipe Mongo",
                        schoolId,
                        preferredSchoolId
                    )
                }
                ensureStaff(schoolId)
                    .then(linkHeadOfSchoolIfNeeded(school))
                    .then(ensureGuardianAndStudent(schoolId))
                    .then(ensureAccounts(schoolId, passwordHash, superAdminUsername))
                    .thenReturn(schoolId)
            }
            .doOnSuccess { schoolId ->
                log.info("Mock users ready schoolId={} (logins: docs/mock-logins.md)", schoolId)
            }
            .doOnError { error ->
                log.error("Mock users seed failed: {}", error.message)
            }
            .block()
    }

    /**
     * Rattache les comptes au tenant déjà créé (seed JSON / UI).
     * Ne recrée une coquille School que si le seed El Shammah est désactivé.
     */
    private fun resolveTenantSchool(preferredSchoolId: String, schoolCode: String): Mono<School> {
        val byPreferred = schoolRepository.findById(preferredSchoolId)
        val byCode = schoolRepository.findByCode(schoolCode)
        val byLegacyId = schoolRepository.findById(LEGACY_DEMO_SCHOOL_ID)
        val byLegacyCode = schoolRepository.findByCode(LEGACY_DEMO_SCHOOL_CODE)

        return byPreferred
            .switchIfEmpty(byCode)
            .switchIfEmpty(byLegacyId)
            .switchIfEmpty(byLegacyCode)
            .switchIfEmpty(
                Mono.defer {
                    if (elShammahSeedProperties.enabled) {
                        Mono.error(
                            IllegalStateException(
                                "Tenant school not found (id=$preferredSchoolId code=$schoolCode). " +
                                    "El Shammah seed should have created it (@Order 15)."
                            )
                        )
                    } else {
                        log.warn(
                            "Creating minimal school shell id={} code={} (El Shammah seed disabled)",
                            preferredSchoolId,
                            schoolCode
                        )
                        schoolRepository.save(
                            School(
                                id = preferredSchoolId,
                                code = schoolCode,
                                name = LocalizedText(fr = schoolCode, en = schoolCode),
                                shortName = schoolCode,
                                active = true
                            )
                        )
                    }
                }
            )
    }

    /** Seul lien métier autorisé ici : chef d’établissement → fiche staff mock directeur. */
    private fun linkHeadOfSchoolIfNeeded(school: School): Mono<Void> {
        if (!school.headOfSchoolStaffId.isNullOrBlank()) {
            return Mono.empty()
        }
        school.headOfSchoolStaffId = STAFF_DIRECTOR
        return schoolRepository.save(school).then()
    }

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
                // SECRETARY + ACCOUNTANT : admin démo peut tester paiements / workspace Solvabilité (BR-F09).
                functions = setOf(StaffFunction.SECRETARY, StaffFunction.ACCOUNTANT),
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
                email = EMAIL_TEACHER
                // teachableSubjectIds : laissé vide — à rattacher aux vraies matières du tenant (passe suivante)
            )
        )

    private fun upsertStaff(
        id: String,
        schoolId: String,
        lastName: String,
        firstName: String,
        civility: Civility,
        functions: Set<StaffFunction>,
        email: String
    ): Mono<Staff> =
        staffRepository.findById(id)
            .flatMap { existing ->
                existing.schoolId = schoolId
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
                // Ne pas réinjecter d’anciens subject-demo-* orphelins.
                if (existing.teachableSubjectIds.any { it.startsWith("subject-demo-") }) {
                    existing.teachableSubjectIds = emptySet()
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
                            teachableSubjectIds = emptySet(),
                            active = true
                        )
                    )
                }
            )

    private fun ensureGuardianAndStudent(schoolId: String): Mono<Void> =
        ensureGuardian(
            id = GUARDIAN_PARENT,
            schoolId = schoolId,
            lastName = "Mbarga",
            firstName = "Claire",
            sex = Sex.FEMALE,
            contact = ContactInfo(
                phones = listOf("+237 6 77 00 00 10"),
                email = EMAIL_PARENT,
                address = "Bonamoussadi, Rue des Écoles",
                city = "Douala"
            )
        ).then(
            ensureGuardian(
                id = GUARDIAN_FATHER,
                schoolId = schoolId,
                lastName = "Mbarga",
                firstName = "Jean",
                sex = Sex.MALE,
                contact = ContactInfo(
                    phones = listOf("+237 6 77 00 00 11"),
                    email = "pere.demo@elshammah.demo",
                    address = "Bonamoussadi, Rue des Écoles",
                    city = "Douala"
                )
            )
        ).then(
            studentRepository.findById(STUDENT_DEMO)
                .flatMap { existing ->
                    enrichLoginDemoStudent(existing, schoolId)
                    studentRepository.save(existing)
                }
                .switchIfEmpty(
                    Mono.defer {
                        studentRepository.save(
                            Student(
                                id = STUDENT_DEMO,
                                schoolId = schoolId,
                                matricule = STUDENT_MATRICULE,
                                lastName = "Mbarga",
                                firstName = "Léo Demo",
                                sex = Sex.MALE,
                                dateOfBirth = java.time.LocalDate.of(2015, 6, 18),
                                placeOfBirth = "Douala",
                                nationality = "Camerounaise",
                                contact = ContactInfo(
                                    phones = listOf("+237 6 90 00 01 00"),
                                    email = EMAIL_STUDENT,
                                    address = "Bonamoussadi, Rue des Écoles",
                                    city = "Douala"
                                ),
                                guardians = listOf(
                                    GuardianLink(
                                        guardianId = GUARDIAN_PARENT,
                                        relationship = GuardianRelationship.MOTHER,
                                        primary = true,
                                        canViewGrades = true,
                                        canDownloadReportCards = true
                                    ),
                                    GuardianLink(
                                        guardianId = GUARDIAN_FATHER,
                                        relationship = GuardianRelationship.FATHER,
                                        primary = false,
                                        canViewGrades = true,
                                        canDownloadReportCards = true
                                    )
                                ),
                                active = true
                            )
                        )
                    }
                )
        ).then()

    private fun ensureGuardian(
        id: String,
        schoolId: String,
        lastName: String,
        firstName: String,
        sex: Sex,
        contact: ContactInfo
    ): Mono<Guardian> =
        guardianRepository.findById(id)
            .flatMap { existing ->
                existing.schoolId = schoolId
                if (existing.contact.phones.isEmpty() && existing.contact.email.isNullOrBlank()) {
                    existing.contact = contact
                }
                guardianRepository.save(existing)
            }
            .switchIfEmpty(
                Mono.defer {
                    guardianRepository.save(
                        Guardian(
                            id = id,
                            schoolId = schoolId,
                            lastName = lastName,
                            firstName = firstName,
                            sex = sex,
                            contact = contact,
                            active = true
                        )
                    )
                }
            )

    /** Complète la fiche élève login démo si les nouveaux champs sont vides. */
    private fun enrichLoginDemoStudent(existing: Student, schoolId: String) {
        existing.schoolId = schoolId
        if (existing.dateOfBirth == null) {
            existing.dateOfBirth = java.time.LocalDate.of(2015, 6, 18)
        }
        if (existing.placeOfBirth.isNullOrBlank()) {
            existing.placeOfBirth = "Douala"
        }
        if (existing.nationality.isNullOrBlank()) {
            existing.nationality = "Camerounaise"
        }
        val c = existing.contact
        if (c.phones.isEmpty() && c.email.isNullOrBlank() && c.address.isNullOrBlank()) {
            existing.contact = ContactInfo(
                phones = listOf("+237 6 90 00 01 00"),
                email = EMAIL_STUDENT,
                address = "Bonamoussadi, Rue des Écoles",
                city = "Douala"
            )
        }
        if (existing.guardians.isEmpty()) {
            existing.guardians = listOf(
                GuardianLink(
                    guardianId = GUARDIAN_PARENT,
                    relationship = GuardianRelationship.MOTHER,
                    primary = true,
                    canViewGrades = true,
                    canDownloadReportCards = true
                ),
                GuardianLink(
                    guardianId = GUARDIAN_FATHER,
                    relationship = GuardianRelationship.FATHER,
                    primary = false,
                    canViewGrades = true,
                    canDownloadReportCards = true
                )
            )
        }
    }

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
        /** Ancien id local ; conservé uniquement pour rattacher une base déjà peuplée. */
        const val LEGACY_DEMO_SCHOOL_ID = "school-demo-elshammah"
        const val LEGACY_DEMO_SCHOOL_CODE = "ELSHAMMAH-DEMO"

        const val STAFF_DIRECTOR = "staff-demo-director"
        const val STAFF_ADMIN = "staff-demo-admin"
        const val STAFF_SG = "staff-demo-sg"
        const val STAFF_TEACHER = "staff-demo-teacher"
        const val GUARDIAN_PARENT = "guardian-demo-parent"
        /** Second tuteur démo (sans compte login) — multi-tuteurs sur fiche élève. */
        const val GUARDIAN_FATHER = "guardian-demo-father"
        const val STUDENT_DEMO = "student-demo-01"
        const val STUDENT_MATRICULE = "250000001"

        const val EMAIL_SUPER_ADMIN = "superadmin@bee-sm.local"
        const val EMAIL_DIRECTOR = "directeur@elshammah.demo"
        const val EMAIL_ADMIN = "admin@elshammah.demo"
        const val EMAIL_SG = "surveillant@elshammah.demo"
        const val EMAIL_TEACHER = "enseignant@elshammah.demo"
        const val EMAIL_PARENT = "parent.demo@elshammah.demo"
        const val EMAIL_STUDENT = "eleve.demo@elshammah.demo"
    }
}
