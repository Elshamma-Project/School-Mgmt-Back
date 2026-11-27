package com.kobe.beesm.model.fees

import com.kobe.beesm.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import java.math.BigDecimal

/**
 * Barème **général** (année / école) — modèle central pour amorcer les niveaux (BR-F16).
 * Les niveaux sans barème individuel reçoivent une copie à l’enregistrement ;
 * les niveaux déjà personnalisés se synchronisent **manuellement**.
 */
@Document(collection = "fees_schedule_defaults")
@CompoundIndexes(
    CompoundIndex(
        name = "uk_school_year_defaults",
        def = "{'schoolId': 1, 'academicYearId': 1}",
        unique = true
    )
)
data class TuitionScheduleDefaults(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,
    @Field(targetType = FieldType.DECIMAL128)
    var enrollmentFee: BigDecimal,
    var installments: MutableList<TuitionInstallment> = mutableListOf(),
    var currency: String = "XAF"
) : TenantDocument()
