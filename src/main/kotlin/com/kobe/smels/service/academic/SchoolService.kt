package com.kobe.smels.service.academic

import com.kobe.smels.dto.academic.CreateSchoolRequest
import com.kobe.smels.dto.academic.SchoolResponse
import com.kobe.smels.dto.academic.UpdateSchoolRequest
import com.kobe.smels.exception.ConflictException
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.academic.School
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.academic.SchoolRepository
import com.kobe.smels.shared.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class SchoolService(
    private val schoolRepository: SchoolRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun create(currentUser: CurrentUser, request: CreateSchoolRequest): Mono<SchoolResponse> {
        if (!currentUser.isSuperAdmin()) {
            throw ForbiddenException("FORBIDDEN", "Only SUPER_ADMIN can create schools")
        }
        val code = request.code.trim().uppercase()
        log.info("school.create start by={} code={}", currentUser.username, code)
        return schoolRepository.findByCode(code)
            .flatMap<School> {
                Mono.error(ConflictException("SCHOOL_CODE_TAKEN", "School code already exists"))
            }
            .switchIfEmpty(
                Mono.defer {
                    schoolRepository.save(
                        School(
                            code = code,
                            name = AcademicMapper.toLocalizedRequired(request.name),
                            shortName = request.shortName?.trim()?.ifBlank { null },
                            officialHeaders = request.officialHeaders.map { AcademicMapper.toOfficialHeader(it) },
                            contact = AcademicMapper.toContact(request.contact),
                            postalBox = request.postalBox?.trim()?.ifBlank { null },
                            signaturePlace = request.signaturePlace?.trim()?.ifBlank { null },
                            headTitle = AcademicMapper.toLocalized(request.headTitle),
                            headOfSchoolStaffId = request.headOfSchoolStaffId?.trim()?.ifBlank { null },
                            logoFileId = request.logoFileId?.trim()?.ifBlank { null },
                            reportCardNotice = AcademicMapper.toLocalized(request.reportCardNotice),
                            reportCardDisclaimer = AcademicMapper.toLocalized(request.reportCardDisclaimer),
                            timezone = request.timezone.trim().ifBlank { "Africa/Douala" },
                            active = true
                        )
                    )
                }
            )
            .map { AcademicMapper.toSchoolResponse(it) }
            .doOnNext { log.info("school.create ok by={} schoolId={}", currentUser.username, it.id) }
    }

    fun update(currentUser: CurrentUser, id: String, request: UpdateSchoolRequest): Mono<SchoolResponse> {
        AcademicAccess.assertCanManageSchool(currentUser)
        assertSchoolScope(currentUser, id)
        log.info("school.update start by={} schoolId={}", currentUser.username, id)
        return schoolRepository.findById(id)
            .switchIfEmpty(Mono.error(NotFoundException("SCHOOL_NOT_FOUND", "School not found")))
            .flatMap { school ->
                school.name = AcademicMapper.toLocalizedRequired(request.name)
                school.shortName = request.shortName?.trim()?.ifBlank { null }
                school.officialHeaders = request.officialHeaders.map { AcademicMapper.toOfficialHeader(it) }
                school.contact = AcademicMapper.toContact(request.contact)
                school.postalBox = request.postalBox?.trim()?.ifBlank { null }
                school.signaturePlace = request.signaturePlace?.trim()?.ifBlank { null }
                school.headTitle = AcademicMapper.toLocalized(request.headTitle)
                school.headOfSchoolStaffId = request.headOfSchoolStaffId?.trim()?.ifBlank { null }
                school.logoFileId = request.logoFileId?.trim()?.ifBlank { null }
                school.reportCardNotice = AcademicMapper.toLocalized(request.reportCardNotice)
                school.reportCardDisclaimer = AcademicMapper.toLocalized(request.reportCardDisclaimer)
                school.timezone = request.timezone.trim().ifBlank { "Africa/Douala" }
                school.active = request.active
                schoolRepository.save(school)
            }
            .map { AcademicMapper.toSchoolResponse(it) }
    }

    fun getById(currentUser: CurrentUser, id: String): Mono<SchoolResponse> {
        assertSchoolScope(currentUser, id)
        return schoolRepository.findById(id)
            .switchIfEmpty(Mono.error(NotFoundException("SCHOOL_NOT_FOUND", "School not found")))
            .map { AcademicMapper.toSchoolResponse(it) }
    }

    fun list(currentUser: CurrentUser): Flux<SchoolResponse> {
        if (!currentUser.isSuperAdmin()) {
            val schoolId = currentUser.schoolId
                ?: throw ForbiddenException("FORBIDDEN", "Missing school context")
            return schoolRepository.findById(schoolId).map { AcademicMapper.toSchoolResponse(it) }.flux()
        }
        return schoolRepository.findAll().map { AcademicMapper.toSchoolResponse(it) }
    }

    private fun assertSchoolScope(currentUser: CurrentUser, schoolId: String) {
        if (currentUser.isSuperAdmin()) return
        if (currentUser.schoolId != schoolId) {
            throw ForbiddenException("FORBIDDEN", "Cross-school access denied")
        }
        if (!currentUser.hasAnyRole(Role.ADMINISTRATION, Role.DIRECTOR, Role.TEACHER, Role.SUPER_ADMIN)) {
            throw ForbiddenException("FORBIDDEN", "Insufficient role")
        }
    }
}
