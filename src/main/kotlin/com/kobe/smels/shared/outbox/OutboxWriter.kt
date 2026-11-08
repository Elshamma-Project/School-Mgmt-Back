package com.kobe.smels.shared.outbox

import com.kobe.smels.model.shared.OutboxEvent
import com.kobe.smels.model.shared.OutboxStatus
import com.kobe.smels.repository.shared.OutboxEventRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * Écriture dans la boîte d'envoi commune (docs/08 §4).
 * Les modules métier passent par ce composant, jamais par le repository d'un autre module.
 */
@Component
class OutboxWriter(
    private val outboxEventRepository: OutboxEventRepository
) {
    fun append(
        schoolId: String,
        module: String,
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        payload: Map<String, Any?> = emptyMap(),
        schemaVersion: Int = 1
    ): Mono<OutboxEvent> {
        val event = OutboxEvent(
            schoolId = schoolId,
            module = module,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            schemaVersion = schemaVersion,
            payload = payload,
            occurredAt = Instant.now(),
            status = OutboxStatus.PENDING
        )
        return outboxEventRepository.save(event)
    }
}
