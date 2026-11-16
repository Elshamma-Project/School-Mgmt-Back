package com.kobe.smels.service.notification

import com.kobe.smels.model.notification.NotificationCategory
import com.kobe.smels.model.notification.NotificationSeverity
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.OutboxEvent
import com.kobe.smels.model.user.Role
import com.kobe.smels.service.user.api.UserAccountApi
import com.kobe.smels.shared.outbox.OutboxEventHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/** Inbox admin/direction lorsqu’un EDT est publié (docs/08 `timetable.published`). */
@Component
class TimetablePublishedNotificationHandler(
    private val notificationService: NotificationService,
    private val userAccountApi: UserAccountApi
) : OutboxEventHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(eventType: String): Boolean = eventType == "timetable.published"

    override fun handle(event: OutboxEvent): Mono<Void> {
        val eventId = event.id ?: return Mono.empty()
        val timetableId = event.payload["timetableId"]?.toString() ?: event.aggregateId
        val revision = when (val r = event.payload["revision"]) {
            is Number -> r.toInt()
            is String -> r.toIntOrNull() ?: 0
            else -> 0
        }
        val title = LocalizedText(
            fr = "Emploi du temps publié",
            en = "Timetable published"
        )
        val body = LocalizedText(
            fr = "La révision $revision de l’emploi du temps est publiée.",
            en = "Timetable revision $revision has been published."
        )
        return userAccountApi
            .findActiveAccountIdsByRoles(
                event.schoolId,
                listOf(Role.ADMINISTRATION, Role.DIRECTOR, Role.SURVEILLANT_GENERAL)
            )
            .distinct()
            .concatMap { accountId ->
                notificationService.deliver(
                    schoolId = event.schoolId,
                    recipientAccountId = accountId,
                    category = NotificationCategory.TIMETABLE_PUBLISHED,
                    severity = NotificationSeverity.SUCCESS,
                    title = title,
                    body = body,
                    sourceEventId = eventId,
                    sourceEventType = event.eventType,
                    resourceType = "Timetable",
                    resourceId = timetableId,
                    deepLink = "/administration/timetable",
                    ackRequired = false
                )
            }
            .doOnComplete {
                log.info("timetable.published notifications done eventId={}", event.id)
            }
            .then()
    }
}
