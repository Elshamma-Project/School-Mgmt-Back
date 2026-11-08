package com.kobe.smels.service.grading.api

import com.kobe.smels.dto.grading.GradingSchemeCalcDto
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/** Lecture barème pour reporting (calculs bulletin) — pas d'accès repo grading hors module. */
interface GradingSchemeApi {
    fun getForCalculation(schoolId: String, schemeId: String): Mono<GradingSchemeCalcDto>
    fun findActiveForCalculation(schoolId: String): Flux<GradingSchemeCalcDto>
}
