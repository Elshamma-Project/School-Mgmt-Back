package com.kobe.smels.service.academic.api

import com.kobe.smels.dto.academic.SubjectGroupSummaryDto
import reactor.core.publisher.Mono

interface SubjectGroupApi {
    fun getSummary(schoolId: String, groupId: String): Mono<SubjectGroupSummaryDto>
}
