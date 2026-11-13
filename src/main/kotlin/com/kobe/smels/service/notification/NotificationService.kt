package com.kobe.smels.service.notification

import com.kobe.smels.dto.notification.InAppNotificationResponse
import com.kobe.smels.dto.notification.LocalizedTextDto
import com.kobe.smels.dto.notification.UnreadCountResponse
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.notification.InAppNotification
import com.kobe.smels.model.notification.NotificationCategory
import com.kobe.smels.model.notification.NotificationSeverity
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.repository.notification.InAppNotificationRepository
import com.kobe.smels.shared.security.CurrentUser
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class NotificationService(
    private val notificationRepository: InAppNotificationRepository
) {
    fun listMine(
        currentUser: CurrentUser,
        unreadOnly: Boolean = false,
        schoolIdOverride: String? = null
    ): Flux<InAppNotificationResponse> {
        NotificationAccess.assertCanUseInbox(currentUser)
        val schoolId = NotificationAccess.resolveSchoolId(currentUser, schoolIdOverride)
        val flux = if (unreadOnly) {
            notificationRepository.findBySchoolIdAndRecipientAccountIdAndReadAtIsNullOrderByCreatedAtDesc(
                schoolId,
                currentUser.accountId
            )
        } else {
            notificationRepository.findBySchoolIdAndRecipientAccountIdOrderByCreatedAtDesc(
                schoolId,
                currentUser.accountId
            )
        }
        return flux.take(100).map { toResponse(it) }
    }

    fun unreadCount(currentUser: CurrentUser, schoolIdOverride: String? = null): Mono<UnreadCountResponse> {
        NotificationAccess.assertCanUseInbox(currentUser)
        val schoolId = NotificationAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return notificationRepository
            .countBySchoolIdAndRecipientAccountIdAndReadAtIsNull(schoolId, currentUser.accountId)
            .map { UnreadCountResponse(it) }
            .defaultIfEmpty(UnreadCountResponse(0))
    }

    fun markRead(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<InAppNotificationResponse> {
        NotificationAccess.assertCanUseInbox(currentUser)
        val schoolId = NotificationAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return notificationRepository
            .findByIdAndSchoolIdAndRecipientAccountId(id, schoolId, currentUser.accountId)
            .switchIfEmpty(Mono.error(NotFoundException("NOTIFICATION_NOT_FOUND", "Notification not found")))
            .flatMap { notif ->
                if (notif.readAt == null) {
                    notif.readAt = Instant.now()
                    notificationRepository.save(notif)
                } else {
                    Mono.just(notif)
                }
            }
            .map { toResponse(it) }
    }

    fun acknowledge(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<InAppNotificationResponse> {
        NotificationAccess.assertCanUseInbox(currentUser)
        val schoolId = NotificationAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return notificationRepository
            .findByIdAndSchoolIdAndRecipientAccountId(id, schoolId, currentUser.accountId)
            .switchIfEmpty(Mono.error(NotFoundException("NOTIFICATION_NOT_FOUND", "Notification not found")))
            .flatMap { notif ->
                if (!notif.ackRequired) {
                    return@flatMap Mono.error(
                        BusinessRuleException("ACK_NOT_REQUIRED", "This notification does not require acknowledgement")
                    )
                }
                val now = Instant.now()
                if (notif.readAt == null) notif.readAt = now
                if (notif.ackedAt == null) notif.ackedAt = now
                notificationRepository.save(notif)
            }
            .map { toResponse(it) }
    }

    /**
     * Création idempotente (ignore les doublons sourceEventId + destinataire).
     */
    fun deliver(
        schoolId: String,
        recipientAccountId: String,
        recipientStaffId: String? = null,
        category: NotificationCategory,
        severity: NotificationSeverity = NotificationSeverity.INFO,
        title: LocalizedText,
        body: LocalizedText,
        sourceEventId: String?,
        sourceEventType: String?,
        resourceType: String? = null,
        resourceId: String? = null,
        deepLink: String? = null,
        ackRequired: Boolean = false
    ): Mono<InAppNotification> {
        if (sourceEventId != null) {
            return notificationRepository
                .findBySchoolIdAndSourceEventIdAndRecipientAccountId(schoolId, sourceEventId, recipientAccountId)
                .switchIfEmpty(
                    Mono.defer {
                        saveNew(
                            schoolId,
                            recipientAccountId,
                            recipientStaffId,
                            category,
                            severity,
                            title,
                            body,
                            sourceEventId,
                            sourceEventType,
                            resourceType,
                            resourceId,
                            deepLink,
                            ackRequired
                        )
                    }
                )
        }
        return saveNew(
            schoolId,
            recipientAccountId,
            recipientStaffId,
            category,
            severity,
            title,
            body,
            null,
            sourceEventType,
            resourceType,
            resourceId,
            deepLink,
            ackRequired
        )
    }

    private fun saveNew(
        schoolId: String,
        recipientAccountId: String,
        recipientStaffId: String?,
        category: NotificationCategory,
        severity: NotificationSeverity,
        title: LocalizedText,
        body: LocalizedText,
        sourceEventId: String?,
        sourceEventType: String?,
        resourceType: String?,
        resourceId: String?,
        deepLink: String?,
        ackRequired: Boolean
    ): Mono<InAppNotification> {
        val notif = InAppNotification(
            schoolId = schoolId,
            recipientAccountId = recipientAccountId,
            recipientStaffId = recipientStaffId,
            category = category,
            severity = severity,
            title = title,
            body = body,
            sourceEventId = sourceEventId,
            sourceEventType = sourceEventType,
            resourceType = resourceType,
            resourceId = resourceId,
            deepLink = deepLink,
            ackRequired = ackRequired
        )
        return notificationRepository.save(notif)
            .onErrorResume(DuplicateKeyException::class.java) {
                if (sourceEventId == null) Mono.error(it)
                else notificationRepository
                    .findBySchoolIdAndSourceEventIdAndRecipientAccountId(
                        schoolId,
                        sourceEventId,
                        recipientAccountId
                    )
            }
    }

    private fun toResponse(n: InAppNotification) = InAppNotificationResponse(
        id = n.id!!,
        schoolId = n.schoolId,
        category = n.category,
        severity = n.severity,
        title = LocalizedTextDto(n.title.fr, n.title.en),
        body = LocalizedTextDto(n.body.fr, n.body.en),
        sourceEventType = n.sourceEventType,
        resourceType = n.resourceType,
        resourceId = n.resourceId,
        deepLink = n.deepLink,
        ackRequired = n.ackRequired,
        createdAt = n.createdAt ?: Instant.now(),
        readAt = n.readAt,
        ackedAt = n.ackedAt,
        unread = n.readAt == null,
        pendingAck = n.ackRequired && n.ackedAt == null
    )
}
