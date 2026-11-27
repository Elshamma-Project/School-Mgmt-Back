package com.kobe.beesm.model.fees

import com.kobe.beesm.model.shared.LocalizedText
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
 * Barème de scolarité pour un **niveau** et une année (BR-F01, BR-F02, Q-67).
 * N tranches (1…12) avec échéances saisies (BR-F03, Q-68).
 * Frais optionnels (examens nationaux, etc.) : [extraFees] (Q-81 / BR-F15).
 */
@Document(collection = "fees_tuition_schedules")
@CompoundIndexes(
    CompoundIndex(
        name = "uk_school_year_level",
        def = "{'schoolId': 1, 'academicYearId': 1, 'levelId': 1}",
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
    /**
     * Niveau (Q-67). Nullable uniquement pour tolérer d’anciens documents `classId`
     * encore en base ; la couche service ignore / purge ceux sans `levelId`.
     */
    var levelId: String? = null,
    /** Libellé figé au moment de la création / maj (docs/08). */
    var levelLabel: String? = null,
    @Field(targetType = FieldType.DECIMAL128)
    var enrollmentFee: BigDecimal,
    var installments: MutableList<TuitionInstallment> = mutableListOf(),
    /**
     * Frais supplémentaires optionnels (0…6) — ex. BEPC / Probatoire / BAC (Q-81).
     * Absents = niveau sans frais d’examen.
     */
    var extraFees: MutableList<TuitionExtraFee> = mutableListOf(),
    var currency: String = "XAF",
    var active: Boolean = true
) : TenantDocument()

data class TuitionInstallment(
    /** 1…N (N ≤ 12). */
    var index: Int,
    @Field(targetType = FieldType.DECIMAL128)
    var amount: BigDecimal,
    var dueDate: LocalDate
)

/**
 * Frais hors tranches (examens nationaux, etc.) — paramétrable Direction (BR-F15 / Q-81).
 */
data class TuitionExtraFee(
    /** 1…M (M ≤ 6). */
    var index: Int,
    /** Code libre : BEPC, PROBATOIRE, BAC, GCE_OL… */
    var code: String? = null,
    var label: LocalizedText = LocalizedText(),
    @Field(targetType = FieldType.DECIMAL128)
    var amount: BigDecimal,
    var dueDate: LocalDate
)
