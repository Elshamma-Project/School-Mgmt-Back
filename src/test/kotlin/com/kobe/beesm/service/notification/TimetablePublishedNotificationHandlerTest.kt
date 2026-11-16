package com.kobe.beesm.service.notification

import com.kobe.beesm.model.notification.InAppNotification
import com.kobe.beesm.model.notification.NotificationCategory
import com.kobe.beesm.model.notification.NotificationSeverity
import com.kobe.beesm.model.shared.LocalizedText
import com.kobe.beesm.model.shared.OutboxEvent
import com.kobe.beesm.model.shared.OutboxStatus
import com.kobe.beesm.model.user.Role
import com.kobe.beesm.service.user.api.UserAccountApi
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Broadcast scolaire sur `timetable.published` (BR-60). */
class TimetablePublishedNotificationHandlerTest {

    @Test
    fun `notifies all school roles with logical timetable deepLink`() {
        val notificationService = mock<NotificationService>()
        val userAccountApi = mock<UserAccountApi>()
        val handler = TimetablePublishedNotificationHandler(notificationService, userAccountApi)

        whenever(
            userAccountApi.findActiveAccountIdsByRoles(
                eq("school-1"),
                eq(TimetablePublishedNotificationHandler.SCHOOL_RECIPIENT_ROLES)
            )
        ).thenReturn(Flux.just("acc-parent", "acc-teacher", "acc-admin"))

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

        val event = OutboxEvent(
            id = "evt-1",
            schoolId = "school-1",
            module = "timetable",
            aggregateType = "Timetable",
            aggregateId = "tt-1",
            eventType = "timetable.published",
            payload = mapOf(
                "timetableId" to "tt-1",
                "academicYearId" to "year-1",
                "revision" to 1
            ),
            occurredAt = Instant.parse("2026-01-15T10:00:00Z"),
            status = OutboxStatus.PENDING
        )

        StepVerifier.create(handler.handle(event)).verifyComplete()

        val deepLinkCaptor = argumentCaptor<String>()
        verify(notificationService, times(3)).deliver(
            schoolId = eq("school-1"),
            recipientAccountId = any(),
            recipientStaffId = anyOrNull(),
            category = eq(NotificationCategory.TIMETABLE_PUBLISHED),
            severity = eq(NotificationSeverity.SUCCESS),
            title = any(),
            body = any(),
            sourceEventId = eq("evt-1"),
            sourceEventType = eq("timetable.published"),
            resourceType = eq("Timetable"),
            resourceId = eq("tt-1"),
            deepLink = deepLinkCaptor.capture(),
            ackRequired = eq(false)
        )
        assertTrue(deepLinkCaptor.allValues.all { it == "/timetable" })
        assertEquals(
            setOf(
                Role.STUDENT,
                Role.PARENT,
                Role.TEACHER,
                Role.SURVEILLANT_GENERAL,
                Role.ADMINISTRATION,
                Role.DIRECTOR
            ),
            TimetablePublishedNotificationHandler.SCHOOL_RECIPIENT_ROLES.toSet()
        )
    }

    @Test
    fun `revision greater than one uses update wording and INFO severity`() {
        val notificationService = mock<NotificationService>()
        val userAccountApi = mock<UserAccountApi>()
        val handler = TimetablePublishedNotificationHandler(notificationService, userAccountApi)

        whenever(
            userAccountApi.findActiveAccountIdsByRoles(any(), any())
        ).thenReturn(Flux.just("acc-1"))

        val titleCaptor = argumentCaptor<LocalizedText>()
        val severityCaptor = argumentCaptor<NotificationSeverity>()

        whenever(
            notificationService.deliver(
                schoolId = any(),
                recipientAccountId = any(),
                recipientStaffId = anyOrNull(),
                category = any(),
                severity = severityCaptor.capture(),
                title = titleCaptor.capture(),
                body = any(),
                sourceEventId = anyOrNull(),
                sourceEventType = anyOrNull(),
                resourceType = anyOrNull(),
                resourceId = anyOrNull(),
                deepLink = anyOrNull(),
                ackRequired = any()
            )
        ).thenReturn(
            Mono.just(
                InAppNotification(
                    schoolId = "school-1",
                    recipientAccountId = "acc-1",
                    category = NotificationCategory.TIMETABLE_PUBLISHED,
                    title = LocalizedText(fr = "x", en = "x"),
                    body = LocalizedText(fr = "y", en = "y")
                )
            )
        )

        val event = OutboxEvent(
            id = "evt-2",
            schoolId = "school-1",
            module = "timetable",
            aggregateType = "Timetable",
            aggregateId = "tt-2",
            eventType = "timetable.published",
            payload = mapOf("timetableId" to "tt-2", "revision" to 3),
            occurredAt = Instant.parse("2026-01-15T11:00:00Z"),
            status = OutboxStatus.PENDING
        )

        StepVerifier.create(handler.handle(event)).verifyComplete()
        assertEquals(NotificationSeverity.INFO, severityCaptor.firstValue)
        assertEquals("Emploi du temps mis à jour", titleCaptor.firstValue.fr)
    }
}
