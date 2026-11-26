package com.kobe.beesm.model.fees

import com.kobe.beesm.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * État scolarité persisté pour rappels / pénalités / exclusion (BR-F11…F14).
 * La solvabilité affichée reste dérivée ; ce document ne fait que tracer les effets de bord.
 */
@Document(collection = "fees_student_states")
@CompoundIndexes(
    CompoundIndex(
        name = "uk_school_year_student",
        def = "{'schoolId': 1, 'academicYearId': 1, 'studentId': 1}",
        unique = true
    )
)
data class FeeStudentFeeState(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,
    var studentId: String,
    /** Premier jour calendaire (Douala) où le statut dérivé est devenu INSOLVENT. */
    var insolvencySince: LocalDate? = null,
    /** Nombre de semaines de pénalité déjà comptabilisées. */
    var penaltyWeeksAccrued: Int = 0,
    @Field(targetType = FieldType.DECIMAL128)
    var penaltyTotalAccrued: BigDecimal = BigDecimal.ZERO,
    var exclusionWarningSent: Boolean = false,
    var excludedAt: Instant? = null,
    var exclusionReasonCode: String? = null
) : TenantDocument()
