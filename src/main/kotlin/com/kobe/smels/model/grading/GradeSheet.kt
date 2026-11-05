package com.kobe.smels.model.grading

import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import java.math.BigDecimal
import java.time.Instant

/**
 * Fiche de notes d'un enseignant : UNE fiche par matière d'une classe (ClassSubject) et par séquence (DEV).
 *
 * C'est le « fichier principal » dont parle l'école (réponse Q-02) : l'enseignant y saisit, pour chaque élève,
 * les notes, les bonus, les malus, etc. (colonnes = [Assessment], valeurs = [Grade]) ; le logiciel totalise
 * ensuite automatiquement chaque élève en une note de séquence ([SequenceMark]) qui alimente la colonne DEVn
 * du bulletin.
 *
 * La fiche est la cible naturelle des permissions : l'enseignant titulaire du ClassSubject modifie sa fiche
 * tant que la séquence est OPEN ; l'administration peut la modifier à tout moment (avec trace).
 *
 * Export : la fiche peut être exportée en PDF/Excel sur plusieurs pages (c'est un document de travail
 * personnel de l'enseignant, la règle « une seule page A4 » ne s'y applique pas).
 */
@Document(collection = "grading_sheets")
@CompoundIndexes(
    CompoundIndex(name = "uk_classsubject_session", def = "{'schoolId': 1, 'classSubjectId': 1, 'sessionId': 1}", unique = true),
    CompoundIndex(name = "idx_teacher_session", def = "{'schoolId': 1, 'teacherId': 1, 'sessionId': 1}"),
    CompoundIndex(name = "idx_class_session", def = "{'schoolId': 1, 'classId': 1, 'sessionId': 1}")
)
data class GradeSheet(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,
    var classId: String,
    var classSubjectId: String,
    var sessionId: String,
    var sessionNumber: Int,

    /** Staff.id de l'enseignant titulaire au moment de la création de la fiche. */
    var teacherId: String? = null,
    var status: SheetStatus = SheetStatus.DRAFT,

    /** Règle de calcul de la note de séquence à partir des colonnes. */
    var totalization: TotalizationRule = TotalizationRule(),
    var submittedAt: Instant? = null,
    var lockedAt: Instant? = null,

    /** Dernière totalisation automatique. */
    var lastTotalizedAt: Instant? = null
) : TenantDocument()

enum class SheetStatus {
    /** En cours de saisie. */
    DRAFT,

    /** Déclarée terminée par l'enseignant (modifiable tant que la séquence est OPEN). */
    SUBMITTED,

    /** Séquence clôturée : seule l'administration peut corriger, avec motif. */
    LOCKED
}

/**
 * Comment les colonnes d'une fiche se totalisent en note de séquence. La règle exacte de l'école n'est pas
 * connue (Q-25) : le défaut ci-dessous est une hypothèse à confirmer avec la fiche Excel d'origine.
 */
data class TotalizationRule(
    var formula: TotalizationFormula = TotalizationFormula.AVERAGE_PLUS_BONUS_MINUS_MALUS,
    @Field(targetType = FieldType.DECIMAL128)
    var minScore: BigDecimal = BigDecimal.ZERO,
    /** Plafond de la note de séquence (20) ; appliqué après bonus et malus si `capAtMax`. */
    @Field(targetType = FieldType.DECIMAL128)
    var maxScore: BigDecimal = BigDecimal("20"),
    var capAtMax: Boolean = true
)

enum class TotalizationFormula {
    /** Moyenne des colonnes d'évaluation ramenées sur 20, + bonus - malus (hypothèse par défaut). */
    AVERAGE_PLUS_BONUS_MINUS_MALUS,

    /** Moyenne pondérée par `Assessment.weight`, + bonus - malus. */
    WEIGHTED_AVERAGE_PLUS_BONUS_MINUS_MALUS,

    /** Somme des colonnes (cas où le total est déjà sur 20). */
    SUM,

    /** Note de séquence saisie directement (cas d'une seule colonne SEQUENCE_MARK). */
    MANUAL
}
