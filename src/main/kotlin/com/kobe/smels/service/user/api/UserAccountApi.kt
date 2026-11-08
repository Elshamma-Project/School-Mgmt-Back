package com.kobe.smels.service.user.api

import reactor.core.publisher.Mono
import java.time.Instant

/**
 * Interface publique du module user (docs/08).
 * Les autres modules / contrôleurs appellent cette API, jamais le repository user.
 */
interface UserAccountApi {
    /**
     * Dernière connexion des comptes liés à des fiches Staff (`profileType=STAFF`).
     * Clé = `profileId` (staffId). Absent si aucun compte lié.
     */
    fun findStaffLastLogins(schoolId: String, staffIds: Collection<String>): Mono<Map<String, Instant>>
}
