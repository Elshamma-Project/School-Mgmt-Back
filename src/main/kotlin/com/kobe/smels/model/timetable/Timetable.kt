package com.kobe.smels.model.timetable

import com.kobe.smels.model.shared.ContactInfo
import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * Emploi du temps d'une année scolaire : généré UNE SEULE FOIS par an (réponse de l'école), valable toute
 * l'année, sous forme de semaine type répétée. Il ne s'agit pas d'un calendrier semaine par semaine.
 *
 * Les lignes (une par créneau et par classe / option) sont dans [TimetableEntry]. Les rares
 * modifications en cours d'année créent une nouvelle `revision` (l'ancienne passe en ARCHIVED) ;
 * une seule version PUBLISHED par année à la fois.
 *
 * Impression : une page A4 PAYSAGE par classe (nom complet de classe, ex. « FORM 4 ARTS ») ;
 * [headerContact] = téléphones / e-mail propres au PDF EDT (distincts des bulletins, Q-45).
 */
@Document(collection = "timetable_timetables")
@CompoundIndexes(
    CompoundIndex(name = "uk_year_revision", def = "{'schoolId': 1, 'academicYearId': 1, 'revision': 1}", unique = true),
    CompoundIndex(name = "idx_year_status", def = "{'schoolId': 1, 'academicYearId': 1, 'status': 1}")
)
data class Timetable(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,
    var gridId: String,
    var revision: Int = 1,
    var status: TimetableStatus = TimetableStatus.DRAFT,
    var generatedAt: Instant? = null,
    /** UserAccount.id. */
    var generatedBy: String? = null,
    var publishedAt: Instant? = null,
    var publishedBy: String? = null,

    /**
     * Coordonnées imprimées sur l’en-tête du PDF EDT (peuvent différer de [com.kobe.smels.model.academic.School.contact]).
     * Ne change pas le modèle visuel original — seulement les numéros / e-mail du document (Q-45).
     */
    var headerContact: ContactInfo? = null,

    var note: String? = null
) : TenantDocument()

enum class TimetableStatus { DRAFT, PUBLISHED, ARCHIVED }
