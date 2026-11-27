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
    INSTALLMENT,
    /**
     * Frais supplémentaires du barème (examens, etc.) — [FeePayment.installmentIndex]
     * porte alors l’index de [TuitionExtraFee] (Q-81 / BR-F15).
     */
    EXTRA,
    /** Règlement des pénalités hebdomadaires accrues (Q-80). */
    PENALTY
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
