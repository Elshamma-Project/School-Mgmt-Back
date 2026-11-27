package com.kobe.beesm.service.fees.api

import com.kobe.beesm.dto.fees.FeesAccessGateDto
import com.kobe.beesm.dto.fees.SolvencySnapshotDto
import com.kobe.beesm.shared.security.CurrentUser
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Interface publique du module `fees` (docs/08, docs/10).
 * Les autres modules (liste élèves, portal, grading) consomment uniquement ceci.
 */
interface FeesApi {
    fun solvencySnapshot(
        schoolId: String,
        academicYearId: String,
        studentIds: Collection<String>
    ): Flux<SolvencySnapshotDto>

    /** BR-F05 / Q-65 : état de blocage pour un élève / année. */
    fun accessGate(
        schoolId: String,
        academicYearId: String,
        studentId: String
    ): Mono<FeesAccessGateDto>

    /**
     * Interdit la **modification** des notes / données académiques si frais non soldés
     * (y compris Direction — consultation seule).
     */
    fun assertCanModifyAcademics(
        schoolId: String,
        academicYearId: String,
        studentId: String
    ): Mono<Void>

    /**
     * Interdit la **consultation** pour parent / élève / enseignant si frais non soldés.
     * Direction / Admin / SG / Super-admin : autorisés en lecture.
     */
    fun assertCanViewAcademics(
        currentUser: CurrentUser,
        schoolId: String,
        academicYearId: String,
        studentId: String
    ): Mono<Void>
}
