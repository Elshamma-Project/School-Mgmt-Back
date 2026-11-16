package com.kobe.smels.service.timetable

import com.kobe.smels.dto.timetable.CreateTimetableRequest
import com.kobe.smels.dto.timetable.DuplicateTimetableRequest
import com.kobe.smels.dto.timetable.TimetableResponse
import com.kobe.smels.dto.timetable.TimetableSummaryDto
import com.kobe.smels.dto.timetable.UpdateTimetableRequest
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.exception.ConflictException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.timetable.Timetable
import com.kobe.smels.model.timetable.TimetableStatus
import com.kobe.smels.repository.timetable.TimeSlotGridRepository
import com.kobe.smels.repository.timetable.TimetableEntryRepository
import com.kobe.smels.repository.timetable.TimetableRepository
import com.kobe.smels.service.academic.api.AcademicYearApi
import com.kobe.smels.service.academic.api.SchoolBrandApi
import com.kobe.smels.service.timetable.api.TimetableApi
import com.kobe.smels.shared.outbox.OutboxWriter
import com.kobe.smels.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class TimetableService(
    private val timetableRepository: TimetableRepository,
    private val entryRepository: TimetableEntryRepository,
    private val gridRepository: TimeSlotGridRepository,
    private val conflictService: TimetableConflictService,
    private val academicYearApi: AcademicYearApi,
    private val schoolBrandApi: SchoolBrandApi,
    private val outboxWriter: OutboxWriter
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
                ensureNoDraft(schoolId, yearId).then(
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
                )
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

    /**
     * BR-60 : publie le brouillon ; l’éventuelle révision PUBLISHED de l’année passe en ARCHIVED ;
     * émet `timetable.published`.
     */
    fun publish(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<TimetableResponse> {
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return assertCanManage(currentUser, schoolId)
            .then(requireDraft(schoolId, id))
            .flatMap { draft ->
                academicYearApi.assertWritable(schoolId, draft.academicYearId).thenReturn(draft)
            }
            .flatMap { draft ->
                conflictService.analyze(currentUser, draft.id!!, schoolId)
                    .flatMap { report ->
                        if (report.blocking.isNotEmpty()) {
                            val first = report.blocking.first()
                            Mono.error(
                                BusinessRuleException(
                                    "PUBLISH_BLOCKED",
                                    "Cannot publish: ${first.code} — ${first.message}"
                                )
                            )
                        } else {
                            Mono.just(draft)
                        }
                    }
            }
            .flatMap { draft ->
                archivePublished(schoolId, draft.academicYearId)
                    .then(
                        Mono.defer {
                            draft.status = TimetableStatus.PUBLISHED
                            draft.publishedAt = Instant.now()
                            draft.publishedBy = currentUser.accountId
                            timetableRepository.save(draft)
                        }
                    )
            }
            .flatMap { published ->
                log.info(
                    "timetable.publish by={} schoolId={} timetableId={} revision={}",
                    currentUser.username,
                    schoolId,
                    published.id,
                    published.revision
                )
                outboxWriter.append(
                    schoolId = schoolId,
                    module = "timetable",
                    aggregateType = "Timetable",
                    aggregateId = published.id!!,
                    eventType = "timetable.published",
                    payload = mapOf(
                        "timetableId" to published.id,
                        "academicYearId" to published.academicYearId,
                        "revision" to published.revision
                    )
                ).thenReturn(published)
            }
            .map { TimetableMapper.toTimetableResponse(it) }
    }

    /**
     * Nouvelle révision DRAFT à partir d’un EDT PUBLISHED (même année) — BR-60.
     */
    fun revise(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<TimetableResponse> {
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return assertCanManage(currentUser, schoolId)
            .then(timetableRepository.findByIdAndSchoolId(id, schoolId))
            .switchIfEmpty(Mono.error(NotFoundException("TIMETABLE_NOT_FOUND", "Timetable not found")))
            .flatMap { source ->
                if (source.status != TimetableStatus.PUBLISHED) {
                    Mono.error(
                        BusinessRuleException(
                            "REVISE_PUBLISHED_ONLY",
                            "Only a PUBLISHED timetable can be revised into a new draft"
                        )
                    )
                } else {
                    academicYearApi.assertWritable(schoolId, source.academicYearId)
                        .then(ensureNoDraft(schoolId, source.academicYearId))
                        .then(copyAsDraft(currentUser, source, source.academicYearId, source.gridId))
                }
            }
            .map { TimetableMapper.toTimetableResponse(it) }
    }

    /**
     * Duplique un EDT (généralement PUBLISHED) vers une autre année scolaire (Q-32).
     * Les `classId` / `classSubjectIds` sont copiés tels quels — à réaligner si les ids changent.
     */
    fun duplicateToYear(
        currentUser: CurrentUser,
        sourceId: String,
        request: DuplicateTimetableRequest
    ): Mono<TimetableResponse> {
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, request.schoolId)
        val targetYearId = request.targetAcademicYearId.trim()
        return assertCanManage(currentUser, schoolId)
            .then(timetableRepository.findByIdAndSchoolId(sourceId, schoolId))
            .switchIfEmpty(Mono.error(NotFoundException("TIMETABLE_NOT_FOUND", "Timetable not found")))
            .flatMap { source ->
                if (source.academicYearId == targetYearId) {
                    Mono.error(
                        BusinessRuleException(
                            "DUPLICATE_SAME_YEAR",
                            "Use revise for a new revision in the same year"
                        )
                    )
                } else {
                    academicYearApi.assertWritable(schoolId, targetYearId)
                        .then(ensureNoDraft(schoolId, targetYearId))
                        .then(resolveTargetGrid(schoolId, source, targetYearId, request.targetGridId))
                        .flatMap { gridId -> copyAsDraft(currentUser, source, targetYearId, gridId) }
                }
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

    override fun hasEntriesForClass(schoolId: String, timetableId: String, classId: String): Mono<Boolean> =
        entryRepository
            .countBySchoolIdAndTimetableIdAndClassId(schoolId, timetableId, classId)
            .map { it > 0L }
            .defaultIfEmpty(false)

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

    private fun copyAsDraft(
        currentUser: CurrentUser,
        source: Timetable,
        targetYearId: String,
        targetGridId: String
    ): Mono<Timetable> =
        nextRevision(source.schoolId, targetYearId).flatMap { revision ->
            timetableRepository.save(
                Timetable(
                    schoolId = source.schoolId,
                    academicYearId = targetYearId,
                    gridId = targetGridId,
                    revision = revision,
                    status = TimetableStatus.DRAFT,
                    generatedAt = Instant.now(),
                    generatedBy = currentUser.accountId,
                    headerContact = source.headerContact,
                    note = source.note
                )
            ).flatMap { draft ->
                entryRepository.findBySchoolIdAndTimetableId(source.schoolId, source.id!!)
                    .collectList()
                    .flatMap { entries ->
                        val clones = TimetableCopySupport.cloneEntries(
                            sourceEntries = entries,
                            targetSchoolId = source.schoolId,
                            targetAcademicYearId = targetYearId,
                            targetTimetableId = draft.id!!
                        )
                        if (clones.isEmpty()) {
                            Mono.just(draft)
                        } else {
                            entryRepository.saveAll(clones).then(Mono.just(draft))
                        }
                    }
            }
        }

    private fun resolveTargetGrid(
        schoolId: String,
        source: Timetable,
        targetYearId: String,
        targetGridId: String?
    ): Mono<String> {
        if (targetGridId != null) {
            return gridRepository.findByIdAndSchoolId(targetGridId.trim(), schoolId)
                .switchIfEmpty(Mono.error(NotFoundException("GRID_NOT_FOUND", "Time slot grid not found")))
                .flatMap { grid ->
                    if (grid.academicYearId != targetYearId) {
                        Mono.error(
                            BusinessRuleException(
                                "GRID_YEAR_MISMATCH",
                                "Target grid does not belong to the target academic year"
                            )
                        )
                    } else {
                        Mono.just(grid.id!!)
                    }
                }
        }
        return gridRepository.findByIdAndSchoolId(source.gridId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("GRID_NOT_FOUND", "Source grid not found")))
            .flatMap { sourceGrid ->
                gridRepository.save(
                    TimetableCopySupport.cloneGrid(sourceGrid, targetYearId, nameSuffix = "(copy)")
                )
            }
            .map { it.id!! }
    }

    private fun archivePublished(schoolId: String, academicYearId: String): Mono<Void> =
        timetableRepository
            .findBySchoolIdAndAcademicYearIdAndStatus(schoolId, academicYearId, TimetableStatus.PUBLISHED)
            .flatMap { published ->
                published.status = TimetableStatus.ARCHIVED
                timetableRepository.save(published)
            }
            .then()

    private fun ensureNoDraft(schoolId: String, academicYearId: String): Mono<Void> =
        timetableRepository
            .findBySchoolIdAndAcademicYearIdAndStatus(schoolId, academicYearId, TimetableStatus.DRAFT)
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
                    Mono.empty()
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
