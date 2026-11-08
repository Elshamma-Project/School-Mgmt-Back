package com.kobe.smels.service.staff.api

import com.kobe.smels.dto.staff.StaffSummaryDto
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Interface publique du module staff (docs/08).
 * Les autres modules appellent uniquement cette API, jamais le repository staff.
 */
interface StaffApi {
    fun getSummary(schoolId: String, staffId: String): Mono<StaffSummaryDto>
    fun findActiveBySchool(schoolId: String): Flux<StaffSummaryDto>
    fun findActiveTeachers(schoolId: String): Flux<StaffSummaryDto>
}
