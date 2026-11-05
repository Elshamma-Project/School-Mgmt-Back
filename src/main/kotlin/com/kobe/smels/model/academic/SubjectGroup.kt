package com.kobe.smels.model.academic

import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Groupe de matières imprimé comme bloc avec son sous-total sur le bulletin
 * (« TOTAL LANGUES ET LITTERATURES », « TOTAL OF VOCATIONAL STUDIES »...).
 *
 * L'appartenance d'une matière à un groupe se décide classe par classe dans [ClassSubject.groupId] :
 * en 6ème francophone l'Histoire est dans « Sciences et technologie », en Form 3 anglophone elle est dans
 * « Human and Social Sciences ».
 */
@Document(collection = "academic_subject_groups")
@CompoundIndexes(
    CompoundIndex(name = "uk_school_code", def = "{'schoolId': 1, 'code': 1}", unique = true)
)
data class SubjectGroup(
    @Id
    var id: String? = null,
    override var schoolId: String,

    /** Code stable, ex. "LANG_LIT", "SCI_TECH", "DEV_PERS", "HUM_SOC", "VOC". */
    var code: String,
    var name: LocalizedText,

    /** Ordre d'affichage par défaut des blocs sur le bulletin. */
    var displayOrder: Int = 0,
    var active: Boolean = true
) : TenantDocument()
