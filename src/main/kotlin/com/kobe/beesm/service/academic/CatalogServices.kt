package com.kobe.beesm.service.academic

import com.kobe.beesm.dto.academic.CreateRoomRequest
import com.kobe.beesm.dto.academic.CreateSubjectGroupRequest
import com.kobe.beesm.dto.academic.CreateSubjectRequest
import com.kobe.beesm.dto.academic.PageResponse
import com.kobe.beesm.dto.academic.RoomResponse
import com.kobe.beesm.dto.academic.SubjectGroupResponse
import com.kobe.beesm.dto.academic.SubjectGroupSummaryDto
import com.kobe.beesm.dto.academic.SubjectResponse
import com.kobe.beesm.dto.academic.SubjectSummaryDto
import com.kobe.beesm.dto.academic.UpdateRoomRequest
import com.kobe.beesm.dto.academic.UpdateSubjectGroupRequest
import com.kobe.beesm.dto.academic.UpdateSubjectRequest
import com.kobe.beesm.exception.ConflictException
import com.kobe.beesm.exception.NotFoundException
import com.kobe.beesm.model.academic.Room
import com.kobe.beesm.model.academic.Subject
import com.kobe.beesm.model.academic.SubjectGroup
import com.kobe.beesm.repository.academic.RoomRepository
import com.kobe.beesm.repository.academic.SubjectGroupRepository
import com.kobe.beesm.repository.academic.SubjectRepository
import com.kobe.beesm.service.academic.api.SubjectApi
import com.kobe.beesm.service.academic.api.SubjectGroupApi
import com.kobe.beesm.shared.outbox.OutboxWriter
import com.kobe.beesm.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.math.ceil

@Service
class RoomService(
    private val roomRepository: RoomRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun create(currentUser: CurrentUser, request: CreateRoomRequest): Mono<RoomResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, request.schoolId)
        val name = request.name.trim()
        log.info("room.create start by={} schoolId={}", currentUser.username, schoolId)
        return roomRepository.findBySchoolIdAndName(schoolId, name)
            .flatMap<Room> { Mono.error(ConflictException("ROOM_NAME_TAKEN", "Room name already exists")) }
            .switchIfEmpty(
                Mono.defer {
                    roomRepository.save(
                        Room(schoolId = schoolId, name = name, type = request.type, capacity = request.capacity)
                    )
                }
            )
            .map { AcademicMapper.toRoomResponse(it) }
    }

    fun update(
        currentUser: CurrentUser,
        id: String,
        request: UpdateRoomRequest,
        schoolIdOverride: String? = null
    ): Mono<RoomResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        val name = request.name.trim()
        return roomRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("ROOM_NOT_FOUND", "Room not found")))
            .flatMap { room ->
                roomRepository.findBySchoolIdAndName(schoolId, name)
                    .flatMap<Room> { existing ->
                        if (existing.id == id) Mono.just(room)
                        else Mono.error(ConflictException("ROOM_NAME_TAKEN", "Room name already exists"))
                    }
                    .switchIfEmpty(Mono.just(room))
                    .flatMap {
                        it.name = name
                        it.type = request.type
                        it.capacity = request.capacity
                        roomRepository.save(it)
                    }
            }
            .map { AcademicMapper.toRoomResponse(it) }
    }

    fun getById(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<RoomResponse> {
        AcademicAccess.assertCanReadCatalog(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return roomRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("ROOM_NOT_FOUND", "Room not found")))
            .map { AcademicMapper.toRoomResponse(it) }
    }

    fun list(
        currentUser: CurrentUser,
        page: Int,
        size: Int,
        active: Boolean?,
        schoolIdOverride: String? = null
    ): Mono<PageResponse<RoomResponse>> {
        AcademicAccess.assertCanReadCatalog(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), Sort.by("name").ascending())
        val source = if (active != null) roomRepository.findBySchoolIdAndActive(schoolId, active, pageable)
        else roomRepository.findBySchoolId(schoolId, pageable)
        val total = if (active != null) roomRepository.countBySchoolIdAndActive(schoolId, active)
        else roomRepository.countBySchoolId(schoolId)
        return source.map { AcademicMapper.toRoomResponse(it) }.collectList().zipWith(total).map { t ->
            PageResponse(t.t1, pageable.pageNumber, pageable.pageSize, t.t2,
                if (t.t2 == 0L) 0 else ceil(t.t2.toDouble() / pageable.pageSize).toInt())
        }
    }

    fun deactivate(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<RoomResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return roomRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("ROOM_NOT_FOUND", "Room not found")))
            .flatMap { room ->
                room.active = false
                roomRepository.save(room)
            }
            .map { AcademicMapper.toRoomResponse(it) }
    }

    fun reactivate(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<RoomResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return roomRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("ROOM_NOT_FOUND", "Room not found")))
            .flatMap { room ->
                room.active = true
                roomRepository.save(room)
            }
            .map { AcademicMapper.toRoomResponse(it) }
    }
}

@Service
class SubjectService(
    private val subjectRepository: SubjectRepository,
    private val outboxWriter: OutboxWriter
) : SubjectApi {
    private val log = LoggerFactory.getLogger(javaClass)

    fun create(currentUser: CurrentUser, request: CreateSubjectRequest): Mono<SubjectResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, request.schoolId)
        val code = request.code.trim().uppercase()
        log.info("subject.create start by={} schoolId={} code={}", currentUser.username, schoolId, code)
        return subjectRepository.findBySchoolIdAndCode(schoolId, code)
            .flatMap<Subject> { Mono.error(ConflictException("SUBJECT_CODE_TAKEN", "Subject code already exists")) }
            .switchIfEmpty(
                Mono.defer {
                    subjectRepository.save(
                        Subject(
                            schoolId = schoolId,
                            code = code,
                            name = AcademicMapper.toLocalizedRequired(request.name),
                            shortName = AcademicMapper.toLocalized(request.shortName),
                            timetableAlias = request.timetableAlias?.trim()?.ifBlank { null },
                            kind = request.kind,
                            teachingLanguage = request.teachingLanguage,
                            active = true
                        )
                    )
                }
            )
            .map { AcademicMapper.toSubjectResponse(it) }
    }

    fun update(
        currentUser: CurrentUser,
        id: String,
        request: UpdateSubjectRequest,
        schoolIdOverride: String? = null
    ): Mono<SubjectResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return subjectRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SUBJECT_NOT_FOUND", "Subject not found")))
            .flatMap { subject ->
                val renamed = subject.name.fr != request.name.fr || subject.name.en != request.name.en
                subject.name = AcademicMapper.toLocalizedRequired(request.name)
                subject.shortName = AcademicMapper.toLocalized(request.shortName)
                subject.timetableAlias = request.timetableAlias?.trim()?.ifBlank { null }
                subject.kind = request.kind
                subject.teachingLanguage = request.teachingLanguage
                subjectRepository.save(subject).flatMap { saved ->
                    if (renamed) {
                        outboxWriter.append(
                            schoolId = saved.schoolId,
                            module = "academic",
                            aggregateType = "Subject",
                            aggregateId = saved.id!!,
                            eventType = "academic.subject.renamed",
                            payload = mapOf(
                                "subjectId" to saved.id,
                                "nameFr" to saved.name.fr,
                                "nameEn" to saved.name.en
                            )
                        ).thenReturn(saved)
                    } else Mono.just(saved)
                }
            }
            .map { AcademicMapper.toSubjectResponse(it) }
    }

    fun getById(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<SubjectResponse> {
        AcademicAccess.assertCanReadCatalog(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return subjectRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SUBJECT_NOT_FOUND", "Subject not found")))
            .map { AcademicMapper.toSubjectResponse(it) }
    }

    fun list(
        currentUser: CurrentUser,
        page: Int,
        size: Int,
        active: Boolean?,
        schoolIdOverride: String? = null
    ): Mono<PageResponse<SubjectResponse>> {
        AcademicAccess.assertCanReadCatalog(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), Sort.by("code").ascending())
        val source = if (active != null) subjectRepository.findBySchoolIdAndActive(schoolId, active, pageable)
        else subjectRepository.findBySchoolId(schoolId, pageable)
        val total = if (active != null) subjectRepository.countBySchoolIdAndActive(schoolId, active)
        else subjectRepository.countBySchoolId(schoolId)
        return source.map { AcademicMapper.toSubjectResponse(it) }.collectList().zipWith(total).map { t ->
            PageResponse(t.t1, pageable.pageNumber, pageable.pageSize, t.t2,
                if (t.t2 == 0L) 0 else ceil(t.t2.toDouble() / pageable.pageSize).toInt())
        }
    }

    fun deactivate(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<SubjectResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return subjectRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SUBJECT_NOT_FOUND", "Subject not found")))
            .flatMap { subject ->
                subject.active = false
                subjectRepository.save(subject)
            }
            .map { AcademicMapper.toSubjectResponse(it) }
    }

    fun reactivate(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<SubjectResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return subjectRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SUBJECT_NOT_FOUND", "Subject not found")))
            .flatMap { subject ->
                subject.active = true
                subjectRepository.save(subject)
            }
            .map { AcademicMapper.toSubjectResponse(it) }
    }

    override fun getSummary(schoolId: String, subjectId: String): Mono<SubjectSummaryDto> =
        subjectRepository.findByIdAndSchoolId(subjectId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SUBJECT_NOT_FOUND", "Subject not found")))
            .map { AcademicMapper.toSubjectSummary(it) }

    override fun findActiveBySchool(schoolId: String): Flux<SubjectSummaryDto> =
        subjectRepository.findBySchoolIdAndActiveTrue(schoolId).map { AcademicMapper.toSubjectSummary(it) }
}

@Service
class SubjectGroupService(
    private val subjectGroupRepository: SubjectGroupRepository
) : SubjectGroupApi {
    fun create(currentUser: CurrentUser, request: CreateSubjectGroupRequest): Mono<SubjectGroupResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, request.schoolId)
        val code = request.code.trim().uppercase()
        return subjectGroupRepository.findBySchoolIdAndCode(schoolId, code)
            .flatMap<SubjectGroup> {
                Mono.error(ConflictException("SUBJECT_GROUP_CODE_TAKEN", "Subject group code already exists"))
            }
            .switchIfEmpty(
                Mono.defer {
                    subjectGroupRepository.save(
                        SubjectGroup(
                            schoolId = schoolId,
                            code = code,
                            name = AcademicMapper.toLocalizedRequired(request.name),
                            displayOrder = request.displayOrder,
                            active = true
                        )
                    )
                }
            )
            .map { AcademicMapper.toGroupResponse(it) }
    }

    fun update(
        currentUser: CurrentUser,
        id: String,
        request: UpdateSubjectGroupRequest,
        schoolIdOverride: String? = null
    ): Mono<SubjectGroupResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return subjectGroupRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SUBJECT_GROUP_NOT_FOUND", "Subject group not found")))
            .flatMap { group ->
                group.name = AcademicMapper.toLocalizedRequired(request.name)
                group.displayOrder = request.displayOrder
                subjectGroupRepository.save(group)
            }
            .map { AcademicMapper.toGroupResponse(it) }
    }

    fun getById(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<SubjectGroupResponse> {
        AcademicAccess.assertCanReadCatalog(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return subjectGroupRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SUBJECT_GROUP_NOT_FOUND", "Subject group not found")))
            .map { AcademicMapper.toGroupResponse(it) }
    }

    fun list(
        currentUser: CurrentUser,
        page: Int,
        size: Int,
        active: Boolean?,
        schoolIdOverride: String? = null
    ): Mono<PageResponse<SubjectGroupResponse>> {
        AcademicAccess.assertCanReadCatalog(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), Sort.by("displayOrder").ascending())
        val source = if (active != null) subjectGroupRepository.findBySchoolIdAndActive(schoolId, active, pageable)
        else subjectGroupRepository.findBySchoolId(schoolId, pageable)
        val total = if (active != null) subjectGroupRepository.countBySchoolIdAndActive(schoolId, active)
        else subjectGroupRepository.countBySchoolId(schoolId)
        return source.map { AcademicMapper.toGroupResponse(it) }.collectList().zipWith(total).map { t ->
            PageResponse(t.t1, pageable.pageNumber, pageable.pageSize, t.t2,
                if (t.t2 == 0L) 0 else ceil(t.t2.toDouble() / pageable.pageSize).toInt())
        }
    }

    fun deactivate(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<SubjectGroupResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return subjectGroupRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SUBJECT_GROUP_NOT_FOUND", "Subject group not found")))
            .flatMap { group ->
                group.active = false
                subjectGroupRepository.save(group)
            }
            .map { AcademicMapper.toGroupResponse(it) }
    }

    fun reactivate(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<SubjectGroupResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return subjectGroupRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SUBJECT_GROUP_NOT_FOUND", "Subject group not found")))
            .flatMap { group ->
                group.active = true
                subjectGroupRepository.save(group)
            }
            .map { AcademicMapper.toGroupResponse(it) }
    }

    override fun getSummary(schoolId: String, groupId: String): Mono<SubjectGroupSummaryDto> =
        subjectGroupRepository.findByIdAndSchoolId(groupId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SUBJECT_GROUP_NOT_FOUND", "Subject group not found")))
            .map { AcademicMapper.toGroupSummary(it) }
}
