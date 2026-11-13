package com.kobe.smels.service.notification

import com.kobe.smels.model.notification.NotificationCategory
import com.kobe.smels.model.notification.NotificationSeverity
import com.kobe.smels.model.shared.LocalizedText
import com.kobe.smels.model.shared.OutboxEvent
import com.kobe.smels.service.user.api.UserAccountApi
import com.kobe.smels.shared.outbox.OutboxEventHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/** Notifie l’enseignant titulaire lorsqu’une fiche de report est rejetée. */
@Component
class ReportSheetRejectedNotificationHandler(
    private val notificationService: NotificationService,
    private val userAccountApi: UserAccountApi
) : OutboxEventHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(eventType: String): Boolean = eventType == "grading.report-sheet.rejected"

    override fun handle(event: OutboxEvent): Mono<Void> {
        val eventId = event.id ?: return Mono.empty()
        val payload = event.payload
        val reportSheetId = payload["reportSheetId"]?.toString() ?: event.aggregateId
        val classSubjectId = payload["classSubjectId"]?.toString()?.trim().orEmpty()
        val termNumber = when (val value = payload["termNumber"]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }
        val teacherId = payload["teacherId"]?.toString()?.trim().orEmpty()
        if (teacherId.isEmpty()) {
            log.info("report-sheet.rejected missing teacherId eventId={}", event.id)
            return Mono.empty()
        }
        val deepLink = buildString {
            append("/teacher/grades")
            val params = mutableListOf<String>()
            if (termNumber in 1..3) params += "term=$termNumber"
            if (classSubjectId.isNotEmpty()) params += "classSubjectId=$classSubjectId"
            if (params.isNotEmpty()) {
                append('?')
                append(params.joinToString("&"))
            }
        }
        return userAccountApi.findStaffLinkedAccounts(event.schoolId, listOf(teacherId))
            .flatMap { linked ->
                val account = linked[teacherId]
                if (account == null) {
                    log.info("report-sheet.rejected no linked account teacherId={} eventId={}", teacherId, event.id)
                    return@flatMap Mono.empty()
                }
                notificationService.deliver(
                    schoolId = event.schoolId,
                    recipientAccountId = account.accountId,
                    recipientStaffId = teacherId,
                    category = NotificationCategory.REPORT_SHEET_REJECTED,
                    severity = NotificationSeverity.WARNING,
                    title = LocalizedText(
                        fr = "Fiche de report rejetée",
                        en = "Grade report sheet rejected"
                    ),
                    body = LocalizedText(
                        fr = if (termNumber in 1..3) {
                            "Votre fiche de report du trimestre $termNumber a été rejetée. Consultez le motif et corrigez-la."
                        } else {
                            "Votre fiche de report a été rejetée. Consultez le motif et corrigez-la."
                        },
                        en = if (termNumber in 1..3) {
                            "Your term $termNumber grade report sheet was rejected. Review the reason and fix it."
                        } else {
                            "Your grade report sheet was rejected. Review the reason and fix it."
                        }
                    ),
                    sourceEventId = eventId,
                    sourceEventType = event.eventType,
                    resourceType = "GradeReportSheet",
                    resourceId = reportSheetId,
                    deepLink = deepLink,
                    ackRequired = false
                )
            }
            .then()
    }
}
