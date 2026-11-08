package com.kobe.smels.service.student

import com.kobe.smels.dto.student.CreateStudentRequest
import com.kobe.smels.dto.student.GuardianLinkDto
import com.kobe.smels.dto.student.PageResponse
import com.kobe.smels.dto.student.StudentResponse
import com.kobe.smels.dto.student.StudentSummaryDto
import com.kobe.smels.dto.student.UpdateStudentRequest
import com.kobe.smels.exception.ConflictException
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.student.Student
import com.kobe.smels.model.user.ProfileType
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.student.GuardianRepository
import com.kobe.smels.repository.student.StudentRepository
import com.kobe.smels.service.student.api.StudentApi
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
class StudentService(
    private val studentRepository: StudentRepository,
    private val guardianRepository: GuardianRepository,
    private val outboxWriter: OutboxWriter
) : StudentApi {

    private val log = LoggerFactory.getLogger(javaClass)

    fun create(currentUser: CurrentUser, request: CreateStudentRequest): Mono<StudentResponse> {
        assertCanManage(currentUser)
        val schoolId = currentUser.requireSchoolId(request.schoolId)
        logSuperAdminTenantAction(currentUser, schoolId, "student.create")
        log.info("student.create start by={} schoolId={}", currentUser.username, schoolId)

        val matricule = request.matricule.trim()
        return ensureMatriculeAvailable(schoolId, matricule, excludeId = null)
            .then(validateGuardianLinks(schoolId, request.guardians))
            .then(
                Mono.fromCallable {
                    Student(
                        schoolId = schoolId,
                        matricule = matricule,
                        lastName = request.lastName.trim(),
                        firstName = request.firstName.trim(),
                        sex = request.sex,
                        dateOfBirth = request.dateOfBirth,
                        placeOfBirth = request.placeOfBirth?.trim()?.ifBlank { null },
                        nationality = request.nationality?.trim()?.ifBlank { null },
                        photoFileId = request.photoFileId?.trim()?.ifBlank { null },
                        contact = StudentMapper.toContact(request.contact),
                        guardians = request.guardians.map { StudentMapper.toGuardianLink(it) },
                        active = true
                    )
                }
            )
            .flatMap { studentRepository.save(it) }
            .flatMap { saved ->
                emitStudentChanged(saved).then(emitGuardianLinks(saved)).thenReturn(StudentMapper.toStudentResponse(saved))
            }
            .doOnNext {
                log.info("student.create ok by={} schoolId={}", currentUser.username, schoolId)
            }
            .doOnError { error ->
                log.warn("student.create failed by={} schoolId={} reason={}", currentUser.username, schoolId, error.message)
            }
    }

    fun update(
        currentUser: CurrentUser,
        id: String,
        request: UpdateStudentRequest,
        schoolIdOverride: String? = null
    ): Mono<StudentResponse> {
        assertCanManage(currentUser)
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        logSuperAdminTenantAction(currentUser, schoolId, "student.update")
        log.info("student.update start by={} schoolId={}", currentUser.username, schoolId)

        val matricule = request.matricule.trim()
        return studentRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("STUDENT_NOT_FOUND", "Student not found")))
            .flatMap { student ->
                ensureMatriculeAvailable(schoolId, matricule, excludeId = id)
                    .then(validateGuardianLinks(schoolId, request.guardians))
                    .then(Mono.fromCallable {
                        student.matricule = matricule
                        student.lastName = request.lastName.trim()
                        student.firstName = request.firstName.trim()
                        student.sex = request.sex
                        student.dateOfBirth = request.dateOfBirth
                        student.placeOfBirth = request.placeOfBirth?.trim()?.ifBlank { null }
                        student.nationality = request.nationality?.trim()?.ifBlank { null }
                        student.photoFileId = request.photoFileId?.trim()?.ifBlank { null }
                        student.contact = StudentMapper.toContact(request.contact)
                        student.guardians = request.guardians.map { StudentMapper.toGuardianLink(it) }
                        student
                    })
            }
            .flatMap { studentRepository.save(it) }
            .flatMap { saved ->
                emitStudentChanged(saved).then(emitGuardianLinks(saved)).thenReturn(StudentMapper.toStudentResponse(saved))
            }
            .doOnNext {
                log.info("student.update ok by={} schoolId={}", currentUser.username, schoolId)
            }
            .doOnError { error ->
                log.warn("student.update failed by={} schoolId={} reason={}", currentUser.username, schoolId, error.message)
            }
    }

    fun getById(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<StudentResponse> {
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        log.debug("student.getById by={} schoolId={}", currentUser.username, schoolId)
        return studentRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("STUDENT_NOT_FOUND", "Student not found")))
            .flatMap { student ->
                canRead(currentUser, student)
                    .flatMap { allowed ->
                        if (!allowed) {
                            log.warn("student.getById forbidden by={} schoolId={}", currentUser.username, schoolId)
                            Mono.error(ForbiddenException("FORBIDDEN", "Cannot read this student"))
                        } else {
                            Mono.just(StudentMapper.toStudentResponse(student))
                        }
                    }
            }
    }

    fun list(
        currentUser: CurrentUser,
        page: Int,
        size: Int,
        active: Boolean?,
        q: String?,
        schoolIdOverride: String? = null
    ): Mono<PageResponse<StudentResponse>> {
        assertCanList(currentUser)
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        logSuperAdminTenantAction(currentUser, schoolId, "student.list")
        log.info(
            "student.list by={} schoolId={} page={} size={} active={} hasQuery={}",
            currentUser.username,
            schoolId,
            page,
            size,
            active,
            !q.isNullOrBlank()
        )

        val pageable = PageRequest.of(
            page.coerceAtLeast(0),
            size.coerceIn(1, 100),
            Sort.by("lastName").ascending().and(Sort.by("firstName").ascending())
        )
        val query = q?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()

        val source: Flux<Student>
        val totalMono: Mono<Long>
        if (active != null) {
            source = studentRepository.findBySchoolIdAndActive(schoolId, active, pageable)
            totalMono = studentRepository.countBySchoolIdAndActive(schoolId, active)
        } else {
            source = studentRepository.findBySchoolId(schoolId, pageable)
            totalMono = studentRepository.countBySchoolId(schoolId)
        }

        return source
            .filter { student ->
                query == null ||
                    student.fullName().lowercase().contains(query) ||
                    student.matricule.lowercase().contains(query)
            }
            .map { StudentMapper.toStudentResponse(it) }
            .collectList()
            .zipWith(totalMono)
            .map { tuple ->
                val content = tuple.t1
                val total = if (query == null) tuple.t2 else content.size.toLong()
                PageResponse(
                    content = content,
                    page = pageable.pageNumber,
                    size = pageable.pageSize,
                    totalElements = total,
                    totalPages = if (total == 0L) 0 else ceil(total.toDouble() / pageable.pageSize).toInt()
                )
            }
            .doOnNext { result ->
                log.info(
                    "student.list ok by={} schoolId={} returned={} totalElements={}",
                    currentUser.username,
                    schoolId,
                    result.content.size,
                    result.totalElements
                )
            }
    }

    /** BR-42 : désactiver plutôt que supprimer. */
    fun deactivate(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<StudentResponse> {
        assertCanManage(currentUser)
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        logSuperAdminTenantAction(currentUser, schoolId, "student.deactivate")
        log.info("student.deactivate start by={} schoolId={}", currentUser.username, schoolId)
        return studentRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("STUDENT_NOT_FOUND", "Student not found")))
            .flatMap { student ->
                student.active = false
                studentRepository.save(student)
            }
            .flatMap { saved -> emitStudentChanged(saved).thenReturn(StudentMapper.toStudentResponse(saved)) }
            .doOnNext {
                log.info("student.deactivate ok by={} schoolId={}", currentUser.username, schoolId)
            }
    }

    fun reactivate(
        currentUser: CurrentUser,
        id: String,
        schoolIdOverride: String? = null
    ): Mono<StudentResponse> {
        assertCanManage(currentUser)
        val schoolId = currentUser.requireSchoolId(schoolIdOverride)
        logSuperAdminTenantAction(currentUser, schoolId, "student.reactivate")
        log.info("student.reactivate start by={} schoolId={}", currentUser.username, schoolId)
        return studentRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("STUDENT_NOT_FOUND", "Student not found")))
            .flatMap { student ->
                student.active = true
                studentRepository.save(student)
            }
            .flatMap { saved -> emitStudentChanged(saved).thenReturn(StudentMapper.toStudentResponse(saved)) }
            .doOnNext {
                log.info("student.reactivate ok by={} schoolId={}", currentUser.username, schoolId)
            }
    }

    override fun getSummary(schoolId: String, studentId: String): Mono<StudentSummaryDto> =
        studentRepository.findByIdAndSchoolId(studentId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("STUDENT_NOT_FOUND", "Student not found")))
            .map { StudentMapper.toStudentSummary(it) }

    override fun getIdentity(schoolId: String, studentId: String): Mono<com.kobe.smels.dto.student.StudentIdentityDto> =
        studentRepository.findByIdAndSchoolId(studentId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("STUDENT_NOT_FOUND", "Student not found")))
            .map { StudentMapper.toStudentIdentity(it) }

    override fun findActiveBySchool(schoolId: String): Flux<StudentSummaryDto> =
        studentRepository.findBySchoolIdAndActiveTrue(schoolId).map { StudentMapper.toStudentSummary(it) }

    override fun findByGuardian(schoolId: String, guardianId: String): Flux<StudentSummaryDto> =
        studentRepository.findBySchoolIdAndGuardiansGuardianId(schoolId, guardianId)
            .map { StudentMapper.toStudentSummary(it) }

    override fun findGuardianLink(
        schoolId: String,
        studentId: String,
        guardianId: String
    ): Mono<GuardianLinkDto> =
        studentRepository.findByIdAndSchoolId(studentId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("STUDENT_NOT_FOUND", "Student not found")))
            .flatMap { student ->
                val link = student.guardians.firstOrNull { it.guardianId == guardianId }
                if (link == null) {
                    Mono.error(NotFoundException("GUARDIAN_LINK_NOT_FOUND", "Guardian link not found"))
                } else {
                    Mono.just(StudentMapper.toGuardianLinkDto(link))
                }
            }

    private fun emitStudentChanged(student: Student): Mono<Void> =
        outboxWriter.append(
            schoolId = student.schoolId,
            module = "student",
            aggregateType = "Student",
            aggregateId = student.id!!,
            eventType = "student.changed",
            payload = mapOf(
                "studentId" to student.id,
                "matricule" to student.matricule,
                "displayName" to student.fullName(),
                "active" to student.active
            )
        ).then()

    private fun emitGuardianLinks(student: Student): Mono<Void> {
        if (student.guardians.isEmpty()) return Mono.empty()
        return Flux.fromIterable(student.guardians)
            .flatMap { link ->
                outboxWriter.append(
                    schoolId = student.schoolId,
                    module = "student",
                    aggregateType = "Student",
                    aggregateId = student.id!!,
                    eventType = "student.guardian.linked",
                    payload = mapOf(
                        "guardianId" to link.guardianId,
                        "studentId" to student.id,
                        "canViewGrades" to link.canViewGrades,
                        "canDownloadReportCards" to link.canDownloadReportCards,
                        "relationship" to link.relationship.name
                    )
                )
            }
            .then()
    }

    /** BR-40 : matricule unique dans l'école. */
    private fun ensureMatriculeAvailable(schoolId: String, matricule: String, excludeId: String?): Mono<Void> =
        studentRepository.findBySchoolIdAndMatricule(schoolId, matricule)
            .flatMap<Void> { existing ->
                if (excludeId != null && existing.id == excludeId) {
                    Mono.empty()
                } else {
                    Mono.error(ConflictException("MATRICULE_TAKEN", "Matricule already exists in this school"))
                }
            }
            .switchIfEmpty(Mono.empty())

    private fun validateGuardianLinks(schoolId: String, links: List<GuardianLinkDto>): Mono<Void> {
        if (links.isEmpty()) return Mono.empty()
        val ids = links.map { it.guardianId.trim() }.distinct()
        return Flux.fromIterable(ids)
            .flatMap { guardianId ->
                guardianRepository.findByIdAndSchoolId(guardianId, schoolId)
                    .switchIfEmpty(
                        Mono.error(NotFoundException("GUARDIAN_NOT_FOUND", "Guardian not found for link"))
                    )
            }
            .then()
    }

    private fun assertCanManage(currentUser: CurrentUser) {
        if (!currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)) {
            throw ForbiddenException("FORBIDDEN", "Insufficient role to manage students")
        }
    }

    private fun assertCanList(currentUser: CurrentUser) {
        if (!currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)) {
            // TEACHER : élèves de ses classes — nécessite AcademicApi (module academic pas encore livré).
            // PARENT / STUDENT : pas de liste globale.
            throw ForbiddenException("FORBIDDEN", "Insufficient role to list students")
        }
    }

    private fun canRead(currentUser: CurrentUser, student: Student): Mono<Boolean> {
        if (currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.SUPER_ADMIN)) {
            return Mono.just(true)
        }
        if (currentUser.hasRole(Role.STUDENT) &&
            currentUser.profileType == ProfileType.STUDENT &&
            currentUser.profileId == student.id
        ) {
            return Mono.just(true)
        }
        if (currentUser.hasRole(Role.PARENT) &&
            currentUser.profileType == ProfileType.GUARDIAN &&
            currentUser.profileId != null
        ) {
            return Mono.just(student.guardians.any { it.guardianId == currentUser.profileId })
        }
        if (currentUser.hasRole(Role.TEACHER)) {
            // TODO: filtrer via AcademicApi / ClassSubjectApi quand le module academic existera (docs/05).
            return Mono.just(false)
        }
        return Mono.just(false)
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
