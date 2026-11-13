package com.kobe.smels.service.grading.api

import reactor.core.publisher.Mono

/** Agrégats corrections post-clôture pour le portail (docs/08, BR-80). */
interface GradeCorrectionApi {
    fun countPending(schoolId: String): Mono<Long>
}
