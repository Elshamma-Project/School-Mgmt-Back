package com.kobe.smels.service.student.api

import com.kobe.smels.dto.student.EnrollmentSummaryDto
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Interface publique des inscriptions (docs/06, docs/08).
 * grading / reporting passent par ici pour vérifier ENROLLED (BR-15).
 */
interface EnrollmentApi {
    fun getSummary(schoolId: String, enrollmentId: String): Mono<EnrollmentSummaryDto>
    fun findByStudentAndYear(
        schoolId: String,
        studentId: String,
        academicYearId: String
    ): Mono<EnrollmentSummaryDto>

    /** Toutes les inscriptions d'un élève (parcours multi-années). */
    fun findByStudent(schoolId: String, studentId: String): Flux<EnrollmentSummaryDto>

    fun findEnrolledByClass(schoolId: String, classId: String): Flux<EnrollmentSummaryDto>
    fun isEnrolled(
        schoolId: String,
        studentId: String,
        classId: String,
        academicYearId: String
    ): Mono<Boolean>
}
