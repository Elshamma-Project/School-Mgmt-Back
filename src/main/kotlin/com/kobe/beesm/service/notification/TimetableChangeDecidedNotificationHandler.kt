package com.kobe.beesm.service.notification

import com.kobe.beesm.model.notification.NotificationCategory
import com.kobe.beesm.model.notification.NotificationSeverity
import com.kobe.beesm.model.shared.LocalizedText
import com.kobe.beesm.model.shared.OutboxEvent
import com.kobe.beesm.shared.outbox.OutboxEventHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/** Notifie l’enseignant demandeur lorsqu’une demande de créneau est tranchée (BR-67). */
@Component
class TimetableChangeDecidedNotificationHandler(
    private val notificationService: NotificationService
) : OutboxEventHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(eventType: String): Boolean =
        eventType == "timetable.change.approved" || eventType == "timetable.change.rejected"

    override fun handle(event: OutboxEvent): Mono<Void> {
        val eventId = event.id ?: return Mono.empty()
        val payload = event.payload
        val requestId = payload["requestId"]?.toString() ?: event.aggregateId
        val requestedBy = payload["requestedBy"]?.toString()?.trim().orEmpty()
        if (requestedBy.isEmpty()) {
            log.info("timetable.change.decided missing requestedBy eventId={}", event.id)
            return Mono.empty()
        }
        val classLabel = payload["classLabel"]?.toString()?.takeIf { it.isNotBlank() }
        val approved = event.eventType == "timetable.change.approved" ||
            payload["decision"]?.toString()?.equals("APPROVED", ignoreCase = true) == true
        val title = if (approved) {
            LocalizedText(
                fr = "Modification d’emploi du temps approuvée",
                en = "Timetable change approved"
            )
        } else {
            LocalizedText(
                fr = "Modification d’emploi du temps refusée",
                en = "Timetable change rejected"
            )
        }
        val body = if (approved) {
            LocalizedText(
                fr = if (classLabel != null) {
                    "Votre demande de modification (classe $classLabel) a été approuvée. Elle est appliquée sur le brouillon."
                } else {
                    "Votre demande de modification a été approuvée. Elle est appliquée sur le brouillon."
                },
                en = if (classLabel != null) {
                    "Your timetable change request (class $classLabel) was approved and applied to the draft."
                } else {
                    "Your timetable change request was approved and applied to the draft."
                }
            )
        } else {
            val note = payload["reviewNote"]?.toString()?.trim().orEmpty()
            LocalizedText(
                fr = buildString {
                    append(
                        if (classLabel != null) {
                            "Votre demande de modification (classe $classLabel) a été refusée."
                        } else {
                            "Votre demande de modification a été refusée."
                        }
                    )
                    if (note.isNotEmpty()) append(" Motif : $note")
                },
                en = buildString {
                    append(
                        if (classLabel != null) {
                            "Your timetable change request (class $classLabel) was rejected."
                        } else {
                            "Your timetable change request was rejected."
                        }
                    )
                    if (note.isNotEmpty()) append(" Reason: $note")
                }
            )
        }
        return notificationService.deliver(
            schoolId = event.schoolId,
            recipientAccountId = requestedBy,
            recipientStaffId = payload["teacherId"]?.toString(),
            category = NotificationCategory.TIMETABLE_CHANGE_DECIDED,
            severity = if (approved) NotificationSeverity.SUCCESS else NotificationSeverity.WARNING,
            title = title,
            body = body,
            sourceEventId = eventId,
            sourceEventType = event.eventType,
            resourceType = "TimetableChangeRequest",
            resourceId = requestId,
            deepLink = "/teacher/timetable",
            ackRequired = false
        ).then()
    }
}
