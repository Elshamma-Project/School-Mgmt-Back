package com.kobe.beesm.service.timetable.api

import com.kobe.beesm.dto.timetable.TimetableSummaryDto
import reactor.core.publisher.Mono

/** Frontière lecture pour portal / notification (docs/08). */
interface TimetableApi {
    fun getSummary(schoolId: String, timetableId: String): Mono<TimetableSummaryDto>

    fun findPublished(schoolId: String, academicYearId: String): Mono<TimetableSummaryDto>

    /** true si l’EDT a au moins une case pour cette classe. */
    fun hasEntriesForClass(schoolId: String, timetableId: String, classId: String): Mono<Boolean>
}
