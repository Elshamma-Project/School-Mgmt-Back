package com.kobe.beesm.service.academic.api

import com.kobe.beesm.dto.academic.SubjectGroupSummaryDto
import reactor.core.publisher.Mono

interface SubjectGroupApi {
    fun getSummary(schoolId: String, groupId: String): Mono<SubjectGroupSummaryDto>
}
