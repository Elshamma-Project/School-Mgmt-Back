package com.kobe.beesm.service.student.api

import com.kobe.beesm.dto.student.GuardianLinkDto
import com.kobe.beesm.dto.student.StudentGuardiansDto
import com.kobe.beesm.dto.student.StudentIdentityDto
import com.kobe.beesm.dto.student.StudentSummaryDto
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

    /** Tuteurs liés pour un lot d’élèves (fees rappels). */
    fun findGuardiansForStudents(
        schoolId: String,
        studentIds: Collection<String>
    ): Flux<StudentGuardiansDto>

    /**
     * Désactivation système pour exclusion scolarité (BR-F14) — sans jeton utilisateur.
     * Pose `inactiveReasonCode` ; émet `student.changed`.
     */
    fun deactivateForFeeExclusion(
        schoolId: String,
        studentId: String,
        reasonCode: String
    ): Mono<StudentSummaryDto>

    /**
     * Réactivation après exclusion scolarité (Q-80) — appelée uniquement par le module `fees`
     * une fois tranches + pénalités soldées. Efface `inactiveReasonCode`.
     */
    fun reactivateAfterFeeExclusion(
        schoolId: String,
        studentId: String
    ): Mono<StudentSummaryDto>
}
