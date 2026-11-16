package com.kobe.beesm.shared.outbox

import com.kobe.beesm.model.shared.OutboxEvent
import reactor.core.publisher.Mono

/** Consommateur d’un type d’événement outbox (idempotent). */
interface OutboxEventHandler {
    fun supports(eventType: String): Boolean
    fun handle(event: OutboxEvent): Mono<Void>
}
