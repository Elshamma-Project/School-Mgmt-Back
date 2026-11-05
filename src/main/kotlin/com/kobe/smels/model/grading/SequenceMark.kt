package com.kobe.smels.model.grading

import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import java.math.BigDecimal
import java.time.Instant

/**
 * Note de séquence d'un élève dans une matière : le résultat de la totalisation de sa ligne de la fiche
 * ([GradeSheet]). C'est CETTE valeur qui s'imprime dans la colonne DEVn du bulletin et qui entre dans la
 * moyenne de la matière.
 *
 * Calculée par le service de notes à chaque modification d'une [Grade] de la fiche (même transaction), ou
 * saisie/corrigée directement par l'administration (`source = MANUAL_OVERRIDE`, avec motif et trace).
 *
 * États : voir [GradeStatus]. Un élève sans note (`PENDING`) n'a pas de valeur : l'école distingue le
 * « - » (non saisi, exclu du calcul) du « 0.0 » (zéro réel, compté). Ne jamais écrire 0 à la place d'une
 * note non saisie.
 */
@Document(collection = "grading_sequence_marks")
@CompoundIndexes(
    CompoundIndex(name = "uk_sheet_student", def = "{'schoolId': 1, 'sheetId': 1, 'studentId': 1}", unique = true),
    CompoundIndex(name = "idx_student_year_session", def = "{'schoolId': 1, 'studentId': 1, 'academicYearId': 1, 'sessionId': 1}"),
    CompoundIndex(name = "idx_class_session", def = "{'schoolId': 1, 'classId': 1, 'sessionId': 1}"),
    CompoundIndex(name = "idx_classsubject_session", def = "{'schoolId': 1, 'classSubjectId': 1, 'sessionId': 1}")
)
data class SequenceMark(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,
    var classId: String,
    var classSubjectId: String,
    var sessionId: String,
    var sessionNumber: Int,
    var termNumber: Int,
    var sheetId: String,
    var studentId: String,
    var enrollmentId: String,

    /** Note finale de la séquence sur 20 (deux décimales). Null si pas de note chiffrée. */
    @Field(targetType = FieldType.DECIMAL128)
    var value: BigDecimal? = null,
    var status: GradeStatus = GradeStatus.PENDING,
    var source: MarkSource = MarkSource.COMPUTED,
    var computedAt: Instant? = null,

    /** Version de la règle de totalisation utilisée, pour expliquer un écart. */
    var calculationVersion: String? = null,
    var comment: String? = null
) : TenantDocument()

enum class MarkSource {
    /** Totalisée automatiquement à partir de la fiche. */
    COMPUTED,

    /** Saisie ou corrigée à la main (administration), tracée dans GradeAuditEntry. */
    MANUAL_OVERRIDE
}
