package com.kobe.smels.model.notification

import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * Signalement officiel enseignant → administration / direction (BR-82).
 *
 * Canal unidirectionnel : pas de fil de discussion, pas d’échange entre enseignants.
 * L’administration accuse réception puis clôture.
 */
@Document(collection = "notification_staff_signals")
@CompoundIndexes(
    CompoundIndex(
        name = "idx_status_created",
        def = "{'schoolId': 1, 'status': 1, 'createdAt': -1}"
    ),
    CompoundIndex(
        name = "idx_author_created",
        def = "{'schoolId': 1, 'authorAccountId': 1, 'createdAt': -1}"
    )
)
data class StaffSignal(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var authorAccountId: String,
    var authorStaffId: String? = null,
    var authorLabel: String,

    var category: StaffSignalCategory,
    var subject: String,
    var message: String,

    var relatedSessionId: String? = null,
    var relatedSessionNumber: Int? = null,
    var relatedClassSubjectId: String? = null,

    var status: StaffSignalStatus = StaffSignalStatus.OPEN,
    var createdAt: Instant = Instant.now(),
    var acknowledgedBy: String? = null,
    var acknowledgedAt: Instant? = null,
    var closedBy: String? = null,
    var closedAt: Instant? = null,
    var adminNote: String? = null
) : TenantDocument()

enum class StaffSignalCategory {
    DATA_ERROR,
    GRADE_ISSUE,
    MISSING_INFO,
    ACCESS_ISSUE,
    OTHER
}

enum class StaffSignalStatus {
    OPEN,
    ACKNOWLEDGED,
    CLOSED
}
