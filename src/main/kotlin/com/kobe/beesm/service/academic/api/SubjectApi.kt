package com.kobe.beesm.service.academic.api

import com.kobe.beesm.dto.academic.SubjectSummaryDto
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface SubjectApi {
    fun getSummary(schoolId: String, subjectId: String): Mono<SubjectSummaryDto>
    fun findActiveBySchool(schoolId: String): Flux<SubjectSummaryDto>
}
