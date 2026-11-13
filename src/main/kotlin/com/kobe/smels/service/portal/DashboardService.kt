package com.kobe.smels.service.portal

import com.kobe.smels.dto.grading.EntryProgressDto
import com.kobe.smels.dto.grading.EvaluationSessionSummaryDto
import com.kobe.smels.dto.grading.ReportSheetStatusCountsDto
import com.kobe.smels.dto.portal.DashboardAcademicYearDto
import com.kobe.smels.dto.portal.DashboardCountsDto
import com.kobe.smels.dto.portal.DashboardEnrollmentByCycleDto
import com.kobe.smels.dto.portal.DashboardEnrollmentOverviewDto
import com.kobe.smels.dto.portal.DashboardEntryProgressDto
import com.kobe.smels.dto.portal.DashboardNotificationBriefDto
import com.kobe.smels.dto.portal.DashboardNotificationsDto
import com.kobe.smels.dto.portal.DashboardReportSheetQueueDto
import com.kobe.smels.dto.portal.DashboardSessionBriefDto
import com.kobe.smels.dto.portal.DashboardSessionOverviewDto
import com.kobe.smels.dto.portal.DashboardSummaryResponse
import com.kobe.smels.dto.portal.DashboardTeacherWorkloadDto
import com.kobe.smels.dto.portal.LocalizedTextDto
import com.kobe.smels.model.grading.SessionStatus
import com.kobe.smels.model.shared.Cycle
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.service.academic.api.AcademicYearApi
import com.kobe.smels.service.academic.api.ClassSubjectApi
import com.kobe.smels.service.academic.api.LevelApi
import com.kobe.smels.service.grading.api.EvaluationSessionApi
import com.kobe.smels.service.grading.api.GradeCorrectionApi
import com.kobe.smels.service.grading.api.GradeReportSheetApi
import com.kobe.smels.service.grading.api.GradeSheetApi
import com.kobe.smels.service.notification.api.NotificationApi
import com.kobe.smels.service.staff.api.StaffApi
import com.kobe.smels.service.student.api.EnrollmentApi
import com.kobe.smels.service.student.api.StudentApi
import com.kobe.smels.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional

/**
 * Agrégat lecture seule du tableau de bord (module portal — docs/08).
 * Orchestre uniquement via `service/*/api` ; aucun repository métier.
 */
@Service
class DashboardService(
    private val academicYearApi: AcademicYearApi,
    private val studentApi: StudentApi,
    private val staffApi: StaffApi,
    private val enrollmentApi: EnrollmentApi,
    private val levelApi: LevelApi,
    private val classSubjectApi: ClassSubjectApi,
    private val evaluationSessionApi: EvaluationSessionApi,
    private val gradeReportSheetApi: GradeReportSheetApi,
    private val gradeCorrectionApi: GradeCorrectionApi,
    private val gradeSheetApi: GradeSheetApi,
    private val notificationApi: NotificationApi
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val douala = ZoneId.of("Africa/Douala")

    fun summary(currentUser: CurrentUser, schoolIdOverride: String?): Mono<DashboardSummaryResponse> {
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        if (currentUser.isSuperAdmin()) {
            log.info(
                "SUPER_ADMIN tenant action action=dashboard.summary by={} targetSchoolId={}",
                currentUser.username,
                schoolId
            )
        }

        val canStudents = currentUser.hasAnyRole(
            Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN
        )
        val canStaff = canStudents
        val canEnrollments = canStudents
        val canSessions = currentUser.hasAnyRole(
            Role.TEACHER,
            Role.SURVEILLANT_GENERAL,
            Role.ADMINISTRATION,
            Role.DIRECTOR,
            Role.SUPER_ADMIN
        )
        val canSheets = currentUser.hasAnyRole(
            Role.SURVEILLANT_GENERAL,
            Role.ADMINISTRATION,
            Role.DIRECTOR,
            Role.SUPER_ADMIN
        )
        val canCorrections = currentUser.hasAnyRole(Role.DIRECTOR, Role.SUPER_ADMIN)
        val canEntryProgress = currentUser.hasAnyRole(
            Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN
        )
        val isTeacher = currentUser.hasRole(Role.TEACHER) &&
            currentUser.profileType == ProfileType.STAFF &&
            !currentUser.profileId.isNullOrBlank()

        val yearMono: Mono<Optional<DashboardAcademicYearDto>> =
            academicYearApi.findCurrent(schoolId)
                .map { year ->
                    Optional.of(
                        DashboardAcademicYearDto(
                            id = year.id,
                            label = year.label,
                            status = year.status,
                            current = year.current,
                            locked = year.locked
                        )
                    )
                }
                .onErrorResume { Mono.just(Optional.empty()) }
                .defaultIfEmpty(Optional.empty())

        val notifsMono = Mono.zip(
            notificationApi.countUnread(schoolId, currentUser.accountId).defaultIfEmpty(0L),
            notificationApi.listRecent(schoolId, currentUser.accountId, 5).collectList()
                .defaultIfEmpty(emptyList())
        ).map { tuple ->
            DashboardNotificationsDto(
                unreadCount = tuple.t1,
                recent = tuple.t2.map { n ->
                    DashboardNotificationBriefDto(
                        id = n.id,
                        category = n.category,
                        severity = n.severity,
                        title = LocalizedTextDto(n.title.fr, n.title.en),
                        body = LocalizedTextDto(n.body.fr, n.body.en),
                        createdAt = n.createdAt,
                        unread = n.unread
                    )
                }
            )
        }.defaultIfEmpty(DashboardNotificationsDto(0, emptyList()))

        return yearMono.flatMap { yearOpt ->
            val year = yearOpt.orElse(null)
            val yearId = year?.id

            val studentsMono: Mono<Optional<Long>> =
                if (canStudents) {
                    studentApi.countActive(schoolId).defaultIfEmpty(0L).map { Optional.of(it) }
                } else Mono.just(Optional.empty())

            val staffMono: Mono<Optional<Long>> =
                if (canStaff) {
                    staffApi.countActive(schoolId).defaultIfEmpty(0L).map { Optional.of(it) }
                } else Mono.just(Optional.empty())

            val enrolledMono: Mono<Optional<Long>> =
                if (canEnrollments && yearId != null) {
                    enrollmentApi.countEnrolledByYear(schoolId, yearId).defaultIfEmpty(0L)
                        .map { Optional.of(it) }
                } else Mono.just(Optional.empty())

            val sessionsMono: Mono<Pair<Optional<DashboardSessionOverviewDto>, List<EvaluationSessionSummaryDto>>> =
                if (canSessions && yearId != null) {
                    evaluationSessionApi.findByYear(schoolId, yearId).collectList()
                        .map { sessions ->
                            Optional.of(buildSessionOverview(sessions)) to sessions
                        }
                        .defaultIfEmpty(Optional.empty<DashboardSessionOverviewDto>() to emptyList())
                } else {
                    Mono.just(Optional.empty<DashboardSessionOverviewDto>() to emptyList())
                }

            val workloadMono: Mono<Optional<DashboardTeacherWorkloadDto>> =
                if (isTeacher && yearId != null) {
                    classSubjectApi.findByTeacher(schoolId, currentUser.profileId!!, yearId)
                        .collectList()
                        .map { rows ->
                            val active = rows.filter { it.active }
                            Optional.of(
                                DashboardTeacherWorkloadDto(
                                    assignmentCount = active.size,
                                    classCount = active.map { it.classId }.distinct().size,
                                    weeklyHours = active.sumOf { it.weeklyHours ?: 0 }
                                )
                            )
                        }
                        .defaultIfEmpty(Optional.empty())
                } else Mono.just(Optional.empty())

            val enrollmentsOverviewMono: Mono<Optional<DashboardEnrollmentOverviewDto>> =
                if (canEnrollments && yearId != null) {
                    buildEnrollmentOverview(schoolId, yearId).map { Optional.of(it) }
                        .defaultIfEmpty(Optional.empty())
                } else Mono.just(Optional.empty())

            Mono.zip(
                studentsMono,
                staffMono,
                enrolledMono,
                sessionsMono,
                workloadMono,
                enrollmentsOverviewMono,
                notifsMono
            ).flatMap { tuple ->
                val students = tuple.t1.orElse(null)
                val staff = tuple.t2.orElse(null)
                val enrolled = tuple.t3.orElse(null)
                val sessionOverview = tuple.t4.first.orElse(null)
                val sessions = tuple.t4.second
                val workload = tuple.t5.orElse(null)
                val enrollments = tuple.t6.orElse(null)
                val notifications = tuple.t7
                val openSessions = sessions.filter { it.status == SessionStatus.OPEN }
                val termNumber = openSessions.firstOrNull()?.termNumber
                    ?: sessions.maxByOrNull { it.number }?.termNumber
                    ?: 1

                val sheetsMono: Mono<Optional<DashboardReportSheetQueueDto>> =
                    if (canSheets && yearId != null) {
                        gradeReportSheetApi.countByStatusForTerm(schoolId, yearId, termNumber)
                            .map { Optional.of(toReportSheetQueue(termNumber, it)) }
                            .defaultIfEmpty(Optional.empty())
                    } else Mono.just(Optional.empty())

                val correctionsMono: Mono<Optional<Long>> =
                    if (canCorrections) {
                        gradeCorrectionApi.countPending(schoolId).defaultIfEmpty(0L)
                            .map { Optional.of(it) }
                    } else Mono.just(Optional.empty())

                val entryMono: Mono<Optional<DashboardEntryProgressDto>> =
                    if (canEntryProgress && yearId != null && openSessions.isNotEmpty()) {
                        classSubjectApi.findActiveByYear(schoolId, yearId).count()
                            .flatMap { expectedPerSession ->
                                gradeSheetApi.entryProgressForSessions(
                                    schoolId,
                                    openSessions.map { it.id },
                                    expectedPerSession
                                )
                            }
                            .map { Optional.of(toEntryProgress(it)) }
                            .defaultIfEmpty(Optional.empty())
                    } else Mono.just(Optional.empty())

                Mono.zip(sheetsMono, correctionsMono, entryMono).map { inner ->
                    val sheets = inner.t1.orElse(null)
                    val pendingCorrections = inner.t2.orElse(null)
                    val entryProgress = inner.t3.orElse(null)
                    val pendingSheets = when {
                        sheets == null -> null
                        currentUser.hasAnyRole(Role.DIRECTOR, Role.SUPER_ADMIN) ->
                            (sheets.submitted + sheets.visaSg).toInt()
                        else -> sheets.submitted.toInt()
                    }

                    DashboardSummaryResponse(
                        academicYear = year,
                        counts = DashboardCountsDto(
                            activeStudents = students,
                            activeStaff = staff,
                            enrolledStudents = enrolled,
                            openSessions = sessionOverview?.open,
                            teacherAssignments = workload?.assignmentCount,
                            pendingReportSheets = pendingSheets,
                            pendingCorrections = pendingCorrections?.toInt(),
                            unreadNotifications = notifications.unreadCount
                        ),
                        sessions = sessionOverview,
                        reportSheets = sheets,
                        entryProgress = entryProgress,
                        enrollments = enrollments,
                        teacherWorkload = workload,
                        notifications = notifications
                    )
                }
            }
        }
    }

    private fun buildSessionOverview(
        sessions: List<EvaluationSessionSummaryDto>
    ): DashboardSessionOverviewDto {
        val today = LocalDate.now(douala)
        val briefs = sessions.map { s ->
            val deadline = s.entryDeadline
            val overdue = deadline != null && s.status == SessionStatus.OPEN && deadline.isBefore(today)
            val soon = deadline != null &&
                s.status == SessionStatus.OPEN &&
                !overdue &&
                !deadline.isAfter(today.plusDays(7))
            DashboardSessionBriefDto(
                id = s.id,
                number = s.number,
                termNumber = s.termNumber,
                shortLabel = s.shortLabel,
                name = s.name?.let { LocalizedTextDto(it.fr, it.en) },
                status = s.status,
                entryDeadline = s.entryDeadline,
                examDate = s.examDate,
                deadlineSoon = soon,
                deadlineOverdue = overdue
            )
        }
        val open = briefs.filter { it.status == SessionStatus.OPEN }
        return DashboardSessionOverviewDto(
            open = open.size,
            closed = briefs.count { it.status == SessionStatus.CLOSED },
            planned = briefs.count { it.status == SessionStatus.PLANNED },
            total = briefs.size,
            openSessions = open,
            upcomingDeadlines = briefs
                .filter { it.deadlineSoon || it.deadlineOverdue }
                .sortedWith(
                    compareBy<DashboardSessionBriefDto> { it.entryDeadline ?: LocalDate.MAX }
                        .thenBy { it.number }
                )
        )
    }

    private fun toReportSheetQueue(
        termNumber: Int,
        counts: ReportSheetStatusCountsDto
    ) = DashboardReportSheetQueueDto(
        termNumber = termNumber,
        draft = counts.draft,
        submitted = counts.submitted,
        visaSg = counts.visaSg,
        visaPrincipal = counts.visaPrincipal,
        rejected = counts.rejected,
        pendingReview = counts.pendingReview
    )

    private fun toEntryProgress(dto: EntryProgressDto) = DashboardEntryProgressDto(
        expectedSheets = dto.expectedSheets,
        draftSheets = dto.draftSheets,
        submittedSheets = dto.submittedSheets,
        lockedSheets = dto.lockedSheets,
        existingSheets = dto.existingSheets,
        completionRatio = dto.completionRatio
    )

    private fun buildEnrollmentOverview(
        schoolId: String,
        academicYearId: String
    ): Mono<DashboardEnrollmentOverviewDto> =
        Mono.zip(
            enrollmentApi.countEnrolledByYear(schoolId, academicYearId).defaultIfEmpty(0L),
            enrollmentApi.countEnrolledByLevelForYear(schoolId, academicYearId).collectList()
                .defaultIfEmpty(emptyList()),
            levelApi.findActiveBySchool(schoolId).collectMap({ it.id }, { it.cycle })
                .defaultIfEmpty(emptyMap())
        ).map { tuple ->
            val total = tuple.t1
            val byLevel = tuple.t2
            val cycleByLevel = tuple.t3
            val byCycle = byLevel
                .groupBy { row -> cycleByLevel[row.levelId] ?: Cycle.SECONDAIRE_1ER_CYCLE }
                .map { (cycle, rows) ->
                    DashboardEnrollmentByCycleDto(
                        cycle = cycle,
                        enrolledCount = rows.sumOf { it.enrolledCount }
                    )
                }
                .sortedBy { it.cycle.name }
            DashboardEnrollmentOverviewDto(totalEnrolled = total, byCycle = byCycle)
        }
}
