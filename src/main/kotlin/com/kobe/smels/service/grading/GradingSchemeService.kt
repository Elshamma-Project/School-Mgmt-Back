package com.kobe.smels.service.grading

import com.kobe.smels.dto.grading.AppreciationScaleWriteDto
import com.kobe.smels.dto.grading.CreateGradingSchemeRequest
import com.kobe.smels.dto.grading.GradingSchemeCalcDto
import com.kobe.smels.dto.grading.GradingSchemeResponse
import com.kobe.smels.dto.grading.ScoreBandWriteDto
import com.kobe.smels.dto.grading.UpdateGradingSchemeRequest
import com.kobe.smels.exception.BusinessRuleException
import com.kobe.smels.exception.ConflictException
import com.kobe.smels.exception.ForbiddenException
import com.kobe.smels.exception.NotFoundException
import com.kobe.smels.model.grading.AppreciationScale
import com.kobe.smels.model.grading.GradingScheme
import com.kobe.smels.model.grading.ScoreBand
import com.kobe.smels.model.user.Role
import com.kobe.smels.repository.grading.GradingSchemeRepository
import com.kobe.smels.service.grading.api.GradingSchemeApi
import com.kobe.smels.shared.security.CurrentUser
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal

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

    fun update(
        currentUser: CurrentUser,
        id: String,
        request: UpdateGradingSchemeRequest
    ): Mono<GradingSchemeResponse> {
        GradingAccess.assertDirector(currentUser)
        val schoolId = GradingAccess.resolveSchoolId(currentUser, request.schoolId)
        return gradingSchemeRepository.findByIdAndSchoolId(id, schoolId)
            .switchIfEmpty(Mono.error(NotFoundException("SCHEME_NOT_FOUND", "Grading scheme not found")))
            .flatMap { scheme ->
                request.name?.let { scheme.name = GradingMapper.toLocalized(it) }
                request.maxScore?.let { scheme.maxScore = requirePositive(it, "maxScore") }
                request.passMark?.let { scheme.passMark = requireNonNegative(it, "passMark") }
                request.classSuccessThreshold?.let {
                    scheme.classSuccessThreshold = requireNonNegative(it, "classSuccessThreshold")
                }
                request.decimals?.let { scheme.decimals = it }
                request.honourRollMinAverage?.let {
                    scheme.honourRollMinAverage = requireNonNegative(it, "honourRollMinAverage")
                }
                request.honourRollBlockedBySanction?.let { scheme.honourRollBlockedBySanction = it }
                if (request.clearEffortSubjectThreshold) {
                    scheme.effortSubjectThreshold = null
                } else {
                    request.effortSubjectThreshold?.let {
                        scheme.effortSubjectThreshold = requireNonNegative(it, "effortSubjectThreshold")
                    }
                }
                request.reportSheetDeadlineHours?.let { scheme.reportSheetDeadlineHours = it }
                request.reportSheetDeadlineBlocking?.let { scheme.reportSheetDeadlineBlocking = it }
                request.reportSheetMaxRowsPerPage?.let { scheme.reportSheetMaxRowsPerPage = it }
                request.appreciationScales?.let { scales ->
                    scheme.appreciationScales = scales.map { toAppreciationScale(it) }
                }
                request.overallMentionBands?.let { bands ->
                    scheme.overallMentionBands = bands.map { toScoreBand(it) }
                }
                request.reportSheetAppreciationScaleCode?.trim()?.takeIf { it.isNotEmpty() }?.let { code ->
                    if (scheme.appreciationScales.isNotEmpty() &&
                        scheme.appreciationScales.none { it.code.equals(code, ignoreCase = true) }
                    ) {
                        return@flatMap Mono.error(
                            BusinessRuleException(
                                "SCALE_CODE_UNKNOWN",
                                "Appreciation scale code is not defined on this scheme"
                            )
                        )
                    }
                    scheme.reportSheetAppreciationScaleCode = code.uppercase()
                }
                request.active?.let { scheme.active = it }
                if (scheme.passMark > scheme.maxScore) {
                    return@flatMap Mono.error(
                        BusinessRuleException("PASS_MARK_INVALID", "passMark cannot exceed maxScore")
                    )
                }
                gradingSchemeRepository.save(scheme)
            }
            .map { GradingMapper.toSchemeResponse(it) }
    }

    private fun toAppreciationScale(dto: AppreciationScaleWriteDto): AppreciationScale {
        val code = dto.code.trim().uppercase()
        if (code.isBlank()) {
            throw BusinessRuleException("SCALE_CODE_REQUIRED", "Appreciation scale code is required")
        }
        if (dto.bands.isEmpty()) {
            throw BusinessRuleException("SCALE_BANDS_REQUIRED", "Each appreciation scale needs at least one band")
        }
        return AppreciationScale(
            code = code,
            bands = dto.bands.map { toScoreBand(it) }
        )
    }

    private fun toScoreBand(dto: ScoreBandWriteDto): ScoreBand {
        val code = dto.code.trim().uppercase()
        if (code.isBlank()) {
            throw BusinessRuleException("BAND_CODE_REQUIRED", "Score band code is required")
        }
        val min = requireNonNegative(dto.minInclusive, "minInclusive")
        val max = dto.maxExclusive?.let { requireNonNegative(it, "maxExclusive") }
        if (max != null && max <= min) {
            throw BusinessRuleException(
                "BAND_RANGE_INVALID",
                "maxExclusive must be greater than minInclusive for band $code"
            )
        }
        return ScoreBand(
            code = code,
            label = dto.label?.let { GradingMapper.toLocalized(it) },
            minInclusive = min,
            maxExclusive = max
        )
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

    private fun requirePositive(value: BigDecimal, field: String): BigDecimal {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessRuleException("INVALID_${field.uppercase()}", "$field must be > 0")
        }
        return value
    }

    private fun requireNonNegative(value: BigDecimal, field: String): BigDecimal {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw BusinessRuleException("INVALID_${field.uppercase()}", "$field must be >= 0")
        }
        return value
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
