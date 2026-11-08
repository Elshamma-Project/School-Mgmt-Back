package com.kobe.smels.model.grading

import com.kobe.smels.model.shared.Cycle
import com.kobe.smels.model.shared.LocalizedText
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
 * Barème de notation : toutes les règles qui ne doivent PAS être codées en dur, car elles peuvent
 * différer selon le cycle ou la section (maternelle, primaire, secondaire FR, secondaire EN).
 * Valeurs observées sur les bulletins : voir docs/04-regles-de-calcul.md.
 */
@Document(collection = "grading_schemes")
@CompoundIndexes(
    CompoundIndex(name = "uk_school_code", def = "{'schoolId': 1, 'code': 1}", unique = true)
)
data class GradingScheme(
    @Id
    var id: String? = null,
    override var schoolId: String,

    /** Code stable, ex. "SECONDAIRE_FR", "SECONDARY_EN". */
    var code: String,
    var name: LocalizedText,

    /** Cycles et sections auxquels ce barème s'applique par défaut. */
    var cycles: Set<Cycle> = emptySet(),
    var sections: Set<Section> = emptySet(),

    /** Note maximale (20). */
    @Field(targetType = FieldType.DECIMAL128)
    var maxScore: BigDecimal = BigDecimal("20"),

    /** Seuil de validation d'une matière (10) : sert au « PASSED IN 15 SUBJECTS ». */
    @Field(targetType = FieldType.DECIMAL128)
    var passMark: BigDecimal = BigDecimal("10"),

    /** Seuil du « Moyenne >= 12 » et du « % Réussite » du profil de classe (12 sur les bulletins annuels observés ; 10 sur le bulletin de trimestre de Form 1 : à surcharger par ReportTemplate.classProfileThreshold). */
    @Field(targetType = FieldType.DECIMAL128)
    var classSuccessThreshold: BigDecimal = BigDecimal("12"),

    /** Nombre de décimales des moyennes, totaux et notes imprimés. */
    var decimals: Int = 2,
    var roundingPolicy: RoundingPolicy = RoundingPolicy.HALF_UP,

    /**
     * Arrondir à `decimals` la moyenne de chaque TRIMESTRE d'une matière avant d'en faire la moyenne annuelle.
     * C'est la pratique de l'ancien logiciel (elle explique les écarts de centimes de toutes les lignes des
     * bulletins analysés). Les autres arrondis se font une seule fois, à l'affichage.
     */
    var roundTermSubjectAverages: Boolean = true,
    var missingMarkPolicy: MissingMarkPolicy = MissingMarkPolicy.IGNORE,
    var absencePolicy: AbsencePolicy = AbsencePolicy.EXCLUDE,
    var rankingPolicy: RankingPolicy = RankingPolicy.COMPETITION,

    /**
     * Échelles d'appréciation d'une matière. Deux sont observées :
     *  - LETTER : A >= 16, B+ [14,16), B [12,14), C < 12 (bulletins annuels) ;
     *  - COMPETENCY : NA non acquis < 10, ECA en cours d'acquisition [10,14), A acquis >= 14, A+ parfaitement
     *    acquis (seuil de A+ non observé, Q-27) — bulletin de trimestre de Form 1.
     * Le gabarit de bulletin ([ReportTemplate]) choisit l'échelle par `appreciationScaleCode`.
     */
    var appreciationScales: List<AppreciationScale> = emptyList(),

    /** Appréciation générale (« TRAVAIL INSUFFISANT », « FAIR »...). Seuils à confirmer (Q-09). */
    var overallMentionBands: List<ScoreBand> = emptyList(),

    /**
     * Moyenne minimale pour le tableau d'honneur (réponse Q-11 : 12/20, paramètre modifiable plus tard
     * depuis l'interface).
     */
    @Field(targetType = FieldType.DECIMAL128)
    var honourRollMinAverage: BigDecimal = BigDecimal("12"),

    /**
     * Hypothèse (Q-11b) : un avertissement ou un blâme de conduite empêche le tableau d'honneur.
     * Observé : élève à 12,30 sans tableau d'honneur avec « Avertissement conduite : Oui ».
     */
    var honourRollBlockedBySanction: Boolean = true,

    /**
     * Les matières dont la moyenne est strictement inférieure à ce seuil sont listées sur le bulletin de
     * trimestre dans « MAKE EFFORT IN ». Observé : 7,5 et 6,0 listées, 8,5 non listée -> seuil 8 (hypothèse,
     * Q-28). Null = fonction désactivée.
     */
    @Field(targetType = FieldType.DECIMAL128)
    var effortSubjectThreshold: BigDecimal? = null,

    /** Comment la moyenne annuelle générale est obtenue (voir docs/04-regles-de-calcul.md). */
    var annualAveragePolicy: AnnualAveragePolicy = AnnualAveragePolicy.WEIGHTED_TOTAL,

    /** Comment la moyenne annuelle d'une matière est obtenue. */
    var subjectYearAveragePolicy: SubjectYearAveragePolicy = SubjectYearAveragePolicy.MEAN_OF_TERM_AVERAGES,

    /** Qui reçoit un rang (Q-07). */
    var rankingEligibility: RankingEligibility = RankingEligibility.HAS_AVERAGE,

    /** Seuils éventuels du tableau d'honneur (vide = décision manuelle du conseil de classe). */
    var distinctionRules: List<DistinctionRule> = emptyList(),

    /**
     * Délai indicatif (heures) après [EvaluationSession.examDate] pour déposer la fiche de report.
     * Réponse Q-37 : non bloquant par défaut (`reportSheetDeadlineBlocking = false`).
     */
    var reportSheetDeadlineHours: Int = 72,

    /** Si true, refuse la soumission après le délai ; sinon avertissement seulement (Q-37). */
    var reportSheetDeadlineBlocking: Boolean = false,

    /** Lignes max par page PDF A4 paysage ; au-delà, 2e page avec en-tête répété (Q-35). */
    var reportSheetMaxRowsPerPage: Int = 45,

    /** Code d'échelle dans [appreciationScales] pour la colonne App (défaut LETTER, Q-10 / Q-36). */
    var reportSheetAppreciationScaleCode: String = "LETTER",

    var active: Boolean = true
) : TenantDocument()

/** Une échelle d'appréciation (lettres ou compétences) : un code et ses tranches. */
data class AppreciationScale(
    /** "LETTER", "COMPETENCY"... référencé par ReportTemplate.appreciationScaleCode. */
    val code: String,
    val bands: List<ScoreBand> = emptyList()
)

/** Moyenne annuelle générale. */
enum class AnnualAveragePolicy {
    /** Somme des totaux de matières / somme des coefficients (égal à la moyenne des 3 trimestres si tout est noté). */
    WEIGHTED_TOTAL,
    /** Moyenne des moyennes de trimestre (méthode apparente de l'ancien logiciel, voir Q-24). */
    MEAN_OF_TERM_AVERAGES
}

/** Moyenne annuelle d'une matière. */
enum class SubjectYearAveragePolicy {
    /** Moyenne des moyennes de trimestre ; dans un trimestre, moyenne des notes présentes (observé). */
    MEAN_OF_TERM_AVERAGES,
    /** Moyenne simple de toutes les notes présentes. */
    MEAN_OF_ALL_MARKS
}

/** Condition pour être classé. */
enum class RankingEligibility {
    /** Toute moyenne calculable donne un rang. */
    HAS_AVERAGE,
    /** Il faut une note dans chaque trimestre (hypothèse pour expliquer « rang 0/0 », Q-07). */
    HAS_ALL_TERMS
}

/**
 * Tranche de notes : minInclusive <= moyenne < maxExclusive (maxExclusive null = sans plafond).
 * Observé : A >= 16 ; B+ de 14 à moins de 16 ; B de 12 à moins de 14 ; C en dessous de 12.
 */
data class ScoreBand(
    val code: String,
    val label: LocalizedText? = null,
    @Field(targetType = FieldType.DECIMAL128)
    val minInclusive: BigDecimal,
    @Field(targetType = FieldType.DECIMAL128)
    val maxExclusive: BigDecimal? = null
)

data class DistinctionRule(
    val level: DistinctionLevel,
    @Field(targetType = FieldType.DECIMAL128)
    val minAverage: BigDecimal
)

enum class RoundingPolicy {
    /** Arrondi décimal à la demi-unité supérieure (8.555 -> 8.56) : choix retenu (Q-14 tranché). */
    HALF_UP,

    /**
     * Arrondi « au pair » (8.625 -> 8.62, 8.375 -> 8.38). C'est celui de l'ancien logiciel de l'école : avec lui
     * toutes les lignes des 7 bulletins analysés se retrouvent exactement. Disponible pour comparer, non retenu
     * par défaut.
     */
    HALF_EVEN
}

/** Que faire d'une séquence sans note pour une matière. */
enum class MissingMarkPolicy {
    /** La séquence est ignorée : moyenne des séquences notées seulement. */
    IGNORE,

    /** La séquence compte pour 0. */
    COUNT_AS_ZERO
}

/** Comment compter une absence à une évaluation (GradeStatus.ABSENT). */
enum class AbsencePolicy { EXCLUDE, COUNT_AS_ZERO }

enum class RankingPolicy {
    /** Ex aequo : 1, 2, 2, 4. */
    COMPETITION,

    /** Ex aequo : 1, 2, 2, 3. */
    DENSE
}

/** Niveau de distinction du tableau d'honneur. */
enum class DistinctionLevel { NONE, HONOR_ROLL, HONOR_ROLL_ENCOURAGEMENT, HONOR_ROLL_CONGRATULATIONS }
