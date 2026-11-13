package com.kobe.smels.dto.notification

import com.kobe.smels.model.notification.NotificationCategory
import com.kobe.smels.model.notification.NotificationSeverity
import com.kobe.smels.model.notification.StaffSignalCategory
import com.kobe.smels.model.notification.StaffSignalStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class LocalizedTextDto(
    val fr: String? = null,
    val en: String? = null
)

data class InAppNotificationResponse(
    val id: String,
    val schoolId: String,
    val category: NotificationCategory,
    val severity: NotificationSeverity,
    val title: LocalizedTextDto,
    val body: LocalizedTextDto,
    val sourceEventType: String?,
    val resourceType: String?,
    val resourceId: String?,
    val deepLink: String?,
    val ackRequired: Boolean,
    val createdAt: Instant,
    val readAt: Instant?,
    val ackedAt: Instant?,
    val unread: Boolean,
    val pendingAck: Boolean
)

data class UnreadCountResponse(
    val count: Long
)

data class CreateStaffSignalRequest(
    val category: StaffSignalCategory = StaffSignalCategory.OTHER,
    @field:NotBlank @field:Size(min = 5, max = 120) val subject: String,
    @field:NotBlank @field:Size(min = 15, max = 2000) val message: String,
    val relatedSessionId: String? = null,
    val relatedSessionNumber: Int? = null,
    val relatedClassSubjectId: String? = null,
    val schoolId: String? = null
)

data class StaffSignalAdminNoteRequest(
    @field:NotBlank @field:Size(min = 3, max = 500) val adminNote: String,
    val schoolId: String? = null
)

data class StaffSignalResponse(
    val id: String,
    val schoolId: String,
    val authorAccountId: String,
    val authorStaffId: String?,
    val authorLabel: String,
    val category: StaffSignalCategory,
    val subject: String,
    val message: String,
    val relatedSessionId: String?,
    val relatedSessionNumber: Int?,
    val relatedClassSubjectId: String?,
    val status: StaffSignalStatus,
    val createdAt: Instant,
    val acknowledgedBy: String?,
    val acknowledgedAt: Instant?,
    val closedBy: String?,
    val closedAt: Instant?,
    val adminNote: String?
)
