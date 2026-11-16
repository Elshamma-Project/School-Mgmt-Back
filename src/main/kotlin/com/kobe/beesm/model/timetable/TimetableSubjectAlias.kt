package com.kobe.beesm.model.timetable

import com.kobe.beesm.model.shared.Section
import com.kobe.beesm.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Table d’alias : libellé imprimé sur l’EDT → un ou plusieurs [com.kobe.beesm.model.academic.ClassSubject].
 *
 * Le PDF 2026-2027 contient ~59 libellés distincts avec fautes (« CNINOIS », « NFORMATIQUE »,
 * « FRANCAIS » / « FRANÇAIS », « LITTERATURE », « F/N », « H.BIOLOGY », « A.MATHS », « APT.DE VIE »…).
 * La saisie et le chargeur ne doivent **jamais** matcher par texte libre : uniquement via cette table
 * (Q-30, Q-44, Q-56).
 *
 * Correspondance 1→1 : le chargeur peut remplir `ClassSubject.weeklyHours`.
 * Correspondance 1→N (ex. FRANÇAIS → expression écrite + orale + …) : heures EDT = somme des weeklyHours (BR-63).
 * Non résolu : [unresolved] = true, rapport pour l’admin.
 */
@Document(collection = "timetable_subject_aliases")
@CompoundIndexes(
    CompoundIndex(
        name = "uk_alias_year_section",
        def = "{'schoolId': 1, 'academicYearId': 1, 'sectionKey': 1, 'aliasNormalized': 1}",
        unique = true
    )
)
data class TimetableSubjectAlias(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,

    /** Section d’application ; null = toutes. */
    var section: Section? = null,

    /**
     * Clé d’index : [Section.name] ou `"_"` si [section] est null (évite l’ambiguïté null Mongo).
     */
    var sectionKey: String = "_",

    /** Forme canonique (majuscules, sans accents de comparaison) : ex. `"CNINOIS"`, `"FRANCAIS"`. */
    var aliasNormalized: String,

    /** Variantes brutes observées sur le PDF (« CNINOIS », « CNINESE », …). */
    var aliasVariants: List<String> = emptyList(),

    /** ClassSubject cibles (souvent 1 ; plusieurs si un libellé regroupe plusieurs matières bulletin). */
    var classSubjectIds: List<String> = emptyList(),

    /** true si aucune correspondance fiable (à traiter manuellement). */
    var unresolved: Boolean = false,

    var note: String? = null,
    var active: Boolean = true
) : TenantDocument()
