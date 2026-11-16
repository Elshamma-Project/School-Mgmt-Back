package com.kobe.beesm.service.notification

import com.kobe.beesm.model.notification.NotificationCategory
import com.kobe.beesm.model.notification.NotificationSeverity
import com.kobe.beesm.model.shared.LocalizedText
import com.kobe.beesm.model.shared.OutboxEvent
import com.kobe.beesm.model.user.Role
import com.kobe.beesm.service.user.api.UserAccountApi
import com.kobe.beesm.shared.outbox.OutboxEventHandler
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * Notifie administration + directeur qu’un utilisateur a demandé une réinitialisation (Q-62).
 * L’envoi d’e-mail au demandeur reste hors v1.
 */
@Component
class PasswordResetRequestedNotificationHandler(
    private val notificationService: NotificationService,
    private val userAccountApi: UserAccountApi
) : OutboxEventHandler {
    override fun supports(eventType: String): Boolean = eventType == "user.password.reset.requested"

    override fun handle(event: OutboxEvent): Mono<Void> {
        val eventId = event.id ?: return Mono.empty()
        val accountId = event.payload["accountId"]?.toString() ?: event.aggregateId
        val username = event.payload["username"]?.toString()?.takeIf { it.isNotBlank() }
        val title = LocalizedText(
            fr = "Demande de réinitialisation de mot de passe",
            en = "Password reset request"
        )
        val body = LocalizedText(
            fr = if (username != null) {
                "Le compte $username demande une réinitialisation. Utilisez « Réinitialiser le mot de passe » sur la fiche compte."
            } else {
                "Un compte demande une réinitialisation de mot de passe. Vérifiez les comptes personnel."
            },
            en = if (username != null) {
                "Account $username requested a password reset. Use “Reset password” on the account record."
            } else {
                "An account requested a password reset. Check staff accounts."
            }
        )
        return userAccountApi
            .findActiveAccountIdsByRoles(
                event.schoolId,
                listOf(Role.ADMINISTRATION, Role.DIRECTOR)
            )
            .distinct()
            .concatMap { recipientId ->
                notificationService.deliver(
                    schoolId = event.schoolId,
                    recipientAccountId = recipientId,
                    category = NotificationCategory.SYSTEM,
                    severity = NotificationSeverity.WARNING,
                    title = title,
                    body = body,
                    sourceEventId = "$eventId:$recipientId",
                    sourceEventType = event.eventType,
                    resourceType = "UserAccount",
                    resourceId = accountId,
                    deepLink = "/administration/staff",
                    ackRequired = false
                )
            }
            .then()
    }
}
