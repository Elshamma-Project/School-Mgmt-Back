package com.kobe.smels.model.shared

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * Événement métier à publier vers les autres modules (« boîte d'envoi transactionnelle »).
 *
 * Pourquoi : aujourd'hui les modules sont dans un même monolithe, demain chacun sera un microservice avec sa
 * propre base. Un module ne lit JAMAIS la base d'un autre ; il garde des identifiants et, au besoin, une copie
 * minimale des données dont il a besoin, mise à jour par ces événements.
 *
 * Fonctionnement : le service écrit l'événement dans cette collection DANS LA MÊME TRANSACTION que la
 * modification métier ; un publieur (à écrire plus tard) l'envoie ensuite (appel interne aujourd'hui, courtier
 * de messages demain) puis passe `status` à PUBLISHED. Catalogue des événements : docs/08-architecture-modulaire.md.
 *
 * Chaque module aura sa propre collection d'événements une fois séparé ; `module` identifie l'émetteur.
 */
@Document(collection = "shared_outbox_events")
@CompoundIndexes(
    CompoundIndex(name = "idx_status_time", def = "{'status': 1, 'occurredAt': 1}"),
    CompoundIndex(name = "idx_aggregate", def = "{'schoolId': 1, 'module': 1, 'aggregateType': 1, 'aggregateId': 1}")
)
data class OutboxEvent(
    @Id
    var id: String? = null,
    override var schoolId: String,
    /** Module émetteur : "academic", "student", "grading"... */
    var module: String,
    /** Type d'objet concerné : "ClassSubject", "Enrollment", "EvaluationSession"... */
    var aggregateType: String,
    var aggregateId: String,
    /** Nom de l'événement, ex. "student.enrolled", "grading.session.closed". */
    var eventType: String,
    /** Version du format de la charge utile, pour évoluer sans casser les consommateurs. */
    var schemaVersion: Int = 1,
    /** Charge utile minimale : identifiants et valeurs utiles (pas de document complet, pas de données inutiles sur les mineurs). */
    var payload: Map<String, Any?> = emptyMap(),
    var occurredAt: Instant,
    var status: OutboxStatus = OutboxStatus.PENDING,
    var attempts: Int = 0,
    var publishedAt: Instant? = null,
    var lastError: String? = null
) : TenantDocument()

enum class OutboxStatus { PENDING, PUBLISHED, FAILED }
