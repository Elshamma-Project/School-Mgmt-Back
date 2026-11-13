package com.kobe.smels.service.student.api

import com.kobe.smels.dto.student.GuardianLinkDto
import com.kobe.smels.dto.student.StudentIdentityDto
import com.kobe.smels.dto.student.StudentSummaryDto
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Interface publique du module student (docs/08).
 * Les autres modules appellent uniquement cette API, jamais le repository student.
 */
interface StudentApi {
    fun getSummary(schoolId: String, studentId: String): Mono<StudentSummaryDto>
    fun getIdentity(schoolId: String, studentId: String): Mono<StudentIdentityDto>
    /**
     * Octets de la photo scolaire (bulletin). [Mono.empty] si absente.
     * Jamais l’avatar compte.
     */
    fun findSchoolPhotoBytes(schoolId: String, studentId: String): Mono<ByteArray>
    fun findActiveBySchool(schoolId: String): Flux<StudentSummaryDto>
    fun findByGuardian(schoolId: String, guardianId: String): Flux<StudentSummaryDto>
    fun findGuardianLink(schoolId: String, studentId: String, guardianId: String): Mono<GuardianLinkDto>
    /** Compteur élèves actifs (tableau de bord). */
    fun countActive(schoolId: String): Mono<Long>
}
