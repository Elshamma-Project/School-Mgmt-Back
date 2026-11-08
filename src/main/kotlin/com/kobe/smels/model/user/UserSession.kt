package com.kobe.smels.model.user

import com.kobe.smels.model.shared.AuditableDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * Session de connexion active (module `user`).
 *
 * Règle métier : au plus [MAX_CONCURRENT_SESSIONS] sessions ACTIVE par compte
 * (même appareil ou appareils différents). Au-delà, la plus ancienne est révoquée
 * lors d'une nouvelle connexion réussie.
 *
 * Le jeton de renouvellement n'est jamais stocké en clair : seule [refreshTokenHash] est persistée.
 * Le jeton d'accès JWT porte `sid` = [id] ; une session révoquée invalide immédiatement l'accès.
 */
@Document(collection = "user_sessions")
@CompoundIndexes(
    CompoundIndex(name = "idx_account_status_seen", def = "{'accountId': 1, 'status': 1, 'lastSeenAt': 1}"),
    CompoundIndex(name = "uk_refresh_hash", def = "{'refreshTokenHash': 1}", unique = true)
)
data class UserSession(
    @Id
    var id: String? = null,
    var accountId: String,
    /** Copie figée pour audit / filtrage ; null pour SUPER_ADMIN. */
    var schoolId: String? = null,
    @Indexed
    var refreshTokenHash: String,
    var status: SessionStatus = SessionStatus.ACTIVE,
    var userAgent: String? = null,
    var ipAddress: String? = null,
    var deviceLabel: String? = null,
    var createdAtSession: Instant = Instant.now(),
    var lastSeenAt: Instant = Instant.now(),
    var expiresAt: Instant,
    var revokedAt: Instant? = null,
    var revokedReason: String? = null
) : AuditableDocument() {
    companion object {
        const val MAX_CONCURRENT_SESSIONS: Int = 2
    }
}

enum class SessionStatus { ACTIVE, REVOKED, EXPIRED }
