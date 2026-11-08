package com.kobe.smels.model.grading

import com.kobe.smels.model.shared.TenantDocument
import com.kobe.smels.model.user.Role
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import java.math.BigDecimal
import java.time.Instant

/**
 * Journal APPEND-ONLY des changements de notes : on n'écrit que de nouvelles lignes, on ne modifie
 * ni ne supprime jamais une ligne existante.
 *
 * Garantit la phrase imprimée sur le bulletin (« délivré sans rature ni surcharge ») : on peut
 * toujours dire qui a changé quelle note, quand, de quelle valeur à quelle valeur, et si la séquence
 * était déjà clôturée.
 */
@Document(collection = "grading_audit_entries")
@CompoundIndexes(
    CompoundIndex(name = "idx_grade_time", def = "{'schoolId': 1, 'gradeId': 1, 'changedAt': -1}"),
    CompoundIndex(name = "idx_mark_time", def = "{'schoolId': 1, 'sequenceMarkId': 1, 'changedAt': -1}"),
    CompoundIndex(name = "idx_session_closed", def = "{'schoolId': 1, 'sessionId': 1, 'afterSessionClosed': 1}"),
    CompoundIndex(name = "idx_student_time", def = "{'schoolId': 1, 'studentId': 1, 'changedAt': -1}")
)
data class GradeAuditEntry(
    @Id
    var id: String? = null,
    override var schoolId: String,
    /** Ce qui a changé : note brute, note de séquence, fiche de travail, ou fiche de report. */
    var target: AuditTarget = AuditTarget.GRADE,
    var gradeId: String? = null,
    var sequenceMarkId: String? = null,
    var assessmentId: String? = null,
    var reportSheetId: String? = null,
    var classSubjectId: String,
    /** Null pour les transitions de fiche de report (niveau trimestre). */
    var sessionId: String? = null,
    /** Null pour les transitions de statut de fiche de report (pas une ligne élève). */
    var studentId: String? = null,
    var action: AuditAction,

    @Field(targetType = FieldType.DECIMAL128)
    var previousValue: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128)
    var newValue: BigDecimal? = null,
    var previousStatus: GradeStatus? = null,
    var newStatus: GradeStatus? = null,
    var previousReportStatus: ReportSheetStatus? = null,
    var newReportStatus: ReportSheetStatus? = null,

    /** UserAccount.id de l'auteur. */
    var changedBy: String,
    var changedByRole: Role? = null,
    var changedAt: Instant,

    /** Motif : obligatoire quand `afterSessionClosed` est vrai, ou correction sur fiche déjà soumise/visée. */
    var reason: String? = null,

    /** La séquence était-elle CLOSED au moment du changement ? */
    var afterSessionClosed: Boolean = false,

    /**
     * Approbation du Principal (directeur, Q-38) pour une correction sur une fiche de report
     * déjà SUBMITTED / visée.
     */
    var approvedBy: String? = null,
    var approvedAt: Instant? = null
) : TenantDocument()

enum class AuditAction { CREATED, UPDATED, DELETED, SUBMITTED, VISAED, REJECTED, REGENERATED }

/** Objet modifié. */
enum class AuditTarget { GRADE, SEQUENCE_MARK, SHEET, REPORT_SHEET }
