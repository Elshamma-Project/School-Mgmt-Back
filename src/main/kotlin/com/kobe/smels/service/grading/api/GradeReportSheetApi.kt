package com.kobe.smels.service.grading.api

import com.kobe.smels.dto.grading.ReportSheetStatusCountsDto
import reactor.core.publisher.Mono

/** Agrégats fiches de report pour le portail (docs/08). */
interface GradeReportSheetApi {
    fun countByStatusForTerm(
        schoolId: String,
        academicYearId: String,
        termNumber: Int
    ): Mono<ReportSheetStatusCountsDto>
}
