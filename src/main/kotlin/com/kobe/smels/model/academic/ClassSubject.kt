package com.kobe.smels.model.academic

import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import java.math.BigDecimal
import com.kobe.smels.model.shared.Language

/**
 * Matière enseignée dans UNE classe pour UNE année : c'est le pivot du système.
 *
 * Un ClassSubject dit : « en 6ème A, en 2025-2026, la matière Mathématiques a le coefficient 4,
 * fait partie du groupe Sciences et technologie, est enseignée par M. X, 4 h par semaine ».
 * C'est aussi lui qui donne le droit de saisir des notes : un enseignant ne peut saisir que pour
 * les ClassSubject dont il est `teacherId`.
 *
 * Règles (voir docs/02-domaine-metier.md) :
 *  - une matière n'apparaît qu'une fois par classe (index unique classe + matière) ;
 *  - un même enseignant peut avoir plusieurs ClassSubject, dans plusieurs classes et sections ;
 *  - un ClassSubject a au plus un enseignant titulaire à la fois ; l'historique d'un remplacement
 *    est conservé par le nom figé dans ReportCard et par les écritures de GradeAuditEntry.
 */
@Document(collection = "academic_class_subjects")
@CompoundIndexes(
    CompoundIndex(name = "uk_class_subject", def = "{'schoolId': 1, 'classId': 1, 'subjectId': 1}", unique = true),
    CompoundIndex(name = "idx_teacher_year", def = "{'schoolId': 1, 'teacherId': 1, 'academicYearId': 1}"),
    CompoundIndex(name = "idx_year_class", def = "{'schoolId': 1, 'academicYearId': 1, 'classId': 1}")
)
data class ClassSubject(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,
    var classId: String,
    var subjectId: String,

    /** Groupe (bloc de sous-total) dans lequel la matière est imprimée pour cette classe. */
    var groupId: String,

    /** Coefficient (peut être décimal, ex. 1.5). Strictement positif. */
    @Field(targetType = FieldType.DECIMAL128)
    var coefficient: BigDecimal,

    /** Staff.id de l'enseignant titulaire (null tant que non affecté). */
    var teacherId: String? = null,

    /**
     * Volume horaire hebdomadaire en nombre de périodes de l'emploi du temps. Sert de cible pour la génération
     * de l'emploi du temps et de contrôle (module timetable).
     */
    var weeklyHours: Int? = null,

    /** Langue d'enseignement dans cette classe (surcharge Subject.teachingLanguage), utile en classe bilingue. */
    var teachingLanguage: Language? = null,

    /** Ordre d'impression à l'intérieur du groupe. */
    var displayOrder: Int = 0,

    /** Note maximale de la matière (20 partout dans les bulletins observés). */
    @Field(targetType = FieldType.DECIMAL128)
    var maxScore: BigDecimal = BigDecimal("20"),

    /** false = matière suivie mais exclue de la moyenne (à confirmer avec l'école). */
    var countsInAverage: Boolean = true,

    /** Matière facultative (non imposée à tous les élèves). */
    var optional: Boolean = false,
    var active: Boolean = true
) : TenantDocument()
