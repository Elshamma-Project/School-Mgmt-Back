package com.kobe.beesm.service.academic.api

import com.kobe.beesm.dto.academic.LevelSummaryDto
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface LevelApi {
    fun getSummary(schoolId: String, levelId: String): Mono<LevelSummaryDto>
    fun findActiveBySchool(schoolId: String): Flux<LevelSummaryDto>
}
