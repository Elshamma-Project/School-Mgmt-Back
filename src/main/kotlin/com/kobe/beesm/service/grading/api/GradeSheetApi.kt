package com.kobe.beesm.service.grading.api

import com.kobe.beesm.dto.grading.EntryProgressDto
import reactor.core.publisher.Mono

/** Agrégats fiches personnelles pour le portail (docs/08). */
interface GradeSheetApi {
    /**
     * Progression de saisie sur les séquences ouvertes.
     * [expectedPerSession] = nombre de matières de classe actives attendues par séquence.
     */
    fun entryProgressForSessions(
        schoolId: String,
        openSessionIds: List<String>,
        expectedPerSession: Long
    ): Mono<EntryProgressDto>
}
