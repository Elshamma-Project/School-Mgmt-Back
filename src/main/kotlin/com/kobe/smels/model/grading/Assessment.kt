package com.kobe.smels.model.grading

import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Une COLONNE de la fiche de notes d'un enseignant (voir [GradeSheet]) : une note d'évaluation, un bonus
 * ou un malus, pour une matière d'une classe pendant une séquence.
 *
 * Réponse Q-02 : l'enseignant tient UNE fiche par séquence (DEV) et par matière/classe. Il y saisit les notes
 * des élèves, les bonus, les malus, etc. ; le logiciel totalise ensuite automatiquement : le résultat est la
 * note de séquence ([SequenceMark]), celle qui s'imprime dans la colonne DEVn du bulletin.
 *
 *  - EVALUATION types (TEST, QUIZ, HOMEWORK, ORAL, PRACTICAL, EXAM, OTHER) : notes qui entrent dans la moyenne ;
 *  - BONUS / MALUS : points ajoutés / retirés (règle exacte de totalisation : Q-25, voir [TotalizationRule]) ;
 *  - SEQUENCE_MARK : cas simple d'une note finale saisie directement.
 *
 * Permissions : l'enseignant titulaire du ClassSubject crée / modifie tant que la séquence est OPEN ;
 * l'administration peut tout faire (avec trace).
 */
@Document(collection = "grading_assessments")
@CompoundIndexes(
    CompoundIndex(name = "idx_classsubject_session", def = "{'schoolId': 1, 'classSubjectId': 1, 'sessionId': 1}"),
    CompoundIndex(name = "idx_class_session", def = "{'schoolId': 1, 'classId': 1, 'sessionId': 1}")
)
data class Assessment(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,
    var classId: String,
    var classSubjectId: String,
    var sessionId: String,

    /** Fiche de notes à laquelle appartient la colonne (GradeSheet.id). */
    var sheetId: String? = null,

    /** Copie du numéro de séquence (1..6) pour éviter une jointure. */
    var sessionNumber: Int,
    var title: String,
    var type: AssessmentType = AssessmentType.SEQUENCE_MARK,

    /** Barème de l'évaluation (20 par défaut). Les notes sont ramenées sur 20 au calcul. */
    @Field(targetType = FieldType.DECIMAL128)
    var maxScore: BigDecimal = BigDecimal("20"),

    /** Poids dans la note de séquence. */
    @Field(targetType = FieldType.DECIMAL128)
    var weight: BigDecimal = BigDecimal.ONE,
    var date: LocalDate? = null,

    /** Staff.id de l'enseignant à l'origine de l'évaluation. */
    var createdByStaffId: String? = null,

    /** false = brouillon, invisible pour élèves et parents (visible tout de suite par défaut : temps réel). */
    var published: Boolean = true
) : TenantDocument()

enum class AssessmentType {
    SEQUENCE_MARK,
    BONUS,
    MALUS,
    TEST,
    QUIZ,
    HOMEWORK,
    ORAL,
    PRACTICAL,
    EXAM,
    OTHER
}
