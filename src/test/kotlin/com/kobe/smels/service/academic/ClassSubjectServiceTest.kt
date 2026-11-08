package com.kobe.smels.service.academic

import com.kobe.smels.dto.academic.CreateClassSubjectRequest
import com.kobe.smels.dto.staff.StaffSummaryDto
import com.kobe.smels.exception.ConflictException
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.model.academic.ClassSubject
import com.kobe.smels.model.academic.SchoolClass
import com.kobe.smels.model.academic.Subject
import com.kobe.smels.model.academic.SubjectGroup
import com.kobe.smels.model.academic.SubjectKind
import com.kobe.smels.model.shared.Cycle
import com.kobe.smels.model.shared.Language
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.OutboxEvent
import com.kobe.smels.model.shared.Section
import com.kobe.smels.model.staff.StaffFunction
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.academic.ClassSubjectRepository
import com.kobe.smels.repository.academic.SchoolClassRepository
import com.kobe.smels.repository.academic.SubjectGroupRepository
import com.kobe.smels.repository.academic.SubjectRepository
import com.kobe.smels.service.staff.api.StaffApi
import com.kobe.smels.shared.outbox.OutboxWriter
import com.kobe.smels.shared.security.CurrentUser
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ClassSubjectServiceTest {
    private val classSubjectRepository: ClassSubjectRepository = mock()
    private val schoolClassRepository: SchoolClassRepository = mock()
    private val subjectRepository: SubjectRepository = mock()
    private val subjectGroupRepository: SubjectGroupRepository = mock()
    private val staffApi: StaffApi = mock()
    private val outboxWriter: OutboxWriter = mock()
    private val service = ClassSubjectService(
        classSubjectRepository,
        schoolClassRepository,
        subjectRepository,
        subjectGroupRepository,
        staffApi,
        outboxWriter
    )

    @Test
    fun `BR-03 rejects duplicate subject in same class`() {
        stubRefs()
        whenever(classSubjectRepository.findBySchoolIdAndClassIdAndSubjectId("school-1", "class-1", "subj-1"))
            .thenReturn(
                Mono.just(
                    ClassSubject(
                        id = "cs-existing",
                        schoolId = "school-1",
                        academicYearId = "year-1",
                        classId = "class-1",
                        subjectId = "subj-1",
                        groupId = "group-1",
                        coefficient = BigDecimal("4")
                    )
                )
            )

        val request = CreateClassSubjectRequest(
            academicYearId = "year-1",
            classId = "class-1",
            subjectId = "subj-1",
            groupId = "group-1",
            coefficient = BigDecimal("3")
        )

        StepVerifier.create(Mono.defer { service.create(adminUser(), request) })
            .expectError(ConflictException::class.java)
            .verify()
    }

    @Test
    fun `admin can create class subject and emits outbox event`() {
        stubRefs()
        whenever(classSubjectRepository.findBySchoolIdAndClassIdAndSubjectId("school-1", "class-1", "subj-1"))
            .thenReturn(Mono.empty())
        whenever(classSubjectRepository.save(any<ClassSubject>())).thenAnswer {
            val cs = it.getArgument<ClassSubject>(0)
            cs.id = "cs-1"
            Mono.just(cs)
        }
        whenever(outboxWriter.append(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(
                Mono.just(
                    OutboxEvent(
                        schoolId = "school-1",
                        module = "academic",
                        aggregateType = "ClassSubject",
                        aggregateId = "cs-1",
                        eventType = "academic.class-subject.changed",
                        occurredAt = Instant.parse("2026-01-01T00:00:00Z")
                    )
                )
            )

        val request = CreateClassSubjectRequest(
            academicYearId = "year-1",
            classId = "class-1",
            subjectId = "subj-1",
            groupId = "group-1",
            coefficient = BigDecimal("4"),
            teacherId = "staff-1"
        )

        StepVerifier.create(service.create(adminUser(), request))
            .assertNext { response ->
                assertEquals("cs-1", response.id)
                assertEquals(0, response.coefficient.compareTo(BigDecimal("4")))
            }
            .verifyComplete()

        verify(outboxWriter).append(
            eq("school-1"),
            eq("academic"),
            eq("ClassSubject"),
            eq("cs-1"),
            eq("academic.class-subject.changed"),
            any(),
            any()
        )
    }

    @Test
    fun `BR-42 deactivate sets active false`() {
        val cs = ClassSubject(
            id = "cs-1",
            schoolId = "school-1",
            academicYearId = "year-1",
            classId = "class-1",
            subjectId = "subj-1",
            groupId = "group-1",
            coefficient = BigDecimal("4"),
            active = true
        )
        whenever(classSubjectRepository.findByIdAndSchoolId("cs-1", "school-1")).thenReturn(Mono.just(cs))
        whenever(classSubjectRepository.save(any<ClassSubject>())).thenAnswer { Mono.just(it.getArgument(0)) }
        whenever(outboxWriter.append(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(
                Mono.just(
                    OutboxEvent(
                        schoolId = "school-1",
                        module = "academic",
                        aggregateType = "ClassSubject",
                        aggregateId = "cs-1",
                        eventType = "academic.class-subject.changed",
                        occurredAt = Instant.parse("2026-01-01T00:00:00Z")
                    )
                )
            )

        StepVerifier.create(service.deactivate(adminUser(), "cs-1"))
            .assertNext { assertFalse(it.active) }
            .verifyComplete()
        assertFalse(cs.active)
    }

    @Test
    fun `teacher cannot manage class subjects`() {
        val request = CreateClassSubjectRequest(
            academicYearId = "year-1",
            classId = "class-1",
            subjectId = "subj-1",
            groupId = "group-1",
            coefficient = BigDecimal("4")
        )
        StepVerifier.create(Mono.defer { service.create(teacherUser(), request) })
            .expectError(ForbiddenException::class.java)
            .verify()
    }

    private fun stubRefs() {
        whenever(schoolClassRepository.findByIdAndSchoolId("class-1", "school-1"))
            .thenReturn(
                Mono.just(
                    SchoolClass(
                        id = "class-1",
                        schoolId = "school-1",
                        academicYearId = "year-1",
                        levelId = "level-1",
                        levelCode = "6EME",
                        section = Section.FRANCOPHONE,
                        cycle = Cycle.SECONDAIRE_1ER_CYCLE,
                        name = "6eme A",
                        defaultLanguage = Language.FR
                    )
                )
            )
        whenever(subjectRepository.findByIdAndSchoolId("subj-1", "school-1"))
            .thenReturn(
                Mono.just(
                    Subject(
                        id = "subj-1",
                        schoolId = "school-1",
                        code = "MATH",
                        name = LocalizedText(fr = "Mathematiques", en = "Mathematics"),
                        kind = SubjectKind.ACADEMIC
                    )
                )
            )
        whenever(subjectGroupRepository.findByIdAndSchoolId("group-1", "school-1"))
            .thenReturn(
                Mono.just(
                    SubjectGroup(
                        id = "group-1",
                        schoolId = "school-1",
                        code = "SCI",
                        name = LocalizedText(fr = "Sciences", en = "Sciences")
                    )
                )
            )
        whenever(staffApi.getSummary("school-1", "staff-1"))
            .thenReturn(
                Mono.just(
                    StaffSummaryDto(
                        id = "staff-1",
                        schoolId = "school-1",
                        displayName = "Teacher Sample",
                        civility = null,
                        functions = setOf(StaffFunction.TEACHER),
                        active = true
                    )
                )
            )
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

    private fun teacherUser() = CurrentUser(
        accountId = "acc-teacher",
        sessionId = "sess-2",
        username = "teacher",
        schoolId = "school-1",
        roles = setOf(Role.TEACHER),
        profileType = ProfileType.STAFF,
        profileId = "staff-t",
        mustChangePassword = false
    )
}
