package com.kobe.smels.model.reporting

import com.kobe.smels.model.shared.Cycle
import com.kobe.smels.model.shared.Language
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.PageFormat
import com.kobe.smels.model.shared.Section
import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import java.math.BigDecimal

/**
 * Gabarit de bulletin : décrit CE QUI S'IMPRIME (colonnes, blocs, échelle d'appréciation), pas COMMENT on
 * calcule (cela reste dans GradingScheme). L'école utilise au moins deux gabarits (observés sur les
 * bulletins fournis) :
 *
 *  - ANNUEL (toutes les classes vues) : colonnes DEV1..DEV6, moyenne /20, coefficient, total, appréciation
 *    par lettres, blocs résultat annuel + rappels des 3 trimestres + profil de classe ;
 *  - TRIMESTRE (Form 1, 1er trimestre) : colonnes Eval1/Eval2, compétence visée sous chaque matière, rang dans
 *    la matière, moyenne de la classe dans la matière, nombre d'élèves >= 10 dans la matière, appréciation
 *    par compétences (NA / ECA / A / A+), total « 420.16 / 720.0 », décision « PASSED IN 12/16 SUBJECTS » et
 *    « MAKE EFFORT IN ».
 *
 * Quel gabarit pour quelle classe / quelle période : Q-26. Les gabarits sont modifiables depuis l'interface
 * (exigence : tout doit pouvoir être paramétré graphiquement, dans les deux langues).
 *
 * Contrainte transversale : le rendu tient sur UNE page A4 portrait (voir [PageFormat]).
 */
@Document(collection = "reporting_templates")
@CompoundIndexes(
    CompoundIndex(name = "uk_school_code", def = "{'schoolId': 1, 'code': 1}", unique = true)
)
data class ReportTemplate(
    @Id
    var id: String? = null,
    override var schoolId: String,
    /** Code stable, ex. "ANNUAL_SECONDARY", "TERM_COMPETENCY". */
    var code: String,
    var name: LocalizedText,

    /** Période concernée. */
    var periodType: ReportPeriodType,

    /** Où s'applique ce gabarit (vide = partout). Plus précis l'emporte : niveau > cycle/section. */
    var cycles: Set<Cycle> = emptySet(),
    var sections: Set<Section> = emptySet(),
    var levelCodes: Set<String> = emptySet(),

    /** Langues dans lesquelles le gabarit peut être rendu. */
    var languages: Set<Language> = setOf(Language.FR, Language.EN),

    var page: PageFormat = PageFormat.A4_PORTRAIT,

    /** Numéros de séquences affichés en colonnes : [1..6] pour l'annuel, [1,2] pour le trimestre 1... */
    var sequenceColumns: List<Int> = listOf(1, 2, 3, 4, 5, 6),

    /** Échelle d'appréciation par matière (GradingScheme.appreciationScales[].code) : "LETTER" ou "COMPETENCY". */
    var appreciationScaleCode: String = "LETTER",

    var showCompetency: Boolean = false,
    var showSubjectRank: Boolean = false,
    var showSubjectClassAverage: Boolean = false,
    var showSubjectPassedCount: Boolean = false,
    var showGroupTotals: Boolean = true,
    var showTermRecap: Boolean = true,
    var showClassProfile: Boolean = true,
    var showEffortSubjects: Boolean = false,

    /** « Total 420.16 / 720.0 » (total des points / (somme des coefficients x 20)). */
    var showTotalPointsOverMax: Boolean = false,

    /** Seuil du profil de classe pour ce gabarit (« Average >= 10 » ou « >= 12 »). Null = GradingScheme.classSuccessThreshold. */
    @Field(targetType = FieldType.DECIMAL128)
    var classProfileThreshold: BigDecimal? = null,

    /** Légende imprimée en pied (« NA = non acquis, ECA = en cours d'acquisition... »). */
    var legend: LocalizedText? = null,

    /**
     * Réglages d'ajustement à une page : taille de police et hauteur de ligne minimales. Si le contenu ne tient
     * pas même au minimum, la génération échoue avec une erreur explicite plutôt que de produire une 2e page.
     */
    var minFontSizePt: Double = 6.5,
    var maxFontSizePt: Double = 9.0,

    var active: Boolean = true
) : TenantDocument()
