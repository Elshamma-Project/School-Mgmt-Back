package com.kobe.smels.service.timetable.api

import com.kobe.smels.dto.timetable.TimetableSummaryDto
import reactor.core.publisher.Mono

/** Frontière lecture pour portal / notification (docs/08). */
interface TimetableApi {
    fun getSummary(schoolId: String, timetableId: String): Mono<TimetableSummaryDto>

    fun findPublished(schoolId: String, academicYearId: String): Mono<TimetableSummaryDto>
}
