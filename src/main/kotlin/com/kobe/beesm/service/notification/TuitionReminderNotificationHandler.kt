package com.kobe.beesm.service.notification

import com.kobe.beesm.model.notification.NotificationCategory
import com.kobe.beesm.model.notification.NotificationSeverity
import com.kobe.beesm.model.shared.LocalizedText
import com.kobe.beesm.model.shared.OutboxEvent
import com.kobe.beesm.shared.outbox.OutboxEventHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * Inbox parent pour `fees.tuition.reminder` (BR-F10…F14).
 * Un événement = un destinataire ; multi-enfants déjà regroupés dans le payload.
 */
@Component
class TuitionReminderNotificationHandler(
    private val notificationService: NotificationService
) : OutboxEventHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(eventType: String): Boolean = eventType == "fees.tuition.reminder"

    override fun handle(event: OutboxEvent): Mono<Void> {
        val payload = event.payload
        val accountId = payload["recipientAccountId"]?.toString()?.trim().orEmpty()
        if (accountId.isEmpty()) {
            log.warn("fees.tuition.reminder missing recipientAccountId eventId={}", event.id)
            return Mono.empty()
        }
        val dedupeKey = payload["dedupeKey"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: event.id
            ?: return Mono.empty()
        val kind = payload["kind"]?.toString()?.uppercase().orEmpty()
        val daysBefore = when (val d = payload["daysBeforeDue"]) {
            is Number -> d.toInt()
            is String -> d.toIntOrNull()
            else -> null
        }
        @Suppress("UNCHECKED_CAST")
        val children = (payload["children"] as? List<*>)?.mapNotNull { row ->
            (row as? Map<*, *>)?.let { ChildLine.from(it) }
        }.orEmpty()

        val currency = payload["currency"]?.toString() ?: "XAF"
        val weeklyPenalty = payload["weeklyPenaltyAmount"]?.toString()
        val maxGrace = when (val g = payload["maxInsolvencyGraceDays"]) {
            is Number -> g.toInt()
            is String -> g.toIntOrNull()
            else -> null
        }
        val exclusionDate = payload["exclusionDate"]?.toString()
        val penaltyAmount = payload["penaltyAmount"]?.toString()
        val penaltyTotal = payload["penaltyTotal"]?.toString()

        val title = titleFor(kind, daysBefore)
        val body = bodyFor(
            kind = kind,
            daysBefore = daysBefore,
            children = children,
            currency = currency,
            weeklyPenalty = weeklyPenalty,
            maxGrace = maxGrace,
            exclusionDate = exclusionDate,
            penaltyAmount = penaltyAmount,
            penaltyTotal = penaltyTotal
        )
        val severity = severityFor(payload["severity"]?.toString(), kind)

        return notificationService.deliver(
            schoolId = event.schoolId,
            recipientAccountId = accountId,
            category = categoryFor(kind),
            severity = severity,
            title = title,
            body = body,
            sourceEventId = dedupeKey,
            sourceEventType = event.eventType,
            resourceType = "FeesReminder",
            resourceId = kind.ifBlank { "REMINDER" },
            deepLink = "/fees",
            ackRequired = kind == "EXCLUDED" || kind == "EXCLUSION_WARNING" || kind == "INSOLVENT"
        ).then()
    }

    private fun categoryFor(kind: String): NotificationCategory =
        when (kind) {
            "INSOLVENT" -> NotificationCategory.FEES_INSOLVENT
            "PENALTY_WEEKLY" -> NotificationCategory.FEES_PENALTY
            "EXCLUSION_WARNING", "EXCLUDED" -> NotificationCategory.FEES_EXCLUSION
            else -> NotificationCategory.FEES_REMINDER
        }

    private fun severityFor(hint: String?, kind: String): NotificationSeverity {
        when (hint?.uppercase()) {
            "URGENT" -> return NotificationSeverity.URGENT
            "WARNING" -> return NotificationSeverity.WARNING
            "INFO" -> return NotificationSeverity.INFO
            "SUCCESS" -> return NotificationSeverity.SUCCESS
        }
        return when (kind) {
            "INSOLVENT", "EXCLUSION_WARNING", "EXCLUDED", "ON_DUE" -> NotificationSeverity.URGENT
            "PENALTY_WEEKLY" -> NotificationSeverity.WARNING
            else -> NotificationSeverity.INFO
        }
    }

    private fun titleFor(kind: String, daysBefore: Int?): LocalizedText =
        when (kind) {
            "ON_DUE" -> LocalizedText(
                fr = "Échéance de scolarité aujourd’hui",
                en = "Tuition due today"
            )
            "INSOLVENT" -> LocalizedText(
                fr = "Compte scolarité insolvable",
                en = "Tuition account insolvent"
            )
            "PENALTY_WEEKLY" -> LocalizedText(
                fr = "Pénalité de retard scolarité",
                en = "Tuition late penalty"
            )
            "EXCLUSION_WARNING" -> LocalizedText(
                fr = "Avertissement : exclusion pour insolvabilité",
                en = "Warning: exclusion for insolvency"
            )
            "EXCLUDED" -> LocalizedText(
                fr = "Exclusion pour insolvabilité",
                en = "Excluded for insolvency"
            )
            else -> {
                val d = daysBefore ?: 0
                LocalizedText(
                    fr = when {
                        d >= 15 -> "Rappel scolarité ($d jours)"
                        d >= 10 -> "Rappel important scolarité ($d jours)"
                        d >= 5 -> "Rappel urgent scolarité ($d jours)"
                        else -> "Dernier rappel scolarité ($d jours)"
                    },
                    en = when {
                        d >= 15 -> "Tuition reminder ($d days)"
                        d >= 10 -> "Important tuition reminder ($d days)"
                        d >= 5 -> "Urgent tuition reminder ($d days)"
                        else -> "Final tuition reminder ($d days)"
                    }
                )
            }
        }

    private fun bodyFor(
        kind: String,
        daysBefore: Int?,
        children: List<ChildLine>,
        currency: String,
        weeklyPenalty: String?,
        maxGrace: Int?,
        exclusionDate: String?,
        penaltyAmount: String?,
        penaltyTotal: String?
    ): LocalizedText {
        val detailsFr = children.joinToString("\n\n") { it.formatFr(currency) }
        val detailsEn = children.joinToString("\n\n") { it.formatEn(currency) }
        val headerFr = when (kind) {
            "ON_DUE" -> "Échéance aujourd’hui. Détail par enfant :"
            "INSOLVENT" -> buildString {
                append("Le statut est passé à Insolvable.")
                if (!weeklyPenalty.isNullOrBlank()) {
                    append(" Pénalité : ").append(weeklyPenalty).append(' ').append(currency)
                    append(" par semaine de retard.")
                }
                if (maxGrace != null) {
                    append(" Délai max avant exclusion : ").append(maxGrace).append(" jours.")
                }
                append("\n\n")
            }
            "PENALTY_WEEKLY" -> buildString {
                append("Une pénalité hebdomadaire a été enregistrée")
                if (!penaltyAmount.isNullOrBlank()) {
                    append(" (").append(penaltyAmount).append(' ').append(currency).append(')')
                }
                if (!penaltyTotal.isNullOrBlank()) {
                    append(". Total pénalités : ").append(penaltyTotal).append(' ').append(currency)
                }
                append(".\n\n")
            }
            "EXCLUSION_WARNING" -> buildString {
                append("Sans paiement, l’exclusion définitive est prévue")
                if (!exclusionDate.isNullOrBlank()) append(" le ").append(exclusionDate)
                append(".\n\n")
            }
            "EXCLUDED" -> "L’élève a été désactivé pour insolvabilité prolongée.\n\n"
            else -> {
                val d = daysBefore ?: 0
                "Rappel : paiement à effectuer dans $d jour(s). Détail par enfant :\n\n"
            }
        }
        val headerEn = when (kind) {
            "ON_DUE" -> "Due today. Per-child details:"
            "INSOLVENT" -> buildString {
                append("Status is now Insolvent.")
                if (!weeklyPenalty.isNullOrBlank()) {
                    append(" Penalty: ").append(weeklyPenalty).append(' ').append(currency)
                    append(" per overdue week.")
                }
                if (maxGrace != null) {
                    append(" Max grace before exclusion: ").append(maxGrace).append(" days.")
                }
                append("\n\n")
            }
            "PENALTY_WEEKLY" -> buildString {
                append("A weekly late penalty was recorded")
                if (!penaltyAmount.isNullOrBlank()) {
                    append(" (").append(penaltyAmount).append(' ').append(currency).append(')')
                }
                if (!penaltyTotal.isNullOrBlank()) {
                    append(". Total penalties: ").append(penaltyTotal).append(' ').append(currency)
                }
                append(".\n\n")
            }
            "EXCLUSION_WARNING" -> buildString {
                append("Without payment, definitive exclusion is scheduled")
                if (!exclusionDate.isNullOrBlank()) append(" on ").append(exclusionDate)
                append(".\n\n")
            }
            "EXCLUDED" -> "The student was deactivated for prolonged insolvency.\n\n"
            else -> {
                val d = daysBefore ?: 0
                "Reminder: payment due in $d day(s). Per-child details:\n\n"
            }
        }
        return LocalizedText(
            fr = headerFr + detailsFr,
            en = headerEn + detailsEn
        )
    }

    private data class ChildLine(
        val displayName: String,
        val matricule: String,
        val solvency: String,
        val enrollmentSettled: Boolean,
        val paidSummary: String,
        val unpaidSummary: String,
        val nextDueDate: String?,
        val nextDueAmount: String?,
        val penaltyTotal: String?,
        val currency: String
    ) {
        fun formatFr(fallbackCurrency: String): String {
            val cur = currency.ifBlank { fallbackCurrency }
            return buildString {
                append("• ").append(displayName)
                if (matricule.isNotBlank()) append(" (").append(matricule).append(')')
                append('\n')
                append("  Statut : ").append(solvencyLabelFr(solvency)).append('\n')
                append("  Inscription : ").append(if (enrollmentSettled) "payée" else "non payée").append('\n')
                append("  Payé : ").append(paidSummary.ifBlank { "—" }).append('\n')
                append("  Non payé : ").append(unpaidSummary.ifBlank { "—" }).append('\n')
                if (!nextDueDate.isNullOrBlank()) {
                    append("  Prochaine échéance : ").append(nextDueDate)
                    if (!nextDueAmount.isNullOrBlank()) {
                        append(" (").append(nextDueAmount).append(' ').append(cur).append(')')
                    }
                    append('\n')
                }
                if (!penaltyTotal.isNullOrBlank() && penaltyTotal != "0" && penaltyTotal != "0.00") {
                    append("  Pénalités : ").append(penaltyTotal).append(' ').append(cur).append('\n')
                }
            }.trimEnd()
        }

        fun formatEn(fallbackCurrency: String): String {
            val cur = currency.ifBlank { fallbackCurrency }
            return buildString {
                append("• ").append(displayName)
                if (matricule.isNotBlank()) append(" (").append(matricule).append(')')
                append('\n')
                append("  Status: ").append(solvencyLabelEn(solvency)).append('\n')
                append("  Enrollment fee: ").append(if (enrollmentSettled) "paid" else "unpaid").append('\n')
                append("  Paid: ").append(paidSummary.ifBlank { "—" }).append('\n')
                append("  Unpaid: ").append(unpaidSummary.ifBlank { "—" }).append('\n')
                if (!nextDueDate.isNullOrBlank()) {
                    append("  Next due: ").append(nextDueDate)
                    if (!nextDueAmount.isNullOrBlank()) {
                        append(" (").append(nextDueAmount).append(' ').append(cur).append(')')
                    }
                    append('\n')
                }
                if (!penaltyTotal.isNullOrBlank() && penaltyTotal != "0" && penaltyTotal != "0.00") {
                    append("  Penalties: ").append(penaltyTotal).append(' ').append(cur).append('\n')
                }
            }.trimEnd()
        }

        companion object {
            fun from(map: Map<*, *>): ChildLine {
                @Suppress("UNCHECKED_CAST")
                val paid = (map["paidInstallments"] as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()
                @Suppress("UNCHECKED_CAST")
                val unpaid = (map["unpaidInstallments"] as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()
                fun summarize(rows: List<Map<*, *>>): String =
                    rows.joinToString(", ") { row ->
                        val idx = row["index"]?.toString() ?: "?"
                        val amount = row["amount"]?.toString() ?: "?"
                        "T$idx=$amount"
                    }
                return ChildLine(
                    displayName = map["displayName"]?.toString().orEmpty(),
                    matricule = map["matricule"]?.toString().orEmpty(),
                    solvency = map["solvency"]?.toString().orEmpty(),
                    enrollmentSettled = map["enrollmentSettled"] as? Boolean
                        ?: (map["enrollmentSettled"]?.toString()?.toBooleanStrictOrNull() ?: false),
                    paidSummary = summarize(paid),
                    unpaidSummary = summarize(unpaid),
                    nextDueDate = map["nextDueDate"]?.toString(),
                    nextDueAmount = map["nextDueAmount"]?.toString(),
                    penaltyTotal = map["penaltyTotal"]?.toString(),
                    currency = map["currency"]?.toString().orEmpty()
                )
            }

            private fun solvencyLabelFr(code: String): String =
                when (code.uppercase()) {
                    "SOLVENT" -> "Solvable"
                    "PENDING" -> "En attente"
                    "INSOLVENT" -> "Insolvable"
                    else -> code
                }

            private fun solvencyLabelEn(code: String): String =
                when (code.uppercase()) {
                    "SOLVENT" -> "Solvent"
                    "PENDING" -> "Pending"
                    "INSOLVENT" -> "Insolvent"
                    else -> code
                }
        }
    }
}
