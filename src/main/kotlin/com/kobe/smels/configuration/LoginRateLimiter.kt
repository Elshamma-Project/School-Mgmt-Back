package com.kobe.smels.configuration

import com.kobe.smels.exception.ForbiddenException
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Limitation simple des tentatives de login par IP (fenêtre glissante).
 * Complète le verrouillage par compte (docs/05 §4).
 */
@Component
class LoginRateLimiter {
    private data class Window(val resetAt: Instant, val count: AtomicInteger)

    private val windows = ConcurrentHashMap<String, Window>()

    private val maxAttempts = 30
    private val windowSeconds = 60L

    fun check(ipAddress: String?) {
        val key = ipAddress?.takeIf { it.isNotBlank() } ?: "unknown"
        val now = Instant.now()
        val window = windows.compute(key) { _, existing ->
            if (existing == null || existing.resetAt.isBefore(now)) {
                Window(now.plusSeconds(windowSeconds), AtomicInteger(0))
            } else {
                existing
            }
        }!!
        if (window.count.incrementAndGet() > maxAttempts) {
            throw ForbiddenException("RATE_LIMITED", "Too many login attempts from this address")
        }
    }
}
