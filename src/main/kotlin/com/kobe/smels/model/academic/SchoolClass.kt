package com.kobe.smels.model.academic

import com.kobe.smels.model.shared.Cycle
import com.kobe.smels.model.shared.Language
import com.kobe.smels.model.shared.Section
import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Classe pour une année scolaire donnée (« 6ème A », « Form 3 »).
 * Une classe est recréée chaque année : l'historique des élèves et des notes reste ainsi intact.
 *
 * Les matières enseignées dans la classe (coefficient, groupe, enseignant) sont dans `academic_class_subjects`.
 * Les élèves de la classe sont dans `student_enrollments`.
 */
@Document(collection = "academic_classes")
@CompoundIndexes(
    CompoundIndex(name = "uk_school_year_name", def = "{'schoolId': 1, 'academicYearId': 1, 'name': 1}", unique = true),
    CompoundIndex(name = "idx_school_year_level", def = "{'schoolId': 1, 'academicYearId': 1, 'levelId': 1}")
)
data class SchoolClass(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,
    var levelId: String,

    /** Copies du niveau pour filtrer sans jointure (immuables après création). */
    var levelCode: String,
    var section: Section,
    var cycle: Cycle,

    /** Nom imprimé de la classe, ex. "6ème", "2NDE C", "2NDE ALL", "FORM 4 ARTS", "FORM 4 SC". */
    var name: String,

    /** Série / filière (ALL, C, A, C/D... / ARTS, SC...). Null sinon. */
    var trackCode: String? = null,
    var capacity: Int? = null,

    /** Staff.id du professeur principal (« Class master/mistress »). Imprimé sur le bulletin. */
    var mainTeacherId: String? = null,

    /** Salle habituelle de la classe (Room.id). */
    var homeRoomId: String? = null,

    /**
     * Langue par défaut des documents de la classe (bulletin, emploi du temps) : FRANCOPHONE -> FR,
     * ANGLOPHONE -> EN ; pour une classe BILINGUAL, à choisir. Réponse Q-01 : la langue de la section est
     * prioritaire.
     */
    var defaultLanguage: Language,

    /**
     * Langues dans lesquelles les documents de la classe peuvent être générés. Une classe FR ou EN peut aussi
     * être imprimée dans l'autre langue ; une classe BILINGUAL doit avoir les deux opérationnelles.
     */
    var availableLanguages: Set<Language> = setOf(Language.FR, Language.EN),

    /** Barème de notation de la classe (sinon celui du niveau). */
    var gradingSchemeId: String? = null,
    var active: Boolean = true
) : TenantDocument()
