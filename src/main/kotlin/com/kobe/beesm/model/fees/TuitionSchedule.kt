package com.kobe.beesm.model.fees

import com.kobe.beesm.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Barème de scolarité pour une classe et une année (BR-F01, BR-F02).
 * Échéances des 3 tranches = dernier jour des mois M0..M2 de la rentrée (BR-F03).
 */
@Document(collection = "fees_tuition_schedules")
@CompoundIndexes(
    CompoundIndex(
        name = "uk_school_year_class",
        def = "{'schoolId': 1, 'academicYearId': 1, 'classId': 1}",
        unique = true
    ),
    CompoundIndex(
        name = "idx_school_year",
        def = "{'schoolId': 1, 'academicYearId': 1, 'active': 1}"
    )
)
data class TuitionSchedule(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,
    var classId: String,
    /** Libellé figé au moment de la création / maj (docs/08). */
    var classLabel: String,
    @Field(targetType = FieldType.DECIMAL128)
    var enrollmentFee: BigDecimal,
    var installments: MutableList<TuitionInstallment> = mutableListOf(),
    var currency: String = "XAF",
    var active: Boolean = true
) : TenantDocument()

data class TuitionInstallment(
    /** 1, 2 ou 3. */
    var index: Int,
    @Field(targetType = FieldType.DECIMAL128)
    var amount: BigDecimal,
    var dueDate: LocalDate
)
