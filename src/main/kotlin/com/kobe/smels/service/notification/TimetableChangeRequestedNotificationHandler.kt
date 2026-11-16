package com.kobe.smels.service.notification

import com.kobe.smels.model.notification.NotificationCategory
import com.kobe.smels.model.notification.NotificationSeverity
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.OutboxEvent
import com.kobe.smels.model.user.Role
import com.kobe.smels.service.user.api.UserAccountApi
import com.kobe.smels.shared.outbox.OutboxEventHandler
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/** Notifie admin + directeur lorsqu’un enseignant demande une modif de créneau (BR-67). */
@Component
class TimetableChangeRequestedNotificationHandler(
    private val notificationService: NotificationService,
    private val userAccountApi: UserAccountApi
) : OutboxEventHandler {
    override fun supports(eventType: String): Boolean = eventType == "timetable.change.requested"

    override fun handle(event: OutboxEvent): Mono<Void> {
        val eventId = event.id ?: return Mono.empty()
        val requestId = event.payload["requestId"]?.toString() ?: event.aggregateId
        val teacherLabel = event.payload["teacherLabel"]?.toString()?.takeIf { it.isNotBlank() }
        val classLabel = event.payload["classLabel"]?.toString()?.takeIf { it.isNotBlank() }
        val title = LocalizedText(
            fr = "Demande de modification d’emploi du temps",
            en = "Timetable change request"
        )
        val body = LocalizedText(
            fr = buildBodyFr(teacherLabel, classLabel),
            en = buildBodyEn(teacherLabel, classLabel)
        )
        return userAccountApi
            .findActiveAccountIdsByRoles(
                event.schoolId,
                listOf(Role.ADMINISTRATION, Role.DIRECTOR)
            )
            .distinct()
            .concatMap { accountId ->
                notificationService.deliver(
                    schoolId = event.schoolId,
                    recipientAccountId = accountId,
                    category = NotificationCategory.TIMETABLE_CHANGE_REQUESTED,
                    severity = NotificationSeverity.WARNING,
                    title = title,
                    body = body,
                    sourceEventId = eventId,
                    sourceEventType = event.eventType,
                    resourceType = "TimetableChangeRequest",
                    resourceId = requestId,
                    deepLink = "/administration/timetable?tab=changes",
                    ackRequired = false
                )
            }
            .then()
    }

    private fun buildBodyFr(teacherLabel: String?, classLabel: String?): String {
        val who = teacherLabel ?: "Un enseignant"
        return if (classLabel != null) {
            "$who propose une modification de créneau (classe $classLabel). Revue requise."
        } else {
            "$who propose une modification de créneau. Revue requise."
        }
    }

    private fun buildBodyEn(teacherLabel: String?, classLabel: String?): String {
        val who = teacherLabel ?: "A teacher"
        return if (classLabel != null) {
            "$who proposed a timetable slot change (class $classLabel). Review required."
        } else {
            "$who proposed a timetable slot change. Review required."
        }
    }
}
