package com.kobe.smels.service.staff

import com.kobe.smels.dto.staff.CreateStaffRequest
import com.kobe.smels.dto.staff.PageResponse
import com.kobe.smels.dto.staff.StaffResponse
import com.kobe.smels.dto.staff.StaffSummaryDto
import com.kobe.smels.dto.staff.UpdateStaffIdentityRequest
import com.kobe.smels.dto.staff.UpdateStaffRequest
import com.kobe.smels.exception.ConflictException
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.staff.Staff
import com.kobe.smels.model.staff.StaffFunction
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.staff.StaffRepository
import com.kobe.smels.service.staff.api.StaffApi
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
class StaffService(
    private val staffRepository: StaffRepository,
    private val outboxWriter: OutboxWriter
) : StaffApi {

    private val log = LoggerFactory.getLogger(javaClass)

    fun create(currentUser: CurrentUser, request: CreateStaffRequest): Mono<StaffResponse> {
        assertCanManageRole(currentUser)
        val schoolId = currentUser.requireSchoolId(request.schoolId)
        val functions = request.functions.ifEmpty { setOf(StaffFunction.TEACHER) }
        StaffAccessPolicies.assertCreatableFunctions(currentUser, functions)
        logSuperAdminTenantAction(currentUser, schoolId, "staff.create")
        log.info(
            "staff.create start by={} accountId={} schoolId={} functions={}",
            currentUser.username,
            currentUser.accountId,
            schoolId,
            functions.joinToString(",") { it.name }
        )

        return ensureStaffNumberAvailable(schoolId, request.staffNumber, excludeId = null)
            .then(
                Mono.fromCallable {
                    Staff(
                        schoolId = schoolId,
                        staffNumber = request.staffNumber?.trim()?.ifBlank { null },
                        lastName = request.lastName.trim(),
                        firstName = request.firstName.trim(),
                        civility = request.civility,
                        sex = request.sex,
                        dateOfBirth = request.dateOfBirth,
                        contact = StaffMapper.toContact(request.contact),
                        functions = functions,
                        employmentStatus = request.employmentStatus,
                        teachableSubjectIds = request.teachableSubjectIds,
                        hireDate = request.hireDate,
                        active = true
                    ).also { stampActor(it, currentUser) }
                }
            )
            .flatMap { staffRepository.save(it) }
            .flatMap { saved ->
                emitChanged(saved).thenReturn(toResponse(currentUser, saved))
            }
            .doOnNext { response ->
                log.info(
                    "staff.create ok by={} accountId={} schoolId={} staffId={} functions={}",
                    currentUser.username,
                    currentUser.accountId,
                    schoolId,
                    response.id,
                    response.functions.joinToString(",") { it.name }
                )
            }
            .doOnError { error ->
                log.warn(
                    "staff.create failed by={} accountId={} schoolId={} reason={}",
                    currentUser.username,
                    currentUser.accountId,
                    schoolId,
                    error.message
                )
            }
    }

    fun update(
        currentUser: CurrentUser,
        id: String,
        request: UpdateStaffRequest,
        schoolIdOverride: String? = null
    ): Mono<StaffResponse> {
        assertCanManageRole(currentUser)
        val schoolId = resolveSchoolId(currentUser, schoolIdOverride)
        val nextFunctions = request.functions.ifEmpty { setOf(StaffFunction.TEACHER) }
        StaffAccessPolicies.assertAssignableOnUpdate(currentUser, nextFunctions)
        logSuperAdminTenantAction(currentUser, schoolId, "staff.update")
        log.info(
            "staff.update start by={} accountId={} schoolId={} staffId={}",
            currentUser.username,
            currentUser.accountId,
            schoolId,
            id
        )
        return staffRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("STAFF_NOT_FOUND", "Staff member not found")))
            .flatMap { staff ->
                assertCanManageTarget(currentUser, staff)
                ensureStaffNumberAvailable(schoolId, request.staffNumber, excludeId = id)
                    .then(Mono.fromCallable {
                        staff.lastName = request.lastName.trim()
                        staff.firstName = request.firstName.trim()
                        staff.staffNumber = request.staffNumber?.trim()?.ifBlank { null }
                        staff.civility = request.civility
                        staff.sex = request.sex
                        staff.dateOfBirth = request.dateOfBirth
                        staff.contact = StaffMapper.toContact(request.contact)
                        staff.functions = nextFunctions
                        staff.employmentStatus = request.employmentStatus
                        staff.teachableSubjectIds = request.teachableSubjectIds
                        staff.hireDate = request.hireDate
                        stampActor(staff, currentUser)
                        staff
                    })
            }
            .flatMap { staffRepository.save(it) }
            .flatMap { saved -> emitChanged(saved).thenReturn(toResponse(currentUser, saved)) }
            .doOnNext {
                log.info(
                    "staff.update ok by={} accountId={} schoolId={} staffId={}",
                    currentUser.username,
                    currentUser.accountId,
                    schoolId,
                    id
                )
            }
            .doOnError { error ->
                log.warn(
                    "staff.update failed by={} accountId={} schoolId={} staffId={} reason={}",
                    currentUser.username,
                    currentUser.accountId,
                    schoolId,
                    id,
                    error.message
                )
            }
    }

    /**
     * Identité (nom, civilité, contact) : propriétaire de la fiche STAFF liée au compte,
     * ou rôles de gestion (ADMINISTRATION / DIRECTOR / SUPER_ADMIN).
     */
    fun updateIdentity(
        currentUser: CurrentUser,
        id: String,
        request: UpdateStaffIdentityRequest,
        schoolIdOverride: String? = null
    ): Mono<StaffResponse> {
        val schoolId = resolveSchoolId(currentUser, schoolIdOverride)
        logSuperAdminTenantAction(currentUser, schoolId, "staff.updateIdentity")
        log.info("staff.updateIdentity start by={} schoolId={} staffId={}", currentUser.username, schoolId, id)
        return staffRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("STAFF_NOT_FOUND", "Staff member not found")))
            .flatMap { staff ->
                if (!canUpdateIdentity(currentUser, staff)) {
                    log.warn(
                        "staff.updateIdentity forbidden by={} accountId={} schoolId={} staffId={}",
                        currentUser.username,
                        currentUser.accountId,
                        schoolId,
                        id
                    )
                    Mono.error(ForbiddenException("FORBIDDEN", "Cannot update this staff identity"))
                } else {
                    staff.lastName = request.lastName.trim()
                    staff.firstName = request.firstName.trim()
                    staff.civility = request.civility
                    staff.contact = StaffMapper.toContact(request.contact)
                    stampActor(staff, currentUser)
                    staffRepository.save(staff)
                }
            }
            .flatMap { saved -> emitChanged(saved).thenReturn(toResponse(currentUser, saved)) }
            .doOnNext {
                log.info(
                    "staff.updateIdentity ok by={} accountId={} schoolId={} staffId={}",
                    currentUser.username,
                    currentUser.accountId,
                    schoolId,
                    id
                )
            }
            .doOnError { error ->
                log.warn(
                    "staff.updateIdentity failed by={} accountId={} schoolId={} staffId={} reason={}",
                    currentUser.username,
                    currentUser.accountId,
                    schoolId,
                    id,
                    error.message
                )
            }
    }

    fun getById(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<StaffResponse> {
        val schoolId = resolveSchoolId(currentUser, schoolIdOverride)
        log.debug("staff.getById by={} schoolId={} staffId={}", currentUser.username, schoolId, id)
        return staffRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("STAFF_NOT_FOUND", "Staff member not found")))
            .flatMap { staff ->
                if (!canReadStaff(currentUser, staff)) {
                    log.warn(
                        "staff.getById forbidden by={} schoolId={} staffId={}",
                        currentUser.username,
                        schoolId,
                        id
                    )
                    Mono.error(ForbiddenException("FORBIDDEN", "Cannot read this staff member"))
                } else {
                    Mono.just(toResponse(currentUser, staff))
                }
            }
    }

    fun list(
        currentUser: CurrentUser,
        page: Int,
        size: Int,
        active: Boolean?,
        function: StaffFunction?,
        q: String?,
        schoolIdOverride: String? = null
    ): Mono<PageResponse<StaffResponse>> {
        assertCanList(currentUser)
        val schoolId = resolveSchoolId(currentUser, schoolIdOverride)
        logSuperAdminTenantAction(currentUser, schoolId, "staff.list")
        log.info(
            "staff.list by={} schoolId={} page={} size={} active={} function={} hasQuery={}",
            currentUser.username,
            schoolId,
            page,
            size,
            active,
            function,
            !q.isNullOrBlank()
        )
        val pageable = PageRequest.of(
            page.coerceAtLeast(0),
            size.coerceIn(1, 100),
            Sort.by("lastName").ascending().and(Sort.by("firstName").ascending())
        )
        val query = q?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()

        val source: Flux<Staff> = when {
            function != null && active != null ->
                staffRepository.findBySchoolIdAndFunctionsContainingAndActive(
                    schoolId, function, active, pageable
                )
            active != null ->
                staffRepository.findBySchoolIdAndActive(schoolId, active, pageable)
            else ->
                staffRepository.findBySchoolId(schoolId, pageable)
        }

        return source
            .filter { staff -> canReadStaff(currentUser, staff) }
            .filter { staff ->
                query == null ||
                    staff.fullName().lowercase().contains(query) ||
                    (staff.staffNumber?.lowercase()?.contains(query) == true)
            }
            .map { toResponse(currentUser, it) }
            .collectList()
            .map { content ->
                // Filtre visibilité / recherche en mémoire : total = taille filtrée (pagination fine = TODO).
                val total = content.size.toLong()
                PageResponse(
                    content = content,
                    page = pageable.pageNumber,
                    size = pageable.pageSize,
                    totalElements = total,
                    totalPages = if (total == 0L) 0 else ceil(total.toDouble() / pageable.pageSize).toInt()
                )
            }
            .doOnNext { pageResult ->
                log.info(
                    "staff.list ok by={} schoolId={} returned={} totalElements={}",
                    currentUser.username,
                    schoolId,
                    pageResult.content.size,
                    pageResult.totalElements
                )
            }
    }

    /** BR-42 : désactiver plutôt que supprimer. */
    fun deactivate(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<StaffResponse> {
        assertCanManageRole(currentUser)
        val schoolId = resolveSchoolId(currentUser, schoolIdOverride)
        logSuperAdminTenantAction(currentUser, schoolId, "staff.deactivate")
        log.info(
            "staff.deactivate start by={} accountId={} schoolId={} staffId={}",
            currentUser.username,
            currentUser.accountId,
            schoolId,
            id
        )
        return staffRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("STAFF_NOT_FOUND", "Staff member not found")))
            .flatMap { staff ->
                assertCanManageTarget(currentUser, staff)
                staff.active = false
                stampActor(staff, currentUser)
                staffRepository.save(staff)
            }
            .flatMap { saved -> emitChanged(saved).thenReturn(toResponse(currentUser, saved)) }
            .doOnNext {
                log.info(
                    "staff.deactivate ok by={} accountId={} schoolId={} staffId={}",
                    currentUser.username,
                    currentUser.accountId,
                    schoolId,
                    id
                )
            }
    }

    fun reactivate(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<StaffResponse> {
        assertCanManageRole(currentUser)
        val schoolId = resolveSchoolId(currentUser, schoolIdOverride)
        logSuperAdminTenantAction(currentUser, schoolId, "staff.reactivate")
        log.info(
            "staff.reactivate start by={} accountId={} schoolId={} staffId={}",
            currentUser.username,
            currentUser.accountId,
            schoolId,
            id
        )
        return staffRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("STAFF_NOT_FOUND", "Staff member not found")))
            .flatMap { staff ->
                assertCanManageTarget(currentUser, staff)
                staff.active = true
                stampActor(staff, currentUser)
                staffRepository.save(staff)
            }
            .flatMap { saved -> emitChanged(saved).thenReturn(toResponse(currentUser, saved)) }
            .doOnNext {
                log.info(
                    "staff.reactivate ok by={} accountId={} schoolId={} staffId={}",
                    currentUser.username,
                    currentUser.accountId,
                    schoolId,
                    id
                )
            }
    }

    override fun getSummary(schoolId: String, staffId: String): Mono<StaffSummaryDto> =
        staffRepository.findByIdAndSchoolId(staffId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("STAFF_NOT_FOUND", "Staff member not found")))
            .map { StaffMapper.toSummary(it) }

    override fun findActiveBySchool(schoolId: String): Flux<StaffSummaryDto> =
        staffRepository.findBySchoolIdAndActiveTrue(schoolId).map { StaffMapper.toSummary(it) }

    override fun findActiveTeachers(schoolId: String): Flux<StaffSummaryDto> =
        staffRepository.findBySchoolIdAndActiveTrue(schoolId)
            .filter { it.functions.contains(StaffFunction.TEACHER) }
            .map { StaffMapper.toSummary(it) }

    private fun emitChanged(staff: Staff): Mono<Void> =
        outboxWriter.append(
            schoolId = staff.schoolId,
            module = "staff",
            aggregateType = "Staff",
            aggregateId = staff.id!!,
            eventType = "staff.member.changed",
            payload = mapOf(
                "staffId" to staff.id,
                "displayName" to staff.fullName(),
                "civility" to staff.civility?.name,
                "active" to staff.active,
                "functions" to staff.functions.map { it.name }
            )
        )
            .doOnNext {
                log.debug(
                    "outbox appended eventType=staff.member.changed schoolId={} staffId={} active={}",
                    staff.schoolId,
                    staff.id,
                    staff.active
                )
            }
            .then()

    private fun ensureStaffNumberAvailable(
        schoolId: String,
        staffNumber: String?,
        excludeId: String?
    ): Mono<Void> {
        val number = staffNumber?.trim()?.ifBlank { null } ?: return Mono.empty()
        return staffRepository.findBySchoolIdAndStaffNumber(schoolId, number)
            .flatMap<Void> { existing ->
                if (excludeId != null && existing.id == excludeId) {
                    Mono.empty()
                } else {
                    Mono.error(ConflictException("STAFF_NUMBER_TAKEN", "Staff number already exists in this school"))
                }
            }
            .switchIfEmpty(Mono.empty())
    }

    private fun assertCanManageRole(currentUser: CurrentUser) {
        if (!currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)) {
            throw ForbiddenException("FORBIDDEN", "Insufficient role to manage staff")
        }
    }

    private fun assertCanManageTarget(currentUser: CurrentUser, staff: Staff) {
        if (!StaffAccessPolicies.canManage(currentUser, staff)) {
            throw ForbiddenException("FORBIDDEN", "Cannot manage this staff member")
        }
    }

    private fun assertCanList(currentUser: CurrentUser) {
        if (!currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN, Role.TEACHER)) {
            throw ForbiddenException("FORBIDDEN", "Insufficient role to list staff")
        }
        // TEACHER : pas de liste globale, seulement sa fiche via getById
        if (currentUser.hasRole(Role.TEACHER) &&
            !currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)
        ) {
            throw ForbiddenException("FORBIDDEN", "Teachers cannot list all staff members")
        }
    }

    private fun canReadStaff(currentUser: CurrentUser, staff: Staff): Boolean {
        if (isOwnStaffProfile(currentUser, staff)) return true
        if (currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)) {
            return StaffAccessPolicies.canRead(currentUser, staff)
        }
        return false
    }

    private fun canUpdateIdentity(currentUser: CurrentUser, staff: Staff): Boolean {
        if (isOwnStaffProfile(currentUser, staff)) return true
        if (currentUser.hasAnyRole(Role.DIRECTOR, Role.SUPER_ADMIN)) return true
        if (currentUser.hasRole(Role.ADMINISTRATION)) {
            return StaffAccessPolicies.canManage(currentUser, staff)
        }
        return false
    }

    private fun isOwnStaffProfile(currentUser: CurrentUser, staff: Staff): Boolean =
        currentUser.profileType == ProfileType.STAFF && currentUser.profileId == staff.id

    private fun toResponse(currentUser: CurrentUser, staff: Staff): StaffResponse =
        StaffMapper.toResponse(
            staff = staff,
            canManage = StaffAccessPolicies.canManage(currentUser, staff)
        )

    /**
     * Trace l’acteur (UserAccount.id) sur chaque écriture RH — complété aussi par
     * `@EnableReactiveMongoAuditing` / `ReactiveAuditorAware`.
     */
    private fun stampActor(staff: Staff, currentUser: CurrentUser) {
        if (staff.createdBy.isNullOrBlank()) {
            staff.createdBy = currentUser.accountId
        }
        staff.updatedBy = currentUser.accountId
    }

    private fun resolveSchoolId(currentUser: CurrentUser, overrideSchoolId: String?): String =
        currentUser.requireSchoolId(overrideSchoolId)

    /** docs/05 + docs/08 : accès multi-établissements SUPER_ADMIN journalisé. */
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
