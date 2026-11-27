package com.kobe.beesm.model.notification

import com.kobe.beesm.model.shared.LocalizedText
import com.kobe.beesm.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * Notification in-app (BR-81) : canal professionnel unique (pas de chat).
 *
 * Idempotence : un couple (`sourceEventId`, `recipientAccountId`) ne crée qu’une notif.
 * Accusé de réception : [ackRequired] + [ackedAt] (ex. clôture de séquence).
 */
@Document(collection = "notification_inbox")
@CompoundIndexes(
    CompoundIndex(
        name = "uk_event_recipient",
        def = "{'schoolId': 1, 'sourceEventId': 1, 'recipientAccountId': 1}",
        unique = true
    ),
    CompoundIndex(
        name = "idx_recipient_created",
        def = "{'schoolId': 1, 'recipientAccountId': 1, 'createdAt': -1}"
    ),
    CompoundIndex(
        name = "idx_recipient_unread",
        def = "{'schoolId': 1, 'recipientAccountId': 1, 'readAt': 1, 'createdAt': -1}"
    )
)
data class InAppNotification(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var recipientAccountId: String,
    var recipientStaffId: String? = null,

    var category: NotificationCategory,
    var severity: NotificationSeverity = NotificationSeverity.INFO,
    var title: LocalizedText,
    var body: LocalizedText,

    /** Identifiant de l’événement outbox source (idempotence). */
    var sourceEventId: String? = null,
    var sourceEventType: String? = null,

    var resourceType: String? = null,
    var resourceId: String? = null,
    /** Chemin relatif front (ex. /teacher/grades) — pas d’URL absolue. */
    var deepLink: String? = null,

    var ackRequired: Boolean = false,
    var readAt: Instant? = null,
    var ackedAt: Instant? = null
) : TenantDocument()

enum class NotificationCategory {
    SESSION_CLOSED,
    CORRECTION_REQUESTED,
    CORRECTION_DECIDED,
    REPORT_SHEET_REJECTED,
    STAFF_SIGNAL,
    /** Emploi du temps publié (`timetable.published`). */
    TIMETABLE_PUBLISHED,
    /** Demande de modification de créneau (BR-67). */
    TIMETABLE_CHANGE_REQUESTED,
    /** Décision admin/directeur sur une demande de créneau (BR-67). */
    TIMETABLE_CHANGE_DECIDED,
    /**
     * Classe active sans cases sur l’EDT publié (`academic.class.changed` CREATED/ACTIVATED).
     * Destinataires : comptes pouvant éditer l’EDT.
     */
    TIMETABLE_UPDATE_NEEDED,
    /** Calendrier de composition modifié (`grading.session.schedule.changed`). */
    SESSION_SCHEDULE_CHANGED,
    /** Rappel échéance tranche scolarité (`fees.tuition.reminder` BEFORE_DUE / ON_DUE). */
    FEES_REMINDER,
    /** Passage insolvable (`fees.tuition.reminder` INSOLVENT). */
    FEES_INSOLVENT,
    /** Pénalité hebdomadaire (`fees.tuition.reminder` PENALTY_WEEKLY). */
    FEES_PENALTY,
    /** Avertissement / exclusion pour insolvabilité. */
    FEES_EXCLUSION,
    /** Politique scolarité mise à jour (Direction) — `fees.policy.updated`. */
    FEES_POLICY_UPDATED,
    SYSTEM
}

enum class NotificationSeverity {
    INFO,
    SUCCESS,
    WARNING,
    URGENT
}
