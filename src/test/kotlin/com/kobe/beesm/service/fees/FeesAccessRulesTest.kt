package com.kobe.beesm.service.fees

import com.kobe.beesm.dto.staff.StaffSummaryDto
import com.kobe.beesm.model.staff.Civility
import com.kobe.beesm.model.staff.StaffFunction
import com.kobe.beesm.model.user.ProfileType
import com.kobe.beesm.model.user.Role
import com.kobe.beesm.repository.fees.FeePaymentRepository
import com.kobe.beesm.repository.fees.TuitionScheduleRepository
import com.kobe.beesm.service.academic.api.AcademicYearApi
import com.kobe.beesm.service.academic.api.LevelApi
import com.kobe.beesm.service.academic.api.SchoolClassApi
import com.kobe.beesm.service.staff.api.StaffApi
import com.kobe.beesm.service.student.api.EnrollmentApi
import com.kobe.beesm.service.student.api.StudentApi
import com.kobe.beesm.shared.security.CurrentUser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

/**
 * BR-F09 — paiements : DIRECTOR ou ADMINISTRATION + ACCOUNTANT.
 * Lecture solvabilité : pas TEACHER ; PARENT = enfants liés.
 */
class FeesAccessRulesTest {

    private val scheduleRepository = mock<TuitionScheduleRepository>()
    private val paymentRepository = mock<FeePaymentRepository>()
    private val academicYearApi = mock<AcademicYearApi>()
    private val levelApi = mock<LevelApi>()
    private val schoolClassApi = mock<SchoolClassApi>()
    private val enrollmentApi = mock<EnrollmentApi>()
    private val staffApi = mock<StaffApi>()
    private val studentApi = mock<StudentApi>()

    private val service = FeesService(
        scheduleRepository,
        paymentRepository,
        academicYearApi,
        levelApi,
        schoolClassApi,
        enrollmentApi,
        staffApi,
        studentApi
    )

    private fun user(
        roles: Set<Role>,
        profileId: String? = "staff-1",
        profileType: ProfileType = ProfileType.STAFF
    ) = CurrentUser(
        accountId = "acc-1",
        sessionId = "sess-1",
        username = "user@example.com",
        schoolId = "school-1",
        roles = roles,
        profileType = profileType,
        profileId = profileId,
        mustChangePassword = false
    )

    @Test
    fun `director can manage schedules and record payments`() {
        StepVerifier.create(service.access(user(setOf(Role.DIRECTOR))))
            .assertNext { access ->
                assertTrue(access.canManageSchedules)
                assertTrue(access.canRecordPayments)
                assertTrue(access.canManagePolicy)
                assertTrue(access.canRead)
            }
            .verifyComplete()
    }

    @Test
    fun `secretary administration cannot record payments`() {
        whenever(staffApi.getSummary(any(), any())).thenReturn(
            Mono.just(
                StaffSummaryDto(
                    id = "staff-1",
                    schoolId = "school-1",
                    displayName = "Secrétaire",
                    civility = Civility.MME,
                    functions = setOf(StaffFunction.SECRETARY),
                    active = true
                )
            )
        )
        StepVerifier.create(service.access(user(setOf(Role.ADMINISTRATION))))
            .assertNext { access ->
                assertFalse(access.canManageSchedules)
                assertFalse(access.canRecordPayments)
                assertTrue(access.canRead)
            }
            .verifyComplete()
    }

    @Test
    fun `accountant administration can record payments but not schedules`() {
        whenever(staffApi.getSummary(any(), any())).thenReturn(
            Mono.just(
                StaffSummaryDto(
                    id = "staff-1",
                    schoolId = "school-1",
                    displayName = "Comptable",
                    civility = Civility.MR,
                    functions = setOf(StaffFunction.ACCOUNTANT),
                    active = true
                )
            )
        )
        StepVerifier.create(service.access(user(setOf(Role.ADMINISTRATION))))
            .assertNext { access ->
                assertFalse(access.canManageSchedules)
                assertTrue(access.canRecordPayments)
                assertEquals(true, access.canRead)
            }
            .verifyComplete()
    }

    @Test
    fun `teacher cannot read solvency`() {
        StepVerifier.create(service.access(user(setOf(Role.TEACHER))))
            .assertNext { access ->
                assertFalse(access.canRead)
                assertFalse(access.canManageSchedules)
                assertFalse(access.canRecordPayments)
            }
            .verifyComplete()
    }

    @Test
    fun `parent can read solvency for own children only`() {
        StepVerifier.create(
            service.access(
                user(
                    roles = setOf(Role.PARENT),
                    profileId = "guardian-1",
                    profileType = ProfileType.GUARDIAN
                )
            )
        )
            .assertNext { access ->
                assertTrue(access.canRead)
                assertFalse(access.canManageSchedules)
                assertFalse(access.canRecordPayments)
            }
            .verifyComplete()
    }
}
