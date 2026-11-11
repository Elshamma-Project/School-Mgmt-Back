package com.kobe.smels.service.academic.api

import com.kobe.smels.dto.academic.AcademicYearSummaryDto
import reactor.core.publisher.Mono

interface AcademicYearApi {
    fun getSummary(schoolId: String, academicYearId: String): Mono<AcademicYearSummaryDto>
    fun findCurrent(schoolId: String): Mono<AcademicYearSummaryDto>

    /**
     * Refuse toute écriture si l’année est CLOSE/ARCHIVED ou si today >= endDate (timezone école).
     * Code d’erreur : YEAR_LOCKED.
     */
    fun assertWritable(schoolId: String, academicYearId: String): Mono<AcademicYearSummaryDto>
}
