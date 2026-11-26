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
 * Pénalité hebdomadaire après insolvabilité (BR-F12). Une ligne = une semaine écoulée.
 */
@Document(collection = "fees_penalty_charges")
@CompoundIndexes(
    CompoundIndex(
        name = "uk_school_year_student_week",
        def = "{'schoolId': 1, 'academicYearId': 1, 'studentId': 1, 'weekIndex': 1}",
        unique = true
    )
)
data class FeePenaltyCharge(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,
    var studentId: String,
    /** Semaine 1 = 1ʳᵉ semaine complète après [insolvencySince]. */
    var weekIndex: Int,
    var weekStartDate: LocalDate,
    @Field(targetType = FieldType.DECIMAL128)
    var amount: BigDecimal,
    var currency: String = "XAF",
    var accruedAt: Instant = Instant.now()
) : TenantDocument()
