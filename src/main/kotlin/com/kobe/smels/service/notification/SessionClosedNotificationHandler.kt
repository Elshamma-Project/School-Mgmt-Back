package com.kobe.smels.service.notification

import com.kobe.smels.model.notification.NotificationCategory
import com.kobe.smels.model.notification.NotificationSeverity
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.OutboxEvent
import com.kobe.smels.service.user.api.UserAccountApi
import com.kobe.smels.shared.outbox.OutboxEventHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * BR-81 : à la clôture d’une séquence, notifie chaque enseignant titulaire (accusé requis).
 */
@Component
class SessionClosedNotificationHandler(
    private val notificationService: NotificationService,
    private val userAccountApi: UserAccountApi
) : OutboxEventHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(eventType: String): Boolean = eventType == "grading.session.closed"

    override fun handle(event: OutboxEvent): Mono<Void> {
        val payload = event.payload
        val number = payloadNumber(payload["number"])
        val termNumber = payloadNumber(payload["termNumber"])
        val shortLabel = payload["shortLabel"]?.toString()?.takeIf { it.isNotBlank() } ?: "DEV$number"
        val sessionId = payload["sessionId"]?.toString() ?: event.aggregateId
        val teacherIds = payloadTeacherIds(payload["teacherIds"])
        if (teacherIds.isEmpty()) {
            log.info("session.closed no teacherIds eventId={}", event.id)
            return Mono.empty()
        }
        val eventId = event.id ?: return Mono.empty()
        val title = LocalizedText(
            fr = "Séquence $shortLabel clôturée",
            en = "Sequence $shortLabel closed"
        )
        val body = LocalizedText(
            fr = "La séquence $shortLabel (trimestre $termNumber) est clôturée. " +
                "La saisie directe est verrouillée. Veuillez accuser réception de cette information.",
            en = "Sequence $shortLabel (term $termNumber) is closed. " +
                "Direct entry is locked. Please acknowledge this notice."
        )
        return userAccountApi.findStaffLinkedAccounts(event.schoolId, teacherIds)
            .flatMapMany { linked ->
                if (linked.isEmpty()) {
                    log.info("session.closed no linked accounts eventId={}", event.id)
                    return@flatMapMany Flux.empty()
                }
                Flux.fromIterable(linked.entries)
                    .concatMap { (staffId, account) ->
                        notificationService.deliver(
                            schoolId = event.schoolId,
                            recipientAccountId = account.accountId,
                            recipientStaffId = staffId,
                            category = NotificationCategory.SESSION_CLOSED,
                            severity = NotificationSeverity.URGENT,
                            title = title,
                            body = body,
                            sourceEventId = eventId,
                            sourceEventType = event.eventType,
                            resourceType = "EvaluationSession",
                            resourceId = sessionId,
                            deepLink = "/teacher/grades?term=$termNumber",
                            ackRequired = true
                        )
                    }
            }
            .then()
    }

    private fun payloadNumber(value: Any?): Int = when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: 0
        else -> 0
    }

    @Suppress("UNCHECKED_CAST")
    private fun payloadTeacherIds(value: Any?): List<String> = when (value) {
        is Collection<*> -> value.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }.distinct()
        is Array<*> -> value.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }.distinct()
        else -> emptyList()
    }
}
