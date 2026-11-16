package com.kobe.smels.service.timetable

import com.kobe.smels.exception.BusinessRuleException
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Clé d’unicité d’une case : période grille (`P4`) ou hors grille (`T0730`).
 */
internal object SlotKeys {
    private val timeFmt = DateTimeFormatter.ofPattern("HHmm")

    fun fromPeriod(startPeriod: Int): String {
        if (startPeriod < 1) {
            throw BusinessRuleException("INVALID_PERIOD", "startPeriod must be >= 1")
        }
        return "P$startPeriod"
    }

    fun fromCustomStart(customStart: LocalTime): String = "T${customStart.format(timeFmt)}"

    fun resolve(startPeriod: Int?, customStart: LocalTime?): String =
        when {
            startPeriod != null && customStart != null ->
                throw BusinessRuleException(
                    "SLOT_AMBIGUOUS",
                    "Provide either startPeriod or customStart, not both"
                )
            startPeriod != null -> fromPeriod(startPeriod)
            customStart != null -> fromCustomStart(customStart)
            else ->
                throw BusinessRuleException(
                    "SLOT_REQUIRED",
                    "Either startPeriod or customStart is required"
                )
        }
}
