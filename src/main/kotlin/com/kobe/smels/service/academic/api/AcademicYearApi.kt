package com.kobe.smels.service.academic.api

import com.kobe.smels.dto.academic.AcademicYearSummaryDto
import reactor.core.publisher.Mono

interface AcademicYearApi {
    fun getSummary(schoolId: String, academicYearId: String): Mono<AcademicYearSummaryDto>
    fun findCurrent(schoolId: String): Mono<AcademicYearSummaryDto>
}
