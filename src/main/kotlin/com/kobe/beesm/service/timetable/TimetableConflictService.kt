package com.kobe.beesm.service.timetable

import com.kobe.beesm.dto.academic.ClassSubjectSummaryDto
import com.kobe.beesm.dto.timetable.TimetableConflictsResponse
import com.kobe.beesm.dto.timetable.TimetableIssueDto
import com.kobe.beesm.dto.timetable.TimetableIssueSeverity
import com.kobe.beesm.exception.BusinessRuleException
import com.kobe.beesm.exception.NotFoundException
import com.kobe.beesm.model.timetable.GridPeriod
import com.kobe.beesm.model.timetable.TimeSlotGrid
import com.kobe.beesm.model.timetable.TimetableEntry
import com.kobe.beesm.repository.timetable.TimeSlotGridRepository
import com.kobe.beesm.repository.timetable.TimetableEntryRepository
import com.kobe.beesm.repository.timetable.TimetableRepository
import com.kobe.beesm.service.academic.api.ClassSubjectApi
import com.kobe.beesm.shared.security.CurrentUser
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class TimetableConflictService(
    private val timetableRepository: TimetableRepository,
    private val entryRepository: TimetableEntryRepository,
    private val gridRepository: TimeSlotGridRepository,
    private val classSubjectApi: ClassSubjectApi
) {
    fun analyze(
        currentUser: CurrentUser,
        timetableId: String,
        schoolIdOverride: String? = null
    ): Mono<TimetableConflictsResponse> {
        TimetableAccess.assertCanRead(currentUser)
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return timetableRepository.findByIdAndSchoolId(timetableId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("TIMETABLE_NOT_FOUND", "Timetable not found")))
            .flatMap { timetable ->
                gridRepository.findByIdAndSchoolId(timetable.gridId, schoolId)
                    .switchIfEmpty(Mono.error(NotFoundException("GRID_NOT_FOUND", "Time slot grid not found")))
                    .flatMap { grid ->
                        entryRepository.findBySchoolIdAndTimetableId(schoolId, timetableId)
                            .collectList()
                            .flatMap { entries ->
                                val candidates = entries.map { toCandidate(it, grid.periods) }
                                val blocking = TimetableConflictEngine.findAllBlocking(candidates)
                                collectWarnings(schoolId, entries)
                                    .map { warnings ->
                                        TimetableConflictsResponse(
                                            timetableId = timetableId,
                                            blocking = blocking,
                                            warnings = warnings
                                        )
                                    }
                            }
                    }
            }
    }

    fun assertNoBlockingForCandidate(
        schoolId: String,
        timetableId: String,
        grid: TimeSlotGrid,
        candidateEntry: TimetableEntry,
        excludeEntryId: String?
    ): Mono<List<TimetableIssueDto>> =
        entryRepository.findBySchoolIdAndTimetableId(schoolId, timetableId)
            .collectList()
            .flatMap { entries ->
                TimetableConflictEngine.assertNotOnBreakOrCleanup(
                    startPeriod = candidateEntry.startPeriod,
                    customStart = candidateEntry.customStart,
                    customEnd = candidateEntry.customEnd,
                    day = candidateEntry.day,
                    gridDays = grid.days,
                    periods = grid.periods
                )
                val candidate = toCandidate(candidateEntry, grid.periods)
                val others = entries
                    .filter { it.id != excludeEntryId }
                    .map { toCandidate(it, grid.periods) }
                val blocking = TimetableConflictEngine.findBlocking(candidate, others)
                if (blocking.isNotEmpty()) {
                    val first = blocking.first()
                    return@flatMap Mono.error(
                        BusinessRuleException(
                            first.code,
                            first.message +
                                (first.otherEntryId?.let { " (otherEntryId=$it)" } ?: "")
                        )
                    )
                }
                val merged = entries.filter { it.id != excludeEntryId } + candidateEntry
                collectWarnings(schoolId, merged, focusEntry = candidateEntry)
            }

    fun collectWarnings(
        schoolId: String,
        entries: List<TimetableEntry>,
        focusEntry: TimetableEntry? = null
    ): Mono<List<TimetableIssueDto>> {
        val classSubjectIds = entries.flatMap { it.classSubjectIds }.distinct()
        if (classSubjectIds.isEmpty()) return Mono.just(emptyList())
        return Flux.fromIterable(classSubjectIds)
            .flatMap { id ->
                classSubjectApi.getSummary(schoolId, id)
                    .onErrorResume { Mono.empty() }
            }
            .collectList()
            .map { summaries ->
                val byId = summaries.associateBy { it.id }
                val warnings = mutableListOf<TimetableIssueDto>()
                val hours = hoursMismatchWarnings(entries, byId)
                warnings += if (focusEntry != null) {
                    val touched = focusEntry.classSubjectIds.toSet()
                    hours.filter { it.classSubjectId in touched }
                } else {
                    hours
                }
                // BR-64 : à l’écriture, avis sur la case cible seulement
                warnings += teacherMismatchWarnings(
                    if (focusEntry != null) listOf(focusEntry) else entries,
                    byId
                )
                warnings
            }
    }

    private fun hoursMismatchWarnings(
        entries: List<TimetableEntry>,
        byId: Map<String, ClassSubjectSummaryDto>
    ): List<TimetableIssueDto> {
        // BR-63 : par classSubject, somme periodCount des cases le contenant vs weeklyHours
        val periodSums = mutableMapOf<String, Int>()
        for (entry in entries) {
            for (csId in entry.classSubjectIds) {
                periodSums[csId] = (periodSums[csId] ?: 0) + entry.periodCount.coerceAtLeast(1)
            }
        }
        return periodSums.mapNotNull { (csId, periods) ->
            val cs = byId[csId] ?: return@mapNotNull null
            val expected = cs.weeklyHours ?: return@mapNotNull null
            if (periods == expected) return@mapNotNull null
            TimetableIssueDto(
                severity = TimetableIssueSeverity.WARNING,
                code = "HOURS_MISMATCH",
                message = "Scheduled periods ($periods) differ from weeklyHours ($expected)",
                classSubjectId = csId,
                classId = cs.classId
            )
        }
    }

    private fun teacherMismatchWarnings(
        entries: List<TimetableEntry>,
        byId: Map<String, ClassSubjectSummaryDto>
    ): List<TimetableIssueDto> {
        // BR-64
        val warnings = mutableListOf<TimetableIssueDto>()
        for (entry in entries) {
            val teacherId = entry.teacherId ?: continue
            for (csId in entry.classSubjectIds) {
                val cs = byId[csId] ?: continue
                val assigned = cs.teacherId ?: continue
                if (assigned != teacherId) {
                    warnings += TimetableIssueDto(
                        severity = TimetableIssueSeverity.WARNING,
                        code = "TEACHER_MISMATCH",
                        message = "Entry teacher differs from ClassSubject.teacherId",
                        entryId = entry.id,
                        teacherId = teacherId,
                        classSubjectId = csId,
                        classId = entry.classId
                    )
                }
            }
        }
        return warnings
    }

    internal fun toCandidate(entry: TimetableEntry, periods: List<GridPeriod>): TimetableConflictEngine.Candidate {
        val (start, end) = TimetableConflictEngine.resolveInterval(
            startPeriod = entry.startPeriod,
            periodCount = entry.periodCount,
            customStart = entry.customStart,
            customEnd = entry.customEnd,
            periods = periods
        )
        return TimetableConflictEngine.Candidate(
            id = entry.id,
            classId = entry.classId,
            day = entry.day,
            start = start,
            end = end,
            kind = entry.kind,
            teacherId = entry.teacherId,
            roomId = entry.roomId,
            sessionGroupId = entry.sessionGroupId,
            optionGroupCode = entry.optionGroupCode,
            optionCode = entry.optionCode,
            classSubjectIds = entry.classSubjectIds,
            periodCount = entry.periodCount
        )
    }
}
