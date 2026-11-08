package com.kobe.smels.configuration

import com.kobe.smels.model.academic.School
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.Sex
import com.kobe.smels.model.staff.Civility
import com.kobe.smels.model.staff.Staff
import com.kobe.smels.model.staff.StaffFunction
import com.kobe.smels.model.student.Guardian
import com.kobe.smels.model.student.GuardianLink
import com.kobe.smels.model.student.GuardianRelationship
import com.kobe.smels.model.student.Student
import com.kobe.smels.model.user.AccountStatus
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.model.user.UserAccount
import com.kobe.smels.repository.academic.SchoolRepository
import com.kobe.smels.repository.staff.StaffRepository
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
    private val userAccountRepository: UserAccountRepository,
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
            .then(ensureGuardianAndStudent(schoolId))
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
                            schoolRepository.save(
                                School(
                                    id = schoolId,
                                    code = code,
                                    name = LocalizedText(
                                        fr = "Collège bilingue démo El Shammah",
                                        en = "El Shammah Bilingual Demo College"
                                    ),
                                    shortName = "El Shammah Demo",
                                    signaturePlace = "YAOUNDE",
                                    headTitle = LocalizedText(fr = "LE PRINCIPAL", en = "THE PRINCIPAL"),
                                    active = true
                                )
                            )
                        }
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
                functions = setOf(StaffFunction.HEAD_OF_SCHOOL, StaffFunction.TEACHER)
            ),
            upsertStaff(
                id = STAFF_ADMIN,
                schoolId = schoolId,
                lastName = "Administration",
                firstName = "Demo",
                civility = Civility.MME,
                functions = setOf(StaffFunction.SECRETARY)
            ),
            upsertStaff(
                id = STAFF_SG,
                schoolId = schoolId,
                lastName = "Surveillant",
                firstName = "General",
                civility = Civility.MR,
                functions = setOf(StaffFunction.SUPERVISOR)
            ),
            upsertStaff(
                id = STAFF_TEACHER,
                schoolId = schoolId,
                lastName = "Enseignant",
                firstName = "Demo",
                civility = Civility.MME,
                functions = setOf(StaffFunction.TEACHER)
            )
        )

    private fun upsertStaff(
        id: String,
        schoolId: String,
        lastName: String,
        firstName: String,
        civility: Civility,
        functions: Set<StaffFunction>
    ): Mono<Staff> =
        staffRepository.findById(id)
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
                            functions = functions,
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
    }
}
