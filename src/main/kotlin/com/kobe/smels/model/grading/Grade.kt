package com.kobe.smels.model.grading

import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import java.math.BigDecimal

/**
 * Note d'un élève pour une évaluation.
 *
 * Invariants (à faire respecter par le service, pas par le constructeur) :
 *  - `value` n'est renseignée que si `status == SCORED`, et 0 <= value <= Assessment.maxScore ;
 *  - `assessmentId` + `studentId` unique ;
 *  - les identifiants dupliqués (classId, classSubjectId, sessionId, academicYearId, enrollmentId) sont
 *    recopiés de l'évaluation / de l'inscription à la création et ne changent jamais ;
 *  - toute création, modification ou suppression écrit une ligne dans [GradeAuditEntry].
 *
 * Ces champs dupliqués servent les trois requêtes chaudes : saisie d'une matière, vue en temps réel
 * d'un élève (ou de son parent), calcul du bulletin d'une classe.
 */
@Document(collection = "grading_grades")
@CompoundIndexes(
    CompoundIndex(name = "uk_assessment_student", def = "{'schoolId': 1, 'assessmentId': 1, 'studentId': 1}", unique = true),
    CompoundIndex(name = "idx_classsubject_session", def = "{'schoolId': 1, 'classSubjectId': 1, 'sessionId': 1}"),
    CompoundIndex(name = "idx_student_year_session", def = "{'schoolId': 1, 'studentId': 1, 'academicYearId': 1, 'sessionId': 1}"),
    CompoundIndex(name = "idx_class_session", def = "{'schoolId': 1, 'classId': 1, 'sessionId': 1}")
)
data class Grade(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,
    var classId: String,
    var classSubjectId: String,
    var sessionId: String,
    var assessmentId: String,
    var studentId: String,
    var enrollmentId: String,

    /** Note brute sur le barème de l'évaluation. Null si pas de note chiffrée. Deux décimales maximum. */
    @Field(targetType = FieldType.DECIMAL128)
    var value: BigDecimal? = null,
    var status: GradeStatus = GradeStatus.SCORED,
    var comment: String? = null
) : TenantDocument()

/**
 * État d'une note. La façon dont chaque état est compté dans la moyenne dépend du barème
 * (GradingScheme.missingMarkPolicy et absencePolicy).
 */
enum class GradeStatus {
    /** Note saisie. */
    SCORED,

    /** Pas encore saisie. */
    PENDING,

    /** Absent à l'évaluation (non justifié). */
    ABSENT,

    /** Absent avec justificatif. */
    ABSENT_EXCUSED,

    /** Dispensé (ex. EPS) : jamais compté. */
    EXEMPTED
}
