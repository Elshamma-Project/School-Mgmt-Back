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
 * Notifie Direction + Administration quand la politique scolarité change (Q-77).
 */
@Component
class FeesPolicyUpdatedNotificationHandler(
    private val notificationService: NotificationService,
    private val userAccountApi: UserAccountApi
) : OutboxEventHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(eventType: String): Boolean = eventType == "fees.policy.updated"

    override fun handle(event: OutboxEvent): Mono<Void> {
        val eventId = event.id ?: return Mono.empty()
        val title = LocalizedText(
            fr = "Politique de scolarité mise à jour",
            en = "Tuition policy updated"
        )
        val body = LocalizedText(
            fr = "Les paramètres de rappels, pénalités ou exclusion ont été modifiés. Consultez Scolarité.",
            en = "Reminder, penalty or exclusion settings were changed. Open Tuition settings."
        )
        return userAccountApi
            .findActiveAccountIdsByRoles(
                event.schoolId,
                listOf(Role.DIRECTOR, Role.ADMINISTRATION, Role.SUPER_ADMIN)
            )
            .distinct()
            .concatMap { accountId ->
                notificationService.deliver(
                    schoolId = event.schoolId,
                    recipientAccountId = accountId,
                    category = NotificationCategory.FEES_POLICY_UPDATED,
                    severity = NotificationSeverity.INFO,
                    title = title,
                    body = body,
                    sourceEventId = eventId,
                    sourceEventType = event.eventType,
                    resourceType = "FeesPolicy",
                    resourceId = event.aggregateId,
                    deepLink = "/fees",
                    ackRequired = false
                )
            }
            .doOnComplete {
                log.info("fees.policy.updated notifications done eventId={}", event.id)
            }
            .then()
    }
}
