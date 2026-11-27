package com.kobe.beesm.service.fees

import com.kobe.beesm.dto.fees.VoidFeePaymentRequest
import com.kobe.beesm.exception.BusinessRuleException
import com.kobe.beesm.model.fees.FeePayment
import com.kobe.beesm.model.fees.FeePaymentKind
import com.kobe.beesm.model.fees.FeePaymentMethod
import com.kobe.beesm.model.fees.FeePaymentStatus
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
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.math.BigDecimal
import java.time.Instant

/** Q-76 — VOID + motif. */
class FeesVoidRulesTest {

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

    private fun recordedPayment() = FeePayment(
        id = "pay-1",
        schoolId = "school-1",
        academicYearId = "year-1",
        studentId = "stu-1",
        enrollmentId = "enr-1",
        classId = "class-1",
        scheduleId = "sched-1",
        kind = FeePaymentKind.ENROLLMENT,
        installmentIndex = null,
        amount = BigDecimal("25000"),
        paidAt = Instant.parse("2026-10-01T10:00:00Z"),
        method = FeePaymentMethod.CASH,
        reference = "CAISSE",
        recordedByAccountId = "acc-1",
        note = null,
        status = FeePaymentStatus.RECORDED
    )

    @Test
    fun `void requires non-blank reason`() {
        StepVerifier.create(
            service.voidPayment(
                director(),
                "pay-1",
                VoidFeePaymentRequest(reason = "   ")
            )
        )
            .expectErrorMatches { it is BusinessRuleException && it.code == "VOID_REASON_REQUIRED" }
            .verify()
    }

    @Test
    fun `void recorded payment succeeds`() {
        val payment = recordedPayment()
        whenever(paymentRepository.findByIdAndSchoolId("pay-1", "school-1"))
            .thenReturn(Mono.just(payment))
        whenever(academicYearApi.assertWritable(any(), any())).thenReturn(Mono.empty())
        whenever(paymentRepository.save(any())).thenAnswer { Mono.just(it.getArgument(0)) }

        StepVerifier.create(
            service.voidPayment(
                director(),
                "pay-1",
                VoidFeePaymentRequest(reason = "Double saisie")
            )
        )
            .assertNext { resp ->
                assert(resp.status == FeePaymentStatus.VOID)
                assert(resp.voidReason == "Double saisie")
                assert(resp.voidedAt != null)
            }
            .verifyComplete()
    }

    @Test
    fun `void already void fails`() {
        val payment = recordedPayment().also { it.status = FeePaymentStatus.VOID }
        whenever(paymentRepository.findByIdAndSchoolId("pay-1", "school-1"))
            .thenReturn(Mono.just(payment))

        StepVerifier.create(
            service.voidPayment(
                director(),
                "pay-1",
                VoidFeePaymentRequest(reason = "retry")
            )
        )
            .expectErrorMatches { it is BusinessRuleException && it.code == "PAYMENT_ALREADY_VOID" }
            .verify()
    }
}
