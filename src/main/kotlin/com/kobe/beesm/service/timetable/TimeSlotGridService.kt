package com.kobe.beesm.service.timetable

import com.kobe.beesm.dto.timetable.CreateTimeSlotGridRequest
import com.kobe.beesm.dto.timetable.GridPeriodDto
import com.kobe.beesm.dto.timetable.TimeSlotGridResponse
import com.kobe.beesm.dto.timetable.UpdateTimeSlotGridRequest
import com.kobe.beesm.exception.BusinessRuleException
import com.kobe.beesm.exception.NotFoundException
import com.kobe.beesm.model.timetable.PeriodKind
import com.kobe.beesm.model.timetable.TimeSlotGrid
import com.kobe.beesm.repository.timetable.TimeSlotGridRepository
import com.kobe.beesm.service.academic.api.AcademicYearApi
import com.kobe.beesm.service.academic.api.SchoolBrandApi
import com.kobe.beesm.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class TimeSlotGridService(
    private val gridRepository: TimeSlotGridRepository,
    private val academicYearApi: AcademicYearApi,
    private val schoolBrandApi: SchoolBrandApi
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun create(currentUser: CurrentUser, request: CreateTimeSlotGridRequest): Mono<TimeSlotGridResponse> {
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, request.schoolId)
        return assertCanManage(currentUser, schoolId)
            .then(academicYearApi.assertWritable(schoolId, request.academicYearId))
            .flatMap {
                val periods = validatePeriods(request.periods)
                log.info(
                    "timetable.grid.create start by={} schoolId={} yearId={}",
                    currentUser.username,
                    schoolId,
                    request.academicYearId
                )
                gridRepository.save(
                    TimeSlotGrid(
                        schoolId = schoolId,
                        academicYearId = request.academicYearId.trim(),
                        name = request.name.trim(),
                        days = request.days,
                        periods = periods.map { TimetableMapper.toPeriod(it) },
                        active = request.active
                    )
                )
            }
            .map { TimetableMapper.toGridResponse(it) }
    }

    fun update(
        currentUser: CurrentUser,
        id: String,
        request: UpdateTimeSlotGridRequest,
        schoolIdOverride: String? = null
    ): Mono<TimeSlotGridResponse> {
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return assertCanManage(currentUser, schoolId)
            .then(gridRepository.findByIdAndSchoolId(id, schoolId))
            .switchIfEmpty(Mono.error(NotFoundException("GRID_NOT_FOUND", "Time slot grid not found")))
            .flatMap { grid ->
                academicYearApi.assertWritable(schoolId, grid.academicYearId).thenReturn(grid)
            }
            .flatMap { grid ->
                val periods = validatePeriods(request.periods)
                grid.name = request.name.trim()
                grid.days = request.days
                grid.periods = periods.map { TimetableMapper.toPeriod(it) }
                grid.active = request.active
                gridRepository.save(grid)
            }
            .map { TimetableMapper.toGridResponse(it) }
    }

    fun getById(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<TimeSlotGridResponse> {
        TimetableAccess.assertCanRead(currentUser)
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return gridRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("GRID_NOT_FOUND", "Time slot grid not found")))
            .map { TimetableMapper.toGridResponse(it) }
    }

    fun listByYear(
        currentUser: CurrentUser,
        academicYearId: String,
        activeOnly: Boolean = false,
        schoolIdOverride: String? = null
    ): Flux<TimeSlotGridResponse> {
        TimetableAccess.assertCanRead(currentUser)
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride)
        val source =
            if (activeOnly) {
                gridRepository.findBySchoolIdAndAcademicYearIdAndActiveTrue(schoolId, academicYearId)
            } else {
                gridRepository.findBySchoolIdAndAcademicYearId(schoolId, academicYearId)
            }
        return source.map { TimetableMapper.toGridResponse(it) }
    }

    private fun assertCanManage(currentUser: CurrentUser, schoolId: String): Mono<Void> =
        schoolBrandApi.getTimetableSettings(schoolId)
            .doOnNext { settings ->
                TimetableAccess.assertCanManage(currentUser, settings.timetableSurveillantCanEdit)
                TimetableAccess.logSuperAdmin(currentUser, schoolId, "timetable.grid.manage")
            }
            .then()

    private fun validatePeriods(periods: List<GridPeriodDto>): List<GridPeriodDto> {
        if (periods.isEmpty()) {
            throw BusinessRuleException("GRID_EMPTY", "At least one period is required")
        }
        val lessonNumbers = mutableSetOf<Int>()
        periods.forEachIndexed { index, p ->
            if (!p.end.isAfter(p.start)) {
                throw BusinessRuleException(
                    "PERIOD_RANGE",
                    "Period #${index + 1}: end must be after start"
                )
            }
            when (p.kind) {
                PeriodKind.LESSON -> {
                    val n = p.number
                        ?: throw BusinessRuleException(
                            "PERIOD_NUMBER_REQUIRED",
                            "LESSON periods require a number"
                        )
                    if (n < 1) {
                        throw BusinessRuleException("PERIOD_NUMBER", "LESSON number must be >= 1")
                    }
                    if (!lessonNumbers.add(n)) {
                        throw BusinessRuleException(
                            "PERIOD_NUMBER_DUP",
                            "Duplicate LESSON number $n"
                        )
                    }
                }
                else -> {
                    if (p.number != null) {
                        throw BusinessRuleException(
                            "PERIOD_NUMBER_FORBIDDEN",
                            "Only LESSON periods may have a number"
                        )
                    }
                }
            }
        }
        val sorted = periods.sortedBy { it.start }
        for (i in 0 until sorted.size - 1) {
            if (sorted[i].end.isAfter(sorted[i + 1].start)) {
                throw BusinessRuleException("PERIOD_OVERLAP", "Grid periods overlap")
            }
        }
        return periods
    }
}
