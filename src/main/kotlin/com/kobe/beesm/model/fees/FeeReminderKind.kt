package com.kobe.beesm.model.fees

/**
 * Types de rappels / alertes scolarité (idempotence outbox + inbox).
 */
enum class FeeReminderKind {
    /** N jours avant l’échéance (N dans [reminderDaysBeforeDue]). */
    BEFORE_DUE,
    /** Jour de l’échéance. */
    ON_DUE,
    /** Devenu insolvable (échéance dépassée, tranche impayée). */
    INSOLVENT,
    /** Rappel hebdomadaire pénalités tant qu’insolvable. */
    PENALTY_WEEKLY,
    /** Avertissement avant exclusion. */
    EXCLUSION_WARNING,
    /** Élève désactivé pour insolvabilité prolongée. */
    EXCLUDED
}
