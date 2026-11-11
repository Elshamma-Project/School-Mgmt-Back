package com.kobe.smels.service.academic

import com.kobe.smels.dto.academic.CreateAcademicYearRequest
import com.kobe.smels.dto.academic.LocalizedTextDto
import com.kobe.smels.dto.academic.TermDto
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.model.academic.AcademicYear
import com.kobe.smels.model.academic.AcademicYearStatus
import com.kobe.smels.model.academic.School
import com.kobe.smels.model.shared.OutboxEvent
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.academic.AcademicYearRepository
import com.kobe.smels.repository.academic.SchoolRepository
import com.kobe.smels.shared.outbox.OutboxWriter
import com.kobe.smels.shared.security.CurrentUser
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AcademicYearServiceTest {
    private val academicYearRepository: AcademicYearRepository = mock()
    private val schoolRepository: SchoolRepository = mock()
    private val outboxWriter: OutboxWriter = mock()
    private val service = AcademicYearService(academicYearRepository, schoolRepository, outboxWriter)

    private fun stubSchoolTz() {
        whenever(schoolRepository.findById("school-1")).thenReturn(
            Mono.just(
                School(
                    id = "school-1",
                    code = "TEST",
                    name = LocalizedText(fr = "Ecole", en = "School"),
                    timezone = "Africa/Douala"
                )
            )
        )
    }

    @Test
    fun `rejects academic year without three terms`() {
        val request = CreateAcademicYearRequest(
            label = "2025-2026",
            startDate = LocalDate.parse("2025-09-01"),
            endDate = LocalDate.parse("2026-06-30"),
            terms = listOf(
                TermDto(1, LocalizedTextDto("T1", "T1"), LocalDate.parse("2025-09-01"), LocalDate.parse("2025-12-15"))
            )
        )
        StepVerifier.create(Mono.defer { service.create(adminUser(), request) })
            .expectError(BusinessRuleException::class.java)
            .verify()
    }

    @Test
    fun `creates academic year and emits academic year created`() {
        stubSchoolTz()
        whenever(academicYearRepository.findBySchoolIdAndLabel("school-1", "2025-2026")).thenReturn(Mono.empty())
        whenever(academicYearRepository.findBySchoolIdAndCurrentTrue("school-1")).thenReturn(Mono.empty())
        whenever(academicYearRepository.save(any<AcademicYear>())).thenAnswer {
            val year = it.getArgument<AcademicYear>(0)
            year.id = "year-1"
            Mono.just(year)
        }
        whenever(outboxWriter.append(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(
                Mono.just(
                    OutboxEvent(
                        schoolId = "school-1",
                        module = "academic",
                        aggregateType = "AcademicYear",
                        aggregateId = "year-1",
                        eventType = "academic.year.created",
                        occurredAt = Instant.parse("2026-01-01T00:00:00Z")
                    )
                )
            )

        val request = CreateAcademicYearRequest(
            label = "2025-2026",
            startDate = LocalDate.parse("2025-09-01"),
            endDate = LocalDate.parse("2026-06-30"),
            current = true,
            terms = listOf(
                TermDto(1, LocalizedTextDto("Trimestre 1", "Term 1"), LocalDate.parse("2025-09-01"), LocalDate.parse("2025-12-15")),
                TermDto(2, LocalizedTextDto("Trimestre 2", "Term 2"), LocalDate.parse("2026-01-05"), LocalDate.parse("2026-03-31")),
                TermDto(3, LocalizedTextDto("Trimestre 3", "Term 3"), LocalDate.parse("2026-04-01"), LocalDate.parse("2026-06-30"))
            )
        )

        StepVerifier.create(service.create(adminUser(), request))
            .assertNext { response ->
                assertTrue(response.current)
                assertTrue(response.terms.map { it.number }.toSet() == setOf(1, 2, 3))
            }
            .verifyComplete()
    }

    @Test
    fun `assertWritable rejects year on or after endDate`() {
        stubSchoolTz()
        val today = LocalDate.now(ZoneId.of("Africa/Douala"))
        val year = AcademicYear(
            id = "year-old",
            schoolId = "school-1",
            label = "past",
            startDate = today.minusYears(1),
            endDate = today,
            current = true,
            status = AcademicYearStatus.ACTIVE
        )
        whenever(academicYearRepository.findBySchoolIdOrderByStartDateDesc("school-1"))
            .thenReturn(Flux.just(year))
        whenever(academicYearRepository.save(any<AcademicYear>())).thenAnswer {
            Mono.just(it.getArgument(0))
        }
        whenever(outboxWriter.append(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(
                Mono.just(
                    OutboxEvent(
                        schoolId = "school-1",
                        module = "academic",
                        aggregateType = "AcademicYear",
                        aggregateId = "year-old",
                        eventType = "academic.year.closed",
                        occurredAt = Instant.parse("2026-01-01T00:00:00Z")
                    )
                )
            )
        whenever(academicYearRepository.findByIdAndSchoolId("year-old", "school-1"))
            .thenReturn(Mono.just(year))

        StepVerifier.create(service.assertWritable("school-1", "year-old"))
            .expectErrorMatches { it is BusinessRuleException && it.code == "YEAR_LOCKED" }
            .verify()
        assertEquals(AcademicYearStatus.CLOSED, year.status)
    }

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
}
