package com.kobe.beesm.repository.shared

import com.kobe.beesm.model.shared.OutboxEvent
import com.kobe.beesm.model.shared.OutboxStatus
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux

interface OutboxEventRepository : ReactiveMongoRepository<OutboxEvent, String> {
    fun findByStatusOrderByOccurredAtAsc(status: OutboxStatus): Flux<OutboxEvent>
}
