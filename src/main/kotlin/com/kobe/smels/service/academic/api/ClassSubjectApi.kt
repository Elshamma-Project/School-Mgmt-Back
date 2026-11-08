package com.kobe.smels.service.academic.api

import com.kobe.smels.dto.academic.ClassSubjectSummaryDto
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/** Interface publique ClassSubject (docs/06, docs/08) — pivot notes / EDT. */
interface ClassSubjectApi {
    fun getSummary(schoolId: String, classSubjectId: String): Mono<ClassSubjectSummaryDto>
    fun findByClass(schoolId: String, classId: String): Flux<ClassSubjectSummaryDto>
    fun findByTeacher(schoolId: String, teacherId: String, academicYearId: String): Flux<ClassSubjectSummaryDto>
    fun findActiveByYear(schoolId: String, academicYearId: String): Flux<ClassSubjectSummaryDto>
}
