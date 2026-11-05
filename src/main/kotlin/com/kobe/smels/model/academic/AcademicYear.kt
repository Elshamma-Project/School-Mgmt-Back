package com.kobe.smels.model.academic

import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDate

/**
 * Année scolaire (« 2025-2026 »). Contient ses 3 trimestres.
 * Les 6 séquences d'évaluation (DEV1..DEV6) sont dans la collection `grading_sessions`.
 *
 * Règle : une seule année `current = true` par établissement (à garantir dans le service).
 */
@Document(collection = "academic_years")
@CompoundIndexes(
    CompoundIndex(name = "uk_school_label", def = "{'schoolId': 1, 'label': 1}", unique = true)
)
data class AcademicYear(
    @Id
    var id: String? = null,
    override var schoolId: String,

    /** Libellé, ex. "2025-2026". */
    var label: String,
    var startDate: LocalDate,
    var endDate: LocalDate,
    var current: Boolean = false,
    var status: AcademicYearStatus = AcademicYearStatus.PLANNED,
    var terms: List<Term> = emptyList()
) : TenantDocument()

/** Trimestre (1, 2 ou 3). Chaque trimestre regroupe 2 séquences : T1 = DEV1+DEV2, T2 = DEV3+DEV4, T3 = DEV5+DEV6. */
data class Term(
    val number: Int,
    val name: LocalizedText,
    val startDate: LocalDate,
    val endDate: LocalDate
)

enum class AcademicYearStatus { PLANNED, ACTIVE, CLOSED, ARCHIVED }
