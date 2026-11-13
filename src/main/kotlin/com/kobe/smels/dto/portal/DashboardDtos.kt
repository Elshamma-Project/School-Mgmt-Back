package com.kobe.smels.dto.portal

import com.kobe.smels.model.academic.AcademicYearStatus
import com.kobe.smels.model.grading.SessionStatus
import com.kobe.smels.model.notification.NotificationCategory
import com.kobe.smels.model.notification.NotificationSeverity
import com.kobe.smels.model.shared.Cycle
import java.time.Instant
import java.time.LocalDate

data class LocalizedTextDto(
    val fr: String? = null,
    val en: String? = null
)

data class DashboardAcademicYearDto(
    val id: String,
    val label: String,
    val status: AcademicYearStatus,
    val current: Boolean,
    val locked: Boolean
)

data class DashboardCountsDto(
    val activeStudents: Long? = null,
    val activeStaff: Long? = null,
    val enrolledStudents: Long? = null,
    val openSessions: Int? = null,
    val teacherAssignments: Int? = null,
    val pendingReportSheets: Int? = null,
    val pendingCorrections: Int? = null,
    val unreadNotifications: Long = 0
)

data class DashboardSessionBriefDto(
    val id: String,
    val number: Int,
    val termNumber: Int,
    val shortLabel: String?,
    val name: LocalizedTextDto?,
    val status: SessionStatus,
    val entryDeadline: LocalDate? = null,
    val examDate: LocalDate? = null,
    val deadlineSoon: Boolean = false,
    val deadlineOverdue: Boolean = false
)

data class DashboardSessionOverviewDto(
    val open: Int,
    val closed: Int,
    val planned: Int,
    val total: Int,
    val openSessions: List<DashboardSessionBriefDto> = emptyList(),
    val upcomingDeadlines: List<DashboardSessionBriefDto> = emptyList()
)

data class DashboardReportSheetQueueDto(
    val termNumber: Int,
    val draft: Long,
    val submitted: Long,
    val visaSg: Long,
    val visaPrincipal: Long,
    val rejected: Long,
    val pendingReview: Long
)

data class DashboardEntryProgressDto(
    val expectedSheets: Long,
    val draftSheets: Long,
    val submittedSheets: Long,
    val lockedSheets: Long,
    val existingSheets: Long,
    val completionRatio: Double
)

data class DashboardEnrollmentByCycleDto(
    val cycle: Cycle,
    val enrolledCount: Long
)

data class DashboardEnrollmentOverviewDto(
    val totalEnrolled: Long,
    val byCycle: List<DashboardEnrollmentByCycleDto> = emptyList()
)

data class DashboardTeacherWorkloadDto(
    val assignmentCount: Int,
    val classCount: Int,
    val weeklyHours: Int
)

data class DashboardNotificationBriefDto(
    val id: String,
    val category: NotificationCategory,
    val severity: NotificationSeverity,
    val title: LocalizedTextDto,
    val body: LocalizedTextDto,
    val createdAt: Instant,
    val unread: Boolean
)

data class DashboardNotificationsDto(
    val unreadCount: Long,
    val recent: List<DashboardNotificationBriefDto> = emptyList()
)

/**
 * Résumé tableau de bord — blocs null = non exposés pour le rôle courant.
 */
data class DashboardSummaryResponse(
    val academicYear: DashboardAcademicYearDto?,
    val counts: DashboardCountsDto,
    val sessions: DashboardSessionOverviewDto?,
    val reportSheets: DashboardReportSheetQueueDto?,
    val entryProgress: DashboardEntryProgressDto?,
    val enrollments: DashboardEnrollmentOverviewDto?,
    val teacherWorkload: DashboardTeacherWorkloadDto?,
    val notifications: DashboardNotificationsDto
)
