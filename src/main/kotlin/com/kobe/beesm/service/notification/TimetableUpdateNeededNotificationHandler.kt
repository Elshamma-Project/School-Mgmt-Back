package com.kobe.beesm.service.notification

import com.kobe.beesm.model.notification.NotificationCategory
import com.kobe.beesm.model.notification.NotificationSeverity
import com.kobe.beesm.model.shared.LocalizedText
import com.kobe.beesm.model.shared.OutboxEvent
import com.kobe.beesm.model.user.Role
import com.kobe.beesm.service.academic.api.SchoolBrandApi
import com.kobe.beesm.service.timetable.api.TimetableApi
import com.kobe.beesm.service.user.api.UserAccountApi
import com.kobe.beesm.shared.outbox.OutboxEventHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * Quand une classe est créée ou réactivée alors qu’un EDT est déjà **PUBLISHED** pour l’année
 * et que cette classe n’a **aucune case**, notifie les comptes autorisés à éditer l’EDT
 * (admin, directeur, SG si `timetableSurveillantCanEdit`) afin de réviser puis republicier.
 *
 * La republication déclenche ensuite le broadcast `timetable.published` (tous les acteurs).
 */
@Component
class TimetableUpdateNeededNotificationHandler(
    private val notificationService: NotificationService,
    private val userAccountApi: UserAccountApi,
    private val timetableApi: TimetableApi,
    private val schoolBrandApi: SchoolBrandApi
) : OutboxEventHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(eventType: String): Boolean = eventType == "academic.class.changed"

    override fun handle(event: OutboxEvent): Mono<Void> {
        val eventId = event.id ?: return Mono.empty()
        val action = event.payload["action"]?.toString()?.uppercase().orEmpty()
        if (action !in TRIGGER_ACTIONS) {
            return Mono.empty()
        }
        val active = when (val a = event.payload["active"]) {
            is Boolean -> a
            is String -> a.equals("true", ignoreCase = true)
            else -> true
        }
        if (!active) return Mono.empty()

        val classId = event.payload["classId"]?.toString()?.takeIf { it.isNotBlank() }
            ?: event.aggregateId
        val academicYearId = event.payload["academicYearId"]?.toString()?.takeIf { it.isNotBlank() }
        if (academicYearId == null) {
            log.warn(
                "academic.class.changed missing academicYearId eventId={} classId={}",
                eventId,
                classId
            )
            return Mono.empty()
        }
        val className = event.payload["name"]?.toString()?.takeIf { it.isNotBlank() } ?: classId

        return timetableApi.findPublished(event.schoolId, academicYearId)
            .flatMap { published ->
                timetableApi.hasEntriesForClass(event.schoolId, published.id, classId)
                    .flatMap { hasEntries ->
                        if (hasEntries) {
                            log.info(
                                "class.changed skip update-needed: entries exist classId={} timetableId={}",
                                classId,
                                published.id
                            )
                            Mono.empty()
                        } else {
                            notifyEditors(
                                event = event,
                                eventId = eventId,
                                classId = classId,
                                className = className,
                                timetableId = published.id,
                                revision = published.revision
                            )
                        }
                    }
            }
            .then()
    }

    private fun notifyEditors(
        event: OutboxEvent,
        eventId: String,
        classId: String,
        className: String,
        timetableId: String,
        revision: Int
    ): Mono<Void> {
        val title = LocalizedText(
            fr = "Emploi du temps à mettre à jour",
            en = "Timetable needs an update"
        )
        val body = LocalizedText(
            fr = "La classe « $className » n’a aucun cours sur l’emploi du temps publié (rév. $revision). " +
                "Créez une nouvelle révision, ajoutez les créneaux, puis republiez.",
            en = "Class “$className” has no lessons on the published timetable (rev. $revision). " +
                "Create a new revision, add slots, then publish again."
        )

        return schoolBrandApi.getTimetableSettings(event.schoolId)
            .map { settings ->
                buildList {
                    add(Role.ADMINISTRATION)
                    add(Role.DIRECTOR)
                    if (settings.timetableSurveillantCanEdit) {
                        add(Role.SURVEILLANT_GENERAL)
                    }
                }
            }
            .defaultIfEmpty(listOf(Role.ADMINISTRATION, Role.DIRECTOR))
            .flatMapMany { roles ->
                userAccountApi.findActiveAccountIdsByRoles(event.schoolId, roles).distinct()
            }
            .concatMap { accountId ->
                notificationService.deliver(
                    schoolId = event.schoolId,
                    recipientAccountId = accountId,
                    category = NotificationCategory.TIMETABLE_UPDATE_NEEDED,
                    severity = NotificationSeverity.WARNING,
                    title = title,
                    body = body,
                    sourceEventId = eventId,
                    sourceEventType = event.eventType,
                    resourceType = "SchoolClass",
                    resourceId = classId,
                    deepLink = "/timetable?tab=editor&classId=$classId",
                    ackRequired = false
                )
            }
            .doOnComplete {
                log.info(
                    "timetable.update-needed notifications done eventId={} classId={} timetableId={}",
                    eventId,
                    classId,
                    timetableId
                )
            }
            .then()
    }

    companion object {
        val TRIGGER_ACTIONS: Set<String> = setOf("CREATED", "ACTIVATED")
    }
}
