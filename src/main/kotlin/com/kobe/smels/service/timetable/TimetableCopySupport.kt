package com.kobe.smels.service.timetable

import com.kobe.smels.model.timetable.TimeSlotGrid
import com.kobe.smels.model.timetable.TimetableEntry
import java.util.UUID

/** Copie de grille / cases pour révision et duplication inter-années. */
internal object TimetableCopySupport {
    fun cloneGrid(source: TimeSlotGrid, targetAcademicYearId: String, nameSuffix: String? = null): TimeSlotGrid =
        TimeSlotGrid(
            schoolId = source.schoolId,
            academicYearId = targetAcademicYearId,
            name = when {
                nameSuffix != null -> "${source.name} $nameSuffix"
                else -> source.name
            },
            days = source.days.toSet(),
            periods = source.periods.toList(),
            active = true
        )

    /**
     * Clone les cases vers un nouvel EDT. Remappe [TimetableEntry.sessionGroupId]
     * pour conserver les séances communes sans réutiliser les ids source.
     */
    fun cloneEntries(
        sourceEntries: List<TimetableEntry>,
        targetSchoolId: String,
        targetAcademicYearId: String,
        targetTimetableId: String
    ): List<TimetableEntry> {
        val sessionRemap = mutableMapOf<String, String>()
        return sourceEntries.map { src ->
            val newSession = src.sessionGroupId?.takeIf { it.isNotBlank() }?.let { old ->
                sessionRemap.getOrPut(old) { UUID.randomUUID().toString() }
            }
            TimetableEntry(
                schoolId = targetSchoolId,
                academicYearId = targetAcademicYearId,
                timetableId = targetTimetableId,
                classId = src.classId,
                day = src.day,
                startPeriod = src.startPeriod,
                periodCount = src.periodCount,
                customStart = src.customStart,
                customEnd = src.customEnd,
                slotKey = src.slotKey,
                kind = src.kind,
                classSubjectIds = src.classSubjectIds.toList(),
                subjectId = src.subjectId,
                teacherId = src.teacherId,
                roomId = src.roomId,
                activityType = src.activityType,
                sessionGroupId = newSession,
                optionGroupCode = src.optionGroupCode,
                optionCode = src.optionCode,
                label = src.label,
                note = src.note,
                subjectLabel = src.subjectLabel,
                teacherLabel = src.teacherLabel
            )
        }
    }
}
