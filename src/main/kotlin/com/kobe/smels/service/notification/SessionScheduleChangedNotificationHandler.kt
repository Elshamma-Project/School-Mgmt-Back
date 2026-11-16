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

/**
 * Synchronise la planification des examens : toute modification du calendrier d’une séquence
 * (`examDate` / période) notifie **tous** les comptes scolaires actifs.
 * Source de vérité = `EvaluationSession` (pas de doublon dans timetable).
 */
@Component
class SessionScheduleChangedNotificationHandler(
    private val notificationService: NotificationService,
    private val userAccountApi: UserAccountApi
) : OutboxEventHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(eventType: String): Boolean = eventType == "grading.session.schedule.changed"

    override fun handle(event: OutboxEvent): Mono<Void> {
        val eventId = event.id ?: return Mono.empty()
        val sessionId = event.payload["sessionId"]?.toString() ?: event.aggregateId
        val shortLabel = event.payload["shortLabel"]?.toString()?.takeIf { it.isNotBlank() } ?: "DEV"
        val examDate = event.payload["examDate"]?.toString()?.takeIf { it.isNotBlank() }
        val termNumber = when (val t = event.payload["termNumber"]) {
            is Number -> t.toInt()
            is String -> t.toIntOrNull() ?: 0
            else -> 0
        }
        val title = LocalizedText(
            fr = "Calendrier de composition mis à jour",
            en = "Exam schedule updated"
        )
        val body = if (examDate != null) {
            LocalizedText(
                fr = "La séquence $shortLabel (trimestre $termNumber) a pour date de composition le $examDate. Consultez le calendrier.",
                en = "Sequence $shortLabel (term $termNumber) exam date is now $examDate. Check the calendar."
            )
        } else {
            LocalizedText(
                fr = "Le calendrier de la séquence $shortLabel (trimestre $termNumber) a été modifié.",
                en = "The schedule for sequence $shortLabel (term $termNumber) has changed."
            )
        }

        return userAccountApi
            .findActiveAccountIdsByRoles(event.schoolId, SCHOOL_ROLES)
            .distinct()
            .concatMap { accountId ->
                notificationService.deliver(
                    schoolId = event.schoolId,
                    recipientAccountId = accountId,
                    category = NotificationCategory.SESSION_SCHEDULE_CHANGED,
                    severity = NotificationSeverity.INFO,
                    title = title,
                    body = body,
                    sourceEventId = eventId,
                    sourceEventType = event.eventType,
                    resourceType = "EvaluationSession",
                    resourceId = sessionId,
                    deepLink = "/timetable?tab=exams",
                    ackRequired = false
                )
            }
            .doOnComplete {
                log.info("session.schedule.changed notifications done eventId={}", eventId)
            }
            .then()
    }

    companion object {
        val SCHOOL_ROLES: List<Role> = listOf(
            Role.STUDENT,
            Role.PARENT,
            Role.TEACHER,
            Role.SURVEILLANT_GENERAL,
            Role.ADMINISTRATION,
            Role.DIRECTOR
        )
    }
}
