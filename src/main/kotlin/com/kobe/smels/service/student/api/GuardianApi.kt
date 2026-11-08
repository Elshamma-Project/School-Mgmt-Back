package com.kobe.smels.service.student.api

import com.kobe.smels.dto.student.GuardianSummaryDto
import reactor.core.publisher.Mono

/** Interface publique tuteurs (docs/08). */
interface GuardianApi {
    fun getSummary(schoolId: String, guardianId: String): Mono<GuardianSummaryDto>
}
