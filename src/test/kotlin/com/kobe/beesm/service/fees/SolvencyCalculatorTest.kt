package com.kobe.beesm.service.fees

import com.kobe.beesm.model.fees.SolvencyStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * BR-F03 / BR-F06 / docs/10 § 2 — matrice Solvable / En attente / Insolvable.
 */
class SolvencyCalculatorTest {

    private val enrollment = BigDecimal("25000")
    private val t1 = BigDecimal("50000")
    private val t2 = BigDecimal("50000")
    private val t3 = BigDecimal("50000")

    private fun installments(
        paid1: BigDecimal = BigDecimal.ZERO,
        paid2: BigDecimal = BigDecimal.ZERO,
        paid3: BigDecimal = BigDecimal.ZERO
    ) = listOf(
        SolvencyCalculator.InstallmentInput(1, t1, LocalDate.of(2026, 9, 30), paid1),
        SolvencyCalculator.InstallmentInput(2, t2, LocalDate.of(2026, 10, 31), paid2),
        SolvencyCalculator.InstallmentInput(3, t3, LocalDate.of(2026, 11, 30), paid3)
    )

    @Test
    fun `BR-F06 unpaid enrollment is insolvent`() {
        val status = SolvencyCalculator.compute(
            today = LocalDate.of(2026, 9, 15),
            enrollmentFeeDue = enrollment,
            enrollmentFeePaid = BigDecimal.ZERO,
            installments = installments()
        )
        assertEquals(SolvencyStatus.INSOLVENT, status)
    }

    @Test
    fun `BR-F06 before first month with enrollment paid is solvent`() {
        val status = SolvencyCalculator.compute(
            today = LocalDate.of(2026, 8, 20),
            enrollmentFeeDue = enrollment,
            enrollmentFeePaid = enrollment,
            installments = installments()
        )
        assertEquals(SolvencyStatus.SOLVENT, status)
    }

    @Test
    fun `BR-F06 current month unpaid is pending when previous settled`() {
        val status = SolvencyCalculator.compute(
            today = LocalDate.of(2026, 10, 12),
            enrollmentFeeDue = enrollment,
            enrollmentFeePaid = enrollment,
            installments = installments(paid1 = t1)
        )
        assertEquals(SolvencyStatus.PENDING, status)
    }

    @Test
    fun `BR-F06 current month paid is solvent`() {
        val status = SolvencyCalculator.compute(
            today = LocalDate.of(2026, 10, 12),
            enrollmentFeeDue = enrollment,
            enrollmentFeePaid = enrollment,
            installments = installments(paid1 = t1, paid2 = t2)
        )
        assertEquals(SolvencyStatus.SOLVENT, status)
    }

    @Test
    fun `BR-F06 previous month unpaid is insolvent`() {
        val status = SolvencyCalculator.compute(
            today = LocalDate.of(2026, 10, 12),
            enrollmentFeeDue = enrollment,
            enrollmentFeePaid = enrollment,
            installments = installments()
        )
        assertEquals(SolvencyStatus.INSOLVENT, status)
    }

    @Test
    fun `BR-F06 after last month unpaid installment is insolvent`() {
        val status = SolvencyCalculator.compute(
            today = LocalDate.of(2026, 12, 5),
            enrollmentFeeDue = enrollment,
            enrollmentFeePaid = enrollment,
            installments = installments(paid1 = t1, paid2 = t2)
        )
        assertEquals(SolvencyStatus.INSOLVENT, status)
    }

    @Test
    fun `BR-F06 after last month all paid is solvent`() {
        val status = SolvencyCalculator.compute(
            today = LocalDate.of(2026, 12, 5),
            enrollmentFeeDue = enrollment,
            enrollmentFeePaid = enrollment,
            installments = installments(paid1 = t1, paid2 = t2, paid3 = t3)
        )
        assertEquals(SolvencyStatus.SOLVENT, status)
    }

    @Test
    fun `BR-F03 due dates are end of three months from year start`() {
        val dues = SolvencyCalculator.installmentDueDates(LocalDate.of(2026, 9, 7))
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 10, 31),
                LocalDate.of(2026, 11, 30)
            ),
            dues
        )
    }

    @Test
    fun `partial installment below due is not settled`() {
        assertEquals(
            false,
            SolvencyCalculator.isSettled(t1, BigDecimal("10000"))
        )
        assertEquals(
            true,
            SolvencyCalculator.isSettled(t1, t1)
        )
    }
}
