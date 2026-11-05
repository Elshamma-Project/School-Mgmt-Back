package com.kobe.smels.model.staff

import com.kobe.smels.model.shared.ContactInfo
import com.kobe.smels.model.shared.Sex
import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDate

/**
 * Membre du personnel : enseignants ET administration dans la même collection.
 *
 * Une même personne cumule souvent des fonctions : sur les bulletins observés, le principal enseigne
 * aussi une matière, et plusieurs professeurs principaux enseignent aussi les mathématiques ou l'anglais.
 * Il n'y a donc PAS de collection `teachers` séparée : un enseignant est un Staff dont
 * `functions` contient TEACHER (l'API peut exposer /teachers et /staff comme deux vues filtrées).
 *
 * Fonction (poste RH) et rôle (droits d'accès, voir iam.Role) sont deux notions distinctes.
 */
@Document(collection = "staff_members")
@CompoundIndexes(
    CompoundIndex(name = "idx_school_name", def = "{'schoolId': 1, 'lastName': 1, 'firstName': 1}"),
    CompoundIndex(name = "idx_school_function", def = "{'schoolId': 1, 'functions': 1, 'active': 1}")
)
data class Staff(
    @Id
    var id: String? = null,
    override var schoolId: String,

    /** Matricule interne ou numéro d'employé (facultatif). */
    var staffNumber: String? = null,

    /** Nom de famille (imprimé en premier : « NGWA Hariette »). */
    var lastName: String,
    var firstName: String = "",

    /** Civilité imprimée sur l'emploi du temps (« Mme X », « Mr Y »). */
    var civility: Civility? = null,
    var sex: Sex? = null,
    var dateOfBirth: LocalDate? = null,
    var contact: ContactInfo = ContactInfo(),

    var functions: Set<StaffFunction> = setOf(StaffFunction.TEACHER),
    var employmentStatus: EmploymentStatus? = null,

    /** Matières que l'enseignant peut enseigner (Subject.id). Aide à l'affectation, ne bloque rien. */
    var teachableSubjectIds: Set<String> = emptySet(),
    var hireDate: LocalDate? = null,

    /** Désactiver plutôt que supprimer : des notes et des bulletins le référencent. */
    var active: Boolean = true
) : TenantDocument() {
    /** Nom tel qu'imprimé sur le bulletin : « NOM Prénom ». */
    fun fullName(): String = listOf(lastName, firstName).filter { it.isNotBlank() }.joinToString(" ")
}

/** Poste occupé dans l'établissement (peut être multiple). */
enum class StaffFunction {
    TEACHER,
    HEAD_OF_SCHOOL,
    CENSOR,
    SECRETARY,
    SUPERVISOR,
    ACCOUNTANT,
    OTHER
}

enum class EmploymentStatus { PERMANENT, CONTRACTUAL, PART_TIME }

/** Civilité (emploi du temps). */
enum class Civility { MR, MME, MLLE, DR, PR }
