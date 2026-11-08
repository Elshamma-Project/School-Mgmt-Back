package com.kobe.smels.model.grading

import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.time.LocalDate

/**
 * Session d'évaluation = une séquence (colonnes DEV1..DEV6 du bulletin), 2 par trimestre.
 *
 * Cycle de vie : PLANNED -> OPEN -> CLOSED (voir docs/02-domaine-metier.md, § 5.3) :
 *  - OPEN   : les enseignants créent et modifient librement les notes de leurs matières ;
 *  - CLOSED : seule l'administration peut corriger une note, avec un motif obligatoire,
 *             et chaque correction est tracée dans [GradeAuditEntry] (afterSessionClosed = true).
 *
 * La clôture est globale (toute l'école) dans cette première version. Une clôture par classe pourra être
 * ajoutée plus tard via un document séparé sans changer ce modèle (voir Q-04).
 */
@Document(collection = "grading_sessions")
@CompoundIndexes(
    CompoundIndex(name = "uk_year_number", def = "{'schoolId': 1, 'academicYearId': 1, 'number': 1}", unique = true),
    CompoundIndex(name = "idx_year_status", def = "{'schoolId': 1, 'academicYearId': 1, 'status': 1}")
)
data class EvaluationSession(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,

    /** Numéro de la séquence dans l'année : 1 à 6. */
    var number: Int,

    /** Trimestre auquel la séquence appartient : 1 à 3 (séquences 1-2 -> T1, 3-4 -> T2, 5-6 -> T3). */
    var termNumber: Int,

    /** Libellé imprimé en tête de colonne (« DEV1 »). */
    var shortLabel: String,
    var name: LocalizedText,
    var startDate: LocalDate? = null,
    var endDate: LocalDate? = null,

    /**
     * Date de composition de la séquence (« Date des séquences » sur la fiche de report).
     * TODO(Q-04) : surcharge éventuelle par classe (aujourd'hui globale à la séquence).
     */
    var examDate: LocalDate? = null,

    /** Date limite de saisie des notes par les enseignants. */
    var entryDeadline: LocalDate? = null,
    var status: SessionStatus = SessionStatus.PLANNED,

    var openedAt: Instant? = null,
    var closedAt: Instant? = null,

    /** UserAccount.id de la personne qui a clôturé. */
    var closedBy: String? = null,
    var reopenedAt: Instant? = null,
    var reopenedBy: String? = null,
    var closeNote: String? = null
) : TenantDocument()

enum class SessionStatus { PLANNED, OPEN, CLOSED }
