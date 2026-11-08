package com.kobe.smels.service.grading

import com.kobe.smels.dto.grading.CreateGradingSchemeRequest
import com.kobe.smels.dto.grading.GradingSchemeCalcDto
import com.kobe.smels.dto.grading.GradingSchemeResponse
import com.kobe.smels.exception.ConflictException
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.grading.GradingScheme
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.grading.GradingSchemeRepository
import com.kobe.smels.service.grading.api.GradingSchemeApi
import com.kobe.smels.shared.security.CurrentUser
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class GradingSchemeService(
    private val gradingSchemeRepository: GradingSchemeRepository
) : GradingSchemeApi {
    fun create(currentUser: CurrentUser, request: CreateGradingSchemeRequest): Mono<GradingSchemeResponse> {
        GradingAccess.assertDirector(currentUser)
        val schoolId = GradingAccess.resolveSchoolId(currentUser, request.schoolId)
        val code = request.code.trim().uppercase()
        return gradingSchemeRepository.findBySchoolIdAndCode(schoolId, code)
            .flatMap<GradingScheme> {
                Mono.error(ConflictException("SCHEME_CODE_TAKEN", "Grading scheme code already exists"))
            }
            .switchIfEmpty(
                Mono.defer {
                    gradingSchemeRepository.save(
                        GradingScheme(
                            schoolId = schoolId,
                            code = code,
                            name = GradingMapper.toLocalized(request.name),
                            active = true
                        )
                    )
                }
            )
            .map { GradingMapper.toSchemeResponse(it) }
    }

    fun list(currentUser: CurrentUser, schoolIdOverride: String? = null): Flux<GradingSchemeResponse> {
        assertCanReadScheme(currentUser)
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return gradingSchemeRepository.findBySchoolId(schoolId).map { GradingMapper.toSchemeResponse(it) }
    }

    fun getById(currentUser: CurrentUser, id: String, schoolIdOverride: String? = null): Mono<GradingSchemeResponse> {
        assertCanReadScheme(currentUser)
        val schoolId = GradingAccess.resolveSchoolId(currentUser, schoolIdOverride)
        return gradingSchemeRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SCHEME_NOT_FOUND", "Grading scheme not found")))
            .map { GradingMapper.toSchemeResponse(it) }
    }

    private fun assertCanReadScheme(currentUser: CurrentUser) {
        if (!currentUser.hasAnyRole(
                Role.ADMINISTRATION,
                Role.DIRECTOR,
                Role.SUPER_ADMIN,
                Role.TEACHER
            )
        ) {
            throw ForbiddenException("FORBIDDEN", "Insufficient role to read grading schemes")
        }
    }

    override fun getForCalculation(schoolId: String, schemeId: String): Mono<GradingSchemeCalcDto> =
        gradingSchemeRepository.findByIdAndSchoolId(schemeId, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SCHEME_NOT_FOUND", "Grading scheme not found")))
            .map { GradingMapper.toSchemeCalc(it) }

    override fun findActiveForCalculation(schoolId: String): Flux<GradingSchemeCalcDto> =
        gradingSchemeRepository.findBySchoolIdAndActiveTrue(schoolId).map { GradingMapper.toSchemeCalc(it) }
}
