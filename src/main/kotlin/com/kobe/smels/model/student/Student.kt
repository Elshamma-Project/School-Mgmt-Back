package com.kobe.smels.model.student

import com.kobe.smels.model.shared.ContactInfo
import com.kobe.smels.model.shared.Sex
import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDate

/**
 * Élève : identité permanente, indépendante de l'année.
 * Tout ce qui dépend de l'année (classe, redoublant, numéro d'ordre) est dans [Enrollment].
 *
 * Le `matricule` est l'identifiant de connexion futur de l'élève et figure sur le bulletin.
 * Données de mineurs : ne jamais les mettre dans les logs, les fixtures de test ou les dépôts Git.
 */
@Document(collection = "student_students")
@CompoundIndexes(
    CompoundIndex(name = "uk_school_matricule", def = "{'schoolId': 1, 'matricule': 1}", unique = true),
    CompoundIndex(name = "idx_school_name", def = "{'schoolId': 1, 'lastName': 1, 'firstName': 1}"),
    CompoundIndex(name = "idx_school_guardian", def = "{'schoolId': 1, 'guardians.guardianId': 1}")
)
data class Student(
    @Id
    var id: String? = null,
    override var schoolId: String,

    /** Matricule unique dans l'école (ex. "250369252"). Format de génération : voir Q-05. */
    var matricule: String,

    /** Nom de famille (le bulletin imprime « NOM PRÉNOMS » dans une seule cellule). */
    var lastName: String,
    var firstName: String,
    /**
     * Nullable : un élève peut être créé sans sexe renseigné.
     * Sur la fiche de report, la cellule reste vide et les compteurs (garçons/filles)
     * émettent un avertissement si boys + girls ≠ effectif.
     */
    var sex: Sex? = null,
    var dateOfBirth: LocalDate? = null,
    var placeOfBirth: String? = null,
    var nationality: String? = null,

    /** Référence du fichier photo (le bulletin anglophone observé en imprime une, le francophone non). */
    var photoFileId: String? = null,

    var contact: ContactInfo = ContactInfo(),

    /** Tuteurs / parents liés (plusieurs enfants par parent : le lien est porté ici). */
    var guardians: List<GuardianLink> = emptyList(),
    var active: Boolean = true
) : TenantDocument() {
    /** Nom tel qu'imprimé sur le bulletin : « NOM PRÉNOMS ». */
    fun fullName(): String = listOf(lastName, firstName).filter { it.isNotBlank() }.joinToString(" ")
}

/** Lien élève -> tuteur, avec ce que le tuteur est autorisé à voir. */
data class GuardianLink(
    val guardianId: String,
    val relationship: GuardianRelationship = GuardianRelationship.OTHER,
    val primary: Boolean = false,
    val canViewGrades: Boolean = true,
    val canDownloadReportCards: Boolean = true
)
