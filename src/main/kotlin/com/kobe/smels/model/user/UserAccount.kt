package com.kobe.smels.model.user

import com.kobe.smels.model.shared.AuditableDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * Compte de connexion (module `user`).
 *
 * Séparé des fiches (Staff, Student, Guardian) : une fiche peut exister sans compte,
 * et un compte pointe vers sa fiche via [profileType] + [profileId].
 *
 * [schoolId] est null pour un SUPER_ADMIN (compte plateforme).
 * [username] : **e-mail de connexion** (normalisé en minuscules). Exception éventuelle élève :
 * matricule si le portail élève l'exige encore (docs/05) — préférer l'e-mail dès qu'il est disponible.
 *
 * Voir docs/05-roles-permissions.md.
 */
@Document(collection = "user_accounts")
@CompoundIndexes(
    CompoundIndex(name = "uk_school_username", def = "{'schoolId': 1, 'username': 1}", unique = true),
    CompoundIndex(name = "idx_profile", def = "{'profileType': 1, 'profileId': 1}")
)
data class UserAccount(
    @Id
    var id: String? = null,
    var schoolId: String? = null,
    var username: String,

    /** Empreinte du mot de passe (jamais le mot de passe en clair). Null tant que le compte n'est pas activé. */
    var passwordHash: String? = null,
    var roles: Set<Role> = emptySet(),
    var profileType: ProfileType = ProfileType.NONE,
    var profileId: String? = null,
    var status: AccountStatus = AccountStatus.PENDING,
    var mustChangePassword: Boolean = true,
    var failedLoginCount: Int = 0,
    var lockedUntil: Instant? = null,
    var lastLoginAt: Instant? = null
) : AuditableDocument()

enum class AccountStatus { PENDING, ACTIVE, LOCKED, DISABLED }
