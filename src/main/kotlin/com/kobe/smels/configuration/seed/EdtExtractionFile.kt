package com.kobe.smels.configuration.seed

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Miroir de `docs/seed-private/edt-2026-2027-extraction.json` (extraction PDF).
 * Champs inconnus ignorés ; structure alignée sur l’extracteur réel.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EdtExtractionFile(
    val _meta: EdtExtractionMeta? = null,
    val academicYearLabel: String? = null,
    val printedAliasMap: Map<String, EdtPrintedAliasInfo> = emptyMap(),
    val teachers: List<EdtTeacher> = emptyList(),
    val saturdayBlocks: List<EdtSaturdayBlock> = emptyList(),
    val weeklyPeriodsByClassAndAlias: Map<String, Map<String, Int>> = emptyMap(),
    val jointSessions: List<EdtJointSession> = emptyList(),
    val entries: List<EdtEntry> = emptyList()
) {
    fun resolvedYearLabel(fallback: String): String =
        academicYearLabel?.takeIf { it.isNotBlank() }
            ?: _meta?.academicYearLabel?.takeIf { it.isNotBlank() }
            ?: fallback
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class EdtExtractionMeta(
    val source: String? = null,
    val note: String? = null,
    val academicYearLabel: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EdtPrintedAliasInfo(
    val candidateSubjectCodes: List<String> = emptyList(),
    val nature: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EdtTeacher(
    val key: String,
    val civility: String? = null,
    val printedName: String,
    val printedVariants: List<String> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EdtSaturdayBlock(
    val `class`: String,
    val subject: String? = null,
    val teacherPrinted: String? = null,
    val start: String? = null,
    val end: String? = null,
    val hours: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EdtJointSession(
    val id: String? = null,
    val day: String? = null,
    /** Période de grille (extraction) — alias de [startPeriod]. */
    val period: Int? = null,
    val startPeriod: Int? = null,
    val teacherKey: String? = null,
    val printedAlias: String? = null,
    val classes: List<String> = emptyList(),
    val classNames: List<String> = emptyList(),
    val hint: String? = null
) {
    fun resolvedClasses(): List<String> = (classes + classNames).distinct()

    fun resolvedStartPeriod(): Int? = startPeriod ?: period
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class EdtEntry(
    val `class`: String,
    val section: String? = null,
    val day: String,
    val startPeriod: Int? = null,
    val periodCount: Int = 1,
    val customStart: String? = null,
    val customEnd: String? = null,
    val jointSessionHint: Any? = null,
    val jointSessionId: String? = null,
    /** Forme plate (une seule matière) si [items] est vide. */
    val printedAlias: String? = null,
    val teacherKey: String? = null,
    val optionGroupCode: String? = null,
    val optionCode: String? = null,
    val kind: String? = null,
    val activityType: String? = null,
    val labelFr: String? = null,
    val labelEn: String? = null,
    /** Une ou plusieurs options parallèles (LV2 ALL/ESP, etc.). */
    val items: List<EdtEntryItem> = emptyList()
) {
    fun resolvedItems(): List<EdtEntryItem> =
        if (items.isNotEmpty()) {
            items
        } else {
            listOf(
                EdtEntryItem(
                    printedAlias = printedAlias,
                    teacherKey = teacherKey,
                    optionGroupCode = optionGroupCode,
                    optionCode = optionCode,
                    kind = kind,
                    activityType = activityType,
                    labelFr = labelFr,
                    labelEn = labelEn,
                    jointSessionHint = jointSessionHint
                )
            )
        }

    fun resolvedJointHints(): List<String> =
        normalizeJointHints(jointSessionId ?: jointSessionHint)
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class EdtEntryItem(
    val printedAlias: String? = null,
    val subjectCodes: List<String> = emptyList(),
    val nature: String? = null,
    val teacher: EdtItemTeacher? = null,
    val teacherKey: String? = null,
    val optionGroupCode: String? = null,
    val optionCode: String? = null,
    val kind: String? = null,
    val activityType: String? = null,
    val labelFr: String? = null,
    val labelEn: String? = null,
    val jointSessionHint: Any? = null
) {
    fun resolvedTeacherKey(): String? =
        teacherKey?.trim()?.takeIf { it.isNotEmpty() }
            ?: teacher?.key?.trim()?.takeIf { it.isNotEmpty() }

    fun resolvedJointHints(): List<String> = normalizeJointHints(jointSessionHint)
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class EdtItemTeacher(
    val key: String? = null,
    val civility: String? = null,
    val printed: String? = null
)

internal fun normalizeJointHints(raw: Any?): List<String> =
    when (raw) {
        null -> emptyList()
        is String -> raw.trim().takeIf { it.isNotEmpty() }?.let { listOf(it) }.orEmpty()
        is Collection<*> ->
            raw.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
        else -> emptyList()
    }

/**
 * Normalise un code matière candidate de l’extraction vers un code catalogue SMELS.
 * Préfixe `?` = incertitude extracteur (ex. `?FRANCAIS`).
 */
internal fun normalizeExtractionSubjectCode(raw: String?): String? {
    val stripped = raw?.trim()?.removePrefix("?")?.trim().orEmpty()
    if (stripped.isEmpty()) return null
    return when (stripped.uppercase()) {
        "FRANCAIS", "FRANÇAIS" -> "LANG_FR" // TODO(Q-63) découpage EXP_* / LANG_FR
        "LITT", "LITTERATURE", "LITTÉRATURE" -> "LITT"
        "BIBLIO", "BIBLIOTHEQUE", "BIBLIOTHÈQUE" -> "BIBLIOTHEQUE"
        "MENAGE", "MENAGE_GENERAL", "MÉNAGE" -> "MENAGE_GENERAL"
        "HISTOIRE" -> "HIST"
        "GEOGRAPHIE", "GÉOGRAPHIE" -> "GEO"
        else -> stripped
    }
}
