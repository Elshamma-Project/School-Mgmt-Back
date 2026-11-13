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

/** Notifie le directeur lorsqu’une demande de correction post-clôture est créée (BR-80). */
@Component
class CorrectionRequestedNotificationHandler(
    private val notificationService: NotificationService,
    private val userAccountApi: UserAccountApi
) : OutboxEventHandler {
    override fun supports(eventType: String): Boolean = eventType == "grading.correction.requested"

    override fun handle(event: OutboxEvent): Mono<Void> {
        val eventId = event.id ?: return Mono.empty()
        val requestId = event.payload["requestId"]?.toString() ?: event.aggregateId
        val studentLabel = event.payload["studentLabel"]?.toString()?.takeIf { it.isNotBlank() }
        val classLabel = event.payload["classLabel"]?.toString()?.takeIf { it.isNotBlank() }
        val teacherLabel = event.payload["teacherLabel"]?.toString()?.takeIf { it.isNotBlank() }
        val title = LocalizedText(
            fr = "Demande de correction de note",
            en = "Grade correction request"
        )
        val body = LocalizedText(
            fr = buildBodyFr(studentLabel, classLabel, teacherLabel),
            en = buildBodyEn(studentLabel, classLabel, teacherLabel)
        )
        return userAccountApi
            .findActiveAccountIdsByRoles(event.schoolId, listOf(Role.DIRECTOR))
            .concatMap { accountId ->
                notificationService.deliver(
                    schoolId = event.schoolId,
                    recipientAccountId = accountId,
                    category = NotificationCategory.CORRECTION_REQUESTED,
                    severity = NotificationSeverity.WARNING,
                    title = title,
                    body = body,
                    sourceEventId = eventId,
                    sourceEventType = event.eventType,
                    resourceType = "GradeCorrectionRequest",
                    resourceId = requestId,
                    deepLink = "/director/sequences?tab=corrections",
                    ackRequired = false
                )
            }
            .then()
    }

    private fun buildBodyFr(
        studentLabel: String?,
        classLabel: String?,
        teacherLabel: String?
    ): String {
        if (studentLabel == null) {
            return "Une demande de correction post-clôture attend votre décision."
        }
        val details = buildList {
            if (classLabel != null) add("classe $classLabel")
            if (teacherLabel != null) add("enseignant $teacherLabel")
        }
        return if (details.isEmpty()) {
            "Une correction a été proposée pour $studentLabel. Revue requise."
        } else {
            "Une correction a été proposée pour $studentLabel (${details.joinToString(", ")}). Revue requise."
        }
    }

    private fun buildBodyEn(
        studentLabel: String?,
        classLabel: String?,
        teacherLabel: String?
    ): String {
        if (studentLabel == null) {
            return "A post-close correction request awaits your decision."
        }
        val details = buildList {
            if (classLabel != null) add("class $classLabel")
            if (teacherLabel != null) add("teacher $teacherLabel")
        }
        return if (details.isEmpty()) {
            "A correction was proposed for $studentLabel. Review required."
        } else {
            "A correction was proposed for $studentLabel (${details.joinToString(", ")}). Review required."
        }
    }
}
