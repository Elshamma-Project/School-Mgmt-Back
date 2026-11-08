package com.kobe.smels.service.grading.api

import com.kobe.smels.dto.grading.EvaluationSessionSummaryDto
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/** Lecture des séquences pour reporting (BR-24). */
interface EvaluationSessionApi {
    fun findByYear(schoolId: String, academicYearId: String): Flux<EvaluationSessionSummaryDto>
    fun areRelevantSessionsClosed(
        schoolId: String,
        academicYearId: String,
        sessionNumbers: List<Int>
    ): Mono<Boolean>
}
