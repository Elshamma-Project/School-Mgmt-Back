package com.kobe.beesm.service.fees

import com.kobe.beesm.dto.academic.LevelSummaryDto
import com.kobe.beesm.dto.academic.LocalizedTextDto
import com.kobe.beesm.dto.fees.SyncLevelScheduleFromDefaultsRequest
import com.kobe.beesm.dto.fees.TuitionInstallmentAmountDto
import com.kobe.beesm.dto.fees.UpsertTuitionScheduleDefaultsRequest
import com.kobe.beesm.model.fees.TuitionInstallment
import com.kobe.beesm.model.fees.TuitionSchedule
import com.kobe.beesm.model.fees.TuitionScheduleDefaults
import com.kobe.beesm.model.shared.Cycle
import com.kobe.beesm.model.shared.Section
import com.kobe.beesm.model.user.ProfileType
import com.kobe.beesm.model.user.Role
import com.kobe.beesm.repository.fees.FeePaymentRepository
import com.kobe.beesm.repository.fees.FeeStudentFeeStateRepository
import com.kobe.beesm.repository.fees.FeesPolicyRepository
import com.kobe.beesm.repository.fees.TuitionScheduleDefaultsRepository
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.math.BigDecimal
import java.time.LocalDate

/** BR-F16 — modèle général + amorçage / sync manuelle. */
class FeesScheduleDefaultsRulesTest {

    private val scheduleRepository = mock<TuitionScheduleRepository>()
    private val defaultsRepository = mock<TuitionScheduleDefaultsRepository>()
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
        defaultsRepository,
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

    @BeforeEach
    fun stubBase() {
        whenever(academicYearApi.assertWritable(any(), any())).thenReturn(Mono.empty())
        whenever(levelApi.getSummary(eq("school-1"), eq("level-empty"))).thenReturn(
            Mono.just(
                LevelSummaryDto(
                    id = "level-empty",
                    schoolId = "school-1",
                    code = "5EME",
                    name = LocalizedTextDto(fr = "5ème", en = "Form 2"),
                    cycle = Cycle.SECONDAIRE_1ER_CYCLE,
                    section = Section.FRANCOPHONE,
                    active = true
                )
            )
        )
        whenever(levelApi.getSummary(eq("school-1"), eq("level-custom"))).thenReturn(
            Mono.just(
                LevelSummaryDto(
                    id = "level-custom",
                    schoolId = "school-1",
                    code = "6EME",
                    name = LocalizedTextDto(fr = "6ème", en = "Form 1"),
                    cycle = Cycle.SECONDAIRE_1ER_CYCLE,
                    section = Section.FRANCOPHONE,
                    active = true
                )
            )
        )
    }

    @Test
    fun `BR-F16 seeds only levels without schedule`() {
        whenever(defaultsRepository.findBySchoolIdAndAcademicYearId(any(), any()))
            .thenReturn(Mono.empty())
        whenever(defaultsRepository.save(any())).thenAnswer { inv ->
            val doc = inv.getArgument<TuitionScheduleDefaults>(0)
            if (doc.id == null) doc.id = "def-1"
            Mono.just(doc)
        }
        whenever(
            scheduleRepository.findBySchoolIdAndAcademicYearIdAndLevelId(
                any(), any(), eq("level-empty")
            )
        ).thenReturn(Mono.empty())
        whenever(
            scheduleRepository.findBySchoolIdAndAcademicYearIdAndLevelId(
                any(), any(), eq("level-custom")
            )
        ).thenReturn(
            Mono.just(
                TuitionSchedule(
                    id = "sched-custom",
                    schoolId = "school-1",
                    academicYearId = "year-1",
                    levelId = "level-custom",
                    levelLabel = "6ème",
                    enrollmentFee = BigDecimal("30000"),
                    installments = mutableListOf(
                        TuitionInstallment(1, BigDecimal("60000"), LocalDate.of(2026, 10, 31))
                    ),
                    currency = "XAF"
                )
            )
        )
        whenever(scheduleRepository.save(any())).thenAnswer { inv ->
            val doc = inv.getArgument<TuitionSchedule>(0)
            if (doc.id == null) doc.id = "sched-new"
            Mono.just(doc)
        }

        StepVerifier.create(
            service.upsertScheduleDefaults(
                director(),
                UpsertTuitionScheduleDefaultsRequest(
                    academicYearId = "year-1",
                    enrollmentFee = BigDecimal("25000"),
                    installments = listOf(
                        TuitionInstallmentAmountDto(1, BigDecimal("50000"), LocalDate.of(2026, 10, 31)),
                        TuitionInstallmentAmountDto(2, BigDecimal("50000"), LocalDate.of(2026, 11, 30))
                    ),
                    seedLevelIds = listOf("level-empty", "level-custom")
                )
            )
        )
            .assertNext { resp ->
                assert(resp.seededLevelIds == listOf("level-empty"))
                assert(resp.enrollmentFee.compareTo(BigDecimal("25000")) == 0)
            }
            .verifyComplete()

        val captor = argumentCaptor<TuitionSchedule>()
        verify(scheduleRepository).save(captor.capture())
        assert(captor.firstValue.levelId == "level-empty")
    }

    @Test
    fun `BR-F16 manual sync overwrites tuition but keeps existing schedule id`() {
        val defaults = TuitionScheduleDefaults(
            id = "def-1",
            schoolId = "school-1",
            academicYearId = "year-1",
            enrollmentFee = BigDecimal("25000"),
            installments = mutableListOf(
                TuitionInstallment(1, BigDecimal("50000"), LocalDate.of(2026, 10, 31))
            ),
            currency = "XAF"
        )
        whenever(defaultsRepository.findBySchoolIdAndAcademicYearId(any(), any()))
            .thenReturn(Mono.just(defaults))
        whenever(
            scheduleRepository.findBySchoolIdAndAcademicYearIdAndLevelId(any(), any(), any())
        ).thenReturn(
            Mono.just(
                TuitionSchedule(
                    id = "sched-custom",
                    schoolId = "school-1",
                    academicYearId = "year-1",
                    levelId = "level-custom",
                    levelLabel = "6ème",
                    enrollmentFee = BigDecimal("99999"),
                    installments = mutableListOf(
                        TuitionInstallment(1, BigDecimal("1"), LocalDate.of(2026, 9, 1))
                    ),
                    currency = "XAF"
                )
            )
        )
        whenever(scheduleRepository.save(any())).thenAnswer { Mono.just(it.getArgument(0)) }

        StepVerifier.create(
            service.syncLevelScheduleFromDefaults(
                director(),
                SyncLevelScheduleFromDefaultsRequest(
                    academicYearId = "year-1",
                    levelId = "level-custom"
                )
            )
        )
            .assertNext { resp ->
                assert(resp.id == "sched-custom")
                assert(resp.enrollmentFee.compareTo(BigDecimal("25000")) == 0)
                assert(resp.installments.size == 1)
                assert(resp.installments[0].amount.compareTo(BigDecimal("50000")) == 0)
            }
            .verifyComplete()

        verify(scheduleRepository, never()).delete(any())
    }
}
