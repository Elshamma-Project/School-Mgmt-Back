package com.kobe.beesm.service.fees

import com.kobe.beesm.dto.fees.ReactivateAfterFeesRequest
import com.kobe.beesm.dto.student.StudentSummaryDto
import com.kobe.beesm.exception.BusinessRuleException
import com.kobe.beesm.exception.ForbiddenException
import com.kobe.beesm.model.fees.FeePayment
import com.kobe.beesm.model.fees.FeePaymentKind
import com.kobe.beesm.model.fees.FeePaymentMethod
import com.kobe.beesm.model.fees.FeePaymentStatus
import com.kobe.beesm.model.fees.FeeStudentFeeState
import com.kobe.beesm.model.fees.TuitionInstallment
import com.kobe.beesm.model.fees.TuitionSchedule
import com.kobe.beesm.model.shared.Language
import com.kobe.beesm.model.shared.Section
import com.kobe.beesm.model.shared.Sex
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
 * Q-80 / BR-F14 — réactivation Direction après soldes tuition + pénalités.
 */
class FeesReactivationRulesTest {

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
            TuitionInstallment(1, BigDecimal("50000"), LocalDate.of(2026, 10, 31))
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

    private fun accountantAdmin() = CurrentUser(
        accountId = "acc-2",
        sessionId = "sess-2",
        username = "compta@example.com",
        schoolId = "school-1",
        roles = setOf(Role.ADMINISTRATION),
        profileType = ProfileType.STAFF,
        profileId = "staff-2",
        mustChangePassword = false
    )

    @BeforeEach
    fun stubBase() {
        whenever(enrollmentApi.findByStudentAndYear(any(), any(), any())).thenReturn(
            Mono.just(
                com.kobe.beesm.dto.student.EnrollmentSummaryDto(
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
                    section = Section.FRANCOPHONE,
                    defaultLanguage = Language.FR,
                    active = true
                )
            )
        )
        whenever(
            scheduleRepository.findBySchoolIdAndAcademicYearIdAndLevelId(any(), any(), any())
        ).thenReturn(Mono.just(schedule))
        whenever(studentApi.getSummary(any(), any())).thenReturn(
            Mono.just(
                StudentSummaryDto(
                    id = "stu-1",
                    schoolId = "school-1",
                    matricule = "M1",
                    displayName = "Élève Démo",
                    sex = Sex.MALE,
                    active = false
                )
            )
        )
    }

    private fun excludedState(penalty: BigDecimal = BigDecimal("2000")) =
        FeeStudentFeeState(
            id = "state-1",
            schoolId = "school-1",
            academicYearId = "year-1",
            studentId = "stu-1",
            insolvencySince = LocalDate.of(2026, 10, 1),
            penaltyWeeksAccrued = 2,
            penaltyTotalAccrued = penalty,
            excludedAt = Instant.parse("2026-10-29T10:00:00Z"),
            exclusionReasonCode = "FEES_INSOLVENCY_EXCLUSION"
        )

    private fun settledTuitionPayments() = listOf(
        FeePayment(
            id = "p1",
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
        ),
        FeePayment(
            id = "p2",
            schoolId = "school-1",
            academicYearId = "year-1",
            studentId = "stu-1",
            enrollmentId = "enr-1",
            classId = "class-1",
            scheduleId = "sched-1",
            kind = FeePaymentKind.INSTALLMENT,
            installmentIndex = 1,
            amount = BigDecimal("50000"),
            paidAt = Instant.now(),
            method = FeePaymentMethod.BANK_TRANSFER,
            recordedByAccountId = "acc-1",
            status = FeePaymentStatus.RECORDED
        )
    )

    @Test
    fun `Q-80 accountant cannot reactivate`() {
        StepVerifier.create(
            service.reactivateAfterFees(
                accountantAdmin(),
                "stu-1",
                ReactivateAfterFeesRequest(academicYearId = "year-1")
            )
        )
            .expectError(ForbiddenException::class.java)
            .verify()
    }

    @Test
    fun `Q-80 blocked when penalties unpaid`() {
        whenever(
            paymentRepository.findBySchoolIdAndAcademicYearIdAndStudentIdAndStatus(
                any(), any(), any(), eq(FeePaymentStatus.RECORDED)
            )
        ).thenReturn(Flux.fromIterable(settledTuitionPayments()))
        whenever(
            stateRepository.findBySchoolIdAndAcademicYearIdAndStudentId(any(), any(), any())
        ).thenReturn(Mono.just(excludedState()))

        StepVerifier.create(
            service.reactivateAfterFees(
                director(),
                "stu-1",
                ReactivateAfterFeesRequest(academicYearId = "year-1")
            )
        )
            .expectErrorMatches { it is BusinessRuleException && it.code == "PENALTIES_NOT_SETTLED" }
            .verify()
    }

    @Test
    fun `Q-80 director reactivates when tuition and penalties settled`() {
        val payments = settledTuitionPayments() + FeePayment(
            id = "p3",
            schoolId = "school-1",
            academicYearId = "year-1",
            studentId = "stu-1",
            enrollmentId = "enr-1",
            classId = "class-1",
            scheduleId = "sched-1",
            kind = FeePaymentKind.PENALTY,
            amount = BigDecimal("2000"),
            paidAt = Instant.now(),
            method = FeePaymentMethod.CASH,
            recordedByAccountId = "acc-1",
            status = FeePaymentStatus.RECORDED
        )
        whenever(
            paymentRepository.findBySchoolIdAndAcademicYearIdAndStudentIdAndStatus(
                any(), any(), any(), eq(FeePaymentStatus.RECORDED)
            )
        ).thenReturn(Flux.fromIterable(payments))
        whenever(
            stateRepository.findBySchoolIdAndAcademicYearIdAndStudentId(any(), any(), any())
        ).thenReturn(Mono.just(excludedState()))
        whenever(studentApi.reactivateAfterFeeExclusion(any(), any())).thenReturn(
            Mono.just(
                StudentSummaryDto(
                    id = "stu-1",
                    schoolId = "school-1",
                    matricule = "M1",
                    displayName = "Élève Démo",
                    sex = Sex.MALE,
                    active = true
                )
            )
        )
        whenever(stateRepository.save(any())).thenAnswer { Mono.just(it.getArgument(0)) }

        StepVerifier.create(
            service.reactivateAfterFees(
                director(),
                "stu-1",
                ReactivateAfterFeesRequest(academicYearId = "year-1")
            )
        )
            .assertNext { resp ->
                assert(resp.active)
                assert(!resp.feesExcluded)
            }
            .verifyComplete()
    }
}
