package com.kobe.smels.service.notification.api

import com.kobe.smels.dto.notification.InAppNotificationResponse
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Interface publique du module notification (docs/08).
 * Le portail consomme les compteurs / aperçus sans toucher aux repositories.
 */
interface NotificationApi {
    fun countUnread(schoolId: String, accountId: String): Mono<Long>
    fun listRecent(schoolId: String, accountId: String, limit: Int): Flux<InAppNotificationResponse>
}
