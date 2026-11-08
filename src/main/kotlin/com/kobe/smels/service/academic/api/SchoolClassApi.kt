package com.kobe.smels.service.academic.api

import com.kobe.smels.dto.academic.SchoolClassSummaryDto
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface SchoolClassApi {
    fun getSummary(schoolId: String, classId: String): Mono<SchoolClassSummaryDto>
    fun findActiveByYear(schoolId: String, academicYearId: String): Flux<SchoolClassSummaryDto>
}
