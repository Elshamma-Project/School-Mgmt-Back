package com.kobe.smels.configuration

import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * Trace chaque requête HTTP en console : méthode, chemin, statut, durée, acteur.
 * BR-43 / docs/05 : aucun identifiant d'élève, pas de corps ni de jetons.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class RequestLoggingWebFilter : WebFilter {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val request = exchange.request
        val path = request.path.pathWithinApplication().value()
        if (shouldSkip(path)) {
            return chain.filter(exchange)
        }

        val method = request.method.name()
        val query = sanitizeQuery(request)
        val startedAt = System.nanoTime()

        log.info("→ {} {}{}", method, path, query)

        return chain.filter(exchange)
            .doOnSuccess {
                logCompletion(exchange, method, path, query, startedAt, null)
            }
            .doOnError { error ->
                logCompletion(exchange, method, path, query, startedAt, error)
            }
            .doOnCancel {
                val elapsedMs = elapsedMs(startedAt)
                log.warn("✕ {} {}{} cancelled after {} ms {}", method, path, query, elapsedMs, actorSuffix(exchange))
            }
    }

    private fun logCompletion(
        exchange: ServerWebExchange,
        method: String,
        path: String,
        query: String,
        startedAt: Long,
        error: Throwable?
    ) {
        val elapsedMs = elapsedMs(startedAt)
        val status = exchange.response.statusCode?.value() ?: if (error != null) 500 else 0
        val actor = actorSuffix(exchange)
        if (error != null) {
            log.warn(
                "← {} {}{} -> {} ({} ms) error={}{}",
                method,
                path,
                query,
                status,
                elapsedMs,
                error.javaClass.simpleName,
                actor
            )
        } else if (status >= 500) {
            log.error("← {} {}{} -> {} ({} ms){}", method, path, query, status, elapsedMs, actor)
        } else if (status >= 400) {
            log.warn("← {} {}{} -> {} ({} ms){}", method, path, query, status, elapsedMs, actor)
        } else {
            log.info("← {} {}{} -> {} ({} ms){}", method, path, query, status, elapsedMs, actor)
        }
    }

    private fun actorSuffix(exchange: ServerWebExchange): String {
        val username = exchange.getAttribute<String>(ATTR_USERNAME) ?: return " [anonymous]"
        val accountId = exchange.getAttribute<String>(ATTR_ACCOUNT_ID)
        val schoolId = exchange.getAttribute<String>(ATTR_SCHOOL_ID) ?: "-"
        val roles = exchange.getAttribute<String>(ATTR_ROLES) ?: "-"
        return " [user=$username accountId=$accountId schoolId=$schoolId roles=$roles]"
    }

    private fun sanitizeQuery(request: ServerHttpRequest): String {
        val params = request.queryParams
        if (params.isEmpty()) return ""
        val safe = params.entries
            .filter { (key, _) -> key.lowercase() !in SENSITIVE_QUERY_KEYS }
            .joinToString("&") { (key, values) ->
                values.joinToString("&") { value -> "$key=$value" }
            }
        return if (safe.isBlank()) "" else "?$safe"
    }

    private fun shouldSkip(path: String): Boolean =
        path.startsWith("/actuator/health") || path == "/favicon.ico"

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000L

    companion object {
        const val ATTR_USERNAME = "smels.log.username"
        const val ATTR_ACCOUNT_ID = "smels.log.accountId"
        const val ATTR_SCHOOL_ID = "smels.log.schoolId"
        const val ATTR_ROLES = "smels.log.roles"

        private val SENSITIVE_QUERY_KEYS = setOf(
            "token",
            "access_token",
            "refresh_token",
            "password",
            "secret"
        )
    }
}
