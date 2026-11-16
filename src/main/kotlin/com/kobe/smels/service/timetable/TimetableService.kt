package com.kobe.smels.service.timetable

import com.kobe.smels.dto.timetable.CreateTimetableRequest
import com.kobe.smels.dto.timetable.TimetableResponse
import com.kobe.smels.dto.timetable.TimetableSummaryDto
import com.kobe.smels.dto.timetable.UpdateTimetableRequest
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.exception.ConflictException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.timetable.Timetable
import com.kobe.smels.model.timetable.TimetableStatus
import com.kobe.smels.repository.timetable.TimeSlotGridRepository
import com.kobe.smels.repository.timetable.TimetableRepository
import com.kobe.smels.service.academic.api.AcademicYearApi
import com.kobe.smels.service.academic.api.SchoolBrandApi
import com.kobe.smels.service.timetable.api.TimetableApi
import com.kobe.smels.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class TimetableService(
    private val timetableRepository: TimetableRepository,
    private val gridRepository: TimeSlotGridRepository,
    private val academicYearApi: AcademicYearApi,
    private val schoolBrandApi: SchoolBrandApi
) : TimetableApi {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createDraft(currentUser: CurrentUser, request: CreateTimetableRequest): Mono<TimetableResponse> {
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, request.schoolId)
        val yearId = request.academicYearId.trim()
        return assertCanManage(currentUser, schoolId)
            .then(academicYearApi.assertWritable(schoolId, yearId))
            .then(gridRepository.findByIdAndSchoolId(request.gridId.trim(), schoolId))
            .switchIfEmpty(Mono.error(NotFoundException("GRID_NOT_FOUND", "Time slot grid not found")))
            .flatMap { grid ->
                if (grid.academicYearId != yearId) {
                    Mono.error(
                        BusinessRuleException(
                            "GRID_YEAR_MISMATCH",
                            "Grid does not belong to the given academic year"
                        )
                    )
                } else {
                    Mono.just(grid)
                }
            }
            .flatMap { grid ->
                timetableRepository
                    .findBySchoolIdAndAcademicYearIdAndStatus(schoolId, yearId, TimetableStatus.DRAFT)
                    .hasElements()
                    .flatMap { hasDraft ->
                        if (hasDraft) {
                            Mono.error(
                                ConflictException(
                                    "DRAFT_EXISTS",
                                    "A draft timetable already exists for this year"
                                )
                            )
                        } else {
                            nextRevision(schoolId, yearId).flatMap { revision ->
                                log.info(
                                    "timetable.createDraft by={} schoolId={} yearId={} revision={}",
                                    currentUser.username,
                                    schoolId,
                                    yearId,
                                    revision
                                )
                                timetableRepository.save(
                                    Timetable(
                                        schoolId = schoolId,
                                        academicYearId = yearId,
                                        gridId = grid.id!!,
                                        revision = revision,
                                        status = TimetableStatus.DRAFT,
                                        generatedAt = Instant.now(),
                                        generatedBy = currentUser.accountId,
                                        headerContact = TimetableMapper.toContact(request.headerContact),
                                        note = request.note?.trim()?.ifBlank { null }
                                    )
                                )
                            }
                        }
                    }
            }
            .map { TimetableMapper.toTimetableResponse(it) }
    }

    fun updateDraft(
        currentUser: CurrentUser,
        id: String,
        request: UpdateTimetableRequest,
        schoolIdOverride: String? = null
    ): Mono<TimetableResponse> {
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return assertCanManage(currentUser, schoolId)
            .then(requireDraft(schoolId, id))
            .flatMap { timetable ->
                academicYearApi.assertWritable(schoolId, timetable.academicYearId).thenReturn(timetable)
            }
            .flatMap { timetable ->
                if (request.headerContact != null) {
                    timetable.headerContact = TimetableMapper.toContact(request.headerContact)
                }
                if (request.note != null) {
                    timetable.note = request.note.trim().ifBlank { null }
                }
                timetableRepository.save(timetable)
            }
            .map { TimetableMapper.toTimetableResponse(it) }
    }

    fun getById(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<TimetableResponse> {
        TimetableAccess.assertCanRead(currentUser)
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return timetableRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("TIMETABLE_NOT_FOUND", "Timetable not found")))
            .map { TimetableMapper.toTimetableResponse(it) }
    }

    fun listByYear(
        currentUser: CurrentUser,
        academicYearId: String,
        schoolIdOverride: String? = null
    ): Flux<TimetableResponse> {
        TimetableAccess.assertCanRead(currentUser)
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return timetableRepository
            .findBySchoolIdAndAcademicYearIdOrderByRevisionDesc(schoolId, academicYearId)
            .map { TimetableMapper.toTimetableResponse(it) }
    }

    override fun getSummary(schoolId: String, timetableId: String): Mono<TimetableSummaryDto> =
        timetableRepository.findByIdAndSchoolId(timetableId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("TIMETABLE_NOT_FOUND", "Timetable not found")))
            .map { TimetableMapper.toSummary(it) }

    override fun findPublished(schoolId: String, academicYearId: String): Mono<TimetableSummaryDto> =
        timetableRepository
            .findFirstBySchoolIdAndAcademicYearIdAndStatusOrderByRevisionDesc(
                schoolId,
                academicYearId,
                TimetableStatus.PUBLISHED
            )
            .map { TimetableMapper.toSummary(it) }

    internal fun requireDraft(schoolId: String, timetableId: String): Mono<Timetable> =
        timetableRepository.findByIdAndSchoolId(timetableId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("TIMETABLE_NOT_FOUND", "Timetable not found")))
            .flatMap { timetable ->
                if (timetable.status != TimetableStatus.DRAFT) {
                    Mono.error(
                        BusinessRuleException(
                            "TIMETABLE_NOT_DRAFT",
                            "Only DRAFT timetables can be modified"
                        )
                    )
                } else {
                    Mono.just(timetable)
                }
            }

    private fun nextRevision(schoolId: String, academicYearId: String): Mono<Int> =
        timetableRepository
            .findBySchoolIdAndAcademicYearIdOrderByRevisionDesc(schoolId, academicYearId)
            .next()
            .map { it.revision + 1 }
            .defaultIfEmpty(1)

    private fun assertCanManage(currentUser: CurrentUser, schoolId: String): Mono<Void> =
        schoolBrandApi.getTimetableSettings(schoolId)
            .doOnNext { settings ->
                TimetableAccess.assertCanManage(currentUser, settings.timetableSurveillantCanEdit)
                TimetableAccess.logSuperAdmin(currentUser, schoolId, "timetable.manage")
            }
            .then()
}
