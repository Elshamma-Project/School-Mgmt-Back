package com.kobe.smels.service.user.api

import com.kobe.smels.dto.user.CreateUserAccountRequest
import com.kobe.smels.dto.user.StaffLinkedAccountDto
import com.kobe.smels.dto.user.UserAccountResponse
import com.kobe.smels.model.user.Role
import com.kobe.smels.shared.security.CurrentUser
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * Interface publique du module user (docs/08).
 * Les autres modules / contrôleurs appellent cette API, jamais le repository user.
 */
interface UserAccountApi {
    /**
     * Crée un compte (mêmes contrôles que POST /api/v1/users).
     * Utilisé par l’orchestration staff → UserAccount.
     */
    fun createAccount(currentUser: CurrentUser, request: CreateUserAccountRequest): Mono<UserAccountResponse>

    /**
     * Comptes liés à des fiches Staff (`profileType=STAFF`).
     * Clé = `profileId` (staffId). Absent si aucun compte lié.
     */
    fun findStaffLinkedAccounts(
        schoolId: String,
        staffIds: Collection<String>
    ): Mono<Map<String, StaffLinkedAccountDto>>

    /**
     * Dernière connexion des comptes liés à des fiches Staff.
     * Clé = `profileId` (staffId). Absent si aucun compte lié ou jamais connecté.
     */
    fun findStaffLastLogins(schoolId: String, staffIds: Collection<String>): Mono<Map<String, Instant>>

    /**
     * Identifiants de comptes ACTIVE d’un établissement ayant au moins un des rôles donnés.
     * Utilisé par le module notification (destinataires administration / direction).
     */
    fun findActiveAccountIdsByRoles(schoolId: String, roles: Collection<Role>): Flux<String>
}
