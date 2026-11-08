package com.kobe.smels.service.student

import com.kobe.smels.dto.student.CreateGuardianRequest
import com.kobe.smels.dto.student.GuardianResponse
import com.kobe.smels.dto.student.GuardianSummaryDto
import com.kobe.smels.dto.student.PageResponse
import com.kobe.smels.dto.student.UpdateGuardianRequest
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.student.Guardian
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.student.GuardianRepository
import com.kobe.smels.service.student.api.GuardianApi
import com.kobe.smels.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import kotlin.math.ceil

@Service
class GuardianService(
    private val guardianRepository: GuardianRepository
) : GuardianApi {

    private val log = LoggerFactory.getLogger(javaClass)

    fun create(currentUser: CurrentUser, request: CreateGuardianRequest): Mono<GuardianResponse> {
        assertCanManage(currentUser)
        val schoolId = currentUser.requireSchoolId(request.schoolId)
        logSuperAdminTenantAction(currentUser, schoolId, "guardian.create")
        log.info("guardian.create start by={} schoolId={}", currentUser.username, schoolId)

        return Mono.fromCallable {
            Guardian(
                schoolId = schoolId,
                lastName = request.lastName.trim(),
                firstName = request.firstName.trim(),
                sex = request.sex,
                profession = request.profession?.trim()?.ifBlank { null },
                contact = StudentMapper.toContact(request.contact),
                active = true
            )
        }
            .flatMap { guardianRepository.save(it) }
            .map { StudentMapper.toGuardianResponse(it) }
            .doOnNext {
                log.info("guardian.create ok by={} schoolId={}", currentUser.username, schoolId)
            }
            .doOnError { error ->
                log.warn("guardian.create failed by={} schoolId={} reason={}", currentUser.username, schoolId, error.message)
            }
    }

    fun update(
        currentUser: CurrentUser,
        id: String,
        request: UpdateGuardianRequest,
        schoolIdOverride: String? = null
    ): Mono<GuardianResponse> {
        assertCanManage(currentUser)
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        log.info("guardian.update start by={} schoolId={}", currentUser.username, schoolId)
        return guardianRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("GUARDIAN_NOT_FOUND", "Guardian not found")))
            .flatMap { guardian ->
                guardian.lastName = request.lastName.trim()
                guardian.firstName = request.firstName.trim()
                guardian.sex = request.sex
                guardian.profession = request.profession?.trim()?.ifBlank { null }
                guardian.contact = StudentMapper.toContact(request.contact)
                guardianRepository.save(guardian)
            }
            .map { StudentMapper.toGuardianResponse(it) }
            .doOnNext {
                log.info("guardian.update ok by={} schoolId={}", currentUser.username, schoolId)
            }
    }

    fun getById(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<GuardianResponse> {
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        return guardianRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("GUARDIAN_NOT_FOUND", "Guardian not found")))
            .flatMap { guardian ->
                if (!canRead(currentUser, guardian)) {
                    Mono.error(ForbiddenException("FORBIDDEN", "Cannot read this guardian"))
                } else {
                    Mono.just(StudentMapper.toGuardianResponse(guardian))
                }
            }
    }

    fun list(
        currentUser: CurrentUser,
        page: Int,
        size: Int,
        active: Boolean?,
        schoolIdOverride: String? = null
    ): Mono<PageResponse<GuardianResponse>> {
        assertCanManage(currentUser)
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        log.info(
            "guardian.list by={} schoolId={} page={} size={} active={}",
            currentUser.username,
            schoolId,
            page,
            size,
            active
        )
        val pageable = PageRequest.of(
            page.coerceAtLeast(0),
            size.coerceIn(1, 100),
            Sort.by("lastName").ascending().and(Sort.by("firstName").ascending())
        )
        val source = if (active != null) {
            guardianRepository.findBySchoolIdAndActive(schoolId, active, pageable)
        } else {
            guardianRepository.findBySchoolId(schoolId, pageable)
        }
        val totalMono = if (active != null) {
            guardianRepository.countBySchoolIdAndActive(schoolId, active)
        } else {
            guardianRepository.countBySchoolId(schoolId)
        }
        return source.map { StudentMapper.toGuardianResponse(it) }
            .collectList()
            .zipWith(totalMono)
            .map { tuple ->
                val content = tuple.t1
                val total = tuple.t2
                PageResponse(
                    content = content,
                    page = pageable.pageNumber,
                    size = pageable.pageSize,
                    totalElements = total,
                    totalPages = if (total == 0L) 0 else ceil(total.toDouble() / pageable.pageSize).toInt()
                )
            }
    }

    /** BR-42 */
    fun deactivate(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<GuardianResponse> {
        assertCanManage(currentUser)
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        log.info("guardian.deactivate start by={} schoolId={}", currentUser.username, schoolId)
        return guardianRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("GUARDIAN_NOT_FOUND", "Guardian not found")))
            .flatMap { guardian ->
                guardian.active = false
                guardianRepository.save(guardian)
            }
            .map { StudentMapper.toGuardianResponse(it) }
    }

    fun reactivate(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<GuardianResponse> {
        assertCanManage(currentUser)
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        return guardianRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("GUARDIAN_NOT_FOUND", "Guardian not found")))
            .flatMap { guardian ->
                guardian.active = true
                guardianRepository.save(guardian)
            }
            .map { StudentMapper.toGuardianResponse(it) }
    }

    override fun getSummary(schoolId: String, guardianId: String): Mono<GuardianSummaryDto> =
        guardianRepository.findByIdAndSchoolId(guardianId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("GUARDIAN_NOT_FOUND", "Guardian not found")))
            .map { StudentMapper.toGuardianSummary(it) }

    private fun assertCanManage(currentUser: CurrentUser) {
        if (!currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)) {
            throw ForbiddenException("FORBIDDEN", "Insufficient role to manage guardians")
        }
    }

    private fun canRead(currentUser: CurrentUser, guardian: Guardian): Boolean {
        if (currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)) return true
        return currentUser.hasRole(Role.PARENT) &&
            currentUser.profileType == ProfileType.GUARDIAN &&
            currentUser.profileId == guardian.id
    }

    private fun logSuperAdminTenantAction(currentUser: CurrentUser, schoolId: String, action: String) {
        if (currentUser.isSuperAdmin()) {
            log.info(
                "SUPER_ADMIN tenant action action={} by={} targetSchoolId={} tokenSchoolId={}",
                action,
                currentUser.username,
                schoolId,
                currentUser.schoolId
            )
        }
    }
}
