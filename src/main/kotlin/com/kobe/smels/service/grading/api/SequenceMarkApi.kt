package com.kobe.smels.service.grading.api

import com.kobe.smels.dto.grading.SequenceMarkSummaryDto
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/** Interface publique des notes de séquence (docs/04, docs/08) pour reporting. */
interface SequenceMarkApi {
    fun findByClassSubjectAndSession(
        schoolId: String,
        classSubjectId: String,
        sessionId: String
    ): Flux<SequenceMarkSummaryDto>

    fun findByClassSubject(
        schoolId: String,
        classSubjectId: String
    ): Flux<SequenceMarkSummaryDto>

    fun findByStudentAndSession(
        schoolId: String,
        studentId: String,
        academicYearId: String,
        sessionId: String
    ): Flux<SequenceMarkSummaryDto>

    fun getBySheetAndStudent(schoolId: String, sheetId: String, studentId: String): Mono<SequenceMarkSummaryDto>
}
