package com.kobe.beesm.service.notification

import com.kobe.beesm.dto.academic.SchoolTimetableSettingsDto
import com.kobe.beesm.dto.timetable.TimetableSummaryDto
import com.kobe.beesm.model.notification.InAppNotification
import com.kobe.beesm.model.notification.NotificationCategory
import com.kobe.beesm.model.notification.NotificationSeverity
import com.kobe.beesm.model.shared.OutboxEvent
import com.kobe.beesm.model.shared.OutboxStatus
import com.kobe.beesm.model.timetable.TimetableStatus
import com.kobe.beesm.model.user.Role
import com.kobe.beesm.service.academic.api.SchoolBrandApi
import com.kobe.beesm.service.timetable.api.TimetableApi
import com.kobe.beesm.service.user.api.UserAccountApi
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import kotlin.test.Test

class TimetableUpdateNeededNotificationHandlerTest {

    private val notificationService: NotificationService = mock()
    private val userAccountApi: UserAccountApi = mock()
    private val timetableApi: TimetableApi = mock()
    private val schoolBrandApi: SchoolBrandApi = mock()
    private val handler = TimetableUpdateNeededNotificationHandler(
        notificationService,
        userAccountApi,
        timetableApi,
        schoolBrandApi
    )

    @Test
    fun `notifies editors when new class has no entries on published timetable`() {
        whenever(timetableApi.findPublished("school-1", "year-1"))
            .thenReturn(
                Mono.just(
                    TimetableSummaryDto(
                        id = "tt-1",
                        academicYearId = "year-1",
                        revision = 2,
                        status = TimetableStatus.PUBLISHED
                    )
                )
            )
        whenever(timetableApi.hasEntriesForClass("school-1", "tt-1", "class-new"))
            .thenReturn(Mono.just(false))
        whenever(schoolBrandApi.getTimetableSettings("school-1"))
            .thenReturn(
                Mono.just(
                    SchoolTimetableSettingsDto(
                        schoolId = "school-1",
                        timetableSurveillantCanEdit = false
                    )
                )
            )
        whenever(
            userAccountApi.findActiveAccountIdsByRoles(
                eq("school-1"),
                eq(listOf(Role.ADMINISTRATION, Role.DIRECTOR))
            )
        ).thenReturn(Flux.just("acc-admin", "acc-dir"))
        stubDeliver()

        StepVerifier.create(handler.handle(classChangedEvent(action = "CREATED"))).verifyComplete()

        verify(notificationService, times(2)).deliver(
            schoolId = eq("school-1"),
            recipientAccountId = any(),
            recipientStaffId = anyOrNull(),
            category = eq(NotificationCategory.TIMETABLE_UPDATE_NEEDED),
            severity = eq(NotificationSeverity.WARNING),
            title = any(),
            body = any(),
            sourceEventId = eq("evt-class-1"),
            sourceEventType = eq("academic.class.changed"),
            resourceType = eq("SchoolClass"),
            resourceId = eq("class-new"),
            deepLink = eq("/timetable?tab=editor&classId=class-new"),
            ackRequired = eq(false)
        )
    }

    @Test
    fun `skips when published timetable already has entries for class`() {
        whenever(timetableApi.findPublished("school-1", "year-1"))
            .thenReturn(
                Mono.just(
                    TimetableSummaryDto(
                        id = "tt-1",
                        academicYearId = "year-1",
                        revision = 1,
                        status = TimetableStatus.PUBLISHED
                    )
                )
            )
        whenever(timetableApi.hasEntriesForClass("school-1", "tt-1", "class-new"))
            .thenReturn(Mono.just(true))

        StepVerifier.create(handler.handle(classChangedEvent(action = "CREATED"))).verifyComplete()
        verify(notificationService, never()).deliver(
            schoolId = any(),
            recipientAccountId = any(),
            recipientStaffId = anyOrNull(),
            category = any(),
            severity = any(),
            title = any(),
            body = any(),
            sourceEventId = anyOrNull(),
            sourceEventType = anyOrNull(),
            resourceType = anyOrNull(),
            resourceId = anyOrNull(),
            deepLink = anyOrNull(),
            ackRequired = any()
        )
    }

    @Test
    fun `skips update action`() {
        StepVerifier.create(handler.handle(classChangedEvent(action = "UPDATED"))).verifyComplete()
        verify(timetableApi, never()).findPublished(any(), any())
    }

    private fun stubDeliver() {
        whenever(
            notificationService.deliver(
                schoolId = any(),
                recipientAccountId = any(),
                recipientStaffId = anyOrNull(),
                category = any(),
                severity = any(),
                title = any(),
                body = any(),
                sourceEventId = anyOrNull(),
                sourceEventType = anyOrNull(),
                resourceType = anyOrNull(),
                resourceId = anyOrNull(),
                deepLink = anyOrNull(),
                ackRequired = any()
            )
        ).thenAnswer { inv ->
            Mono.just(
                InAppNotification(
                    schoolId = inv.getArgument(0),
                    recipientAccountId = inv.getArgument(1),
                    category = inv.getArgument(3),
                    title = inv.getArgument(5),
                    body = inv.getArgument(6)
                )
            )
        }
    }

    private fun classChangedEvent(action: String) = OutboxEvent(
        id = "evt-class-1",
        schoolId = "school-1",
        module = "academic",
        aggregateType = "SchoolClass",
        aggregateId = "class-new",
        eventType = "academic.class.changed",
        payload = mapOf(
            "classId" to "class-new",
            "academicYearId" to "year-1",
            "name" to "2nde D",
            "active" to true,
            "action" to action
        ),
        occurredAt = Instant.parse("2026-01-20T08:00:00Z"),
        status = OutboxStatus.PENDING
    )
}
