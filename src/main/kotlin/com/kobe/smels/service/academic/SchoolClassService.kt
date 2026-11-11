package com.kobe.smels.service.academic

import com.kobe.smels.dto.academic.CreateSchoolClassRequest
import com.kobe.smels.dto.academic.PageResponse
import com.kobe.smels.dto.academic.SchoolClassResponse
import com.kobe.smels.dto.academic.SchoolClassSummaryDto
import com.kobe.smels.dto.academic.UpdateSchoolClassRequest
import com.kobe.smels.exception.ConflictException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.academic.SchoolClass
import com.kobe.smels.repository.academic.LevelRepository
import com.kobe.smels.repository.academic.SchoolClassRepository
import com.kobe.smels.service.academic.api.AcademicYearApi
import com.kobe.smels.service.academic.api.SchoolClassApi
import com.kobe.smels.shared.outbox.OutboxWriter
import com.kobe.smels.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.math.ceil

@Service
class SchoolClassService(
    private val schoolClassRepository: SchoolClassRepository,
    private val academicYearApi: AcademicYearApi,
    private val levelRepository: LevelRepository,
    private val outboxWriter: OutboxWriter
) : SchoolClassApi {

    private val log = LoggerFactory.getLogger(javaClass)

    fun create(currentUser: CurrentUser, request: CreateSchoolClassRequest): Mono<SchoolClassResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, request.schoolId)
        AcademicAccess.logSuperAdmin(currentUser, schoolId, "class.create")
        val name = request.name.trim()
        log.info("class.create start by={} schoolId={} yearId={}", currentUser.username, schoolId, request.academicYearId)

        return academicYearApi.assertWritable(schoolId, request.academicYearId)
            .then(levelRepository.findByIdAndSchoolId(request.levelId, schoolId))
            .switchIfEmpty(Mono.error(NotFoundException("LEVEL_NOT_FOUND", "Level not found")))
            .flatMap { level ->
                ensureNameAvailable(schoolId, request.academicYearId, name)
                    .then(
                        schoolClassRepository.save(
                            SchoolClass(
                                schoolId = schoolId,
                                academicYearId = request.academicYearId,
                                levelId = level.id!!,
                                levelCode = level.code,
                                section = level.section,
                                cycle = level.cycle,
                                name = name,
                                trackCode = request.trackCode?.trim()?.ifBlank { null },
                                capacity = request.capacity,
                                mainTeacherId = request.mainTeacherId?.trim()?.ifBlank { null },
                                homeRoomId = request.homeRoomId?.trim()?.ifBlank { null },
                                defaultLanguage = request.defaultLanguage,
                                availableLanguages = request.availableLanguages.ifEmpty {
                                    setOf(request.defaultLanguage)
                                },
                                gradingSchemeId = request.gradingSchemeId,
                                active = true
                            )
                        )
                    )
            }
            .flatMap { saved -> emitClassChanged(saved).thenReturn(AcademicMapper.toClassResponse(saved)) }
            .doOnNext {
                log.info("class.create ok by={} schoolId={} classId={}", currentUser.username, schoolId, it.id)
            }
    }

    fun update(
        currentUser: CurrentUser,
        id: String,
        request: UpdateSchoolClassRequest,
        schoolIdOverride: String? = null
    ): Mono<SchoolClassResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        val name = request.name.trim()
        return schoolClassRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("CLASS_NOT_FOUND", "Class not found")))
            .flatMap { schoolClass ->
                academicYearApi.assertWritable(schoolId, schoolClass.academicYearId)
                    .then(ensureNameAvailable(schoolId, schoolClass.academicYearId, name, excludeId = id))
                    .then(Mono.fromCallable {
                        schoolClass.name = name
                        schoolClass.trackCode = request.trackCode?.trim()?.ifBlank { null }
                        schoolClass.capacity = request.capacity
                        schoolClass.mainTeacherId = request.mainTeacherId?.trim()?.ifBlank { null }
                        schoolClass.homeRoomId = request.homeRoomId?.trim()?.ifBlank { null }
                        schoolClass.defaultLanguage = request.defaultLanguage
                        schoolClass.availableLanguages = request.availableLanguages.ifEmpty {
                            setOf(request.defaultLanguage)
                        }
                        schoolClass.gradingSchemeId = request.gradingSchemeId
                        schoolClass
                    })
            }
            .flatMap { schoolClassRepository.save(it) }
            .flatMap { saved -> emitClassChanged(saved).thenReturn(AcademicMapper.toClassResponse(saved)) }
    }

    fun getById(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<SchoolClassResponse> {
        AcademicAccess.assertCanReadCatalog(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return schoolClassRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("CLASS_NOT_FOUND", "Class not found")))
            .map { AcademicMapper.toClassResponse(it) }
    }

    fun list(
        currentUser: CurrentUser,
        academicYearId: String,
        page: Int,
        size: Int,
        active: Boolean?,
        schoolIdOverride: String? = null
    ): Mono<PageResponse<SchoolClassResponse>> {
        AcademicAccess.assertCanReadCatalog(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), Sort.by("name").ascending())
        val source = if (active != null) {
            schoolClassRepository.findBySchoolIdAndAcademicYearIdAndActive(schoolId, academicYearId, active, pageable)
        } else {
            schoolClassRepository.findBySchoolIdAndAcademicYearId(schoolId, academicYearId, pageable)
        }
        val total = if (active != null) {
            schoolClassRepository.countBySchoolIdAndAcademicYearIdAndActive(schoolId, academicYearId, active)
        } else {
            schoolClassRepository.countBySchoolIdAndAcademicYearId(schoolId, academicYearId)
        }
        return source.map { AcademicMapper.toClassResponse(it) }.collectList().zipWith(total).map { t ->
            PageResponse(t.t1, pageable.pageNumber, pageable.pageSize, t.t2,
                if (t.t2 == 0L) 0 else ceil(t.t2.toDouble() / pageable.pageSize).toInt())
        }
    }

    fun deactivate(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<SchoolClassResponse> =
        setActive(currentUser, id, false, schoolIdOverride)

    fun reactivate(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<SchoolClassResponse> =
        setActive(currentUser, id, true, schoolIdOverride)

    override fun getSummary(schoolId: String, classId: String): Mono<SchoolClassSummaryDto> =
        schoolClassRepository.findByIdAndSchoolId(classId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("CLASS_NOT_FOUND", "Class not found")))
            .map { AcademicMapper.toClassSummary(it) }

    override fun findActiveByYear(schoolId: String, academicYearId: String): Flux<SchoolClassSummaryDto> =
        schoolClassRepository.findBySchoolIdAndAcademicYearIdAndActiveTrue(schoolId, academicYearId)
            .map { AcademicMapper.toClassSummary(it) }

    private fun setActive(
        currentUser: CurrentUser,
        id: String,
        active: Boolean,
        schoolIdOverride: String?
    ): Mono<SchoolClassResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return schoolClassRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("CLASS_NOT_FOUND", "Class not found")))
            .flatMap { schoolClass ->
                schoolClass.active = active
                schoolClassRepository.save(schoolClass)
            }
            .flatMap { saved -> emitClassChanged(saved).thenReturn(AcademicMapper.toClassResponse(saved)) }
    }

    private fun emitClassChanged(schoolClass: SchoolClass): Mono<Void> =
        outboxWriter.append(
            schoolId = schoolClass.schoolId,
            module = "academic",
            aggregateType = "SchoolClass",
            aggregateId = schoolClass.id!!,
            eventType = "academic.class.changed",
            payload = mapOf(
                "classId" to schoolClass.id,
                "name" to schoolClass.name,
                "levelCode" to schoolClass.levelCode,
                "section" to schoolClass.section.name,
                "defaultLanguage" to schoolClass.defaultLanguage.name,
                "active" to schoolClass.active
            )
        ).then()

    private fun ensureNameAvailable(
        schoolId: String,
        academicYearId: String,
        name: String,
        excludeId: String? = null
    ): Mono<Void> =
        schoolClassRepository.findBySchoolIdAndAcademicYearIdAndName(schoolId, academicYearId, name)
            .flatMap<Void> { existing ->
                if (excludeId != null && existing.id == excludeId) Mono.empty()
                else Mono.error(ConflictException("CLASS_NAME_TAKEN", "Class name already exists for this year"))
            }
            .switchIfEmpty(Mono.empty())
}
