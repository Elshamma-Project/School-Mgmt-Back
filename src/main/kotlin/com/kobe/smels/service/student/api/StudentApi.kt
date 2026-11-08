package com.kobe.smels.service.student.api

import com.kobe.smels.dto.student.GuardianLinkDto
import com.kobe.smels.dto.student.StudentSummaryDto
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Interface publique du module student (docs/08).
 * Les autres modules appellent uniquement cette API, jamais le repository student.
 */
interface StudentApi {
    fun getSummary(schoolId: String, studentId: String): Mono<StudentSummaryDto>
    fun findActiveBySchool(schoolId: String): Flux<StudentSummaryDto>
    fun findByGuardian(schoolId: String, guardianId: String): Flux<StudentSummaryDto>
    fun findGuardianLink(schoolId: String, studentId: String, guardianId: String): Mono<GuardianLinkDto>
}
