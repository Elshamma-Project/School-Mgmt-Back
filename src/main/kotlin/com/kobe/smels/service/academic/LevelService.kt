package com.kobe.smels.service.academic

import com.kobe.smels.dto.academic.CreateLevelRequest
import com.kobe.smels.dto.academic.LevelResponse
import com.kobe.smels.dto.academic.LevelSummaryDto
import com.kobe.smels.dto.academic.PageResponse
import com.kobe.smels.dto.academic.UpdateLevelRequest
import com.kobe.smels.exception.ConflictException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.academic.Level
import com.kobe.smels.repository.academic.LevelRepository
import com.kobe.smels.service.academic.api.LevelApi
import com.kobe.smels.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.math.ceil

@Service
class LevelService(
    private val levelRepository: LevelRepository
) : LevelApi {

    private val log = LoggerFactory.getLogger(javaClass)

    fun create(currentUser: CurrentUser, request: CreateLevelRequest): Mono<LevelResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, request.schoolId)
        val code = request.code.trim().uppercase()
        log.info("level.create start by={} schoolId={} code={}", currentUser.username, schoolId, code)
        return ensureCodeAvailable(schoolId, code)
            .then(
                levelRepository.save(
                    Level(
                        schoolId = schoolId,
                        code = code,
                        name = AcademicMapper.toLocalizedRequired(request.name),
                        cycle = request.cycle,
                        section = request.section,
                        order = request.order,
                        equivalenceIndex = request.equivalenceIndex,
                        nextLevelCode = request.nextLevelCode?.trim()?.ifBlank { null },
                        defaultGradingSchemeId = request.defaultGradingSchemeId,
                        active = true
                    )
                )
            )
            .map { AcademicMapper.toLevelResponse(it) }
    }

    fun update(
        currentUser: CurrentUser,
        id: String,
        request: UpdateLevelRequest,
        schoolIdOverride: String? = null
    ): Mono<LevelResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return levelRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("LEVEL_NOT_FOUND", "Level not found")))
            .flatMap { level ->
                level.name = AcademicMapper.toLocalizedRequired(request.name)
                level.cycle = request.cycle
                level.section = request.section
                level.order = request.order
                level.equivalenceIndex = request.equivalenceIndex
                level.nextLevelCode = request.nextLevelCode?.trim()?.ifBlank { null }
                level.defaultGradingSchemeId = request.defaultGradingSchemeId
                levelRepository.save(level)
            }
            .map { AcademicMapper.toLevelResponse(it) }
    }

    fun getById(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<LevelResponse> {
        AcademicAccess.assertCanReadCatalog(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return levelRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("LEVEL_NOT_FOUND", "Level not found")))
            .map { AcademicMapper.toLevelResponse(it) }
    }

    fun list(
        currentUser: CurrentUser,
        page: Int,
        size: Int,
        active: Boolean?,
        schoolIdOverride: String? = null
    ): Mono<PageResponse<LevelResponse>> {
        AcademicAccess.assertCanReadCatalog(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), Sort.by("order").ascending())
        val source = if (active != null) levelRepository.findBySchoolIdAndActive(schoolId, active, pageable)
        else levelRepository.findBySchoolId(schoolId, pageable)
        val total = if (active != null) levelRepository.countBySchoolIdAndActive(schoolId, active)
        else levelRepository.countBySchoolId(schoolId)
        return source.map { AcademicMapper.toLevelResponse(it) }.collectList().zipWith(total).map { t ->
            PageResponse(t.t1, pageable.pageNumber, pageable.pageSize, t.t2,
                if (t.t2 == 0L) 0 else ceil(t.t2.toDouble() / pageable.pageSize).toInt())
        }
    }

    fun deactivate(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<LevelResponse> =
        setActive(currentUser, id, false, schoolIdOverride)

    fun reactivate(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<LevelResponse> =
        setActive(currentUser, id, true, schoolIdOverride)

    override fun getSummary(schoolId: String, levelId: String): Mono<LevelSummaryDto> =
        levelRepository.findByIdAndSchoolId(levelId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("LEVEL_NOT_FOUND", "Level not found")))
            .map { AcademicMapper.toLevelSummary(it) }

    override fun findActiveBySchool(schoolId: String): Flux<LevelSummaryDto> =
        levelRepository.findBySchoolIdAndActiveTrue(schoolId).map { AcademicMapper.toLevelSummary(it) }

    private fun setActive(
        currentUser: CurrentUser,
        id: String,
        active: Boolean,
        schoolIdOverride: String?
    ): Mono<LevelResponse> {
        AcademicAccess.assertCanManage(currentUser)
        val schoolId = AcademicAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return levelRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("LEVEL_NOT_FOUND", "Level not found")))
            .flatMap { level ->
                level.active = active
                levelRepository.save(level)
            }
            .map { AcademicMapper.toLevelResponse(it) }
    }

    private fun ensureCodeAvailable(schoolId: String, code: String): Mono<Void> =
        levelRepository.findBySchoolIdAndCode(schoolId, code)
            .flatMap<Void> { Mono.error(ConflictException("LEVEL_CODE_TAKEN", "Level code already exists")) }
            .switchIfEmpty(Mono.empty())
}
