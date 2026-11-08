package com.kobe.smels.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "smels.security")
data class SecurityProperties(
    val jwt: Jwt = Jwt(),
    val lockout: Lockout = Lockout(),
    val cors: Cors = Cors(),
    val bootstrap: Bootstrap = Bootstrap()
) {
    data class Jwt(
        val secret: String = "local-dev-only-change-me-not-for-production",
        val accessTokenValidityMs: Long = 900_000,
        val refreshTokenValidityMs: Long = 604_800_000
    )

    data class Lockout(
        val maxFailedAttempts: Int = 5,
        val lockDurationMinutes: Long = 15
    )

    data class Cors(
        val allowedOrigins: List<String> = listOf("http://localhost:3000")
    )

    data class Bootstrap(
        val enabled: Boolean = false,
        val username: String = "superadmin",
        val password: String = "",
        val schoolId: String? = null
    )
}
