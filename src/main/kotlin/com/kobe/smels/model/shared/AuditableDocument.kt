package com.kobe.smels.model.shared

import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.annotation.Version
import java.time.Instant

/**
 * Base de tous les documents MongoDB : audit technique + verrouillage optimiste.
 *
 * Les champs sont dans le corps de la classe (et non dans le constructeur) pour que
 * les annotations Spring Data s'appliquent bien au champ.
 *
 * RÈGLE : ne jamais utiliser `copy()` sur un document persistant. `copy()` ne recopie pas
 * ces champs hérités (dont `version`) et ferait traiter l'objet comme un nouveau document.
 * On modifie l'instance chargée (`var`) puis on appelle `repository.save(...)`.
 *
 * Activé via `@EnableReactiveMongoAuditing` + `ReactiveAuditorAware<String>`
 * (`MongoConfig`) : `createdBy` / `updatedBy` = `UserAccount.id` de l’acteur.
 */
abstract class AuditableDocument {
    @CreatedDate
    var createdAt: Instant? = null

    @LastModifiedDate
    var updatedAt: Instant? = null

    /** Identifiant de l'utilisateur (UserAccount.id) qui a créé le document. */
    @CreatedBy
    var createdBy: String? = null

    /** Identifiant de l'utilisateur (UserAccount.id) qui a modifié le document en dernier. */
    @LastModifiedBy
    var updatedBy: String? = null

    /** Verrouillage optimiste : évite que deux saisies simultanées s'écrasent. */
    @field:Version
    var version: Long? = null
}

/**
 * Document rattaché à un établissement (multi-tenant dès le départ).
 * Toute requête et tout index composite commencent par `schoolId`.
 */
abstract class TenantDocument : AuditableDocument() {
    abstract var schoolId: String
}
