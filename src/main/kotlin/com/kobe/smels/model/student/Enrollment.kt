package com.kobe.smels.model.student

import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDate

/**
 * Inscription d'un élève dans une classe pour une année scolaire.
 *
 * C'est ce document (et non Student) que les notes et les bulletins référencent, car « redoublant »,
 * « classe » et « numéro d'ordre » changent d'une année à l'autre.
 *
 * Effectif de la classe = nombre d'Enrollment ENROLLED de la classe.
 * Un élève classé = un élève dont la moyenne a pu être calculée (peut être inférieur à l'effectif :
 * sur les deux bulletins observés, 1 élève de la classe n'est pas classé, voir Q-07).
 */
@Document(collection = "student_enrollments")
@CompoundIndexes(
    CompoundIndex(name = "uk_student_year", def = "{'schoolId': 1, 'studentId': 1, 'academicYearId': 1}", unique = true),
    CompoundIndex(name = "idx_class_status", def = "{'schoolId': 1, 'classId': 1, 'status': 1}"),
    CompoundIndex(name = "idx_year_level", def = "{'schoolId': 1, 'academicYearId': 1, 'levelId': 1}")
)
data class Enrollment(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var studentId: String,
    var academicYearId: String,
    var classId: String,

    /** Copie du niveau de la classe (immuable sauf changement de classe). */
    var levelId: String,

    /** Numéro d'ordre dans la liste de classe (« N° ON ROLL » sur le bulletin anglophone, à confirmer : Q-08). */
    var rollNumber: Int? = null,

    /** « Redoublant : Oui/Non » (REPEATER sur le bulletin anglophone). Propre à l'année. */
    var repeater: Boolean = false,

    /** Nouvel élève dans l'école cette année. */
    var newStudent: Boolean = false,
    var status: EnrollmentStatus = EnrollmentStatus.ENROLLED,
    var enrolledOn: LocalDate? = null,
    var leftOn: LocalDate? = null,
    var previousSchool: String? = null
) : TenantDocument()

enum class EnrollmentStatus { ENROLLED, TRANSFERRED_OUT, WITHDRAWN, EXCLUDED, COMPLETED }
