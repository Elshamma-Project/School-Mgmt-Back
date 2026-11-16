package com.kobe.smels.service.timetable

import com.kobe.smels.dto.timetable.LinkSessionGroupRequest
import com.kobe.smels.dto.timetable.SessionGroupResponse
import com.kobe.smels.dto.timetable.UnlinkSessionGroupRequest
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.timetable.TimetableEntry
import com.kobe.smels.repository.timetable.TimeSlotGridRepository
import com.kobe.smels.repository.timetable.TimetableEntryRepository
import com.kobe.smels.service.academic.api.AcademicYearApi
import com.kobe.smels.service.academic.api.SchoolBrandApi
import com.kobe.smels.shared.security.CurrentUser
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Séances communes multi-classes (Q-55) : même `sessionGroupId` → pas de conflit enseignant/salle (BR-09).
 */
@Service
class TimetableSessionGroupService(
    private val entryRepository: TimetableEntryRepository,
    private val gridRepository: TimeSlotGridRepository,
    private val timetableService: TimetableService,
    private val academicYearApi: AcademicYearApi,
    private val schoolBrandApi: SchoolBrandApi
) {
    fun link(
        currentUser: CurrentUser,
        timetableId: String,
        request: LinkSessionGroupRequest
    ): Mono<SessionGroupResponse> {
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, request.schoolId)
        val ids = request.entryIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (ids.size < 2) {
            return Mono.error(
                BusinessRuleException("SESSION_GROUP_SIZE", "At least two entries are required")
            )
        }
        return assertCanManage(currentUser, schoolId)
            .then(timetableService.requireDraft(schoolId, timetableId))
            .flatMap { timetable ->
                academicYearApi.assertWritable(schoolId, timetable.academicYearId)
                    .then(gridRepository.findByIdAndSchoolId(timetable.gridId, schoolId))
                    .switchIfEmpty(Mono.error(NotFoundException("GRID_NOT_FOUND", "Time slot grid not found")))
                    .flatMap { grid ->
                        Flux.fromIterable(ids)
                            .flatMap { id ->
                                entryRepository.findByIdAndSchoolId(id, schoolId)
                                    .switchIfEmpty(
                                        Mono.error(NotFoundException("ENTRY_NOT_FOUND", "Entry not found: $id"))
                                    )
                            }
                            .collectList()
                            .flatMap { entries ->
                                validateLinkable(entries, timetableId, grid.periods)
                                val groupId = UUID.randomUUID().toString()
                                Flux.fromIterable(entries)
                                    .flatMap { entry ->
                                        entry.sessionGroupId = groupId
                                        entryRepository.save(entry)
                                    }
                                    .collectList()
                                    .map { saved ->
                                        SessionGroupResponse(
                                            sessionGroupId = groupId,
                                            entryIds = saved.mapNotNull { it.id }
                                        )
                                    }
                            }
                    }
            }
    }

    fun unlink(
        currentUser: CurrentUser,
        timetableId: String,
        sessionGroupId: String,
        request: UnlinkSessionGroupRequest
    ): Mono<SessionGroupResponse> {
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, request.schoolId)
        return assertCanManage(currentUser, schoolId)
            .then(timetableService.requireDraft(schoolId, timetableId))
            .flatMap { timetable ->
                academicYearApi.assertWritable(schoolId, timetable.academicYearId).thenReturn(timetable)
            }
            .flatMap {
                val filterIds = request.entryIds.map { it.trim() }.filter { it.isNotBlank() }.toSet()
                entryRepository
                    .findBySchoolIdAndTimetableIdAndSessionGroupId(schoolId, timetableId, sessionGroupId)
                    .filter { filterIds.isEmpty() || it.id in filterIds }
                    .collectList()
                    .flatMap { entries ->
                        if (entries.isEmpty()) {
                            Mono.error(
                                NotFoundException("SESSION_GROUP_NOT_FOUND", "Session group not found")
                            )
                        } else {
                            Flux.fromIterable(entries)
                                .flatMap { entry ->
                                    entry.sessionGroupId = null
                                    entryRepository.save(entry)
                                }
                                .collectList()
                                .map { saved ->
                                    SessionGroupResponse(
                                        sessionGroupId = sessionGroupId,
                                        entryIds = saved.mapNotNull { it.id }
                                    )
                                }
                        }
                    }
            }
    }

    private fun validateLinkable(
        entries: List<TimetableEntry>,
        timetableId: String,
        periods: List<com.kobe.smels.model.timetable.GridPeriod>
    ) {
        if (entries.any { it.timetableId != timetableId }) {
            throw BusinessRuleException("SESSION_GROUP_TIMETABLE", "All entries must belong to the timetable")
        }
        val days = entries.map { it.day }.toSet()
        if (days.size != 1) {
            throw BusinessRuleException("SESSION_GROUP_DAY", "Linked entries must share the same day")
        }
        val intervals = entries.map { entry ->
            TimetableConflictEngine.resolveInterval(
                startPeriod = entry.startPeriod,
                periodCount = entry.periodCount,
                customStart = entry.customStart,
                customEnd = entry.customEnd,
                periods = periods
            )
        }
        val base = intervals.first()
        for (interval in intervals.drop(1)) {
            if (!TimetableConflictEngine.overlaps(base.first, base.second, interval.first, interval.second)) {
                throw BusinessRuleException(
                    "SESSION_GROUP_TIME",
                    "Linked entries must overlap in time"
                )
            }
        }
        val classIds = entries.map { it.classId }.toSet()
        if (classIds.size < 2) {
            throw BusinessRuleException(
                "SESSION_GROUP_CLASSES",
                "A session group must involve at least two different classes"
            )
        }
    }

    private fun assertCanManage(currentUser: CurrentUser, schoolId: String): Mono<Void> =
        schoolBrandApi.getTimetableSettings(schoolId)
            .doOnNext { settings ->
                TimetableAccess.assertCanManage(currentUser, settings.timetableSurveillantCanEdit)
                TimetableAccess.logSuperAdmin(currentUser, schoolId, "timetable.sessionGroup.manage")
            }
            .then()
}
