package com.kobe.beesm.service.fees

import com.kobe.beesm.model.fees.FeeReminderKind
import com.kobe.beesm.model.fees.FeesPolicy
import com.kobe.beesm.model.fees.SolvencyStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class FeesReminderPlannerTest {

    private val policy = FeesPolicy(
        schoolId = "school-1",
        reminderDaysBeforeDue = mutableListOf(15, 10, 5, 3, 2, 1),
        remindOnDueDate = true,
        weeklyPenaltyAmount = BigDecimal("1000"),
        maxInsolvencyGraceDays = 28,
        exclusionEnabled = true,
        exclusionWarningDaysBefore = 7
    )

    @Test
    fun `BR-F10 reminder 15 days before unpaid installment`() {
        val due = LocalDate.of(2026, 9, 30)
        val today = due.minusDays(15)
        val child = child(
            solvency = SolvencyStatus.PENDING,
            installments = listOf(
                inst(1, due, paid = BigDecimal.ZERO)
            )
        )
        val triggers = FeesReminderPlanner.installmentReminderTriggers(today, policy, child)
        assertEquals(1, triggers.size)
        assertEquals(FeeReminderKind.BEFORE_DUE, triggers[0].kind)
        assertEquals(15, triggers[0].daysBeforeDue)
        assertEquals(FeesReminderPlanner.SeverityHint.INFO, triggers[0].severityHint)
    }

    @Test
    fun `BR-F10 urgent at 5 days and last three days`() {
        val due = LocalDate.of(2026, 10, 31)
        for (days in listOf(5, 3, 2, 1)) {
            val triggers = FeesReminderPlanner.installmentReminderTriggers(
                due.minusDays(days.toLong()),
                policy,
                child(
                    solvency = SolvencyStatus.PENDING,
                    installments = listOf(inst(2, due, paid = BigDecimal.ZERO))
                )
            )
            assertEquals(1, triggers.size, "days=$days")
            assertEquals(days, triggers[0].daysBeforeDue)
            assertEquals(FeesReminderPlanner.SeverityHint.URGENT, triggers[0].severityHint)
        }
    }

    @Test
    fun `BR-F10 no reminder on non configured day`() {
        val due = LocalDate.of(2026, 9, 30)
        val triggers = FeesReminderPlanner.installmentReminderTriggers(
            due.minusDays(7),
            policy,
            child(
                solvency = SolvencyStatus.PENDING,
                installments = listOf(inst(1, due, paid = BigDecimal.ZERO))
            )
        )
        assertTrue(triggers.isEmpty())
    }

    @Test
    fun `BR-F10 no installment reminder when already insolvent`() {
        val due = LocalDate.of(2026, 9, 30)
        val triggers = FeesReminderPlanner.installmentReminderTriggers(
            due.minusDays(5),
            policy,
            child(
                solvency = SolvencyStatus.INSOLVENT,
                installments = listOf(inst(1, due, paid = BigDecimal.ZERO))
            )
        )
        assertTrue(triggers.isEmpty())
    }

    @Test
    fun `BR-F12 penalty weeks complete after 7 days`() {
        val since = LocalDate.of(2026, 10, 1)
        assertEquals(0, FeesReminderPlanner.completedPenaltyWeeks(since, since))
        assertEquals(0, FeesReminderPlanner.completedPenaltyWeeks(since, since.plusDays(6)))
        assertEquals(1, FeesReminderPlanner.completedPenaltyWeeks(since, since.plusDays(7)))
        assertEquals(2, FeesReminderPlanner.completedPenaltyWeeks(since, since.plusDays(14)))
        assertEquals(listOf(1, 2), FeesReminderPlanner.nextPenaltyWeekIndexes(since, since.plusDays(14), 0))
        assertEquals(listOf(2), FeesReminderPlanner.nextPenaltyWeekIndexes(since, since.plusDays(14), 1))
    }

    @Test
    fun `BR-F14 exclusion after grace and warning window`() {
        val since = LocalDate.of(2026, 11, 1)
        val exclusionDay = since.plusDays(28)
        assertFalse(
            FeesReminderPlanner.shouldExclude(exclusionDay.minusDays(1), since, policy, false)
        )
        assertTrue(FeesReminderPlanner.shouldExclude(exclusionDay, since, policy, false))
        assertTrue(
            FeesReminderPlanner.shouldWarnExclusion(
                exclusionDay.minusDays(7),
                since,
                policy,
                warningAlreadySent = false
            )
        )
        assertFalse(
            FeesReminderPlanner.shouldWarnExclusion(
                exclusionDay.minusDays(7),
                since,
                policy,
                warningAlreadySent = true
            )
        )
    }

    private fun child(
        solvency: SolvencyStatus,
        installments: List<FeesReminderPlanner.InstallmentView>
    ) = FeesReminderPlanner.ChildAccountView(
        studentId = "stu-1",
        displayName = "DOE Jane",
        matricule = "M001",
        active = true,
        solvency = solvency,
        enrollmentFeeDue = BigDecimal("10000"),
        enrollmentFeePaid = BigDecimal("10000"),
        enrollmentSettled = true,
        installments = installments,
        currency = "XAF"
    )

    private fun inst(index: Int, due: LocalDate, paid: BigDecimal) =
        FeesReminderPlanner.InstallmentView(
            index = index,
            amount = BigDecimal("50000"),
            dueDate = due,
            paidAmount = paid,
            settled = paid.compareTo(BigDecimal("50000")) >= 0
        )
}
