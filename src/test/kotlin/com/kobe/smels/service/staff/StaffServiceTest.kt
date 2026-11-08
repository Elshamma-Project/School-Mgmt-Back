package com.kobe.smels.service.staff

import com.kobe.smels.dto.staff.CreateStaffRequest
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.model.shared.OutboxEvent
import com.kobe.smels.model.staff.Civility
import com.kobe.smels.model.staff.Staff
import com.kobe.smels.model.staff.StaffFunction
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.staff.StaffRepository
import com.kobe.smels.shared.outbox.OutboxWriter
import com.kobe.smels.shared.security.CurrentUser
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StaffServiceTest {
    private val staffRepository: StaffRepository = mock()
    private val outboxWriter: OutboxWriter = mock()
    private val service = StaffService(staffRepository, outboxWriter)

    @Test
    fun `admin can create staff and emits outbox event`() {
        whenever(staffRepository.findBySchoolIdAndStaffNumber(any(), any())).thenReturn(Mono.empty())
        whenever(staffRepository.save(any<Staff>())).thenAnswer { invocation ->
            val staff = invocation.getArgument<Staff>(0)
            staff.id = "staff-1"
            Mono.just(staff)
        }
        whenever(outboxWriter.append(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Mono.just(sampleOutboxEvent()))

        val request = CreateStaffRequest(
            lastName = "NGWA",
            firstName = "Hariette",
            civility = Civility.MME,
            functions = setOf(StaffFunction.TEACHER)
        )

        StepVerifier.create(service.create(adminUser(), request))
            .assertNext { response ->
                assertEquals("staff-1", response.id)
                assertEquals("NGWA Hariette", response.fullName)
                assertEquals(true, response.active)
            }
            .verifyComplete()

        verify(outboxWriter).append(
            eq("school-1"),
            eq("staff"),
            eq("Staff"),
            eq("staff-1"),
            eq("staff.member.changed"),
            any(),
            any()
        )
    }

    @Test
    fun `teacher cannot create staff`() {
        val request = CreateStaffRequest(lastName = "DOE", firstName = "John")
        StepVerifier.create(Mono.defer { service.create(teacherUser("other"), request) })
            .expectError(ForbiddenException::class.java)
            .verify()
    }

    @Test
    fun `BR-42 deactivate sets active false instead of deleting`() {
        val staff = Staff(
            id = "staff-1",
            schoolId = "school-1",
            lastName = "NGWA",
            firstName = "Hariette",
            active = true
        )
        whenever(staffRepository.findByIdAndSchoolId("staff-1", "school-1")).thenReturn(Mono.just(staff))
        whenever(staffRepository.save(any<Staff>())).thenAnswer { Mono.just(it.getArgument(0)) }
        whenever(outboxWriter.append(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Mono.just(sampleOutboxEvent()))

        StepVerifier.create(service.deactivate(adminUser(), "staff-1"))
            .assertNext { response ->
                assertFalse(response.active)
                assertEquals("staff-1", response.id)
            }
            .verifyComplete()

        assertFalse(staff.active)
        verify(staffRepository).save(staff)
    }

    @Test
    fun `teacher can read own staff profile`() {
        val staff = Staff(
            id = "staff-42",
            schoolId = "school-1",
            lastName = "TEACHER",
            firstName = "Self",
            active = true
        )
        whenever(staffRepository.findByIdAndSchoolId("staff-42", "school-1")).thenReturn(Mono.just(staff))

        StepVerifier.create(service.getById(teacherUser("staff-42"), "staff-42"))
            .assertNext { assertEquals("staff-42", it.id) }
            .verifyComplete()
    }

    @Test
    fun `teacher cannot read another staff profile`() {
        val staff = Staff(
            id = "staff-99",
            schoolId = "school-1",
            lastName = "OTHER",
            firstName = "Person",
            active = true
        )
        whenever(staffRepository.findByIdAndSchoolId("staff-99", "school-1")).thenReturn(Mono.just(staff))

        StepVerifier.create(service.getById(teacherUser("staff-42"), "staff-99"))
            .expectError(ForbiddenException::class.java)
            .verify()
    }

    @Test
    fun `teacher cannot list all staff`() {
        StepVerifier.create(Mono.defer { service.list(teacherUser("staff-42"), 0, 20, null, null, null) })
            .expectError(ForbiddenException::class.java)
            .verify()
    }

    @Test
    fun `admin cannot create leadership staff`() {
        val request = CreateStaffRequest(
            lastName = "DIR",
            firstName = "Demo",
            functions = setOf(StaffFunction.HEAD_OF_SCHOOL)
        )
        StepVerifier.create(Mono.defer { service.create(adminUser(), request) })
            .expectError(ForbiddenException::class.java)
            .verify()
    }

    @Test
    fun `admin can create OTHER staff`() {
        whenever(staffRepository.findBySchoolIdAndStaffNumber(any(), any())).thenReturn(Mono.empty())
        whenever(staffRepository.save(any<Staff>())).thenAnswer { invocation ->
            val staff = invocation.getArgument<Staff>(0)
            staff.id = "staff-other-1"
            Mono.just(staff)
        }
        whenever(outboxWriter.append(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Mono.just(sampleOutboxEvent()))

        val request = CreateStaffRequest(
            lastName = "AUTRE",
            firstName = "Membre",
            functions = setOf(StaffFunction.OTHER)
        )

        StepVerifier.create(service.create(adminUser(), request))
            .assertNext { response ->
                assertEquals("staff-other-1", response.id)
                assertEquals(setOf(StaffFunction.OTHER), response.functions)
                assertEquals("acc-admin", response.createdBy)
                assertEquals("acc-admin", response.updatedBy)
            }
            .verifyComplete()
    }

    @Test
    fun `admin cannot read leadership staff`() {
        val staff = Staff(
            id = "staff-dir",
            schoolId = "school-1",
            lastName = "DIR",
            firstName = "Demo",
            functions = setOf(StaffFunction.HEAD_OF_SCHOOL),
            active = true
        )
        whenever(staffRepository.findByIdAndSchoolId("staff-dir", "school-1")).thenReturn(Mono.just(staff))

        StepVerifier.create(service.getById(adminUser(), "staff-dir"))
            .expectError(ForbiddenException::class.java)
            .verify()
    }

    @Test
    fun `admin can read peer admin but cannot deactivate`() {
        val staff = Staff(
            id = "staff-sec",
            schoolId = "school-1",
            lastName = "SEC",
            firstName = "Demo",
            functions = setOf(StaffFunction.SECRETARY),
            active = true
        )
        whenever(staffRepository.findByIdAndSchoolId("staff-sec", "school-1")).thenReturn(Mono.just(staff))

        StepVerifier.create(service.getById(adminUser(), "staff-sec"))
            .assertNext { response ->
                assertEquals("staff-sec", response.id)
                assertFalse(response.canManage)
            }
            .verifyComplete()

        StepVerifier.create(Mono.defer { service.deactivate(adminUser(), "staff-sec") })
            .expectError(ForbiddenException::class.java)
            .verify()
    }

    private fun sampleOutboxEvent() = OutboxEvent(
        schoolId = "school-1",
        module = "staff",
        aggregateType = "Staff",
        aggregateId = "staff-1",
        eventType = "staff.member.changed",
        occurredAt = java.time.Instant.parse("2026-01-01T00:00:00Z")
    )

    private fun adminUser() = CurrentUser(
        accountId = "acc-admin",
        sessionId = "sess-1",
        username = "admin",
        schoolId = "school-1",
        roles = setOf(Role.ADMINISTRATION),
        profileType = ProfileType.STAFF,
        profileId = "staff-admin",
        mustChangePassword = false
    )

    private fun teacherUser(profileId: String) = CurrentUser(
        accountId = "acc-teacher",
        sessionId = "sess-2",
        username = "teacher",
        schoolId = "school-1",
        roles = setOf(Role.TEACHER),
        profileType = ProfileType.STAFF,
        profileId = profileId,
        mustChangePassword = false
    )
}
