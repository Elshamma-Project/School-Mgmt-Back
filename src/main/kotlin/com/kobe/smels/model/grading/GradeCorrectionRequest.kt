package com.kobe.smels.model.grading

import com.kobe.smels.model.shared.TenantDocument
import org.bson.types.Binary
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import java.math.BigDecimal
import java.time.Instant

/**
 * Demande de correction de note après clôture de séquence (BR-80).
 *
 * L’enseignant (titulaire) propose une nouvelle valeur + motif + pièces jointes.
 * Seul le directeur (Principal) peut **approuver** et rendre la modification persistante.
 */
@Document(collection = "grading_correction_requests")
@CompoundIndexes(
    CompoundIndex(
        name = "idx_status_requested",
        def = "{'schoolId': 1, 'status': 1, 'requestedAt': -1}"
    ),
    CompoundIndex(
        name = "idx_session_classsubject",
        def = "{'schoolId': 1, 'sessionId': 1, 'classSubjectId': 1, 'status': 1}"
    ),
    CompoundIndex(
        name = "idx_pending_target",
        def = "{'schoolId': 1, 'sessionId': 1, 'classSubjectId': 1, 'studentId': 1, 'assessmentId': 1, 'status': 1}"
    )
)
data class GradeCorrectionRequest(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,
    var sessionId: String,
    var sessionNumber: Int,
    var classId: String,
    var classSubjectId: String,
    var assessmentId: String,
    var studentId: String,
    var enrollmentId: String? = null,

    /** Libellés figés pour revue directeur (pas de jointure). */
    var studentLabel: String,
    var classLabel: String? = null,
    var subjectLabelFr: String? = null,
    var subjectLabelEn: String? = null,
    var teacherId: String? = null,

    var previousStatus: GradeStatus = GradeStatus.PENDING,
    @Field(targetType = FieldType.DECIMAL128)
    var previousValue: BigDecimal? = null,
    var proposedStatus: GradeStatus = GradeStatus.SCORED,
    @Field(targetType = FieldType.DECIMAL128)
    var proposedValue: BigDecimal? = null,

    /** Motif obligatoire (min. 15 caractères). */
    var reason: String,
    var evidence: List<GradeCorrectionEvidenceMeta> = emptyList(),

    var status: GradeCorrectionStatus = GradeCorrectionStatus.PENDING,
    var requestedBy: String,
    var requestedAt: Instant = Instant.now(),
    var reviewedBy: String? = null,
    var reviewedAt: Instant? = null,
    var reviewNote: String? = null
) : TenantDocument()

enum class GradeCorrectionStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}

/** Métadonnées d’une pièce jointe (le binaire est dans [GradeCorrectionEvidenceFile]). */
data class GradeCorrectionEvidenceMeta(
    val id: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val sha256: String,
    val capturedAt: Instant = Instant.now()
)

@Document(collection = "grading_correction_evidence")
@CompoundIndexes(
    CompoundIndex(name = "idx_request", def = "{'schoolId': 1, 'requestId': 1}")
)
data class GradeCorrectionEvidenceFile(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var requestId: String,
    var fileName: String,
    var contentType: String,
    var sizeBytes: Long,
    var sha256: String,
    var data: Binary,
    var uploadedAt: Instant = Instant.now()
) : TenantDocument()
