package com.kobe.beesm.model.fees

/**
 * Solvabilité dérivée (non stockée) — docs/10 § 2, BR-F06.
 */
enum class SolvencyStatus {
    /** Tranche du mois en cours payée ; aucune échéance échue impayée. */
    SOLVENT,

    /** Tranche du mois en cours non payée ; mois précédents OK. */
    PENDING,

    /** Au moins une échéance échue (ou inscription) impayée. */
    INSOLVENT,

    /** Pas d’inscription année / pas de barème. */
    NOT_APPLICABLE
}

enum class FeePaymentKind {
    ENROLLMENT,
    INSTALLMENT
}

enum class FeePaymentMethod {
    CASH,
    MOBILE_MONEY,
    BANK_TRANSFER,
    OTHER
}

enum class FeePaymentStatus {
    RECORDED,
    VOID
}
