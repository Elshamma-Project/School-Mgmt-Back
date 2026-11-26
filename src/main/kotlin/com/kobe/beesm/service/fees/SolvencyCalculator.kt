package com.kobe.beesm.service.fees

import com.kobe.beesm.model.fees.SolvencyStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Calcul pur de solvabilité (docs/10 § 2, BR-F06) — testable sans Mongo.
 *
 * - Inscription non soldée → [SolvencyStatus.INSOLVENT]
 * - Tranche d’un mois civil **strictement antérieur** non soldée → INSOLVENT
 * - Tranche du mois civil **en cours** non soldée → PENDING
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
        installments: List<InstallmentInput>
    ): SolvencyStatus {
        if (!isSettled(enrollmentFeeDue, enrollmentFeePaid)) {
            return SolvencyStatus.INSOLVENT
        }
        if (installments.isEmpty()) {
            return SolvencyStatus.SOLVENT
        }

        val todayYm = YearMonth.from(today)
        val sorted = installments.sortedBy { it.dueDate }

        val unpaidOverdue = sorted.any { installment ->
            YearMonth.from(installment.dueDate).isBefore(todayYm) &&
                !isSettled(installment.amount, installment.paidAmount)
        }
        if (unpaidOverdue) {
            return SolvencyStatus.INSOLVENT
        }

        val currentMonth = sorted.find { YearMonth.from(it.dueDate) == todayYm }
        if (currentMonth != null) {
            return if (isSettled(currentMonth.amount, currentMonth.paidAmount)) {
                SolvencyStatus.SOLVENT
            } else {
                SolvencyStatus.PENDING
            }
        }

        // Avant le 1er mois de tranche, ou après le dernier : tout échu déjà contrôlé.
        val anyUnpaid = sorted.any { !isSettled(it.amount, it.paidAmount) }
        return if (anyUnpaid) SolvencyStatus.INSOLVENT else SolvencyStatus.SOLVENT
    }

    /**
     * Dernier jour calendaire des mois M0, M1, M2 à partir du mois de [yearStart]
     * (BR-F03 / Q-68).
     */
    fun installmentDueDates(yearStart: LocalDate): List<LocalDate> {
        val m0 = YearMonth.from(yearStart)
        return listOf(
            m0.atEndOfMonth(),
            m0.plusMonths(1).atEndOfMonth(),
            m0.plusMonths(2).atEndOfMonth()
        )
    }
}
