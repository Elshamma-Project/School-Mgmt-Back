package com.kobe.smels.configuration

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
import reactor.core.publisher.Mono

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@EnableConfigurationProperties(SecurityProperties::class)
class SecurityConfig(
    private val securityProperties: SecurityProperties,
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .pathMatchers(
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/public/report-cards/**",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info"
                    ).permitAll()
                    .anyExchange().authenticated()
            }
            .exceptionHandling { handling ->
                handling
                    .authenticationEntryPoint { exchange, _ ->
                        writeProblem(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required")
                    }
                    .accessDeniedHandler { exchange, _ ->
                        writeProblem(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied")
                    }
            }
            .headers { headers ->
                headers
                    .contentTypeOptions { }
                    .frameOptions { it.disable() }
                    .hsts { hsts ->
                        hsts.includeSubdomains(true).maxAge(java.time.Duration.ofDays(365))
                    }
            }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = securityProperties.cors.allowedOrigins
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            exposedHeaders = listOf("Authorization")
            allowCredentials = true
            maxAge = 3600
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }

    private fun writeProblem(
        exchange: org.springframework.web.server.ServerWebExchange,
        status: HttpStatus,
        code: String,
        detail: String
    ): Mono<Void> {
        val response = exchange.response
        response.statusCode = status
        response.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON
        val safeDetail = detail.replace("\"", "'")
        val json =
            """{"type":"urn:smels:error:${code.lowercase()}","title":"$code","status":${status.value()},"detail":"$safeDetail","code":"$code","instance":"${exchange.request.path.value()}"}"""
        val buffer = response.bufferFactory().wrap(json.toByteArray())
        return response.writeWith(Mono.just(buffer))
    }
}
