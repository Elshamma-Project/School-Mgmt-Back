package com.kobe.smels.service.timetable

import com.kobe.smels.dto.timetable.TimetableEntryResponse
import com.kobe.smels.dto.timetable.TimetableEntryWriteResponse
import com.kobe.smels.dto.timetable.UpsertTimetableEntryRequest
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.exception.ConflictException
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.timetable.EntryKind
import com.kobe.smels.model.timetable.PeriodKind
import com.kobe.smels.model.timetable.TimeSlotGrid
import com.kobe.smels.model.timetable.TimetableEntry
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.timetable.TimeSlotGridRepository
import com.kobe.smels.repository.timetable.TimetableEntryRepository
import com.kobe.smels.service.academic.api.AcademicYearApi
import com.kobe.smels.service.academic.api.SchoolBrandApi
import com.kobe.smels.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.Optional

@Service
class TimetableEntryService(
    private val entryRepository: TimetableEntryRepository,
    private val gridRepository: TimeSlotGridRepository,
    private val timetableService: TimetableService,
    private val conflictService: TimetableConflictService,
    private val academicYearApi: AcademicYearApi,
    private val schoolBrandApi: SchoolBrandApi
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun upsert(
        currentUser: CurrentUser,
        timetableId: String,
        request: UpsertTimetableEntryRequest
    ): Mono<TimetableEntryWriteResponse> {
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, request.schoolId)
        return assertCanManage(currentUser, schoolId)
            .then(timetableService.requireDraft(schoolId, timetableId))
            .flatMap { timetable ->
                academicYearApi.assertWritable(schoolId, timetable.academicYearId)
                    .then(loadGrid(schoolId, timetable.gridId))
                    .flatMap { grid ->
                        validateAgainstGrid(grid, request)
                        applyEntryRules(request)
                        val optionCode = request.optionCode.trim()
                        val slotKey = SlotKeys.resolve(request.startPeriod, request.customStart)
                        findExisting(schoolId, timetableId, request, slotKey, optionCode)
                            .flatMap { existingOpt ->
                                val existing = existingOpt.orElse(null)
                                val excludeId = existing?.id
                                val candidate = buildCandidate(
                                    schoolId = schoolId,
                                    academicYearId = timetable.academicYearId,
                                    timetableId = timetableId,
                                    request = request,
                                    slotKey = slotKey,
                                    optionCode = optionCode,
                                    existingId = excludeId
                                )
                                conflictService
                                    .assertNoBlockingForCandidate(
                                        schoolId = schoolId,
                                        timetableId = timetableId,
                                        grid = grid,
                                        candidateEntry = candidate,
                                        excludeEntryId = excludeId
                                    )
                                    .flatMap { warnings ->
                                        val toSave = if (existing != null) {
                                            applyFields(existing, request, slotKey, optionCode)
                                            existing
                                        } else {
                                            candidate
                                        }
                                        entryRepository.save(toSave)
                                            .onErrorMap(DuplicateKeyException::class.java) {
                                                ConflictException(
                                                    "ENTRY_SLOT_TAKEN",
                                                    "An entry already exists for this class, day, slot and option"
                                                )
                                            }
                                            .map { saved ->
                                                TimetableEntryWriteResponse(
                                                    entry = TimetableMapper.toEntryResponse(saved),
                                                    warnings = warnings
                                                )
                                            }
                                    }
                            }
                    }
            }
            .doOnSuccess {
                log.info(
                    "timetable.entry.upsert by={} schoolId={} timetableId={}",
                    currentUser.username,
                    schoolId,
                    timetableId
                )
            }
    }

    fun update(
        currentUser: CurrentUser,
        id: String,
        request: UpsertTimetableEntryRequest,
        schoolIdOverride: String? = null
    ): Mono<TimetableEntryWriteResponse> {
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride ?: request.schoolId)
        return assertCanManage(currentUser, schoolId)
            .then(entryRepository.findByIdAndSchoolId(id, schoolId))
            .switchIfEmpty(Mono.error(NotFoundException("ENTRY_NOT_FOUND", "Timetable entry not found")))
            .flatMap { entry ->
                timetableService.requireDraft(schoolId, entry.timetableId)
                    .flatMap { timetable ->
                        academicYearApi.assertWritable(schoolId, entry.academicYearId)
                            .then(loadGrid(schoolId, timetable.gridId))
                            .flatMap { grid ->
                                validateAgainstGrid(grid, request)
                                applyEntryRules(request)
                                val optionCode = request.optionCode.trim()
                                val slotKey = SlotKeys.resolve(request.startPeriod, request.customStart)
                                applyFields(entry, request, slotKey, optionCode)
                                conflictService
                                    .assertNoBlockingForCandidate(
                                        schoolId = schoolId,
                                        timetableId = entry.timetableId,
                                        grid = grid,
                                        candidateEntry = entry,
                                        excludeEntryId = entry.id
                                    )
                                    .flatMap { warnings ->
                                        entryRepository.save(entry)
                                            .onErrorMap(DuplicateKeyException::class.java) {
                                                ConflictException(
                                                    "ENTRY_SLOT_TAKEN",
                                                    "An entry already exists for this class, day, slot and option"
                                                )
                                            }
                                            .map { saved ->
                                                TimetableEntryWriteResponse(
                                                    entry = TimetableMapper.toEntryResponse(saved),
                                                    warnings = warnings
                                                )
                                            }
                                    }
                            }
                    }
            }
    }

    fun delete(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<Void> {
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return assertCanManage(currentUser, schoolId)
            .then(entryRepository.findByIdAndSchoolId(id, schoolId))
            .switchIfEmpty(Mono.error(NotFoundException("ENTRY_NOT_FOUND", "Timetable entry not found")))
            .flatMap { entry ->
                timetableService.requireDraft(schoolId, entry.timetableId)
                    .then(academicYearApi.assertWritable(schoolId, entry.academicYearId))
                    .then(entryRepository.delete(entry))
            }
    }

    fun getById(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<TimetableEntryResponse> {
        TimetableAccess.assertCanRead(currentUser)
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return entryRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("ENTRY_NOT_FOUND", "Timetable entry not found")))
            .flatMap { entry -> assertTeacherMaySee(currentUser, entry).thenReturn(entry) }
            .map { TimetableMapper.toEntryResponse(it) }
    }

    fun listByTimetable(
        currentUser: CurrentUser,
        timetableId: String,
        classId: String? = null,
        teacherId: String? = null,
        schoolIdOverride: String? = null
    ): Flux<TimetableEntryResponse> {
        TimetableAccess.assertCanRead(currentUser)
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride)
        val scopedTeacherId = resolveTeacherScope(currentUser, teacherId)
        val source = when {
            classId != null ->
                entryRepository.findBySchoolIdAndTimetableIdAndClassId(
                    schoolId,
                    timetableId,
                    classId
                )
            scopedTeacherId != null ->
                entryRepository.findBySchoolIdAndTimetableIdAndTeacherId(
                    schoolId,
                    timetableId,
                    scopedTeacherId
                )
            else -> entryRepository.findBySchoolIdAndTimetableId(schoolId, timetableId)
        }
        return source
            .filter { entry ->
                !isTeacherOnly(currentUser) || entry.teacherId == scopedTeacherId
            }
            .map { TimetableMapper.toEntryResponse(it) }
    }

    private fun findExisting(
        schoolId: String,
        timetableId: String,
        request: UpsertTimetableEntryRequest,
        slotKey: String,
        optionCode: String
    ): Mono<Optional<TimetableEntry>> =
        entryRepository
            .findBySchoolIdAndTimetableIdAndDayAndSlotKeyAndOptionCode(
                schoolId,
                timetableId,
                request.day,
                slotKey,
                optionCode
            )
            .filter { it.classId == request.classId.trim() }
            .next()
            .map { Optional.of(it) }
            .defaultIfEmpty(Optional.empty())

    private fun buildCandidate(
        schoolId: String,
        academicYearId: String,
        timetableId: String,
        request: UpsertTimetableEntryRequest,
        slotKey: String,
        optionCode: String,
        existingId: String?
    ): TimetableEntry =
        TimetableEntry(
            id = existingId,
            schoolId = schoolId,
            academicYearId = academicYearId,
            timetableId = timetableId,
            classId = request.classId.trim(),
            day = request.day,
            startPeriod = request.startPeriod,
            periodCount = request.periodCount.coerceAtLeast(1),
            customStart = request.customStart,
            customEnd = request.customEnd,
            slotKey = slotKey,
            kind = request.kind,
            classSubjectIds = request.classSubjectIds.map { it.trim() }.filter { it.isNotBlank() },
            subjectId = request.subjectId?.trim()?.ifBlank { null },
            teacherId = request.teacherId?.trim()?.ifBlank { null },
            roomId = request.roomId?.trim()?.ifBlank { null },
            activityType = request.activityType,
            sessionGroupId = request.sessionGroupId?.trim()?.ifBlank { null },
            optionGroupCode = request.optionGroupCode?.trim()?.ifBlank { null },
            optionCode = optionCode,
            label = TimetableMapper.toLocalized(request.label),
            note = request.note?.trim()?.ifBlank { null },
            subjectLabel = request.subjectLabel?.trim()?.ifBlank { null },
            teacherLabel = request.teacherLabel?.trim()?.ifBlank { null }
        )

    private fun applyFields(
        entry: TimetableEntry,
        request: UpsertTimetableEntryRequest,
        slotKey: String,
        optionCode: String
    ): TimetableEntry {
        entry.classId = request.classId.trim()
        entry.day = request.day
        entry.startPeriod = request.startPeriod
        entry.periodCount = request.periodCount.coerceAtLeast(1)
        entry.customStart = request.customStart
        entry.customEnd = request.customEnd
        entry.slotKey = slotKey
        entry.kind = request.kind
        entry.classSubjectIds = request.classSubjectIds.map { it.trim() }.filter { it.isNotBlank() }
        entry.subjectId = request.subjectId?.trim()?.ifBlank { null }
        entry.teacherId = request.teacherId?.trim()?.ifBlank { null }
        entry.roomId = request.roomId?.trim()?.ifBlank { null }
        entry.activityType = request.activityType
        entry.sessionGroupId = request.sessionGroupId?.trim()?.ifBlank { null }
        entry.optionGroupCode = request.optionGroupCode?.trim()?.ifBlank { null }
        entry.optionCode = optionCode
        entry.label = TimetableMapper.toLocalized(request.label)
        entry.note = request.note?.trim()?.ifBlank { null }
        entry.subjectLabel = request.subjectLabel?.trim()?.ifBlank { null }
        entry.teacherLabel = request.teacherLabel?.trim()?.ifBlank { null }
        return entry
    }

    private fun applyEntryRules(request: UpsertTimetableEntryRequest) {
        if (request.periodCount < 1) {
            throw BusinessRuleException("PERIOD_COUNT", "periodCount must be >= 1")
        }
        if (request.customStart != null || request.customEnd != null) {
            val start = request.customStart
                ?: throw BusinessRuleException("CUSTOM_START", "customStart required with customEnd")
            val end = request.customEnd
                ?: throw BusinessRuleException("CUSTOM_END", "customEnd required with customStart")
            if (!end.isAfter(start)) {
                throw BusinessRuleException("CUSTOM_RANGE", "customEnd must be after customStart")
            }
        }
        if (request.kind == EntryKind.ACTIVITY && request.activityType == null) {
            throw BusinessRuleException("ACTIVITY_TYPE", "activityType is required for ACTIVITY")
        }
        if (request.kind == EntryKind.LESSON && request.activityType != null) {
            throw BusinessRuleException("ACTIVITY_TYPE", "activityType is only for ACTIVITY")
        }
        if (request.optionCode.isNotBlank() && request.optionGroupCode.isNullOrBlank()) {
            throw BusinessRuleException(
                "OPTION_GROUP",
                "optionGroupCode is required when optionCode is set"
            )
        }
    }

    private fun loadGrid(schoolId: String, gridId: String): Mono<TimeSlotGrid> =
        gridRepository.findByIdAndSchoolId(gridId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("GRID_NOT_FOUND", "Time slot grid not found")))

    private fun validateAgainstGrid(grid: TimeSlotGrid, request: UpsertTimetableEntryRequest) {
        if (request.startPeriod == null) return
        if (request.day !in grid.days) {
            throw BusinessRuleException(
                "DAY_NOT_IN_GRID",
                "Day is not part of the grid; use customStart/customEnd for off-grid slots"
            )
        }
        val lesson = grid.periods.firstOrNull {
            it.kind == PeriodKind.LESSON && it.number == request.startPeriod
        } ?: throw BusinessRuleException(
            "PERIOD_NOT_IN_GRID",
            "startPeriod ${request.startPeriod} is not a LESSON period of the grid"
        )
        // silence unused if needed - lesson confirms LESSON
        check(lesson.kind == PeriodKind.LESSON)
        val maxLesson = grid.periods.mapNotNull { it.number }.maxOrNull() ?: 0
        val last = request.startPeriod!! + request.periodCount - 1
        if (last > maxLesson) {
            throw BusinessRuleException("PERIOD_SPAN", "periodCount exceeds remaining lesson periods")
        }
    }

    private fun assertCanManage(currentUser: CurrentUser, schoolId: String): Mono<Void> =
        schoolBrandApi.getTimetableSettings(schoolId)
            .doOnNext { settings ->
                TimetableAccess.assertCanManage(currentUser, settings.timetableSurveillantCanEdit)
                TimetableAccess.logSuperAdmin(currentUser, schoolId, "timetable.entry.manage")
            }
            .then()

    private fun isTeacherOnly(currentUser: CurrentUser): Boolean =
        currentUser.hasRole(Role.TEACHER) &&
            !currentUser.hasAnyRole(
                Role.SURVEILLANT_GENERAL,
                Role.ADMINISTRATION,
                Role.DIRECTOR,
                Role.SUPER_ADMIN
            )

    private fun resolveTeacherScope(currentUser: CurrentUser, teacherIdParam: String?): String? {
        if (!isTeacherOnly(currentUser)) return teacherIdParam
        val ownId = currentUser.profileId.takeIf {
            currentUser.profileType == ProfileType.STAFF && !it.isNullOrBlank()
        } ?: throw ForbiddenException("FORBIDDEN", "Teacher profile required")
        if (teacherIdParam != null && teacherIdParam != ownId) {
            throw ForbiddenException("FORBIDDEN", "Teachers may only read their own timetable entries")
        }
        return ownId
    }

    private fun assertTeacherMaySee(currentUser: CurrentUser, entry: TimetableEntry): Mono<Void> {
        if (!isTeacherOnly(currentUser)) return Mono.empty()
        val ownId = currentUser.profileId
        if (ownId != null && entry.teacherId == ownId) return Mono.empty()
        return Mono.error(ForbiddenException("FORBIDDEN", "Teachers may only read their own timetable entries"))
    }
}
