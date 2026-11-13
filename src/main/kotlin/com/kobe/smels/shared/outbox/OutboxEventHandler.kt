package com.kobe.smels.shared.outbox

import com.kobe.smels.model.shared.OutboxEvent
import reactor.core.publisher.Mono

/** Consommateur d’un type d’événement outbox (idempotent). */
interface OutboxEventHandler {
    fun supports(eventType: String): Boolean
    fun handle(event: OutboxEvent): Mono<Void>
}
