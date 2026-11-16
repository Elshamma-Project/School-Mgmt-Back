package com.kobe.smels.configuration.seed

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Miroir de `docs/seed-private/edt-2026-2027-extraction.json` (extraction PDF).
 * Champs inconnus ignorés ; structure alignée sur le extracteur (teachers, aliases, entries…).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EdtExtractionFile(
    val academicYearLabel: String? = null,
    val teachers: List<EdtTeacher> = emptyList(),
    val printedAliasMap: Map<String, String> = emptyMap(),
    val weeklyPeriodsByClassAndAlias: Map<String, Map<String, Int>> = emptyMap(),
    val jointSessions: List<EdtJointSession> = emptyList(),
    val entries: List<EdtEntry> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EdtTeacher(
    val key: String,
    val civility: String? = null,
    val printedName: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EdtJointSession(
    val id: String? = null,
    val hint: String? = null,
    val classNames: List<String> = emptyList(),
    val day: String? = null,
    val startPeriod: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EdtEntry(
    val `class`: String,
    val day: String,
    val startPeriod: Int? = null,
    val periodCount: Int = 1,
    val customStart: String? = null,
    val customEnd: String? = null,
    val jointSessionHint: String? = null,
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
                    labelEn = labelEn
                )
            )
        }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class EdtEntryItem(
    val printedAlias: String? = null,
    val teacherKey: String? = null,
    val optionGroupCode: String? = null,
    val optionCode: String? = null,
    val kind: String? = null,
    val activityType: String? = null,
    val labelFr: String? = null,
    val labelEn: String? = null
)
