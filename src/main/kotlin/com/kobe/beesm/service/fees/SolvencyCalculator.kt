package com.kobe.beesm.service.fees

import com.kobe.beesm.model.fees.SolvencyStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Calcul pur de solvabilité (docs/10 § 2, BR-F06, **Q-70 / Q-72 figés**) — testable sans Mongo.
 *
 * Comparaison par **mois civil** (`YearMonth`) des `dueDate` (pas le jour exact).
 * - Inscription non soldée → [SolvencyStatus.INSOLVENT]
 * - Tranche d’un mois civil **strictement antérieur** non soldée → INSOLVENT
 * - Tranche du mois civil **en cours** non soldée → PENDING
 * - Avant 1ʳᵉ / après dernière échéance (sans impayé passé) → SOLVENT (Q-72)
 * - Sinon → SOLVENT
 */
object SolvencyCalculator {

    data class InstallmentInput(
        val index: Int,
        val amount: BigDecimal,
        val dueDate: LocalDate,
        val paidAmount: BigDecimal
    )

    fun isSettled(due: BigDecimal, paid: BigDecimal): Boolean =
        paid.compareTo(due) >= 0

    fun compute(
        today: LocalDate,
        enrollmentFeeDue: BigDecimal,
        enrollmentFeePaid: BigDecimal,
        installments: List<InstallmentInput>,
        /** Frais optionnels (examens…) — même règle mois civil (Q-81 provisoire). */
        extraFees: List<InstallmentInput> = emptyList()
    ): SolvencyStatus {
        if (!isSettled(enrollmentFeeDue, enrollmentFeePaid)) {
            return SolvencyStatus.INSOLVENT
        }
        val dated = (installments + extraFees).sortedBy { it.dueDate }
        if (dated.isEmpty()) {
            return SolvencyStatus.SOLVENT
        }

        val todayYm = YearMonth.from(today)

        val unpaidOverdue = dated.any { item ->
            YearMonth.from(item.dueDate).isBefore(todayYm) &&
                !isSettled(item.amount, item.paidAmount)
        }
        if (unpaidOverdue) {
            return SolvencyStatus.INSOLVENT
        }

        val currentMonthItems = dated.filter { YearMonth.from(it.dueDate) == todayYm }
        if (currentMonthItems.isNotEmpty()) {
            val allSettled = currentMonthItems.all { isSettled(it.amount, it.paidAmount) }
            return if (allSettled) SolvencyStatus.SOLVENT else SolvencyStatus.PENDING
        }

        // Avant le 1er mois (échéances futures) ou après le dernier (échues déjà filtrées) :
        // inscription soldée + aucune échéance passée impayée → solvable.
        return SolvencyStatus.SOLVENT
    }

    /**
     * Dernier jour calendaire du mois de [yearStart] +(index−1) (BR-F03 / Q-68).
     * Suggestion UI / seed — les échéances réelles sont saisies sur le barème.
     */
    fun defaultDueDate(yearStart: LocalDate, index: Int): LocalDate {
        require(index in 1..12) { "Installment index must be 1..12" }
        return YearMonth.from(yearStart).plusMonths((index - 1).toLong()).atEndOfMonth()
    }

    /** Suggestions pour N tranches (1…[count]). */
    fun installmentDueDates(yearStart: LocalDate, count: Int = 3): List<LocalDate> {
        require(count in 1..12) { "Installment count must be 1..12" }
        return (1..count).map { defaultDueDate(yearStart, it) }
    }
}
