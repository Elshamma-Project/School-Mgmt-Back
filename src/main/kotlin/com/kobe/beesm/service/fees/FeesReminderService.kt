package com.kobe.beesm.service.fees

import com.kobe.beesm.dto.student.StudentGuardiansDto
import com.kobe.beesm.model.fees.FeePayment
import com.kobe.beesm.model.fees.FeePaymentKind
import com.kobe.beesm.model.fees.FeePaymentStatus
import com.kobe.beesm.model.fees.FeePenaltyCharge
import com.kobe.beesm.model.fees.FeeReminderKind
import com.kobe.beesm.model.fees.FeeStudentFeeState
import com.kobe.beesm.model.fees.FeesPolicy
import com.kobe.beesm.model.fees.SolvencyStatus
import com.kobe.beesm.model.fees.TuitionSchedule
import com.kobe.beesm.repository.fees.FeePaymentRepository
import com.kobe.beesm.repository.fees.FeePenaltyChargeRepository
import com.kobe.beesm.repository.fees.FeeStudentFeeStateRepository
import com.kobe.beesm.repository.fees.FeesPolicyRepository
import com.kobe.beesm.repository.fees.TuitionScheduleRepository
import com.kobe.beesm.service.academic.api.AcademicYearApi
import com.kobe.beesm.service.student.api.EnrollmentApi
import com.kobe.beesm.service.student.api.StudentApi
import com.kobe.beesm.service.user.api.UserAccountApi
import com.kobe.beesm.shared.outbox.OutboxWriter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Job quotidien scolarité : rappels parents groupés, pénalités, exclusion (BR-F10…F14).
 * Émet `fees.tuition.reminder` (outbox) — le module notification livre l’inbox.
 */
@Service
class FeesReminderService(
    private val feesPolicyService: FeesPolicyService,
    private val policyRepository: FeesPolicyRepository,
    private val scheduleRepository: TuitionScheduleRepository,
    private val paymentRepository: FeePaymentRepository,
    private val stateRepository: FeeStudentFeeStateRepository,
    private val penaltyRepository: FeePenaltyChargeRepository,
    private val academicYearApi: AcademicYearApi,
    private val enrollmentApi: EnrollmentApi,
    private val studentApi: StudentApi,
    private val userAccountApi: UserAccountApi,
    private val outboxWriter: OutboxWriter
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val zone: ZoneId = ZoneId.of("Africa/Douala")
    private val dayFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun runDaily(todayOverride: LocalDate? = null): Mono<Void> {
        val today = todayOverride ?: LocalDate.now(zone)
        return ensurePoliciesForSchoolsWithSchedules()
            .thenMany(policyRepository.findByActiveTrue())
            .concatMap { policy -> processSchool(policy, today) }
            .then()
            .doOnSuccess { log.info("fees.reminder.daily done today={}", today) }
            .doOnError { err -> log.error("fees.reminder.daily failed", err) }
    }

    /** Crée la politique par défaut pour toute école ayant au moins un barème actif. */
    private fun ensurePoliciesForSchoolsWithSchedules(): Mono<Void> =
        scheduleRepository.findByActiveTrue()
            .map { it.schoolId }
            .distinct()
            .concatMap { schoolId -> feesPolicyService.getOrCreate(schoolId) }
            .then()

    private fun processSchool(policy: FeesPolicy, today: LocalDate): Mono<Void> {
        val schoolId = policy.schoolId
        return academicYearApi.findCurrent(schoolId)
            .flatMap { year ->
                val academicYearId = year.id
                enrollmentApi.findEnrolledByYear(schoolId, academicYearId)
                    .collectList()
                    .flatMap { enrollments ->
                        if (enrollments.isEmpty()) {
                            return@flatMap Mono.empty()
                        }
                        val studentIds = enrollments.map { it.studentId }.distinct()
                        scheduleRepository
                            .findBySchoolIdAndAcademicYearIdAndActiveTrue(schoolId, academicYearId)
                            .collectMap({ it.classId }, { it })
                            .flatMap { schedules ->
                                paymentRepository
                                    .findBySchoolIdAndAcademicYearIdAndStudentIdInAndStatus(
                                        schoolId,
                                        academicYearId,
                                        studentIds,
                                        FeePaymentStatus.RECORDED
                                    )
                                    .collectMultimap({ it.studentId }, { it })
                                    .flatMap { paymentsByStudent ->
                                        studentApi.findGuardiansForStudents(schoolId, studentIds)
                                            .collectList()
                                            .flatMap { guardians ->
                                                stateRepository
                                                    .findBySchoolIdAndAcademicYearId(schoolId, academicYearId)
                                                    .collectMap({ it.studentId }, { it })
                                                    .flatMap { stateMap ->
                                                        val states = stateMap.toMutableMap()
                                                        val enrollmentByStudent =
                                                            enrollments.associateBy { it.studentId }
                                                        val guardianByStudent =
                                                            guardians.associateBy { it.studentId }
                                                        val children = studentIds.mapNotNull { studentId ->
                                                            val enrollment = enrollmentByStudent[studentId]
                                                                ?: return@mapNotNull null
                                                            val schedule = schedules[enrollment.classId]
                                                                ?: return@mapNotNull null
                                                            val meta = guardianByStudent[studentId]
                                                            toChildView(
                                                                studentId = studentId,
                                                                displayName = meta?.displayName ?: studentId,
                                                                matricule = meta?.matricule.orEmpty(),
                                                                active = meta?.active ?: true,
                                                                schedule = schedule,
                                                                payments = paymentsByStudent[studentId]
                                                                    ?.toList()
                                                                    .orEmpty(),
                                                                today = today
                                                            )
                                                        }
                                                        val guardianIds =
                                                            guardians.flatMap { it.guardianIds }.distinct()
                                                        userAccountApi
                                                            .findGuardianLinkedAccounts(schoolId, guardianIds)
                                                            .flatMap { linked ->
                                                                processChildren(
                                                                    schoolId = schoolId,
                                                                    academicYearId = academicYearId,
                                                                    policy = policy,
                                                                    today = today,
                                                                    children = children,
                                                                    guardians = guardians,
                                                                    linkedAccounts = linked.mapValues {
                                                                        it.value.accountId
                                                                    },
                                                                    states = states
                                                                )
                                                            }
                                                    }
                                            }
                                    }
                            }
                    }
            }
            .onErrorResume { err ->
                log.warn("fees.reminder schoolId={} skipped: {}", schoolId, err.message)
                Mono.empty()
            }
    }

    private fun processChildren(
        schoolId: String,
        academicYearId: String,
        policy: FeesPolicy,
        today: LocalDate,
        children: List<FeesReminderPlanner.ChildAccountView>,
        guardians: List<StudentGuardiansDto>,
        linkedAccounts: Map<String, String>,
        states: MutableMap<String, FeeStudentFeeState>
    ): Mono<Void> =
        Flux.fromIterable(children)
            .concatMap { child ->
                handleInsolvencyLifecycle(
                    schoolId = schoolId,
                    academicYearId = academicYearId,
                    policy = policy,
                    today = today,
                    child = child,
                    state = states[child.studentId],
                    states = states,
                    guardians = guardians,
                    linkedAccounts = linkedAccounts
                )
            }
            .then(
                emitGroupedParentReminders(
                    schoolId = schoolId,
                    academicYearId = academicYearId,
                    policy = policy,
                    today = today,
                    children = children,
                    guardians = guardians,
                    linkedAccounts = linkedAccounts,
                    states = states
                )
            )

    private fun handleInsolvencyLifecycle(
        schoolId: String,
        academicYearId: String,
        policy: FeesPolicy,
        today: LocalDate,
        child: FeesReminderPlanner.ChildAccountView,
        state: FeeStudentFeeState?,
        states: MutableMap<String, FeeStudentFeeState>,
        guardians: List<StudentGuardiansDto>,
        linkedAccounts: Map<String, String>
    ): Mono<Void> {
        if (child.solvency == SolvencyStatus.NOT_APPLICABLE) {
            return Mono.empty()
        }

        if (child.solvency != SolvencyStatus.INSOLVENT) {
            val current = state ?: return Mono.empty()
            if (current.excludedAt != null) return Mono.empty()
            if (current.insolvencySince == null && !current.exclusionWarningSent) {
                return Mono.empty()
            }
            current.insolvencySince = null
            current.exclusionWarningSent = false
            return stateRepository.save(current).doOnNext { states[child.studentId] = it }.then()
        }

        val derivedSince = FeesReminderPlanner.deriveInsolvencySince(today, child) ?: today
        val current = state ?: FeeStudentFeeState(
            schoolId = schoolId,
            academicYearId = academicYearId,
            studentId = child.studentId
        )
        if (current.insolvencySince == null) {
            current.insolvencySince = derivedSince
        }
        val insolvencySince = current.insolvencySince!!

        var chain: Mono<Void> = stateRepository.save(current)
            .doOnNext { states[child.studentId] = it }
            .then()

        if (today == insolvencySince) {
            chain = chain.then(
                emitToGuardians(
                    schoolId = schoolId,
                    academicYearId = academicYearId,
                    today = today,
                    kind = FeeReminderKind.INSOLVENT,
                    anchorDate = insolvencySince,
                    child = child,
                    policy = policy,
                    guardians = guardians,
                    linkedAccounts = linkedAccounts,
                    states = states,
                    extra = mapOf(
                        "weeklyPenaltyAmount" to policy.weeklyPenaltyAmount.toPlainString(),
                        "maxInsolvencyGraceDays" to policy.maxInsolvencyGraceDays
                    )
                )
            )
        }

        val weekIndexes = FeesReminderPlanner.nextPenaltyWeekIndexes(
            insolvencySince = insolvencySince,
            today = today,
            alreadyAccrued = current.penaltyWeeksAccrued
        )
        if (weekIndexes.isNotEmpty() && policy.weeklyPenaltyAmount.compareTo(BigDecimal.ZERO) > 0) {
            chain = chain.then(
                Flux.fromIterable(weekIndexes)
                    .concatMap { weekIndex ->
                        val charge = FeePenaltyCharge(
                            schoolId = schoolId,
                            academicYearId = academicYearId,
                            studentId = child.studentId,
                            weekIndex = weekIndex,
                            weekStartDate = FeesReminderPlanner.weekStartDate(insolvencySince, weekIndex),
                            amount = policy.weeklyPenaltyAmount,
                            currency = policy.currency,
                            accruedAt = Instant.now()
                        )
                        penaltyRepository.save(charge)
                            .onErrorResume { Mono.empty() }
                            .flatMap { saved ->
                                current.penaltyWeeksAccrued = maxOf(current.penaltyWeeksAccrued, weekIndex)
                                current.penaltyTotalAccrued =
                                    current.penaltyTotalAccrued.add(policy.weeklyPenaltyAmount)
                                stateRepository.save(current)
                                    .doOnNext { states[child.studentId] = it }
                                    .then(
                                        emitToGuardians(
                                            schoolId = schoolId,
                                            academicYearId = academicYearId,
                                            today = today,
                                            kind = FeeReminderKind.PENALTY_WEEKLY,
                                            anchorDate = saved.weekStartDate,
                                            child = child,
                                            policy = policy,
                                            guardians = guardians,
                                            linkedAccounts = linkedAccounts,
                                            states = states,
                                            extra = mapOf(
                                                "weekIndex" to weekIndex,
                                                "penaltyAmount" to policy.weeklyPenaltyAmount.toPlainString(),
                                                "penaltyTotal" to current.penaltyTotalAccrued.toPlainString()
                                            )
                                        )
                                    )
                            }
                    }
                    .then()
            )
        }

        if (FeesReminderPlanner.shouldWarnExclusion(
                today,
                insolvencySince,
                policy,
                current.exclusionWarningSent
            )
        ) {
            chain = chain.then(
                Mono.defer {
                    current.exclusionWarningSent = true
                    val exclusionDate =
                        insolvencySince.plusDays(policy.maxInsolvencyGraceDays.toLong())
                    stateRepository.save(current)
                        .doOnNext { states[child.studentId] = it }
                        .then(
                            emitToGuardians(
                                schoolId = schoolId,
                                academicYearId = academicYearId,
                                today = today,
                                kind = FeeReminderKind.EXCLUSION_WARNING,
                                anchorDate = exclusionDate,
                                child = child,
                                policy = policy,
                                guardians = guardians,
                                linkedAccounts = linkedAccounts,
                                states = states,
                                extra = mapOf(
                                    "maxInsolvencyGraceDays" to policy.maxInsolvencyGraceDays,
                                    "exclusionDate" to exclusionDate.format(dayFmt)
                                )
                            )
                        )
                }
            )
        }

        if (FeesReminderPlanner.shouldExclude(
                today,
                insolvencySince,
                policy,
                current.excludedAt != null
            ) && child.active
        ) {
            chain = chain.then(
                studentApi.deactivateForFeeExclusion(
                    schoolId,
                    child.studentId,
                    "FEES_INSOLVENCY_EXCLUSION"
                )
                    .flatMap {
                        current.excludedAt = Instant.now()
                        current.exclusionReasonCode = "FEES_INSOLVENCY_EXCLUSION"
                        stateRepository.save(current)
                            .doOnNext { states[child.studentId] = it }
                    }
                    .then(
                        emitToGuardians(
                            schoolId = schoolId,
                            academicYearId = academicYearId,
                            today = today,
                            kind = FeeReminderKind.EXCLUDED,
                            anchorDate = today,
                            child = child.copy(active = false),
                            policy = policy,
                            guardians = guardians,
                            linkedAccounts = linkedAccounts,
                            states = states,
                            extra = mapOf(
                                "reasonCode" to "FEES_INSOLVENCY_EXCLUSION",
                                "penaltyTotal" to current.penaltyTotalAccrued.toPlainString()
                            )
                        )
                    )
            )
        }

        return chain
    }

    private fun emitGroupedParentReminders(
        schoolId: String,
        academicYearId: String,
        policy: FeesPolicy,
        today: LocalDate,
        children: List<FeesReminderPlanner.ChildAccountView>,
        guardians: List<StudentGuardiansDto>,
        linkedAccounts: Map<String, String>,
        states: Map<String, FeeStudentFeeState>
    ): Mono<Void> {
        data class Pending(
            val accountId: String,
            val trigger: FeesReminderPlanner.ReminderTrigger,
            val child: FeesReminderPlanner.ChildAccountView
        )

        val guardianMap = guardians.associateBy { it.studentId }
        val pending = mutableListOf<Pending>()
        for (child in children) {
            if (!child.active) continue
            val triggers = FeesReminderPlanner.installmentReminderTriggers(today, policy, child)
            if (triggers.isEmpty()) continue
            val accountIds = guardianMap[child.studentId]
                ?.guardianIds
                .orEmpty()
                .mapNotNull { linkedAccounts[it] }
                .distinct()
            for (trigger in triggers) {
                for (accountId in accountIds) {
                    pending += Pending(accountId, trigger, child)
                }
            }
        }

        val groups = pending.groupBy {
            listOf(
                it.accountId,
                it.trigger.kind.name,
                it.trigger.daysBeforeDue?.toString().orEmpty(),
                it.trigger.anchorDate.format(dayFmt)
            )
        }

        return Flux.fromIterable(groups.values)
            .concatMap { items ->
                val first = items.first()
                val trigger = first.trigger
                val kids = items.map { it.child }.distinctBy { it.studentId }
                val dedupeKey =
                    "fees-reminder:$schoolId:${first.accountId}:${trigger.kind.name}:" +
                        "${trigger.daysBeforeDue ?: -1}:${trigger.anchorDate.format(dayFmt)}:${today.format(dayFmt)}"
                outboxWriter.append(
                    schoolId = schoolId,
                    module = "fees",
                    aggregateType = "FeesReminder",
                    aggregateId = dedupeKey,
                    eventType = "fees.tuition.reminder",
                    payload = buildReminderPayload(
                        schoolId = schoolId,
                        academicYearId = academicYearId,
                        accountId = first.accountId,
                        today = today,
                        kind = trigger.kind,
                        daysBeforeDue = trigger.daysBeforeDue,
                        severityHint = trigger.severityHint.name,
                        dedupeKey = dedupeKey,
                        children = kids,
                        policy = policy,
                        states = states
                    ),
                    schemaVersion = 1
                ).then()
            }
            .then()
    }

    private fun emitToGuardians(
        schoolId: String,
        academicYearId: String,
        today: LocalDate,
        kind: FeeReminderKind,
        anchorDate: LocalDate,
        child: FeesReminderPlanner.ChildAccountView,
        policy: FeesPolicy,
        guardians: List<StudentGuardiansDto>,
        linkedAccounts: Map<String, String>,
        states: Map<String, FeeStudentFeeState>,
        extra: Map<String, Any?>
    ): Mono<Void> {
        val accountIds = guardians
            .firstOrNull { it.studentId == child.studentId }
            ?.guardianIds
            .orEmpty()
            .mapNotNull { linkedAccounts[it] }
            .distinct()
        if (accountIds.isEmpty()) return Mono.empty()

        val severity = when (kind) {
            FeeReminderKind.INSOLVENT,
            FeeReminderKind.EXCLUSION_WARNING,
            FeeReminderKind.EXCLUDED -> "URGENT"
            FeeReminderKind.PENALTY_WEEKLY -> "WARNING"
            else -> "INFO"
        }

        return Flux.fromIterable(accountIds)
            .concatMap { accountId ->
                val dedupeKey =
                    "fees-reminder:$schoolId:$accountId:${kind.name}:" +
                        "${anchorDate.format(dayFmt)}:${today.format(dayFmt)}"
                outboxWriter.append(
                    schoolId = schoolId,
                    module = "fees",
                    aggregateType = "FeesReminder",
                    aggregateId = dedupeKey,
                    eventType = "fees.tuition.reminder",
                    payload = buildReminderPayload(
                        schoolId = schoolId,
                        academicYearId = academicYearId,
                        accountId = accountId,
                        today = today,
                        kind = kind,
                        daysBeforeDue = null,
                        severityHint = severity,
                        dedupeKey = dedupeKey,
                        children = listOf(child),
                        policy = policy,
                        states = states,
                        extra = extra
                    ),
                    schemaVersion = 1
                ).then()
            }
            .then()
    }

    private fun buildReminderPayload(
        schoolId: String,
        academicYearId: String,
        accountId: String,
        today: LocalDate,
        kind: FeeReminderKind,
        daysBeforeDue: Int?,
        severityHint: String,
        dedupeKey: String,
        children: List<FeesReminderPlanner.ChildAccountView>,
        policy: FeesPolicy,
        states: Map<String, FeeStudentFeeState>,
        extra: Map<String, Any?> = emptyMap()
    ): Map<String, Any?> {
        val childPayloads = children.map { child ->
            val paid = child.installments.filter { it.settled }
            val unpaid = child.installments.filter { !it.settled }
            val upcoming = unpaid.filter { !it.dueDate.isBefore(today) }.minByOrNull { it.dueDate }
            val state = states[child.studentId]
            mapOf(
                "studentId" to child.studentId,
                "displayName" to child.displayName,
                "matricule" to child.matricule,
                "solvency" to child.solvency.name,
                "active" to child.active,
                "enrollmentFeeDue" to child.enrollmentFeeDue.toPlainString(),
                "enrollmentFeePaid" to child.enrollmentFeePaid.toPlainString(),
                "enrollmentSettled" to child.enrollmentSettled,
                "paidInstallments" to paid.map {
                    mapOf(
                        "index" to it.index,
                        "amount" to it.amount.toPlainString(),
                        "dueDate" to it.dueDate.format(dayFmt),
                        "paidAmount" to it.paidAmount.toPlainString()
                    )
                },
                "unpaidInstallments" to unpaid.map {
                    mapOf(
                        "index" to it.index,
                        "amount" to it.amount.toPlainString(),
                        "dueDate" to it.dueDate.format(dayFmt),
                        "paidAmount" to it.paidAmount.toPlainString()
                    )
                },
                "nextDueDate" to upcoming?.dueDate?.format(dayFmt),
                "nextDueAmount" to upcoming?.amount?.toPlainString(),
                "penaltyTotal" to (state?.penaltyTotalAccrued ?: BigDecimal.ZERO).toPlainString(),
                "currency" to child.currency
            )
        }
        return mapOf(
            "dedupeKey" to dedupeKey,
            "recipientAccountId" to accountId,
            "kind" to kind.name,
            "daysBeforeDue" to daysBeforeDue,
            "severity" to severityHint,
            "today" to today.format(dayFmt),
            "academicYearId" to academicYearId,
            "weeklyPenaltyAmount" to policy.weeklyPenaltyAmount.toPlainString(),
            "currency" to policy.currency,
            "maxInsolvencyGraceDays" to policy.maxInsolvencyGraceDays,
            "children" to childPayloads,
            "schoolId" to schoolId
        ) + extra
    }

    private fun toChildView(
        studentId: String,
        displayName: String,
        matricule: String,
        active: Boolean,
        schedule: TuitionSchedule,
        payments: List<FeePayment>,
        today: LocalDate
    ): FeesReminderPlanner.ChildAccountView {
        val enrollmentPaid = sumPayments(payments, FeePaymentKind.ENROLLMENT, null)
        val installments = schedule.installments.sortedBy { it.index }.map { inst ->
            val paid = sumPayments(payments, FeePaymentKind.INSTALLMENT, inst.index)
            FeesReminderPlanner.InstallmentView(
                index = inst.index,
                amount = inst.amount,
                dueDate = inst.dueDate,
                paidAmount = paid,
                settled = SolvencyCalculator.isSettled(inst.amount, paid)
            )
        }
        val status = SolvencyCalculator.compute(
            today = today,
            enrollmentFeeDue = schedule.enrollmentFee,
            enrollmentFeePaid = enrollmentPaid,
            installments = installments.map {
                SolvencyCalculator.InstallmentInput(
                    index = it.index,
                    amount = it.amount,
                    dueDate = it.dueDate,
                    paidAmount = it.paidAmount
                )
            }
        )
        return FeesReminderPlanner.ChildAccountView(
            studentId = studentId,
            displayName = displayName,
            matricule = matricule,
            active = active,
            solvency = status,
            enrollmentFeeDue = schedule.enrollmentFee,
            enrollmentFeePaid = enrollmentPaid,
            enrollmentSettled = SolvencyCalculator.isSettled(schedule.enrollmentFee, enrollmentPaid),
            installments = installments,
            currency = schedule.currency
        )
    }

    private fun sumPayments(
        payments: List<FeePayment>,
        kind: FeePaymentKind,
        installmentIndex: Int?
    ): BigDecimal =
        payments
            .filter { it.kind == kind && it.installmentIndex == installmentIndex }
            .fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amount) }
}
