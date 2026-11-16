package com.kobe.beesm.service.notification

import com.kobe.beesm.model.notification.NotificationCategory
import com.kobe.beesm.model.notification.NotificationSeverity
import com.kobe.beesm.model.shared.LocalizedText
import com.kobe.beesm.model.shared.OutboxEvent
import com.kobe.beesm.model.user.Role
import com.kobe.beesm.service.user.api.UserAccountApi
import com.kobe.beesm.shared.outbox.OutboxEventHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * Inbox de **tous** les comptes scolaires actifs lorsqu’un EDT est publié ou republicié
 * (docs/08 `timetable.published`, BR-60).
 *
 * Destinataires : élèves, parents, enseignants, SG, administration, directeur.
 * Deep-link logique `/timetable` — le front le réécrit vers l’espace du rôle
 * (`/teacher/timetable`, `/parent/timetable`, etc.).
 */
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
        val isUpdate = revision > 1
        val title = if (isUpdate) {
            LocalizedText(
                fr = "Emploi du temps mis à jour",
                en = "Timetable updated"
            )
        } else {
            LocalizedText(
                fr = "Emploi du temps publié",
                en = "Timetable published"
            )
        }
        val body = if (isUpdate) {
            LocalizedText(
                fr = "La révision $revision de l’emploi du temps est publiée. Consultez la grille à jour.",
                en = "Timetable revision $revision has been published. Open the updated grid."
            )
        } else {
            LocalizedText(
                fr = "La révision $revision de l’emploi du temps est publiée. Consultez la grille.",
                en = "Timetable revision $revision has been published. Open the grid."
            )
        }
        val severity = if (isUpdate) NotificationSeverity.INFO else NotificationSeverity.SUCCESS

        return userAccountApi
            .findActiveAccountIdsByRoles(
                event.schoolId,
                SCHOOL_RECIPIENT_ROLES
            )
            .distinct()
            .concatMap { accountId ->
                notificationService.deliver(
                    schoolId = event.schoolId,
                    recipientAccountId = accountId,
                    category = NotificationCategory.TIMETABLE_PUBLISHED,
                    severity = severity,
                    title = title,
                    body = body,
                    sourceEventId = eventId,
                    sourceEventType = event.eventType,
                    resourceType = "Timetable",
                    resourceId = timetableId,
                    // Chemin logique : réécrit côté front selon le rôle (voir deep-link.ts).
                    deepLink = "/timetable",
                    ackRequired = false
                )
            }
            .doOnComplete {
                log.info("timetable.published notifications done eventId={}", event.id)
            }
            .then()
    }

    companion object {
        /** Tous les acteurs scolaires avec un espace EDT (hors SUPER_ADMIN plateforme). */
        val SCHOOL_RECIPIENT_ROLES: List<Role> = listOf(
            Role.STUDENT,
            Role.PARENT,
            Role.TEACHER,
            Role.SURVEILLANT_GENERAL,
            Role.ADMINISTRATION,
            Role.DIRECTOR
        )
    }
}
