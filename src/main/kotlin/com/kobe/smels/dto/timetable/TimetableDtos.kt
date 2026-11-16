package com.kobe.smels.dto.timetable

import com.kobe.smels.model.timetable.ActivityType
import com.kobe.smels.model.timetable.EntryKind
import com.kobe.smels.model.timetable.PeriodKind
import com.kobe.smels.model.timetable.TimetableStatus
import com.kobe.smels.model.shared.Section
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

data class LocalizedTextDto(
    val fr: String? = null,
    val en: String? = null
)

data class ContactInfoDto(
    val phones: List<String> = emptyList(),
    val email: String? = null,
    val address: String? = null,
    val city: String? = null
)

data class GridPeriodDto(
    val number: Int? = null,
    @field:NotNull
    val kind: PeriodKind,
    @field:NotNull
    val start: LocalTime,
    @field:NotNull
    val end: LocalTime,
    @field:Valid
    val label: LocalizedTextDto? = null
)

data class CreateTimeSlotGridRequest(
    @field:NotBlank
    val academicYearId: String,
    @field:NotBlank
    @field:Size(max = 120)
    val name: String,
    @field:NotEmpty
    val days: Set<DayOfWeek>,
    @field:NotEmpty
    @field:Valid
    val periods: List<GridPeriodDto>,
    val active: Boolean = true,
    val schoolId: String? = null
)

data class UpdateTimeSlotGridRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val name: String,
    @field:NotEmpty
    val days: Set<DayOfWeek>,
    @field:NotEmpty
    @field:Valid
    val periods: List<GridPeriodDto>,
    val active: Boolean = true
)

data class TimeSlotGridResponse(
    val id: String,
    val schoolId: String,
    val academicYearId: String,
    val name: String,
    val days: Set<DayOfWeek>,
    val periods: List<GridPeriodDto>,
    val active: Boolean
)

data class CreateTimetableRequest(
    @field:NotBlank
    val academicYearId: String,
    @field:NotBlank
    val gridId: String,
    @field:Valid
    val headerContact: ContactInfoDto? = null,
    val note: String? = null,
    val schoolId: String? = null
)

data class UpdateTimetableRequest(
    @field:Valid
    val headerContact: ContactInfoDto? = null,
    val note: String? = null
)

data class TimetableResponse(
    val id: String,
    val schoolId: String,
    val academicYearId: String,
    val gridId: String,
    val revision: Int,
    val status: TimetableStatus,
    val generatedAt: Instant?,
    val generatedBy: String?,
    val publishedAt: Instant?,
    val publishedBy: String?,
    val headerContact: ContactInfoDto?,
    val note: String?
)

data class UpsertTimetableEntryRequest(
    @field:NotBlank
    val classId: String,
    @field:NotNull
    val day: DayOfWeek,
    @field:Min(1)
    val startPeriod: Int? = null,
    @field:Min(1)
    val periodCount: Int = 1,
    val customStart: LocalTime? = null,
    val customEnd: LocalTime? = null,
    @field:NotNull
    val kind: EntryKind = EntryKind.LESSON,
    val classSubjectIds: List<String> = emptyList(),
    val subjectId: String? = null,
    val teacherId: String? = null,
    val roomId: String? = null,
    val activityType: ActivityType? = null,
    val sessionGroupId: String? = null,
    val optionGroupCode: String? = null,
    /** Vide = pas d’option (index unique). */
    val optionCode: String = "",
    @field:Valid
    val label: LocalizedTextDto? = null,
    val note: String? = null,
    val subjectLabel: String? = null,
    val teacherLabel: String? = null,
    val schoolId: String? = null
)

data class TimetableEntryResponse(
    val id: String,
    val schoolId: String,
    val academicYearId: String,
    val timetableId: String,
    val classId: String,
    val day: DayOfWeek,
    val startPeriod: Int?,
    val periodCount: Int,
    val customStart: LocalTime?,
    val customEnd: LocalTime?,
    val slotKey: String,
    val kind: EntryKind,
    val classSubjectIds: List<String>,
    val subjectId: String?,
    val teacherId: String?,
    val roomId: String?,
    val activityType: ActivityType?,
    val sessionGroupId: String?,
    val optionGroupCode: String?,
    val optionCode: String,
    val label: LocalizedTextDto?,
    val note: String?,
    val subjectLabel: String?,
    val teacherLabel: String?
)

data class UpsertTimetableSubjectAliasRequest(
    @field:NotBlank
    val academicYearId: String,
    val section: Section? = null,
    @field:NotBlank
    val aliasNormalized: String,
    val aliasVariants: List<String> = emptyList(),
    val classSubjectIds: List<String> = emptyList(),
    val unresolved: Boolean = false,
    val note: String? = null,
    val active: Boolean = true,
    val schoolId: String? = null
)

data class TimetableSubjectAliasResponse(
    val id: String,
    val schoolId: String,
    val academicYearId: String,
    val section: Section?,
    val sectionKey: String,
    val aliasNormalized: String,
    val aliasVariants: List<String>,
    val classSubjectIds: List<String>,
    val unresolved: Boolean,
    val note: String?,
    val active: Boolean
)

/** Résumé inter-modules (portal / notification). */
data class TimetableSummaryDto(
    val id: String,
    val academicYearId: String,
    val revision: Int,
    val status: TimetableStatus
)

enum class TimetableIssueSeverity { BLOCKING, WARNING }

data class TimetableIssueDto(
    val severity: TimetableIssueSeverity,
    val code: String,
    val message: String,
    val entryId: String? = null,
    val otherEntryId: String? = null,
    val classId: String? = null,
    val teacherId: String? = null,
    val roomId: String? = null,
    val classSubjectId: String? = null
)

data class TimetableEntryWriteResponse(
    val entry: TimetableEntryResponse,
    val warnings: List<TimetableIssueDto> = emptyList()
)

data class TimetableConflictsResponse(
    val timetableId: String,
    val blocking: List<TimetableIssueDto>,
    val warnings: List<TimetableIssueDto>
)

data class LinkSessionGroupRequest(
    @field:NotEmpty
    val entryIds: List<String>,
    val schoolId: String? = null
)

data class UnlinkSessionGroupRequest(
    val entryIds: List<String> = emptyList(),
    val schoolId: String? = null
)

data class SessionGroupResponse(
    val sessionGroupId: String,
    val entryIds: List<String>
)

data class DuplicateTimetableRequest(
    @field:NotBlank
    val targetAcademicYearId: String,
    /** Si absent, la grille source est clonée vers l’année cible. */
    val targetGridId: String? = null,
    val schoolId: String? = null
)
