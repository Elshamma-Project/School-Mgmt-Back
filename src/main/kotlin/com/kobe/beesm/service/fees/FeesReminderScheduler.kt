package com.kobe.beesm.service.fees

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Rappels / pénalités / exclusion scolarité — tous les jours à 07:00 Africa/Douala (BR-F10).
 */
@Component
class FeesReminderScheduler(
    private val feesReminderService: FeesReminderService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        cron = "\${beesm.fees.reminder-cron:0 0 7 * * *}",
        zone = "Africa/Douala"
    )
    fun dailyReminders() {
        feesReminderService.runDaily()
            .doOnSubscribe { log.info("fees.reminder.scheduler start") }
            .doOnError { err -> log.error("fees.reminder.scheduler error", err) }
            .subscribe()
    }
}
