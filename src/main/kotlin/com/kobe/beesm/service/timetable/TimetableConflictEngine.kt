package com.kobe.beesm.service.timetable

import com.kobe.beesm.dto.timetable.TimetableIssueDto
import com.kobe.beesm.dto.timetable.TimetableIssueSeverity
import com.kobe.beesm.exception.BusinessRuleException
import com.kobe.beesm.model.timetable.EntryKind
import com.kobe.beesm.model.timetable.GridPeriod
import com.kobe.beesm.model.timetable.PeriodKind
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Détection de conflits EDT (BR-09, BR-65, BR-66) — pure, sans accès base.
 */
internal object TimetableConflictEngine {

    data class Candidate(
        val id: String?,
        val classId: String,
        val day: DayOfWeek,
        val start: LocalTime,
        val end: LocalTime,
        val kind: EntryKind,
        val teacherId: String?,
        val roomId: String?,
        val sessionGroupId: String?,
        val optionGroupCode: String?,
        val optionCode: String,
        val classSubjectIds: List<String> = emptyList(),
        val periodCount: Int = 1
    )

    fun resolveInterval(
        startPeriod: Int?,
        periodCount: Int,
        customStart: LocalTime?,
        customEnd: LocalTime?,
        periods: List<GridPeriod>
    ): Pair<LocalTime, LocalTime> {
        if (customStart != null && customEnd != null) {
            if (!customEnd.isAfter(customStart)) {
                throw BusinessRuleException("CUSTOM_RANGE", "customEnd must be after customStart")
            }
            return customStart to customEnd
        }
        val startNo = startPeriod
            ?: throw BusinessRuleException("SLOT_REQUIRED", "startPeriod or custom times required")
        val count = periodCount.coerceAtLeast(1)
        val lessonByNumber = periods
            .filter { it.kind == PeriodKind.LESSON && it.number != null }
            .associateBy { it.number!! }
        val first = lessonByNumber[startNo]
            ?: throw BusinessRuleException("PERIOD_NOT_IN_GRID", "Unknown lesson period $startNo")
        val lastNo = startNo + count - 1
        val last = lessonByNumber[lastNo]
            ?: throw BusinessRuleException("PERIOD_SPAN", "periodCount exceeds lesson periods")
        return first.start to last.end
    }

    /** BR-66 : une case sur grille ne doit pas démarrer sur pause / nettoyage. */
    fun assertNotOnBreakOrCleanup(
        startPeriod: Int?,
        customStart: LocalTime?,
        customEnd: LocalTime?,
        day: DayOfWeek,
        gridDays: Set<DayOfWeek>,
        periods: List<GridPeriod>
    ) {
        if (startPeriod != null) {
            val isLesson = periods.any { it.kind == PeriodKind.LESSON && it.number == startPeriod }
            if (!isLesson) {
                throw BusinessRuleException(
                    "BR66_NOT_LESSON",
                    "Grid entry must start on a LESSON period"
                )
            }
            return
        }
        if (customStart == null || customEnd == null) return
        if (day !in gridDays) return // hors jours de grille (ex. samedi) — autorisé
        val blocked = periods.filter { it.kind == PeriodKind.BREAK || it.kind == PeriodKind.CLEANUP }
        for (p in blocked) {
            if (customStart < p.end && p.start < customEnd) {
                // chevauchement total à l’intérieur d’une pause/nettoyage
                if (!customStart.isBefore(p.start) && !customEnd.isAfter(p.end)) {
                    throw BusinessRuleException(
                        "BR66_ON_BREAK",
                        "Entry falls entirely within a break or cleanup slot"
                    )
                }
            }
        }
    }

    fun findBlocking(
        candidate: Candidate,
        others: List<Candidate>
    ): List<TimetableIssueDto> {
        val issues = mutableListOf<TimetableIssueDto>()
        for (other in others) {
            if (candidate.id != null && candidate.id == other.id) continue
            if (candidate.day != other.day) continue
            if (!overlaps(candidate.start, candidate.end, other.start, other.end)) continue

            if (candidate.classId == other.classId && !parallelOptions(candidate, other)) {
                issues += issue(
                    code = "CLASS_OVERLAP",
                    message = "Class has overlapping entries",
                    a = candidate,
                    b = other,
                    classId = candidate.classId
                )
            }

            if (sameTeacherConflict(candidate, other)) {
                issues += issue(
                    code = "TEACHER_OVERLAP",
                    message = "Teacher has overlapping entries",
                    a = candidate,
                    b = other,
                    teacherId = candidate.teacherId
                )
            }

            if (sameRoomConflict(candidate, other)) {
                issues += issue(
                    code = "ROOM_OVERLAP",
                    message = "Room has overlapping entries",
                    a = candidate,
                    b = other,
                    roomId = candidate.roomId
                )
            }
        }
        return issues.distinctBy { Triple(it.code, it.entryId, it.otherEntryId) }
    }

    fun findAllBlocking(entries: List<Candidate>): List<TimetableIssueDto> {
        val issues = mutableListOf<TimetableIssueDto>()
        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                issues += findBlocking(entries[i], listOf(entries[j]))
            }
        }
        return issues.distinctBy { setOf(it.code, it.entryId, it.otherEntryId) }
    }

    private fun parallelOptions(a: Candidate, b: Candidate): Boolean {
        val group = a.optionGroupCode?.takeIf { it.isNotBlank() } ?: return false
        val otherGroup = b.optionGroupCode?.takeIf { it.isNotBlank() } ?: return false
        if (group != otherGroup) return false
        val codeA = a.optionCode
        val codeB = b.optionCode
        return codeA.isNotBlank() && codeB.isNotBlank() && codeA != codeB
    }

    private fun sameTeacherConflict(a: Candidate, b: Candidate): Boolean {
        val teacher = a.teacherId?.takeIf { it.isNotBlank() } ?: return false
        if (b.teacherId != teacher) return false
        // BR-65 / Q-57 : ACTIVITY n’engage pas l’enseignant
        if (a.kind == EntryKind.ACTIVITY || b.kind == EntryKind.ACTIVITY) return false
        if (sameSessionGroup(a, b)) return false
        return true
    }

    private fun sameRoomConflict(a: Candidate, b: Candidate): Boolean {
        val room = a.roomId?.takeIf { it.isNotBlank() } ?: return false
        if (b.roomId != room) return false
        if (sameSessionGroup(a, b)) return false
        return true
    }

    private fun sameSessionGroup(a: Candidate, b: Candidate): Boolean {
        val g = a.sessionGroupId?.takeIf { it.isNotBlank() } ?: return false
        return b.sessionGroupId == g
    }

    fun overlaps(aStart: LocalTime, aEnd: LocalTime, bStart: LocalTime, bEnd: LocalTime): Boolean =
        aStart < bEnd && bStart < aEnd

    private fun issue(
        code: String,
        message: String,
        a: Candidate,
        b: Candidate,
        classId: String? = null,
        teacherId: String? = null,
        roomId: String? = null
    ) = TimetableIssueDto(
        severity = TimetableIssueSeverity.BLOCKING,
        code = code,
        message = message,
        entryId = a.id,
        otherEntryId = b.id,
        classId = classId,
        teacherId = teacherId,
        roomId = roomId
    )
}
