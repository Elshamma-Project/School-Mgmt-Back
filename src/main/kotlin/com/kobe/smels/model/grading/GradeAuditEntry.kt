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
    /** Ce qui a changé : une note brute, une note de séquence totalisée, ou la fiche elle-même. */
    var target: AuditTarget = AuditTarget.GRADE,
    var gradeId: String? = null,
    var sequenceMarkId: String? = null,
    var assessmentId: String? = null,
    var classSubjectId: String,
    var sessionId: String,
    var studentId: String,
    var action: AuditAction,

    @Field(targetType = FieldType.DECIMAL128)
    var previousValue: BigDecimal? = null,
    @Field(targetType = FieldType.DECIMAL128)
    var newValue: BigDecimal? = null,
    var previousStatus: GradeStatus? = null,
    var newStatus: GradeStatus? = null,

    /** UserAccount.id de l'auteur. */
    var changedBy: String,
    var changedByRole: Role? = null,
    var changedAt: Instant,

    /** Motif : obligatoire quand `afterSessionClosed` est vrai (règle du service). */
    var reason: String? = null,

    /** La séquence était-elle CLOSED au moment du changement ? */
    var afterSessionClosed: Boolean = false
) : TenantDocument()

enum class AuditAction { CREATED, UPDATED, DELETED }

/** Objet modifié. */
enum class AuditTarget { GRADE, SEQUENCE_MARK, SHEET }
