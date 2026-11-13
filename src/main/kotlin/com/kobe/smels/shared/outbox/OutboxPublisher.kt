package com.kobe.smels.shared.outbox

import com.kobe.smels.model.shared.OutboxStatus
import com.kobe.smels.repository.shared.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant

/**
 * Publieur outbox (docs/08 §4) : appelle les [OutboxEventHandler] puis marque PUBLISHED.
 * Aujourd’hui : polling interne ; demain : courtier de messages.
 */
@Component
class OutboxPublisher(
    private val outboxEventRepository: OutboxEventRepository,
    private val handlers: List<OutboxEventHandler>
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${smels.outbox.poll-ms:4000}", initialDelayString = "8000")
    fun poll() {
        try {
            dispatchBatch().block(Duration.ofSeconds(45))
        } catch (ex: Exception) {
            log.warn("outbox.poll failed: {}", ex.message)
        }
    }

    fun dispatchBatch(limit: Int = 40): Mono<Void> =
        outboxEventRepository.findByStatusOrderByOccurredAtAsc(OutboxStatus.PENDING)
            .take(limit.toLong())
            .concatMap { event -> processOne(event) }
            .then()

    private fun processOne(event: com.kobe.smels.model.shared.OutboxEvent): Mono<Void> {
        val matching = handlers.filter { it.supports(event.eventType) }
        if (matching.isEmpty()) {
            return markPublished(event)
        }
        return Flux.fromIterable(matching)
            .concatMap { handler ->
                handler.handle(event)
                    .onErrorResume { err ->
                        log.warn(
                            "outbox.handler failed type={} id={} err={}",
                            event.eventType,
                            event.id,
                            err.message
                        )
                        Mono.error(err)
                    }
            }
            .then(markPublished(event))
            .onErrorResume { err ->
                event.attempts += 1
                event.lastError = err.message?.take(400)
                if (event.attempts >= 8) {
                    event.status = OutboxStatus.FAILED
                }
                outboxEventRepository.save(event).then()
            }
    }

    private fun markPublished(event: com.kobe.smels.model.shared.OutboxEvent): Mono<Void> {
        event.status = OutboxStatus.PUBLISHED
        event.publishedAt = Instant.now()
        event.lastError = null
        return outboxEventRepository.save(event).then()
    }
}
