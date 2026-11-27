package com.kobe.beesm.service.fees

import com.kobe.beesm.model.fees.SolvencyStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * BR-F03 / BR-F06 / Q-70 / Q-72 / docs/10 § 2 — matrice Solvable / En attente / Insolvable.
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
    fun `Q-70 unpaid enrollment is insolvent`() {
        val status = SolvencyCalculator.compute(
            today = LocalDate.of(2026, 9, 15),
            enrollmentFeeDue = enrollment,
            enrollmentFeePaid = BigDecimal.ZERO,
            installments = installments()
        )
        assertEquals(SolvencyStatus.INSOLVENT, status)
    }

    @Test
    fun `Q-72 before first due month with enrollment paid is solvent`() {
        val status = SolvencyCalculator.compute(
            today = LocalDate.of(2026, 8, 20),
            enrollmentFeeDue = enrollment,
            enrollmentFeePaid = enrollment,
            installments = installments()
        )
        assertEquals(SolvencyStatus.SOLVENT, status)
    }

    @Test
    fun `Q-72 before first due month unpaid enrollment is insolvent`() {
        val status = SolvencyCalculator.compute(
            today = LocalDate.of(2026, 8, 20),
            enrollmentFeeDue = enrollment,
            enrollmentFeePaid = BigDecimal.ZERO,
            installments = installments()
        )
        assertEquals(SolvencyStatus.INSOLVENT, status)
    }

    @Test
    fun `Q-70 current month unpaid is pending when previous settled`() {
        // Ex. plan : 15/11, T1 (oct) payée, T2 (nov) non → PENDING
        val status = SolvencyCalculator.compute(
            today = LocalDate.of(2026, 10, 12),
            enrollmentFeeDue = enrollment,
            enrollmentFeePaid = enrollment,
            installments = installments(paid1 = t1)
        )
        assertEquals(SolvencyStatus.PENDING, status)
    }

    /** Exemple plan Q-70 : échéances 31/10, 30/11, 31/12. */
    private fun installmentsOctNovDec(
        paid1: BigDecimal = BigDecimal.ZERO,
        paid2: BigDecimal = BigDecimal.ZERO,
        paid3: BigDecimal = BigDecimal.ZERO
    ) = listOf(
        SolvencyCalculator.InstallmentInput(1, t1, LocalDate.of(2026, 10, 31), paid1),
        SolvencyCalculator.InstallmentInput(2, t2, LocalDate.of(2026, 11, 30), paid2),
        SolvencyCalculator.InstallmentInput(3, t3, LocalDate.of(2026, 12, 31), paid3)
    )

    @Test
    fun `Q-70 november example T1 paid T2 unpaid is pending`() {
        val status = SolvencyCalculator.compute(
            today = LocalDate.of(2026, 11, 15),
            enrollmentFeeDue = enrollment,
            enrollmentFeePaid = enrollment,
            installments = installmentsOctNovDec(paid1 = t1)
        )
        assertEquals(SolvencyStatus.PENDING, status)
    }

    @Test
    fun `Q-70 november example T1 unpaid is insolvent`() {
        val status = SolvencyCalculator.compute(
            today = LocalDate.of(2026, 11, 15),
            enrollmentFeeDue = enrollment,
            enrollmentFeePaid = enrollment,
            installments = installmentsOctNovDec()
        )
        assertEquals(SolvencyStatus.INSOLVENT, status)
    }

    @Test
    fun `Q-70 current month paid is solvent`() {
        val status = SolvencyCalculator.compute(
            today = LocalDate.of(2026, 10, 12),
            enrollmentFeeDue = enrollment,
            enrollmentFeePaid = enrollment,
            installments = installments(paid1 = t1, paid2 = t2)
        )
        assertEquals(SolvencyStatus.SOLVENT, status)
    }

    @Test
    fun `Q-70 previous month unpaid is insolvent even if current paid`() {
        val status = SolvencyCalculator.compute(
            today = LocalDate.of(2026, 10, 12),
            enrollmentFeeDue = enrollment,
            enrollmentFeePaid = enrollment,
            installments = installments(paid2 = t2)
        )
        assertEquals(SolvencyStatus.INSOLVENT, status)
    }

    @Test
    fun `Q-70 previous month unpaid is insolvent`() {
        val status = SolvencyCalculator.compute(
            today = LocalDate.of(2026, 10, 12),
            enrollmentFeeDue = enrollment,
            enrollmentFeePaid = enrollment,
            installments = installments()
        )
        assertEquals(SolvencyStatus.INSOLVENT, status)
    }

    @Test
    fun `Q-72 after last month unpaid installment is insolvent not pending`() {
        val status = SolvencyCalculator.compute(
            today = LocalDate.of(2026, 12, 5),
            enrollmentFeeDue = enrollment,
            enrollmentFeePaid = enrollment,
            installments = installments(paid1 = t1, paid2 = t2)
        )
        assertEquals(SolvencyStatus.INSOLVENT, status)
    }

    @Test
    fun `Q-72 after last month all paid is solvent`() {
        val status = SolvencyCalculator.compute(
            today = LocalDate.of(2026, 12, 5),
            enrollmentFeeDue = enrollment,
            enrollmentFeePaid = enrollment,
            installments = installments(paid1 = t1, paid2 = t2, paid3 = t3)
        )
        assertEquals(SolvencyStatus.SOLVENT, status)
    }

    @Test
    fun `Q-70 enrollment only schedule with no installments is solvent when paid`() {
        val status = SolvencyCalculator.compute(
            today = LocalDate.of(2026, 10, 1),
            enrollmentFeeDue = enrollment,
            enrollmentFeePaid = enrollment,
            installments = emptyList()
        )
        assertEquals(SolvencyStatus.SOLVENT, status)
    }

    @Test
    fun `BR-F03 due dates are end of months from year start for N installments`() {
        val yearStart = LocalDate.of(2026, 9, 7)
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 10, 31),
                LocalDate.of(2026, 11, 30)
            ),
            SolvencyCalculator.installmentDueDates(yearStart, 3)
        )
        assertEquals(LocalDate.of(2026, 12, 31), SolvencyCalculator.defaultDueDate(yearStart, 4))
        assertEquals(4, SolvencyCalculator.installmentDueDates(yearStart, 4).size)
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

    @Test
    fun `Q-81 unpaid overdue exam fee is insolvent`() {
        val exam = SolvencyCalculator.InstallmentInput(
            index = 1,
            amount = BigDecimal("15000"),
            dueDate = LocalDate.of(2026, 5, 31),
            paidAmount = BigDecimal.ZERO
        )
        val status = SolvencyCalculator.compute(
            today = LocalDate.of(2026, 6, 10),
            enrollmentFeeDue = enrollment,
            enrollmentFeePaid = enrollment,
            installments = installments(paid1 = t1, paid2 = t2, paid3 = t3),
            extraFees = listOf(exam)
        )
        assertEquals(SolvencyStatus.INSOLVENT, status)
    }

    @Test
    fun `Q-81 current month unpaid exam fee is pending`() {
        val exam = SolvencyCalculator.InstallmentInput(
            index = 1,
            amount = BigDecimal("15000"),
            dueDate = LocalDate.of(2026, 6, 30),
            paidAmount = BigDecimal.ZERO
        )
        val status = SolvencyCalculator.compute(
            today = LocalDate.of(2026, 6, 10),
            enrollmentFeeDue = enrollment,
            enrollmentFeePaid = enrollment,
            installments = installments(paid1 = t1, paid2 = t2, paid3 = t3),
            extraFees = listOf(exam)
        )
        assertEquals(SolvencyStatus.PENDING, status)
    }
}
