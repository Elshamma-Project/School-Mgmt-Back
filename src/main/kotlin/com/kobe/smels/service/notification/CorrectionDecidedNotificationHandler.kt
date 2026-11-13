package com.kobe.smels.service.notification

import com.kobe.smels.model.notification.NotificationCategory
import com.kobe.smels.model.notification.NotificationSeverity
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.OutboxEvent
import com.kobe.smels.shared.outbox.OutboxEventHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * Notifie l’enseignant demandeur lorsqu’une correction post-clôture est approuvée ou rejetée (BR-80).
 */
@Component
class CorrectionDecidedNotificationHandler(
    private val notificationService: NotificationService
) : OutboxEventHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(eventType: String): Boolean =
        eventType == "grading.correction.approved" || eventType == "grading.correction.rejected"

    override fun handle(event: OutboxEvent): Mono<Void> {
        val eventId = event.id ?: return Mono.empty()
        val payload = event.payload
        val requestId = payload["requestId"]?.toString() ?: event.aggregateId
        val requestedBy = payload["requestedBy"]?.toString()?.trim().orEmpty()
        if (requestedBy.isEmpty()) {
            log.info("correction.decided missing requestedBy eventId={}", event.id)
            return Mono.empty()
        }
        val classSubjectId = payload["classSubjectId"]?.toString()?.trim().orEmpty()
        val studentLabel = payload["studentLabel"]?.toString()?.takeIf { it.isNotBlank() }
        val approved = event.eventType == "grading.correction.approved" ||
            payload["decision"]?.toString()?.equals("APPROVED", ignoreCase = true) == true
        val title = if (approved) {
            LocalizedText(
                fr = "Correction de note approuvée",
                en = "Grade correction approved"
            )
        } else {
            LocalizedText(
                fr = "Correction de note rejetée",
                en = "Grade correction rejected"
            )
        }
        val body = if (approved) {
            LocalizedText(
                fr = if (studentLabel != null) {
                    "Votre proposition de correction pour $studentLabel a été approuvée."
                } else {
                    "Votre proposition de correction a été approuvée."
                },
                en = if (studentLabel != null) {
                    "Your correction proposal for $studentLabel was approved."
                } else {
                    "Your correction proposal was approved."
                }
            )
        } else {
            val note = payload["reviewNote"]?.toString()?.trim().orEmpty()
            LocalizedText(
                fr = buildString {
                    append(
                        if (studentLabel != null) {
                            "Votre proposition de correction pour $studentLabel a été rejetée."
                        } else {
                            "Votre proposition de correction a été rejetée."
                        }
                    )
                    if (note.isNotEmpty()) append(" Motif : $note")
                },
                en = buildString {
                    append(
                        if (studentLabel != null) {
                            "Your correction proposal for $studentLabel was rejected."
                        } else {
                            "Your correction proposal was rejected."
                        }
                    )
                    if (note.isNotEmpty()) append(" Reason: $note")
                }
            )
        }
        val deepLink = buildString {
            append("/teacher/grades")
            if (classSubjectId.isNotEmpty()) {
                append("?classSubjectId=")
                append(classSubjectId)
            }
        }
        return notificationService.deliver(
            schoolId = event.schoolId,
            recipientAccountId = requestedBy,
            recipientStaffId = payload["teacherId"]?.toString(),
            category = NotificationCategory.CORRECTION_DECIDED,
            severity = if (approved) NotificationSeverity.SUCCESS else NotificationSeverity.WARNING,
            title = title,
            body = body,
            sourceEventId = eventId,
            sourceEventType = event.eventType,
            resourceType = "GradeCorrectionRequest",
            resourceId = requestId,
            deepLink = deepLink,
            ackRequired = false
        ).then()
    }
}
