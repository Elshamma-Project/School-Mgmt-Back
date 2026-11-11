package com.kobe.smels.service.academic

import com.kobe.smels.dto.academic.CreateSchoolRequest
import com.kobe.smels.dto.academic.SchoolResponse
import com.kobe.smels.dto.academic.UpdateSchoolRequest
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.exception.ConflictException
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.academic.School
import com.kobe.smels.model.academic.SchoolLogo
import com.kobe.smels.repository.academic.SchoolLogoRepository
import com.kobe.smels.repository.academic.SchoolRepository
import com.kobe.smels.shared.security.CurrentUser
import org.bson.types.Binary
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class SchoolService(
    private val schoolRepository: SchoolRepository,
    private val schoolLogoRepository: SchoolLogoRepository
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
        AcademicAccess.assertCanManageSchoolSettings(currentUser)
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
                // Ne pas écraser le logo via PUT JSON (upload dédié).
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

    fun uploadLogo(currentUser: CurrentUser, schoolId: String, file: FilePart): Mono<SchoolResponse> {
        AcademicAccess.assertCanManageSchoolSettings(currentUser)
        assertSchoolScope(currentUser, schoolId)
        val contentType = file.headers().contentType?.toString()?.substringBefore(";")?.trim()?.lowercase()
            ?: ""
        if (contentType !in LOGO_CONTENT_TYPES) {
            return Mono.error(
                BusinessRuleException("LOGO_TYPE", "Logo must be JPEG, PNG, WebP or SVG")
            )
        }
        log.info("school.uploadLogo start by={} schoolId={}", currentUser.username, schoolId)
        return DataBufferUtils.join(file.content())
            .flatMap { buffer ->
                val size = buffer.readableByteCount()
                if (size <= 0) {
                    DataBufferUtils.release(buffer)
                    return@flatMap Mono.error(BusinessRuleException("LOGO_EMPTY", "Logo file is empty"))
                }
                if (size > LOGO_MAX_BYTES) {
                    DataBufferUtils.release(buffer)
                    return@flatMap Mono.error(
                        BusinessRuleException("LOGO_TOO_LARGE", "Logo must be at most 2 MB")
                    )
                }
                val bytes = ByteArray(size)
                buffer.read(bytes)
                DataBufferUtils.release(buffer)
                Mono.just(bytes to contentType)
            }
            .flatMap { (bytes, type) ->
                schoolRepository.findById(schoolId)
                    .switchIfEmpty(Mono.error(NotFoundException("SCHOOL_NOT_FOUND", "School not found")))
                    .flatMap { school ->
                        val previousId = school.logoFileId
                        val logo = SchoolLogo(
                            id = previousId,
                            schoolId = schoolId,
                            contentType = type,
                            sizeBytes = bytes.size.toLong(),
                            data = Binary(bytes)
                        )
                        schoolLogoRepository.save(logo)
                            .flatMap { saved ->
                                school.logoFileId = saved.id
                                schoolRepository.save(school)
                            }
                    }
            }
            .map { AcademicMapper.toSchoolResponse(it) }
            .doOnNext {
                log.info("school.uploadLogo ok by={} schoolId={}", currentUser.username, schoolId)
            }
    }

    fun getLogo(currentUser: CurrentUser, schoolId: String): Mono<SchoolLogo> {
        assertSchoolScope(currentUser, schoolId)
        return schoolRepository.findById(schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SCHOOL_NOT_FOUND", "School not found")))
            .flatMap { school ->
                val logoId = school.logoFileId
                    ?: return@flatMap Mono.error(NotFoundException("LOGO_NOT_FOUND", "No school logo"))
                schoolLogoRepository.findById(logoId)
                    .switchIfEmpty(
                        schoolLogoRepository.findBySchoolId(schoolId)
                            .switchIfEmpty(Mono.error(NotFoundException("LOGO_NOT_FOUND", "No school logo")))
                    )
            }
    }

    fun deleteLogo(currentUser: CurrentUser, schoolId: String): Mono<SchoolResponse> {
        AcademicAccess.assertCanManageSchoolSettings(currentUser)
        assertSchoolScope(currentUser, schoolId)
        log.info("school.deleteLogo start by={} schoolId={}", currentUser.username, schoolId)
        return schoolRepository.findById(schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SCHOOL_NOT_FOUND", "School not found")))
            .flatMap { school ->
                val logoId = school.logoFileId
                school.logoFileId = null
                schoolRepository.save(school)
                    .flatMap { saved ->
                        val cleanup = when {
                            !logoId.isNullOrBlank() -> schoolLogoRepository.deleteById(logoId)
                            else -> schoolLogoRepository.deleteBySchoolId(schoolId)
                        }
                        cleanup.thenReturn(AcademicMapper.toSchoolResponse(saved))
                    }
            }
            .doOnNext {
                log.info("school.deleteLogo ok by={} schoolId={}", currentUser.username, schoolId)
            }
    }

    private fun assertSchoolScope(currentUser: CurrentUser, schoolId: String) {
        if (currentUser.isSuperAdmin()) return
        if (currentUser.schoolId != schoolId) {
            throw ForbiddenException("FORBIDDEN", "Cross-school access denied")
        }
        // Lecture identité / logo : tout compte rattaché à l’école.
    }

    companion object {
        private val LOGO_CONTENT_TYPES = setOf(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/svg+xml"
        )
        private const val LOGO_MAX_BYTES = 2L * 1024 * 1024
    }
}
