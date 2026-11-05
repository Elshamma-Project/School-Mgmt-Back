package com.kobe.smels.model.academic

import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Compétence visée par une matière pendant un trimestre, imprimée sous le nom de la matière sur le
 * bulletin de trimestre (exemple observé : « Satisfy basic daily needs, family life and relationships. »).
 *
 * Texte saisi par l'enseignant titulaire du ClassSubject. Il peut être dans l'une ou l'autre langue (observé :
 * un bulletin anglophone contient des compétences en français pour la matière FRENCH), d'où LocalizedText
 * avec au moins une langue renseignée. Le bulletin en fige une copie.
 */
@Document(collection = "academic_term_competencies")
@CompoundIndexes(
    CompoundIndex(name = "uk_classsubject_term", def = "{'schoolId': 1, 'classSubjectId': 1, 'termNumber': 1}", unique = true)
)
data class TermCompetency(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,
    var classId: String,
    var classSubjectId: String,
    /** 1 à 3. */
    var termNumber: Int,
    var text: LocalizedText,
    /** Staff.id de l'auteur. */
    var authorStaffId: String? = null
) : TenantDocument()
