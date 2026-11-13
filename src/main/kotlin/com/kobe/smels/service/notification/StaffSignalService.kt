package com.kobe.smels.service.notification

import com.kobe.smels.dto.notification.CreateStaffSignalRequest
import com.kobe.smels.dto.notification.StaffSignalResponse
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.notification.NotificationCategory
import com.kobe.smels.model.notification.NotificationSeverity
import com.kobe.smels.model.notification.StaffSignal
import com.kobe.smels.model.notification.StaffSignalStatus
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.notification.StaffSignalRepository
import com.kobe.smels.service.user.api.UserAccountApi
import com.kobe.smels.shared.security.CurrentUser
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

/** Signalements officiels enseignant → admin/directeur (BR-82). Pas de discussion. */
@Service
class StaffSignalService(
    private val staffSignalRepository: StaffSignalRepository,
    private val notificationService: NotificationService,
    private val userAccountApi: UserAccountApi
) {
    fun create(currentUser: CurrentUser, request: CreateStaffSignalRequest): Mono<StaffSignalResponse> {
        NotificationAccess.assertCanSubmitSignal(currentUser)
        val schoolId = NotificationAccess.resolveSchoolId(currentUser, request.schoolId)
        val subject = request.subject.trim()
        val message = request.message.trim()
        if (subject.length < 5) {
            return Mono.error(BusinessRuleException("SUBJECT_TOO_SHORT", "Subject must be at least 5 characters"))
        }
        if (message.length < 15) {
            return Mono.error(BusinessRuleException("MESSAGE_TOO_SHORT", "Message must be at least 15 characters"))
        }
        val authorLabel = currentUser.username
        val signal = StaffSignal(
            schoolId = schoolId,
            authorAccountId = currentUser.accountId,
            authorStaffId = currentUser.profileId,
            authorLabel = authorLabel,
            category = request.category,
            subject = subject,
            message = message,
            relatedSessionId = request.relatedSessionId?.trim()?.ifBlank { null },
            relatedSessionNumber = request.relatedSessionNumber,
            relatedClassSubjectId = request.relatedClassSubjectId?.trim()?.ifBlank { null }
        )
        return staffSignalRepository.save(signal).flatMap { saved ->
            notifyAdmins(saved).thenReturn(toResponse(saved))
        }
    }

    fun listMine(currentUser: CurrentUser, schoolIdOverride: String? = null): Flux<StaffSignalResponse> {
        NotificationAccess.assertCanSubmitSignal(currentUser)
        val schoolId = NotificationAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return staffSignalRepository
            .findBySchoolIdAndAuthorAccountIdOrderByCreatedAtDesc(schoolId, currentUser.accountId)
            .take(100)
            .map { toResponse(it) }
    }

    fun listForAdmin(
        currentUser: CurrentUser,
        status: StaffSignalStatus? = null,
        schoolIdOverride: String? = null
    ): Flux<StaffSignalResponse> {
        NotificationAccess.assertCanManageSignals(currentUser)
        val schoolId = NotificationAccess.resolveSchoolId(currentUser, schoolIdOverride)
        val flux = if (status != null) {
            staffSignalRepository.findBySchoolIdAndStatusOrderByCreatedAtDesc(schoolId, status)
        } else {
            staffSignalRepository.findBySchoolIdOrderByCreatedAtDesc(schoolId)
        }
        return flux.take(150).map { toResponse(it) }
    }

    fun acknowledge(
        currentUser: CurrentUser,
        id: String,
        adminNote: String?,
        schoolIdOverride: String? = null
    ): Mono<StaffSignalResponse> {
        NotificationAccess.assertCanManageSignals(currentUser)
        val schoolId = NotificationAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return staffSignalRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SIGNAL_NOT_FOUND", "Staff signal not found")))
            .flatMap { signal ->
                if (signal.status == StaffSignalStatus.CLOSED) {
                    return@flatMap Mono.error(
                        BusinessRuleException("SIGNAL_CLOSED", "Closed signals cannot be acknowledged")
                    )
                }
                signal.status = StaffSignalStatus.ACKNOWLEDGED
                signal.acknowledgedBy = currentUser.accountId
                signal.acknowledgedAt = Instant.now()
                if (!adminNote.isNullOrBlank()) signal.adminNote = adminNote.trim()
                staffSignalRepository.save(signal).flatMap { saved ->
                    notifyAuthor(
                        saved,
                        title = LocalizedText(
                            fr = "Accusé de réception de votre signalement",
                            en = "Acknowledgement of your report"
                        ),
                        body = LocalizedText(
                            fr = "L’administration a accusé réception de « ${saved.subject} ».",
                            en = "Administration acknowledged “${saved.subject}”."
                        )
                    ).thenReturn(toResponse(saved))
                }
            }
    }

    fun close(
        currentUser: CurrentUser,
        id: String,
        adminNote: String?,
        schoolIdOverride: String? = null
    ): Mono<StaffSignalResponse> {
        NotificationAccess.assertCanManageSignals(currentUser)
        val schoolId = NotificationAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return staffSignalRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SIGNAL_NOT_FOUND", "Staff signal not found")))
            .flatMap { signal ->
                val now = Instant.now()
                if (signal.acknowledgedAt == null) {
                    signal.acknowledgedBy = currentUser.accountId
                    signal.acknowledgedAt = now
                }
                signal.status = StaffSignalStatus.CLOSED
                signal.closedBy = currentUser.accountId
                signal.closedAt = now
                if (!adminNote.isNullOrBlank()) signal.adminNote = adminNote.trim()
                staffSignalRepository.save(signal).flatMap { saved ->
                    notifyAuthor(
                        saved,
                        title = LocalizedText(
                            fr = "Signalement clôturé",
                            en = "Report closed"
                        ),
                        body = LocalizedText(
                            fr = "Votre signalement « ${saved.subject} » a été clôturé.",
                            en = "Your report “${saved.subject}” has been closed."
                        )
                    ).thenReturn(toResponse(saved))
                }
            }
    }

    private fun notifyAdmins(signal: StaffSignal): Mono<Void> =
        userAccountApi
            .findActiveAccountIdsByRoles(
                signal.schoolId,
                listOf(Role.ADMINISTRATION, Role.DIRECTOR)
            )
            .filter { it != signal.authorAccountId }
            .concatMap { accountId ->
                notificationService.deliver(
                    schoolId = signal.schoolId,
                    recipientAccountId = accountId,
                    category = NotificationCategory.STAFF_SIGNAL,
                    severity = NotificationSeverity.WARNING,
                    title = LocalizedText(
                        fr = "Nouveau signalement enseignant",
                        en = "New teacher report"
                    ),
                    body = LocalizedText(
                        fr = "${signal.authorLabel} : ${signal.subject}",
                        en = "${signal.authorLabel}: ${signal.subject}"
                    ),
                    sourceEventId = "signal:${signal.id}:$accountId",
                    sourceEventType = "notification.staff-signal.created",
                    resourceType = "StaffSignal",
                    resourceId = signal.id,
                    deepLink = "/notifications?tab=signals",
                    ackRequired = false
                )
            }
            .then()

    private fun notifyAuthor(signal: StaffSignal, title: LocalizedText, body: LocalizedText): Mono<Void> =
        notificationService.deliver(
            schoolId = signal.schoolId,
            recipientAccountId = signal.authorAccountId,
            recipientStaffId = signal.authorStaffId,
            category = NotificationCategory.STAFF_SIGNAL,
            severity = NotificationSeverity.INFO,
            title = title,
            body = body,
            sourceEventId = "signal-status:${signal.id}:${signal.status.name}",
            sourceEventType = "notification.staff-signal.updated",
            resourceType = "StaffSignal",
            resourceId = signal.id,
            deepLink = "/notifications?tab=signals",
            ackRequired = false
        ).then()

    private fun toResponse(s: StaffSignal) = StaffSignalResponse(
        id = s.id!!,
        schoolId = s.schoolId,
        authorAccountId = s.authorAccountId,
        authorStaffId = s.authorStaffId,
        authorLabel = s.authorLabel,
        category = s.category,
        subject = s.subject,
        message = s.message,
        relatedSessionId = s.relatedSessionId,
        relatedSessionNumber = s.relatedSessionNumber,
        relatedClassSubjectId = s.relatedClassSubjectId,
        status = s.status,
        createdAt = s.createdAt,
        acknowledgedBy = s.acknowledgedBy,
        acknowledgedAt = s.acknowledgedAt,
        closedBy = s.closedBy,
        closedAt = s.closedAt,
        adminNote = s.adminNote
    )
}
