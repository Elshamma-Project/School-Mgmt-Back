package com.kobe.beesm.service.fees

import com.kobe.beesm.dto.fees.RecordFeePaymentRequest
import com.kobe.beesm.dto.student.EnrollmentSummaryDto
import com.kobe.beesm.exception.BusinessRuleException
import com.kobe.beesm.model.fees.FeePayment
import com.kobe.beesm.model.fees.FeePaymentKind
import com.kobe.beesm.model.fees.FeePaymentMethod
import com.kobe.beesm.model.fees.FeePaymentStatus
import com.kobe.beesm.model.fees.TuitionInstallment
import com.kobe.beesm.model.fees.TuitionSchedule
import com.kobe.beesm.model.student.EnrollmentStatus
import com.kobe.beesm.model.user.ProfileType
import com.kobe.beesm.model.user.Role
import com.kobe.beesm.repository.fees.FeePaymentRepository
import com.kobe.beesm.repository.fees.FeeStudentFeeStateRepository
import com.kobe.beesm.repository.fees.FeesPolicyRepository
import com.kobe.beesm.repository.fees.TuitionScheduleRepository
import com.kobe.beesm.service.academic.api.AcademicYearApi
import com.kobe.beesm.service.academic.api.LevelApi
import com.kobe.beesm.service.academic.api.SchoolBrandApi
import com.kobe.beesm.service.academic.api.SchoolClassApi
import com.kobe.beesm.service.staff.api.StaffApi
import com.kobe.beesm.service.student.api.EnrollmentApi
import com.kobe.beesm.service.student.api.StudentApi
import com.kobe.beesm.shared.security.CurrentUser
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Q-68 / Q-74 / Q-75 — CASH inscription, montant exact, ordre inscription → T1 → T2.
 */
class FeesPaymentRulesTest {

    private val scheduleRepository = mock<TuitionScheduleRepository>()
    private val paymentRepository = mock<FeePaymentRepository>()
    private val stateRepository = mock<FeeStudentFeeStateRepository>()
    private val policyRepository = mock<FeesPolicyRepository>()
    private val academicYearApi = mock<AcademicYearApi>()
    private val levelApi = mock<LevelApi>()
    private val schoolClassApi = mock<SchoolClassApi>()
    private val schoolBrandApi = mock<SchoolBrandApi>()
    private val enrollmentApi = mock<EnrollmentApi>()
    private val staffApi = mock<StaffApi>()
    private val studentApi = mock<StudentApi>()
    private val enrollmentReceiptPdfService = mock<FeeEnrollmentReceiptPdfService>()

    private val service = FeesService(
        scheduleRepository,
        paymentRepository,
        stateRepository,
        policyRepository,
        academicYearApi,
        levelApi,
        schoolClassApi,
        schoolBrandApi,
        enrollmentApi,
        staffApi,
        studentApi,
        enrollmentReceiptPdfService
    )

    private val schedule = TuitionSchedule(
        id = "sched-1",
        schoolId = "school-1",
        academicYearId = "year-1",
        levelId = "level-1",
        levelLabel = "6ème",
        enrollmentFee = BigDecimal("25000"),
        installments = mutableListOf(
            TuitionInstallment(1, BigDecimal("50000"), LocalDate.of(2026, 10, 31)),
            TuitionInstallment(2, BigDecimal("50000"), LocalDate.of(2026, 11, 30))
        ),
        currency = "XAF",
        active = true
    )

    private fun director() = CurrentUser(
        accountId = "acc-1",
        sessionId = "sess-1",
        username = "dir@example.com",
        schoolId = "school-1",
        roles = setOf(Role.DIRECTOR),
        profileType = ProfileType.STAFF,
        profileId = "staff-1",
        mustChangePassword = false
    )

    @BeforeEach
    fun stubCommon() {
        whenever(academicYearApi.assertWritable(any(), any())).thenReturn(Mono.empty())
        whenever(enrollmentApi.findByStudentAndYear(any(), any(), any())).thenReturn(
            Mono.just(
                EnrollmentSummaryDto(
                    id = "enr-1",
                    schoolId = "school-1",
                    studentId = "stu-1",
                    academicYearId = "year-1",
                    classId = "class-1",
                    levelId = "level-1",
                    status = EnrollmentStatus.ENROLLED,
                    repeater = false
                )
            )
        )
        whenever(schoolClassApi.getSummary(any(), any())).thenReturn(
            Mono.just(
                com.kobe.beesm.dto.academic.SchoolClassSummaryDto(
                    id = "class-1",
                    schoolId = "school-1",
                    academicYearId = "year-1",
                    levelId = "level-1",
                    levelCode = "6E",
                    name = "6ème A",
                    section = com.kobe.beesm.model.shared.Section.FRANCOPHONE,
                    defaultLanguage = com.kobe.beesm.model.shared.Language.FR,
                    active = true
                )
            )
        )
        whenever(
            scheduleRepository.findBySchoolIdAndAcademicYearIdAndLevelId(any(), any(), any())
        ).thenReturn(Mono.just(schedule))
        whenever(paymentRepository.save(any())).thenAnswer { inv ->
            val p = inv.getArgument<FeePayment>(0)
            if (p.id == null) {
                p.id = "pay-new"
            }
            Mono.just(p)
        }
        whenever(
            stateRepository.findBySchoolIdAndAcademicYearIdAndStudentId(any(), any(), any())
        ).thenReturn(Mono.empty())
    }

    private fun existing(vararg payments: FeePayment): Flux<FeePayment> = Flux.fromArray(payments)

    @Test
    fun `Q-75 enrollment must be CASH`() {
        whenever(
            paymentRepository.findBySchoolIdAndAcademicYearIdAndStudentIdAndStatus(
                any(), any(), any(), eq(FeePaymentStatus.RECORDED)
            )
        ).thenReturn(Flux.empty())

        val request = RecordFeePaymentRequest(
            academicYearId = "year-1",
            studentId = "stu-1",
            kind = FeePaymentKind.ENROLLMENT,
            amount = BigDecimal("25000"),
            method = FeePaymentMethod.BANK_TRANSFER,
            reference = "X"
        )
        StepVerifier.create(service.recordPayment(director(), request))
            .expectErrorMatches { it is BusinessRuleException && it.code == "ENROLLMENT_CASH_ONLY" }
            .verify()
    }

    @Test
    fun `Q-74 enrollment amount must match exact due`() {
        whenever(
            paymentRepository.findBySchoolIdAndAcademicYearIdAndStudentIdAndStatus(
                any(), any(), any(), eq(FeePaymentStatus.RECORDED)
            )
        ).thenReturn(Flux.empty())

        val request = RecordFeePaymentRequest(
            academicYearId = "year-1",
            studentId = "stu-1",
            kind = FeePaymentKind.ENROLLMENT,
            amount = BigDecimal("20000"),
            method = FeePaymentMethod.CASH,
            reference = "CAISSE"
        )
        StepVerifier.create(service.recordPayment(director(), request))
            .expectErrorMatches { it is BusinessRuleException && it.code == "AMOUNT_MUST_MATCH_DUE" }
            .verify()
    }

    @Test
    fun `Q-68 installment blocked until enrollment settled`() {
        whenever(
            paymentRepository.findBySchoolIdAndAcademicYearIdAndStudentIdAndStatus(
                any(), any(), any(), eq(FeePaymentStatus.RECORDED)
            )
        ).thenReturn(Flux.empty())

        val request = RecordFeePaymentRequest(
            academicYearId = "year-1",
            studentId = "stu-1",
            kind = FeePaymentKind.INSTALLMENT,
            installmentIndex = 1,
            amount = BigDecimal("50000"),
            method = FeePaymentMethod.BANK_TRANSFER,
            reference = "BANK-1"
        )
        StepVerifier.create(service.recordPayment(director(), request))
            .expectErrorMatches { it is BusinessRuleException && it.code == "ENROLLMENT_REQUIRED_FIRST" }
            .verify()
    }

    @Test
    fun `Q-68 installments must be sequential`() {
        val enrollmentPaid = FeePayment(
            id = "pay-enr",
            schoolId = "school-1",
            academicYearId = "year-1",
            studentId = "stu-1",
            enrollmentId = "enr-1",
            classId = "class-1",
            scheduleId = "sched-1",
            kind = FeePaymentKind.ENROLLMENT,
            amount = BigDecimal("25000"),
            paidAt = Instant.now(),
            method = FeePaymentMethod.CASH,
            recordedByAccountId = "acc-1",
            status = FeePaymentStatus.RECORDED
        )
        whenever(
            paymentRepository.findBySchoolIdAndAcademicYearIdAndStudentIdAndStatus(
                any(), any(), any(), eq(FeePaymentStatus.RECORDED)
            )
        ).thenReturn(existing(enrollmentPaid))

        val request = RecordFeePaymentRequest(
            academicYearId = "year-1",
            studentId = "stu-1",
            kind = FeePaymentKind.INSTALLMENT,
            installmentIndex = 2,
            amount = BigDecimal("50000"),
            method = FeePaymentMethod.BANK_TRANSFER,
            reference = "BANK-2"
        )
        StepVerifier.create(service.recordPayment(director(), request))
            .expectErrorMatches { it is BusinessRuleException && it.code == "INSTALLMENTS_MUST_BE_SEQUENTIAL" }
            .verify()
    }

    @Test
    fun `Q-75 enrollment cash exact amount succeeds`() {
        whenever(
            paymentRepository.findBySchoolIdAndAcademicYearIdAndStudentIdAndStatus(
                any(), any(), any(), eq(FeePaymentStatus.RECORDED)
            )
        ).thenReturn(Flux.empty())

        val request = RecordFeePaymentRequest(
            academicYearId = "year-1",
            studentId = "stu-1",
            kind = FeePaymentKind.ENROLLMENT,
            amount = BigDecimal("25000"),
            method = FeePaymentMethod.CASH,
            reference = "CAISSE"
        )
        StepVerifier.create(service.recordPayment(director(), request))
            .assertNext { resp ->
                assert(resp.kind == FeePaymentKind.ENROLLMENT)
                assert(resp.method == FeePaymentMethod.CASH)
            }
            .verifyComplete()
    }
}
