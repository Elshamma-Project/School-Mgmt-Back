package com.kobe.smels.model.reporting

/** Période couverte par un bulletin ou une appréciation. */
enum class ReportPeriodType {
    /** Une séquence (number = 1..6). */
    SEQUENCE,

    /** Un trimestre (number = 1..3). */
    TERM,

    /** Toute l'année (number = null). */
    ANNUAL
}

data class ReportPeriod(
    val type: ReportPeriodType,
    val number: Int? = null
)
