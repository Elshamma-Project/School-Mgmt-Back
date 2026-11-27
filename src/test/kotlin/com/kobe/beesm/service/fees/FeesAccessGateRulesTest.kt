package com.kobe.beesm.service.fees

import com.kobe.beesm.exception.ForbiddenException
import com.kobe.beesm.model.fees.FeePaymentStatus
import com.kobe.beesm.model.fees.TuitionInstallment
import com.kobe.beesm.model.fees.TuitionSchedule
import com.kobe.beesm.model.shared.Language
import com.kobe.beesm.model.shared.Section
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
import java.time.LocalDate

/** BR-F05 / Q-65 — porte d’accès academics. */
class FeesAccessGateRulesTest {

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

    @BeforeEach
    fun stubEnrollmentAndSchedule() {
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
        whenever(
            paymentRepository.findBySchoolIdAndAcademicYearIdAndStudentIdAndStatus(
                any(), any(), any(), eq(FeePaymentStatus.RECORDED)
            )
        ).thenReturn(Flux.empty())
        whenever(
            stateRepository.findBySchoolIdAndAcademicYearIdAndStudentId(any(), any(), any())
        ).thenReturn(Mono.empty())
    }

    @Test
    fun `accessGate blocked when enrollment unpaid`() {
        StepVerifier.create(service.accessGate("school-1", "year-1", "stu-1"))
            .assertNext { gate ->
                assert(gate.blocked)
                assert(gate.reasonCode == "FEES_UNPAID")
                assert(!gate.enrollmentSettled)
                assert(gate.messageFr != null && gate.messageEn != null)
            }
            .verifyComplete()
    }

    @Test
    fun `parent cannot view academics when blocked`() {
        StepVerifier.create(
            service.assertCanViewAcademics(parentUser(), "school-1", "year-1", "stu-1")
        )
            .expectErrorMatches { err ->
                err is ForbiddenException && err.code == "FEES_UNPAID"
            }
            .verify()
    }

    @Test
    fun `director can view academics when blocked`() {
        StepVerifier.create(
            service.assertCanViewAcademics(directorUser(), "school-1", "year-1", "stu-1")
        )
            .verifyComplete()
    }

    @Test
    fun `director cannot modify academics when blocked`() {
        StepVerifier.create(
            service.assertCanModifyAcademics("school-1", "year-1", "stu-1")
        )
            .expectError(ForbiddenException::class.java)
            .verify()
    }

    private fun parentUser() = CurrentUser(
        accountId = "acc-p",
        sessionId = "sess-p",
        username = "parent",
        schoolId = "school-1",
        roles = setOf(Role.PARENT),
        profileType = ProfileType.GUARDIAN,
        profileId = "guard-1",
        mustChangePassword = false
    )

    private fun directorUser() = CurrentUser(
        accountId = "acc-d",
        sessionId = "sess-d",
        username = "director",
        schoolId = "school-1",
        roles = setOf(Role.DIRECTOR),
        profileType = ProfileType.STAFF,
        profileId = "staff-d",
        mustChangePassword = false
    )
}
