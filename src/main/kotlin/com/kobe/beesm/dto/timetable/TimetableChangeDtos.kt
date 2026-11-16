package com.kobe.beesm.dto.timetable

import com.kobe.beesm.model.timetable.ActivityType
import com.kobe.beesm.model.timetable.EntryKind
import com.kobe.beesm.model.timetable.TimetableChangeKind
import com.kobe.beesm.model.timetable.TimetableChangeStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

data class TimetableEntrySnapshotDto(
    @field:NotBlank
    val classId: String,
    @field:NotNull
    val day: DayOfWeek,
    val startPeriod: Int? = null,
    val periodCount: Int = 1,
    val customStart: LocalTime? = null,
    val customEnd: LocalTime? = null,
    val kind: EntryKind = EntryKind.LESSON,
    val classSubjectIds: List<String> = emptyList(),
    val subjectId: String? = null,
    val teacherId: String? = null,
    val roomId: String? = null,
    val activityType: ActivityType? = null,
    val sessionGroupId: String? = null,
    val optionGroupCode: String? = null,
    val optionCode: String = "",
    @field:Valid
    val label: LocalizedTextDto? = null,
    val note: String? = null,
    val subjectLabel: String? = null,
    val teacherLabel: String? = null
)

data class CreateTimetableChangeRequest(
    @field:NotBlank
    val sourceTimetableId: String,
    /** Obligatoire sauf ADD. */
    val sourceEntryId: String? = null,
    @field:NotNull
    val changeKind: TimetableChangeKind = TimetableChangeKind.UPDATE,
    @field:Valid
    @field:NotNull
    val proposed: TimetableEntrySnapshotDto,
    @field:NotBlank
    @field:Size(min = 15, max = 2000)
    val reason: String,
    val classLabel: String? = null,
    val teacherLabel: String? = null,
    val schoolId: String? = null
)

data class ReviewTimetableChangeRequest(
    @field:NotBlank
    @field:Size(min = 5, max = 2000)
    val reviewNote: String,
    val schoolId: String? = null
)

data class TimetableChangeRequestResponse(
    val id: String,
    val schoolId: String,
    val academicYearId: String,
    val sourceTimetableId: String,
    val sourceRevision: Int,
    val sourceEntryId: String?,
    val sourceClassId: String,
    val sourceDay: DayOfWeek,
    val sourceSlotKey: String,
    val sourceOptionCode: String,
    val changeKind: TimetableChangeKind,
    val currentSnapshot: TimetableEntrySnapshotDto?,
    val proposed: TimetableEntrySnapshotDto,
    val classLabel: String?,
    val teacherId: String,
    val teacherLabel: String?,
    val reason: String,
    val status: TimetableChangeStatus,
    val requestedBy: String,
    val requestedAt: Instant,
    val reviewedBy: String?,
    val reviewedAt: Instant?,
    val reviewNote: String?,
    val appliedTimetableId: String?,
    val appliedEntryId: String?
)
