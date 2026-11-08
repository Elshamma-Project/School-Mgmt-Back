package com.kobe.smels.shared.security

import com.kobe.smels.exception.UnauthorizedException
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import reactor.core.publisher.Mono

object SecuritySupport {
    fun currentUser(): Mono<CurrentUser> =
        ReactiveSecurityContextHolder.getContext()
            .flatMap { context ->
                val authentication = context.authentication
                val principal = authentication?.principal
                if (authentication != null && authentication.isAuthenticated && principal is CurrentUser) {
                    Mono.just(principal)
                } else {
                    Mono.empty()
                }
            }
            .switchIfEmpty(Mono.error(UnauthorizedException("UNAUTHORIZED", "Authentication required")))
}
