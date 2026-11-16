package com.kobe.beesm.service.timetable

import com.kobe.beesm.dto.timetable.CreateTimetableChangeRequest
import com.kobe.beesm.dto.timetable.TimetableChangeRequestResponse
import com.kobe.beesm.dto.timetable.TimetableEntrySnapshotDto
import com.kobe.beesm.dto.timetable.UpsertTimetableEntryRequest
import com.kobe.beesm.exception.BusinessRuleException
import com.kobe.beesm.exception.ConflictException
import com.kobe.beesm.exception.ForbiddenException
import com.kobe.beesm.exception.NotFoundException
import com.kobe.beesm.model.timetable.TimetableChangeKind
import com.kobe.beesm.model.timetable.TimetableChangeRequest
import com.kobe.beesm.model.timetable.TimetableChangeStatus
import com.kobe.beesm.model.timetable.TimetableEntry
import com.kobe.beesm.model.timetable.TimetableEntrySnapshot
import com.kobe.beesm.model.timetable.TimetableStatus
import com.kobe.beesm.model.user.ProfileType
import com.kobe.beesm.model.user.Role
import com.kobe.beesm.repository.timetable.TimetableChangeRequestRepository
import com.kobe.beesm.repository.timetable.TimetableEntryRepository
import com.kobe.beesm.repository.timetable.TimetableRepository
import com.kobe.beesm.shared.outbox.OutboxWriter
import com.kobe.beesm.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.Optional

@Service
class TimetableChangeRequestService(
    private val requestRepository: TimetableChangeRequestRepository,
    private val timetableRepository: TimetableRepository,
    private val entryRepository: TimetableEntryRepository,
    private val timetableService: TimetableService,
    private val entryService: TimetableEntryService,
    private val outboxWriter: OutboxWriter
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun create(
        currentUser: CurrentUser,
        request: CreateTimetableChangeRequest
    ): Mono<TimetableChangeRequestResponse> {
        TimetableAccess.assertTeacher(currentUser)
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, request.schoolId)
        val teacherId = currentUser.profileId.takeIf {
            currentUser.profileType == ProfileType.STAFF && !it.isNullOrBlank()
        } ?: throw ForbiddenException("FORBIDDEN", "Teacher staff profile required")
        val reason = request.reason.trim()
        if (reason.length < 15) {
            return Mono.error(BusinessRuleException("REASON_TOO_SHORT", "Reason must be at least 15 characters"))
        }
        if (request.changeKind != TimetableChangeKind.ADD && request.sourceEntryId.isNullOrBlank()) {
            return Mono.error(BusinessRuleException("ENTRY_REQUIRED", "sourceEntryId is required"))
        }

        return timetableRepository.findByIdAndSchoolId(request.sourceTimetableId.trim(), schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("TIMETABLE_NOT_FOUND", "Timetable not found")))
            .flatMap { timetable ->
                if (timetable.status != TimetableStatus.PUBLISHED) {
                    Mono.error(
                        BusinessRuleException(
                            "SOURCE_NOT_PUBLISHED",
                            "Change requests must target a PUBLISHED timetable"
                        )
                    )
                } else {
                    resolveSourceEntry(schoolId, request)
                        .map { Optional.of(it) }
                        .defaultIfEmpty(Optional.empty())
                        .flatMap { sourceOpt ->
                        val sourceEntry = sourceOpt.orElse(null)
                        if (sourceEntry != null &&
                            sourceEntry.teacherId != null &&
                            sourceEntry.teacherId != teacherId &&
                            !currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)
                        ) {
                            return@flatMap Mono.error(
                                ForbiddenException(
                                    "FORBIDDEN",
                                    "You may only request changes on your own timetable entries"
                                )
                            )
                        }
                        val entryId = sourceEntry?.id
                        val pendingCheck =
                            if (entryId != null) {
                                requestRepository
                                    .findBySchoolIdAndSourceTimetableIdAndSourceEntryIdAndStatus(
                                        schoolId,
                                        timetable.id!!,
                                        entryId,
                                        TimetableChangeStatus.PENDING
                                    )
                                    .hasElements()
                            } else {
                                Mono.just(false)
                            }
                        pendingCheck.flatMap { hasPending ->
                            if (hasPending) {
                                Mono.error(
                                    ConflictException(
                                        "PENDING_EXISTS",
                                        "A pending change request already exists for this entry"
                                    )
                                )
                            } else {
                                val current = sourceEntry?.let { snapshotFromEntry(it) }
                                val proposed = snapshotFromDto(request.proposed)
                                val entity = TimetableChangeRequest(
                                    schoolId = schoolId,
                                    academicYearId = timetable.academicYearId,
                                    sourceTimetableId = timetable.id!!,
                                    sourceRevision = timetable.revision,
                                    sourceEntryId = entryId,
                                    sourceClassId = sourceEntry?.classId ?: proposed.classId,
                                    sourceDay = sourceEntry?.day ?: proposed.day,
                                    sourceSlotKey = sourceEntry?.slotKey
                                        ?: SlotKeys.resolve(proposed.startPeriod, proposed.customStart),
                                    sourceOptionCode = sourceEntry?.optionCode ?: proposed.optionCode,
                                    changeKind = request.changeKind,
                                    currentSnapshot = current,
                                    proposed = proposed,
                                    classLabel = request.classLabel?.trim()?.ifBlank { null },
                                    teacherId = teacherId,
                                    teacherLabel = request.teacherLabel?.trim()?.ifBlank { null }
                                        ?: proposed.teacherLabel,
                                    reason = reason,
                                    requestedBy = currentUser.accountId
                                )
                                requestRepository.save(entity).flatMap { saved ->
                                    log.info(
                                        "timetable.change.requested by={} schoolId={} requestId={}",
                                        currentUser.username,
                                        schoolId,
                                        saved.id
                                    )
                                    outboxWriter.append(
                                        schoolId = schoolId,
                                        module = "timetable",
                                        aggregateType = "TimetableChangeRequest",
                                        aggregateId = saved.id!!,
                                        eventType = "timetable.change.requested",
                                        payload = mapOf(
                                            "requestId" to saved.id,
                                            "timetableId" to saved.sourceTimetableId,
                                            "teacherId" to saved.teacherId,
                                            "teacherLabel" to saved.teacherLabel,
                                            "classLabel" to saved.classLabel,
                                            "changeKind" to saved.changeKind.name,
                                            "status" to TimetableChangeStatus.PENDING.name
                                        )
                                    ).thenReturn(toResponse(saved))
                                }
                            }
                        }
                    }
                }
            }
    }

    fun listPending(
        currentUser: CurrentUser,
        schoolIdOverride: String? = null
    ): Flux<TimetableChangeRequestResponse> {
        TimetableAccess.assertCanReviewChangeRequests(currentUser)
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return requestRepository
            .findBySchoolIdAndStatusOrderByRequestedAtDesc(schoolId, TimetableChangeStatus.PENDING)
            .map { toResponse(it) }
    }

    fun listMine(
        currentUser: CurrentUser,
        schoolIdOverride: String? = null
    ): Flux<TimetableChangeRequestResponse> {
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride)
        TimetableAccess.assertCanRead(currentUser)
        return requestRepository
            .findBySchoolIdAndRequestedByOrderByRequestedAtDesc(schoolId, currentUser.accountId)
            .map { toResponse(it) }
    }

    fun approve(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<TimetableChangeRequestResponse> {
        TimetableAccess.assertCanReviewChangeRequests(currentUser)
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return requestRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("CHANGE_REQUEST_NOT_FOUND", "Change request not found")))
            .flatMap { req ->
                if (req.status != TimetableChangeStatus.PENDING) {
                    Mono.error(BusinessRuleException("INVALID_STATUS", "Only PENDING requests can be approved"))
                } else {
                    ensureDraft(currentUser, schoolId, req).flatMap { draftId ->
                        applyToDraft(currentUser, schoolId, draftId, req)
                            .map { Optional.of(it) }
                            .defaultIfEmpty(Optional.empty())
                            .flatMap { appliedOpt ->
                            req.status = TimetableChangeStatus.APPROVED
                            req.reviewedBy = currentUser.accountId
                            req.reviewedAt = Instant.now()
                            req.appliedTimetableId = draftId
                            req.appliedEntryId = appliedOpt.orElse(null)
                            requestRepository.save(req).flatMap { saved ->
                                outboxWriter.append(
                                    schoolId = schoolId,
                                    module = "timetable",
                                    aggregateType = "TimetableChangeRequest",
                                    aggregateId = saved.id!!,
                                    eventType = "timetable.change.approved",
                                    payload = mapOf(
                                        "requestId" to saved.id,
                                        "requestedBy" to saved.requestedBy,
                                        "teacherId" to saved.teacherId,
                                        "classLabel" to saved.classLabel,
                                        "decision" to "APPROVED",
                                        "appliedTimetableId" to draftId
                                    )
                                ).thenReturn(toResponse(saved))
                            }
                        }
                    }
                }
            }
    }

    fun reject(
        currentUser: CurrentUser,
        id: String,
        reviewNote: String,
        schoolIdOverride: String? = null
    ): Mono<TimetableChangeRequestResponse> {
        TimetableAccess.assertCanReviewChangeRequests(currentUser)
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride)
        val note = reviewNote.trim()
        if (note.length < 5) {
            return Mono.error(BusinessRuleException("REVIEW_NOTE_REQUIRED", "Review note is required"))
        }
        return requestRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("CHANGE_REQUEST_NOT_FOUND", "Change request not found")))
            .flatMap { req ->
                if (req.status != TimetableChangeStatus.PENDING) {
                    Mono.error(BusinessRuleException("INVALID_STATUS", "Only PENDING requests can be rejected"))
                } else {
                    req.status = TimetableChangeStatus.REJECTED
                    req.reviewedBy = currentUser.accountId
                    req.reviewedAt = Instant.now()
                    req.reviewNote = note
                    requestRepository.save(req).flatMap { saved ->
                        outboxWriter.append(
                            schoolId = schoolId,
                            module = "timetable",
                            aggregateType = "TimetableChangeRequest",
                            aggregateId = saved.id!!,
                            eventType = "timetable.change.rejected",
                            payload = mapOf(
                                "requestId" to saved.id,
                                "requestedBy" to saved.requestedBy,
                                "teacherId" to saved.teacherId,
                                "classLabel" to saved.classLabel,
                                "decision" to "REJECTED",
                                "reviewNote" to note
                            )
                        ).thenReturn(toResponse(saved))
                    }
                }
            }
    }

    fun cancel(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<TimetableChangeRequestResponse> {
        val schoolId = TimetableAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return requestRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("CHANGE_REQUEST_NOT_FOUND", "Change request not found")))
            .flatMap { req ->
                if (req.requestedBy != currentUser.accountId &&
                    !currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)
                ) {
                    return@flatMap Mono.error(ForbiddenException("FORBIDDEN", "Not your request"))
                }
                if (req.status != TimetableChangeStatus.PENDING) {
                    return@flatMap Mono.error(
                        BusinessRuleException("INVALID_STATUS", "Only PENDING requests can be cancelled")
                    )
                }
                req.status = TimetableChangeStatus.CANCELLED
                requestRepository.save(req).map { toResponse(it) }
            }
    }

    private fun ensureDraft(
        currentUser: CurrentUser,
        schoolId: String,
        req: TimetableChangeRequest
    ): Mono<String> =
        timetableRepository
            .findBySchoolIdAndAcademicYearIdAndStatus(
                schoolId,
                req.academicYearId,
                TimetableStatus.DRAFT
            )
            .next()
            .map { it.id!! }
            .switchIfEmpty(
                Mono.defer {
                    timetableService.revise(currentUser, req.sourceTimetableId, schoolId)
                        .map { it.id }
                }
            )

    private fun applyToDraft(
        currentUser: CurrentUser,
        schoolId: String,
        draftId: String,
        req: TimetableChangeRequest
    ): Mono<String> =
        when (req.changeKind) {
            TimetableChangeKind.REMOVE ->
                findDraftEntry(schoolId, draftId, req)
                    .flatMap { entry ->
                        val entryId = entry.id ?: return@flatMap Mono.empty()
                        entryService.delete(currentUser, entryId, schoolId).thenReturn(entryId)
                    }
                    .switchIfEmpty(Mono.empty())
            TimetableChangeKind.ADD, TimetableChangeKind.UPDATE -> {
                val upsert = upsertFromSnapshot(req.proposed, schoolId)
                findDraftEntry(schoolId, draftId, req)
                    .flatMap { existing ->
                        val existingId = existing.id ?: return@flatMap Mono.empty()
                        entryService.update(currentUser, existingId, upsert, schoolId)
                            .flatMap { write ->
                                val id = write.entry.id
                                if (id != null) Mono.just(id) else Mono.empty()
                            }
                    }
                    .switchIfEmpty(
                        Mono.defer {
                            entryService.upsert(currentUser, draftId, upsert).flatMap { write ->
                                val id = write.entry.id
                                if (id != null) Mono.just(id) else Mono.empty()
                            }
                        }
                    )
            }
        }

    private fun findDraftEntry(
        schoolId: String,
        draftId: String,
        req: TimetableChangeRequest
    ): Mono<TimetableEntry> =
        entryRepository.findBySchoolIdAndTimetableId(schoolId, draftId)
            .filter { entry ->
                entry.classId == req.sourceClassId &&
                    entry.day == req.sourceDay &&
                    entry.slotKey == req.sourceSlotKey &&
                    entry.optionCode == req.sourceOptionCode
            }
            .next()

    private fun resolveSourceEntry(
        schoolId: String,
        request: CreateTimetableChangeRequest
    ): Mono<TimetableEntry> {
        if (request.changeKind == TimetableChangeKind.ADD || request.sourceEntryId.isNullOrBlank()) {
            return Mono.empty()
        }
        return entryRepository.findByIdAndSchoolId(request.sourceEntryId!!.trim(), schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("ENTRY_NOT_FOUND", "Timetable entry not found")))
    }

    private fun snapshotFromEntry(entry: TimetableEntry) = TimetableEntrySnapshot(
        classId = entry.classId,
        day = entry.day,
        startPeriod = entry.startPeriod,
        periodCount = entry.periodCount,
        customStart = entry.customStart,
        customEnd = entry.customEnd,
        slotKey = entry.slotKey,
        kind = entry.kind,
        classSubjectIds = entry.classSubjectIds,
        subjectId = entry.subjectId,
        teacherId = entry.teacherId,
        roomId = entry.roomId,
        activityType = entry.activityType,
        sessionGroupId = entry.sessionGroupId,
        optionGroupCode = entry.optionGroupCode,
        optionCode = entry.optionCode,
        label = entry.label,
        note = entry.note,
        subjectLabel = entry.subjectLabel,
        teacherLabel = entry.teacherLabel
    )

    private fun snapshotFromDto(dto: TimetableEntrySnapshotDto): TimetableEntrySnapshot {
        val slotKey = SlotKeys.resolve(dto.startPeriod, dto.customStart)
        return TimetableEntrySnapshot(
            classId = dto.classId.trim(),
            day = dto.day,
            startPeriod = dto.startPeriod,
            periodCount = dto.periodCount.coerceAtLeast(1),
            customStart = dto.customStart,
            customEnd = dto.customEnd,
            slotKey = slotKey,
            kind = dto.kind,
            classSubjectIds = dto.classSubjectIds.map { it.trim() }.filter { it.isNotBlank() },
            subjectId = dto.subjectId?.trim()?.ifBlank { null },
            teacherId = dto.teacherId?.trim()?.ifBlank { null },
            roomId = dto.roomId?.trim()?.ifBlank { null },
            activityType = dto.activityType,
            sessionGroupId = dto.sessionGroupId?.trim()?.ifBlank { null },
            optionGroupCode = dto.optionGroupCode?.trim()?.ifBlank { null },
            optionCode = dto.optionCode.trim(),
            label = TimetableMapper.toLocalized(dto.label),
            note = dto.note?.trim()?.ifBlank { null },
            subjectLabel = dto.subjectLabel?.trim()?.ifBlank { null },
            teacherLabel = dto.teacherLabel?.trim()?.ifBlank { null }
        )
    }

    private fun upsertFromSnapshot(s: TimetableEntrySnapshot, schoolId: String) =
        UpsertTimetableEntryRequest(
            classId = s.classId,
            day = s.day,
            startPeriod = s.startPeriod,
            periodCount = s.periodCount,
            customStart = s.customStart,
            customEnd = s.customEnd,
            kind = s.kind,
            classSubjectIds = s.classSubjectIds,
            subjectId = s.subjectId,
            teacherId = s.teacherId,
            roomId = s.roomId,
            activityType = s.activityType,
            sessionGroupId = s.sessionGroupId,
            optionGroupCode = s.optionGroupCode,
            optionCode = s.optionCode,
            label = TimetableMapper.toLocalizedDto(s.label),
            note = s.note,
            subjectLabel = s.subjectLabel,
            teacherLabel = s.teacherLabel,
            schoolId = schoolId
        )

    private fun toSnapshotDto(s: TimetableEntrySnapshot) = TimetableEntrySnapshotDto(
        classId = s.classId,
        day = s.day,
        startPeriod = s.startPeriod,
        periodCount = s.periodCount,
        customStart = s.customStart,
        customEnd = s.customEnd,
        kind = s.kind,
        classSubjectIds = s.classSubjectIds,
        subjectId = s.subjectId,
        teacherId = s.teacherId,
        roomId = s.roomId,
        activityType = s.activityType,
        sessionGroupId = s.sessionGroupId,
        optionGroupCode = s.optionGroupCode,
        optionCode = s.optionCode,
        label = TimetableMapper.toLocalizedDto(s.label),
        note = s.note,
        subjectLabel = s.subjectLabel,
        teacherLabel = s.teacherLabel
    )

    private fun toResponse(r: TimetableChangeRequest) = TimetableChangeRequestResponse(
        id = r.id!!,
        schoolId = r.schoolId,
        academicYearId = r.academicYearId,
        sourceTimetableId = r.sourceTimetableId,
        sourceRevision = r.sourceRevision,
        sourceEntryId = r.sourceEntryId,
        sourceClassId = r.sourceClassId,
        sourceDay = r.sourceDay,
        sourceSlotKey = r.sourceSlotKey,
        sourceOptionCode = r.sourceOptionCode,
        changeKind = r.changeKind,
        currentSnapshot = r.currentSnapshot?.let { toSnapshotDto(it) },
        proposed = toSnapshotDto(r.proposed),
        classLabel = r.classLabel,
        teacherId = r.teacherId,
        teacherLabel = r.teacherLabel,
        reason = r.reason,
        status = r.status,
        requestedBy = r.requestedBy,
        requestedAt = r.requestedAt,
        reviewedBy = r.reviewedBy,
        reviewedAt = r.reviewedAt,
        reviewNote = r.reviewNote,
        appliedTimetableId = r.appliedTimetableId,
        appliedEntryId = r.appliedEntryId
    )
}
