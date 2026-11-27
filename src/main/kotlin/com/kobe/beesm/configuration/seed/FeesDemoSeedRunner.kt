package com.kobe.beesm.configuration.seed

import com.kobe.beesm.configuration.MockUsersBootstrapRunner
import com.kobe.beesm.configuration.SecurityProperties
import com.kobe.beesm.model.fees.FeePayment
import com.kobe.beesm.model.fees.FeePaymentKind
import com.kobe.beesm.model.fees.FeePaymentMethod
import com.kobe.beesm.model.fees.FeePaymentStatus
import com.kobe.beesm.model.fees.FeesPolicy
import com.kobe.beesm.model.fees.TuitionInstallment
import com.kobe.beesm.model.fees.TuitionSchedule
import com.kobe.beesm.model.student.EnrollmentStatus
import com.kobe.beesm.repository.academic.AcademicYearRepository
import com.kobe.beesm.repository.academic.LevelRepository
import com.kobe.beesm.repository.academic.SchoolClassRepository
import com.kobe.beesm.repository.academic.SchoolRepository
import com.kobe.beesm.repository.fees.FeePaymentRepository
import com.kobe.beesm.repository.fees.FeesPolicyRepository
import com.kobe.beesm.repository.fees.TuitionScheduleRepository
import com.kobe.beesm.repository.student.EnrollmentRepository
import com.kobe.beesm.service.fees.SolvencyCalculator
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Seed DEV scolarité : barème **par niveau** + paiements variés pour visualiser SOLVENT / PENDING / INSOLVENT.
 *
 * - `@Order(26)` : après [TeacherDemoSeedRunner] (élèves + inscriptions).
 * - Insert-if-absent ; n’écrase jamais un paiement déjà présent.
 */
@Component
@Order(26)
class FeesDemoSeedRunner(
    private val securityProperties: SecurityProperties,
    private val elShammahSeedProperties: ElShammahSeedProperties,
    private val schoolRepository: SchoolRepository,
    private val academicYearRepository: AcademicYearRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val levelRepository: LevelRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val scheduleRepository: TuitionScheduleRepository,
    private val paymentRepository: FeePaymentRepository,
    private val policyRepository: FeesPolicyRepository
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (!securityProperties.mockUsers.enabled) {
            log.info("Fees demo seed skipped (beesm.security.mock-users.enabled=false)")
            return
        }
        try {
            seed()
        } catch (error: Exception) {
            log.error("Fees demo seed failed: {}", error.message, error)
        }
    }

    private fun seed() {
        val preferredSchoolId = elShammahSeedProperties.schoolId.takeIf { elShammahSeedProperties.enabled }
            ?: securityProperties.mockUsers.schoolId
        val school = schoolRepository.findById(preferredSchoolId).block()
            ?: schoolRepository.findByCode(securityProperties.mockUsers.schoolCode).block()
            ?: return
        val schoolId = school.id ?: return
        val year = academicYearRepository.findBySchoolIdAndCurrentTrue(schoolId).block()
            ?: academicYearRepository.findBySchoolIdOrderByStartDateDesc(schoolId).collectList().block()
                ?.firstOrNull()
            ?: return
        val yearId = year.id ?: return

        ensurePolicy(schoolId)

        val enrollment = enrollmentRepository
            .findBySchoolIdAndStudentIdAndAcademicYearId(schoolId, "stu-demo-01", yearId)
            .block()
            ?: return
        if (enrollment.status != EnrollmentStatus.ENROLLED) return
        val classId = enrollment.classId
        val clazz = schoolClassRepository.findByIdAndSchoolId(classId, schoolId).block() ?: return
        val levelId = clazz.levelId
        val level = levelRepository.findByIdAndSchoolId(levelId, schoolId).block()
        val levelLabel = level?.name?.fr?.takeIf { it.isNotBlank() }
            ?: level?.name?.en?.takeIf { it.isNotBlank() }
            ?: level?.code
            ?: clazz.name

        val dueDates = SolvencyCalculator.installmentDueDates(year.startDate, 3)
        val schedule = scheduleRepository
            .findBySchoolIdAndAcademicYearIdAndLevelId(schoolId, yearId, levelId)
            .block()
            ?: scheduleRepository.save(
                TuitionSchedule(
                    id = "fees-sched-demo-$levelId",
                    schoolId = schoolId,
                    academicYearId = yearId,
                    levelId = levelId,
                    levelLabel = levelLabel,
                    enrollmentFee = BigDecimal("25000"),
                    installments = mutableListOf(
                        TuitionInstallment(1, BigDecimal("50000"), dueDates[0]),
                        TuitionInstallment(2, BigDecimal("50000"), dueDates[1]),
                        TuitionInstallment(3, BigDecimal("50000"), dueDates[2])
                    ),
                    currency = "XAF",
                    active = true
                )
            ).block()
            ?: return

        val scheduleId = schedule.id ?: return
        var paymentsCreated = 0

        // Profils de solvabilité pour la démo visuelle.
        paymentsCreated += seedPayments(
            schoolId, yearId, classId, scheduleId,
            studentId = "stu-demo-01",
            enrollmentId = "enr-demo-01",
            // Tout soldé → SOLVENT
            kinds = listOf(
                PaySpec(FeePaymentKind.ENROLLMENT, null, "25000", "REC-DEMO-01-E"),
                PaySpec(FeePaymentKind.INSTALLMENT, 1, "50000", "REC-DEMO-01-T1"),
                PaySpec(FeePaymentKind.INSTALLMENT, 2, "50000", "REC-DEMO-01-T2"),
                PaySpec(FeePaymentKind.INSTALLMENT, 3, "50000", "REC-DEMO-01-T3")
            )
        )
        paymentsCreated += seedPayments(
            schoolId, yearId, classId, scheduleId,
            studentId = "stu-demo-02",
            enrollmentId = "enr-demo-02",
            // Inscription + T1+T2 ; T3 du mois en cours manquante → PENDING (si mois = M2)
            kinds = listOf(
                PaySpec(FeePaymentKind.ENROLLMENT, null, "25000", "REC-DEMO-02-E"),
                PaySpec(FeePaymentKind.INSTALLMENT, 1, "50000", "REC-DEMO-02-T1"),
                PaySpec(FeePaymentKind.INSTALLMENT, 2, "50000", "REC-DEMO-02-T2")
            )
        )
        paymentsCreated += seedPayments(
            schoolId, yearId, classId, scheduleId,
            studentId = "stu-demo-03",
            enrollmentId = "enr-demo-03",
            // Inscription seule ; T1 échue manquante → INSOLVENT
            kinds = listOf(
                PaySpec(FeePaymentKind.ENROLLMENT, null, "25000", "REC-DEMO-03-E")
            )
        )
        paymentsCreated += seedPayments(
            schoolId, yearId, classId, scheduleId,
            studentId = "stu-demo-05",
            enrollmentId = "enr-demo-05",
            kinds = listOf(
                PaySpec(FeePaymentKind.ENROLLMENT, null, "25000", "REC-DEMO-05-E"),
                PaySpec(FeePaymentKind.INSTALLMENT, 1, "50000", "REC-DEMO-05-T1"),
                PaySpec(FeePaymentKind.INSTALLMENT, 2, "50000", "REC-DEMO-05-T2"),
                PaySpec(FeePaymentKind.INSTALLMENT, 3, "50000", "REC-DEMO-05-T3")
            )
        )
        // stu-demo-04 / 06… : aucun paiement → INSOLVENT (inscription manquante)

        log.info(
            "Fees demo seed done schoolId={} scheduleId={} paymentsCreated={}",
            schoolId,
            scheduleId,
            paymentsCreated
        )
    }

    private fun ensurePolicy(schoolId: String) {
        if (policyRepository.findBySchoolId(schoolId).block() != null) return
        policyRepository.save(
            FeesPolicy(
                id = "fees-policy-$schoolId",
                schoolId = schoolId,
                reminderDaysBeforeDue = mutableListOf(15, 10, 5, 3, 2, 1),
                remindOnDueDate = true,
                weeklyPenaltyAmount = BigDecimal("1000"),
                currency = "XAF",
                maxInsolvencyGraceDays = 28,
                exclusionEnabled = true,
                exclusionWarningDaysBefore = 7,
                active = true
            )
        ).block()
    }

    private fun seedPayments(
        schoolId: String,
        yearId: String,
        classId: String,
        scheduleId: String,
        studentId: String,
        enrollmentId: String,
        kinds: List<PaySpec>
    ): Int {
        var created = 0
        kinds.forEachIndexed { index, spec ->
            val paymentId = "fees-pay-$studentId-${index + 1}"
            if (paymentRepository.findById(paymentId).block() != null) return@forEachIndexed
            paymentRepository.save(
                FeePayment(
                    id = paymentId,
                    schoolId = schoolId,
                    academicYearId = yearId,
                    studentId = studentId,
                    enrollmentId = enrollmentId,
                    classId = classId,
                    scheduleId = scheduleId,
                    kind = spec.kind,
                    installmentIndex = spec.installmentIndex,
                    amount = BigDecimal(spec.amount),
                    paidAt = Instant.now().minus((kinds.size - index).toLong() * 7, ChronoUnit.DAYS),
                    method = if (index % 2 == 0) FeePaymentMethod.BANK_TRANSFER else FeePaymentMethod.CASH,
                    reference = spec.reference,
                    recordedByAccountId = "acc-demo-director",
                    note = "Seed démo scolarité",
                    status = FeePaymentStatus.RECORDED
                )
            ).block()
            created++
        }
        return created
    }

    private data class PaySpec(
        val kind: FeePaymentKind,
        val installmentIndex: Int?,
        val amount: String,
        val reference: String
    )
}
