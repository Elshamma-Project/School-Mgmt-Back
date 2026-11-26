package com.kobe.beesm.service.fees

import com.kobe.beesm.model.fees.FeeReminderKind
import com.kobe.beesm.model.fees.FeesPolicy
import com.kobe.beesm.model.fees.SolvencyStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Planification pure des rappels / pénalités / exclusion (BR-F10…F14) — testable sans Mongo.
 */
object FeesReminderPlanner {

    data class InstallmentView(
        val index: Int,
        val amount: BigDecimal,
        val dueDate: LocalDate,
        val paidAmount: BigDecimal,
        val settled: Boolean
    )

    data class ChildAccountView(
        val studentId: String,
        val displayName: String,
        val matricule: String,
        val active: Boolean,
        val solvency: SolvencyStatus,
        val enrollmentFeeDue: BigDecimal,
        val enrollmentFeePaid: BigDecimal,
        val enrollmentSettled: Boolean,
        val installments: List<InstallmentView>,
        val currency: String
    )

    data class ReminderTrigger(
        val kind: FeeReminderKind,
        /** Jours avant échéance (BEFORE_DUE) ; null sinon. */
        val daysBeforeDue: Int? = null,
        /** Ancre (dueDate ou insolvencySince) pour déduplication. */
        val anchorDate: LocalDate,
        val severityHint: SeverityHint
    )

    enum class SeverityHint {
        INFO,
        WARNING,
        URGENT
    }

    /**
     * Déclencheurs de rappel tranche pour un enfant encore concerné (PENDING / avant échéance).
     * Un seul déclencheur max par jour (le plus « proche » / urgent).
     */
    fun installmentReminderTriggers(
        today: LocalDate,
        policy: FeesPolicy,
        child: ChildAccountView
    ): List<ReminderTrigger> {
        if (!child.active) return emptyList()
        if (child.solvency == SolvencyStatus.NOT_APPLICABLE) return emptyList()
        if (child.solvency == SolvencyStatus.INSOLVENT) return emptyList()

        val unpaidUpcoming = child.installments
            .filter { !it.settled && !it.dueDate.isBefore(today) }
            .minByOrNull { it.dueDate }
            ?: return emptyList()

        val days = ChronoUnit.DAYS.between(today, unpaidUpcoming.dueDate).toInt()
        if (days < 0) return emptyList()

        if (days == 0 && policy.remindOnDueDate) {
            return listOf(
                ReminderTrigger(
                    kind = FeeReminderKind.ON_DUE,
                    daysBeforeDue = 0,
                    anchorDate = unpaidUpcoming.dueDate,
                    severityHint = SeverityHint.URGENT
                )
            )
        }

        val configured = policy.reminderDaysBeforeDue
            .map { it.coerceAtLeast(0) }
            .distinct()
            .sortedDescending()
        if (days !in configured) return emptyList()

        val hint = when {
            days >= 15 -> SeverityHint.INFO
            days >= 10 -> SeverityHint.WARNING
            else -> SeverityHint.URGENT
        }
        return listOf(
            ReminderTrigger(
                kind = FeeReminderKind.BEFORE_DUE,
                daysBeforeDue = days,
                anchorDate = unpaidUpcoming.dueDate,
                severityHint = hint
            )
        )
    }

    /** Premier jour d’insolvabilité dérivé des échéances / inscription. */
    fun deriveInsolvencySince(today: LocalDate, child: ChildAccountView): LocalDate? {
        if (child.solvency != SolvencyStatus.INSOLVENT) return null
        val candidates = mutableListOf<LocalDate>()
        if (!child.enrollmentSettled) {
            // Inscription due dès le début d’année côté métier : ancre = today si déjà insolvable.
            candidates += today
        }
        child.installments
            .filter { !it.settled && it.dueDate.isBefore(today) }
            .forEach { candidates += it.dueDate.plusDays(1) }
        return candidates.minOrNull()
    }

    /** Semaines complètes écoulées depuis [insolvencySince] (0 le premier jour). */
    fun completedPenaltyWeeks(insolvencySince: LocalDate, today: LocalDate): Int {
        if (today.isBefore(insolvencySince)) return 0
        val days = ChronoUnit.DAYS.between(insolvencySince, today).toInt()
        return (days / 7).coerceAtLeast(0)
    }

    fun shouldSendInsolventNotice(
        today: LocalDate,
        insolvencySince: LocalDate,
        alreadyNotifiedOn: LocalDate?
    ): Boolean {
        if (today != insolvencySince) return false
        return alreadyNotifiedOn != insolvencySince
    }

    fun shouldWarnExclusion(
        today: LocalDate,
        insolvencySince: LocalDate,
        policy: FeesPolicy,
        warningAlreadySent: Boolean
    ): Boolean {
        if (!policy.exclusionEnabled || warningAlreadySent) return false
        val warnDays = policy.exclusionWarningDaysBefore.coerceAtLeast(0)
        val exclusionDay = insolvencySince.plusDays(policy.maxInsolvencyGraceDays.toLong())
        val warnDay = exclusionDay.minusDays(warnDays.toLong())
        return !today.isBefore(warnDay) && today.isBefore(exclusionDay)
    }

    fun shouldExclude(
        today: LocalDate,
        insolvencySince: LocalDate,
        policy: FeesPolicy,
        alreadyExcluded: Boolean
    ): Boolean {
        if (!policy.exclusionEnabled || alreadyExcluded) return false
        val exclusionDay = insolvencySince.plusDays(policy.maxInsolvencyGraceDays.toLong())
        return !today.isBefore(exclusionDay)
    }

    fun nextPenaltyWeekIndexes(
        insolvencySince: LocalDate,
        today: LocalDate,
        alreadyAccrued: Int
    ): List<Int> {
        val completed = completedPenaltyWeeks(insolvencySince, today)
        if (completed <= alreadyAccrued) return emptyList()
        return ((alreadyAccrued + 1)..completed).toList()
    }

    fun weekStartDate(insolvencySince: LocalDate, weekIndex: Int): LocalDate =
        insolvencySince.plusDays(((weekIndex - 1).coerceAtLeast(0) * 7).toLong())
}
