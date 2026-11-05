package com.kobe.smels.model.academic

import com.kobe.smels.model.shared.Cycle
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.Section
import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Niveau d'études (donnée de référence, ex. « 6ème », « Form 3 », « Tle », « CM2 », « Nursery 2 »).
 * Un niveau appartient à une section (francophone ou anglophone). La liste initiale à charger
 * est dans docs/03-modele-de-donnees.md (§ 5 Données de référence).
 */
@Document(collection = "academic_levels")
@CompoundIndexes(
    CompoundIndex(name = "uk_school_code", def = "{'schoolId': 1, 'code': 1}", unique = true)
)
data class Level(
    @Id
    var id: String? = null,
    override var schoolId: String,

    /** Code stable, ex. "6EME", "FORM3", "TLE", "CM2". */
    var code: String,

    /** Nom affiché. En pratique seule la langue de la section est renseignée. */
    var name: LocalizedText,
    var cycle: Cycle,
    var section: Section,

    /** Ordre de progression au sein de la section (1 = plus bas). */
    var order: Int,

    /**
     * Index d'équivalence entre sections (secondaire : 6ème = Form 1 = 1 ... Tle = Upper Sixth = 7)
     * pour des statistiques croisées. Indicatif, à valider avec l'école.
     */
    var equivalenceIndex: Int? = null,

    /** Code du niveau suivant (passage en classe supérieure). */
    var nextLevelCode: String? = null,

    /** Barème de notation par défaut du niveau (GradingScheme.id). */
    var defaultGradingSchemeId: String? = null,
    var active: Boolean = true
) : TenantDocument()
