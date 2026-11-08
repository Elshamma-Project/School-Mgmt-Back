package com.kobe.smels.configuration

import com.kobe.smels.shared.security.CurrentUser
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.ReactiveAuditorAware
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import reactor.core.publisher.Mono

@Configuration
@EnableReactiveMongoAuditing
class MongoConfig {
    @Bean
    fun reactiveAuditorAware(): ReactiveAuditorAware<String> =
        ReactiveAuditorAware {
            ReactiveSecurityContextHolder.getContext()
                .mapNotNull { ctx ->
                    val principal = ctx.authentication?.principal
                    when (principal) {
                        is CurrentUser -> principal.accountId
                        else -> null
                    }
                }
                .defaultIfEmpty("system")
        }
}
