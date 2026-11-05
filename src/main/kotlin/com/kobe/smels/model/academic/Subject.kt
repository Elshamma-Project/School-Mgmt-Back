package com.kobe.smels.model.academic

import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import com.kobe.smels.model.shared.Language

/**
 * Matière du catalogue de l'école (« MATHEMATIQUES » / « MATHEMATICS »).
 *
 * Le catalogue ne contient ni coefficient, ni enseignant, ni groupe : ces informations
 * dépendent de la classe et sont dans [ClassSubject].
 *
 * Les sous-disciplines imprimées séparément sur un bulletin (Expression écrite, Expression orale,
 * Étude de texte...) sont des matières distinctes du catalogue.
 * « ANGLAIS » (section francophone) et « ENGLISH LANGUAGE » (section anglophone) sont aussi
 * deux matières distinctes (coefficients et programmes différents).
 */
@Document(collection = "academic_subjects")
@CompoundIndexes(
    CompoundIndex(name = "uk_school_code", def = "{'schoolId': 1, 'code': 1}", unique = true)
)
data class Subject(
    @Id
    var id: String? = null,
    override var schoolId: String,

    /** Code stable, ex. "MATH", "ENG_LANG", "EXP_ECR". */
    var code: String,

    /** Nom officiel imprimé sur le bulletin (dans la langue de la classe). */
    var name: LocalizedText,

    /** Nom court pour les tableaux et emplois du temps. */
    var shortName: LocalizedText? = null,

    /** Abréviation telle qu'écrite sur l'emploi du temps (« SVTEEHB », « PCT », « ECM », « LCN », « A.MATHS »). */
    var timetableAlias: String? = null,

    /** ACADEMIC = noté et imprimé sur le bulletin ; ACTIVITY = sans note (club, bibliothèque, musique...). */
    var kind: SubjectKind = SubjectKind.ACADEMIC,

    /**
     * Langue dans laquelle la matière est enseignée. Important pour les classes BILINGUAL (Q-01) : certaines
     * matières sont faites entièrement en français, d'autres entièrement en anglais. Null = langue de la section.
     */
    var teachingLanguage: Language? = null,
    var active: Boolean = true
) : TenantDocument()

/** ACADEMIC : matière notée. ACTIVITY : activité sans note qui peut figurer à l'emploi du temps. */
enum class SubjectKind { ACADEMIC, ACTIVITY }
