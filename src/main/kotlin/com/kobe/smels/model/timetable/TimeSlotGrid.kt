package com.kobe.smels.model.timetable

import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.TenantDocument
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Grille horaire de l'école : les périodes de cours, les pauses et les temps de service d'une journée type.
 *
 * Exemple observé (emplois du temps 2026-2027, identique du lundi au vendredi pour toutes les classes) :
 *
 *   P1 7:35-8:25 · P2 8:25-9:15 · P3 9:15-10:05 · PREMIÈRE PAUSE 10:05-10:20 ·
 *   P4 10:20-11:10 · P5 11:10-12:00 · P6 12:00-12:50 · DEUXIÈME PAUSE 12:50-13:20 ·
 *   P7 13:20-14:20 · P8 14:20-15:10 · NETTOYAGE GÉNÉRAL 15:10-15:30 · RENTRÉE (sortie).
 *
 * Une période de cours dure en général 50 minutes ; P7 en dure 60. Les heures ne sont donc pas calculables :
 * elles sont stockées. Des cours hors grille (ex. samedi 8h-10h, observé en 1ère C/D) sont possibles dans
 * [TimetableEntry] via customStart / customEnd.
 */
@Document(collection = "timetable_grids")
@CompoundIndexes(
    CompoundIndex(name = "idx_school_year", def = "{'schoolId': 1, 'academicYearId': 1}")
)
data class TimeSlotGrid(
    @Id
    var id: String? = null,
    override var schoolId: String,
    var academicYearId: String,
    var name: String,

    /** Jours de cours (lundi à vendredi observé ; le samedi existe en exception). */
    var days: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    ),
    var periods: List<GridPeriod> = emptyList(),
    var active: Boolean = true
) : TenantDocument()

/** Une ligne de la grille. Les lignes de type LESSON sont numérotées (1, 2, 3...). */
data class GridPeriod(
    /** Numéro de période de cours (1..8). Null pour une pause ou un temps de service. */
    val number: Int? = null,
    val kind: PeriodKind,
    val start: LocalTime,
    val end: LocalTime,
    /** « PREMIÈRE PAUSE / FIRST BREAK », « NETTOYAGE GÉNÉRAL / GENERAL CLEAN UP »... */
    val label: LocalizedText? = null
)

enum class PeriodKind {
    LESSON,
    BREAK,

    /** Nettoyage général (observé en fin de journée). */
    CLEANUP,

    /** Fin de journée (« RENTRÉE » / « CLOSING »). */
    CLOSING,
    ASSEMBLY
}
