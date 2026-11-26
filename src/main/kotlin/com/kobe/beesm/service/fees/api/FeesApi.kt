package com.kobe.beesm.service.fees.api

import com.kobe.beesm.dto.fees.SolvencySnapshotDto
import reactor.core.publisher.Flux

/**
 * Interface publique du module `fees` (docs/08, docs/10).
 * Les autres modules (liste élèves, portal) consomment uniquement ceci.
 */
interface FeesApi {
    fun solvencySnapshot(
        schoolId: String,
        academicYearId: String,
        studentIds: Collection<String>
    ): Flux<SolvencySnapshotDto>
}
